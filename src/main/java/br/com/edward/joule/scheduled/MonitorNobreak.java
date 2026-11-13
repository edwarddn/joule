// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.scheduled;

import br.com.edward.joule.enums.EnumFlagNobreak;
import br.com.edward.joule.enums.EnumStatusNobreak.Acao;
import br.com.edward.joule.model.NobreakModel;
import br.com.edward.joule.service.NobreakService;
import br.com.edward.joule.service.SshService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnExpression("!'${nobreak.serial-port:}'.isEmpty()")
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class MonitorNobreak {

    static final int SEGUNDOS_EMERGENCIA = 30;

    private static final Set<EnumFlagNobreak> AVISOS_LOGADOS =
            EnumSet.of(EnumFlagNobreak.SEM_BATERIA, EnumFlagNobreak.BATERIA_VELHA, EnumFlagNobreak.CARGA_ALTA);

    private final AtomicInteger count = new AtomicInteger(0);
    private boolean inShutdown = false;
    private Set<EnumFlagNobreak> avisosAtivos = EnumSet.noneOf(EnumFlagNobreak.class);

    private final NobreakService nobreakService;
    private final SshService sshService;

    @Value("${nobreak.shutdown-enabled}")
    private boolean shutdownEnabled;

    @Value("${nobreak.shutdown-steps}")
    private int shutdownSteps;

    @Value("${nobreak.shutdown-delay-seconds}")
    private int shutdownDelaySeconds;

    @PostConstruct
    void validar() {
        if (this.shutdownSteps < 1) {
            throw new IllegalStateException("SHUTDOWN_STEPS inválido (" + this.shutdownSteps + "). Use 1 ou mais.");
        }
        if (!this.shutdownEnabled) {
            log.warn("| Desligamento automático DESATIVADO (SHUTDOWN_ENABLED=false). Nem nobreak nem servidores serão desligados.");
        }
    }

    @Scheduled(
            initialDelayString = "${nobreak.monitor-interval-seconds}",
            fixedDelayString = "${nobreak.monitor-interval-seconds}",
            timeUnit = TimeUnit.SECONDS
    )
    public void monitorar() {

        final var status = this.nobreakService.status();
        if (Objects.isNull(status)) {
            log.warn("| Status null ignorado...");
            return;
        }

        this.logarAvisosNovos(status);

        switch (status.getStatus().getAcao()) {
            case EMERGENCIA, CALMA -> this.tratarFalha(status);
            case CONTAR_FALTA -> this.tratarFalta(status);
            case NENHUMA -> {
                if (status.getInputVoltage() < 50) {
                    this.tratarFalta(status);
                } else {
                    this.normal();
                }
            }
        }
    }

    private void tratarFalha(final NobreakModel status) {
        if (this.inShutdown) {
            return;
        }

        final var confirmacao = this.nobreakService.status();
        if (Objects.isNull(confirmacao) || !ehFalha(confirmacao.getStatus().getAcao())) {
            log.warn("| Falha do nobreak não confirmada na releitura ({}). Ignorando.", status.getStatus().getDescricao());
            return;
        }

        final var emergencia = confirmacao.getStatus().getAcao() == Acao.EMERGENCIA;
        final var segundos = emergencia ? SEGUNDOS_EMERGENCIA : this.shutdownDelaySeconds;
        log.warn("| {}: {} (alertas: {}). Desligando em {}s, sem religamento automático.",
                emergencia ? "EMERGÊNCIA" : "FALHA",
                confirmacao.getStatus().getDescricao(),
                confirmacao.alertasTexto(),
                segundos);

        this.executarDesligamento(() -> this.nobreakService.desliga(segundos, false));
    }

    private void tratarFalta(final NobreakModel status) {
        final var tentativaAtual = this.count.incrementAndGet();
        log.warn("| Atenção: operando sem rede principal (Status: {} | Bateria: {}% | Entrada: {}V). Tentativa {}/{}",
                status.getStatus().getDescricao(),
                status.getBatteryPercentage().intValue(),
                status.getInputVoltage(),
                tentativaAtual,
                this.shutdownSteps);

        if (!this.inShutdown && tentativaAtual >= this.shutdownSteps) {
            log.info("| Tempo limite de segurança esgotado. Desligando.");
            this.executarDesligamento(this.nobreakService::desliga);
        }
    }

    private void normal() {
        if (this.inShutdown) {
            this.inShutdown = false;
        }
        if (this.count.get() > 0) {
            log.info("| Energia estabilizada (rede OK). Contador resetado.");
            this.count.set(0);
        }
    }

    private void logarAvisosNovos(final NobreakModel status) {
        final var atuais = status.getFlags().stream()
                .filter(AVISOS_LOGADOS::contains)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EnumFlagNobreak.class)));
        final var novos = EnumSet.copyOf(atuais);
        novos.removeAll(this.avisosAtivos);
        this.avisosAtivos = atuais;

        novos.forEach(aviso -> log.warn("| Aviso do nobreak: {}.", aviso.getDescricao()));
    }

    private static boolean ehFalha(final Acao acao) {
        return acao == Acao.EMERGENCIA || acao == Acao.CALMA;
    }

    // os servidores sao desligados mesmo se o comando do nobreak falhar, e vice-versa
    private void executarDesligamento(final Runnable comandoNobreak) {
        this.inShutdown = true;

        if (!this.shutdownEnabled) {
            log.warn("| Desligamento automático desativado (SHUTDOWN_ENABLED=false). Nenhum comando foi enviado.");
            return;
        }

        var falhou = false;

        try {
            comandoNobreak.run();
        } catch (final Exception e) {
            falhou = true;
            log.error("| Erro ao armar o desligamento do nobreak: {}", e.getMessage(), e);
        }

        try {
            this.sshService.shutdownAllServers();
        } catch (final Exception e) {
            falhou = true;
            log.error("| Erro ao desligar os servidores: {}", e.getMessage(), e);
        }

        if (falhou) {
            this.inShutdown = false;
        }
    }
}
