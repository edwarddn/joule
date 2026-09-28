// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.scheduled;

import br.com.edward.joule.enums.EnumStatusNobreak;
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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnExpression("!'${nobreak.serial-port:}'.isEmpty()")
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class MonitorNobreak {

    private final AtomicInteger count = new AtomicInteger(0);
    private boolean inShutdown = false;

    private final NobreakService nobreakService;
    private final SshService sshService;

    @Value("${nobreak.shutdown-enabled}")
    private boolean shutdownEnabled;

    @Value("${nobreak.shutdown-steps}")
    private int shutdownSteps;

    @Value("${nobreak.critical-temperature}")
    private int criticalTemperature;

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

        if (status.getTemperature() > this.criticalTemperature) {
            if (!this.inShutdown) {
                log.warn("| EMERGÊNCIA: temperatura crítica ({}°C). Desligando IMEDIATAMENTE.", status.getTemperature());
                this.desligarForce();
            }
            return;
        }

        if (status.getStatus().isCritico()) {
            if (!this.inShutdown) {
                log.warn("| EMERGÊNCIA: status crítico ({}). Desligando IMEDIATAMENTE.", status.getStatus().getDescricao());
                this.desligarForce();
            }
            return;
        }

        if (EnumStatusNobreak.EM_BATERIA.equals(status.getStatus()) || status.getInputVoltage() < 50) {
            final var tentativaAtual = this.count.incrementAndGet();
            log.warn("| Atenção: operando sem rede principal (Status: {} | Bateria: {}% | Entrada: {}V). Tentativa {}/{}",
                    status.getStatus().getDescricao(),
                    status.getBatteryPercentage().intValue(),
                    status.getInputVoltage(),
                    tentativaAtual,
                    this.shutdownSteps);

            if (!this.inShutdown && tentativaAtual >= this.shutdownSteps) {
                log.info("| Tempo limite de segurança esgotado. Desligando.");
                this.desligar();
            }
        } else {
            if (this.inShutdown) {
                this.inShutdown = false;
            }
            if (this.count.get() > 0) {
                log.info("| Energia estabilizada (rede OK). Contador resetado.");
                this.count.set(0);
            }
        }
    }

    private void desligar() {
        this.executarDesligamento(this.nobreakService::desliga);
    }

    private void desligarForce() {
        this.executarDesligamento(() -> this.nobreakService.desliga(10, false));
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
