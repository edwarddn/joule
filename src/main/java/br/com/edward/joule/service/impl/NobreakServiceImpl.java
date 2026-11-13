// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service.impl;

import br.com.edward.joule.enums.EnumFlagNobreak;
import br.com.edward.joule.enums.EnumModeloEasy;
import br.com.edward.joule.integration.MqttIntegration;
import br.com.edward.joule.integration.SerialPortIntegration;
import br.com.edward.joule.model.HaConfigModel;
import br.com.edward.joule.model.HaDeviceModel;
import br.com.edward.joule.model.NobreakModel;
import br.com.edward.joule.nobreak.RagtechEasy;
import br.com.edward.joule.nobreak.RagtechMemoria;
import br.com.edward.joule.service.HomeAssistantService;
import br.com.edward.joule.service.NobreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static br.com.edward.joule.nobreak.RagtechEasy.*;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class NobreakServiceImpl implements NobreakService {

    private final HomeAssistantService homeAssistantService;

    private final MqttIntegration mqttIntegration;

    private final SerialPortIntegration serialPortIntegration;

    private final JsonMapper jsonMapper;

    private final AtomicBoolean modeloAvisado = new AtomicBoolean(false);

    @Value("${spring.application.version}")
    private String version;

    @Value("${nobreak.shutdown-delay-seconds}")
    private int shutdownDelaySeconds;

    @Value("${nobreak.critical-temperature}")
    private int criticalTemperature;

    @Value("${mqtt.state-topic}")
    private String stateTopic;

    @Value("${homeassistant.device-id}")
    private String deviceId;

    @Override
    public NobreakModel status() {
        final var memoria = this.lerMemoria();
        if (Objects.isNull(memoria)) {
            return null;
        }

        final var model = RagtechEasy.decodificar(memoria, this.criticalTemperature);
        this.avisarModelo(model);
        this.publicarEstado(model);
        return model;
    }

    @Override
    public void desliga() {
        this.desliga(this.shutdownDelaySeconds);
    }

    @Override
    public void desliga(final int segundos) {
        this.desliga(segundos, true);
    }

    @Override
    public void desliga(final int segundos, final boolean religarAutomaticamente) {
        if (segundos < 1 || segundos > 90) {
            throw new IllegalArgumentException("Tempo inválido! Use entre 1 e 90 segundos.");
        }

        log.warn("| INICIANDO SHUTDOWN ({}s) | Modo religar: {}", segundos, religarAutomaticamente);

        final var religar = religarAutomaticamente ? this.ligar(F_AUTOSTART) : this.desligar(F_AUTOSTART);
        if (!religar) {
            log.error("| Nobreak não confirmou o F_AUTOSTART={}.", religarAutomaticamente ? 1 : 0);
        }

        if (!this.serialPortIntegration.gravarByte(V_SHUTDOWNTIMER, segundos)) {
            throw new IllegalStateException("Nobreak não confirmou o timer de desligamento (" + segundos + "s).");
        }

        log.warn("| COMANDO ENVIADO! Saída do nobreak cai em {} segundos.", segundos);
    }

    @Override
    public void definirSensibilidadeRede(final boolean ligada) {
        log.info("| Nobreak: sensibilidade de rede (F_LINESENS) {}", ligada ? "ligada" : "desligada");
        this.confirmar(ligada ? this.ligar(F_LINESENS) : this.desligar(F_LINESENS), "setLineSens");
    }

    @Override
    public void alterarCorLed(final int vermelho, final int verde, final int azul, final int aleatorio) {
        log.info("| Nobreak: LED RGB({}, {}, {}) aleatório={}", vermelho, verde, azul, aleatorio);
        this.confirmar(this.serialPortIntegration.gravarByte(V_LEDRED, vermelho)
                && this.serialPortIntegration.gravarByte(V_LEDGREEN, verde)
                && this.serialPortIntegration.gravarByte(V_LEDBLUE, azul)
                && this.serialPortIntegration.gravarByte(V_RANDOM, aleatorio), "changeColor");
    }

    @Override
    public void iniciarDescargaCompleta() {
        log.warn("| Nobreak: iniciando descarga completa da bateria (fullDischarge).");
        this.confirmar(this.desligar(F_OLDBAT) && this.ligar(F_OPCHECKUP) && this.ligar(F_TRANSFINV), "fullDischarge");
    }

    @Override
    public void cancelarDescargaCompleta() {
        log.info("| Nobreak: cancelando descarga completa (cancelDischarge).");
        this.confirmar(this.desligar(F_OPCHECKUP), "cancelDischarge");
    }

    @Override
    public void agendarReligamento(final long segundos) {
        this.validarContador(segundos);
        log.info("| Nobreak: religamento em {}s (wakeUp).", segundos);
        this.confirmar(this.gravarContador(RagtechEasy.contador(segundos, 1.0)) && this.ligar(F_NIGHTOFFON), "wakeUp");
    }

    @Override
    public void agendarDesligamentoNoturno(final long segundos) {
        this.validarContador(segundos);
        final var memoria = this.lerMemoria();
        final var fator = RagtechEasy.fatorFrequencia(Objects.isNull(memoria) ? null : RagtechEasy.frequencia(memoria));
        log.info("| Nobreak: desligamento noturno de {}s (nightOff, fator de frequência {}).", segundos, fator);
        this.confirmar(this.gravarContador(RagtechEasy.contador(segundos, fator)) && this.desligar(F_NIGHTOFFON),
                "nightOff");
    }

    @Override
    public void supervisao(final boolean ligada) {
        this.confirmar(ligada ? this.ligar(F_SUPERVON) : this.desligar(F_SUPERVON),
                ligada ? "superviseOn" : "superviseOff");
    }

    @Override
    public void registrarNobreak() {
        final var device = HaDeviceModel.builder()
                .identifiers(List.of(this.deviceId))
                .name("Nobreak")
                .manufacturer("Ragtech")
                .model("Linha EASY (New Easy Pro)")
                .sw_version(this.version)
                .build();

        this.sensor(device, "Tensão Entrada", "in_volt", "voltage", "V", "input_volt", "mdi:flash-outline", 1, null);
        this.sensor(device, "Tensão Saída", "out_volt", "voltage", "V", "output_volt", "mdi:flash", 1, null);
        this.sensor(device, "Corrente Saída", "out_amp", "current", "A", "output_amp", "mdi:current-ac", 2, null);
        this.sensor(device, "Potência (estimada)", "power", "power", "W", "real_power", "mdi:lightning-bolt", 0, null);
        this.sensor(device, "Frequência", "freq", "frequency", "Hz", "frequency", "mdi:sine-wave", null, null);
        this.sensor(device, "Tensão Bateria", "bat_volt", "voltage", "V", "battery_volt", "mdi:car-battery", 1, null);
        this.sensor(device, "Nível Bateria", "bat_level", "battery", "%", "battery_pct", null, null, null);
        this.sensor(device, "Temperatura Interna", "temp", "temperature", "°C", "temperature", "mdi:thermometer", null, null);
        this.sensor(device, "Carga de Saída", "load", null, "%", "load_pct", "mdi:gauge", null, null);
        this.sensor(device, "Tensão Nominal Entrada", "nominal_in_volt", "voltage", "V", "nominal_input_volt", null,
                null, "diagnostic");
        this.sensor(device, "Potência Nominal", "nominal_power", "apparent_power", "VA", "nominal_power", null,
                null, "diagnostic");

        this.texto(device, "Status Operacional", "status", "status_desc", "mdi:information-outline", null);
        this.texto(device, "Alertas", "alertas", "alertas", "mdi:alert-outline", null);
        this.texto(device, "Desligamento", "desligamento", "desligamento", "mdi:timer-outline", null);
        this.texto(device, "Modelo", "modelo", "modelo_nome", "mdi:information-outline", "diagnostic");
        this.texto(device, "Firmware", "firmware", "firmware", "mdi:chip", "diagnostic");
        this.texto(device, "Sensibilidade de Rede", "line_sense", "line_sense", "mdi:tune-variant", "diagnostic");

        this.binario(device, "Em Bateria", "em_bateria", "'EM_BATERIA' in value_json.flags", null, "mdi:battery-arrow-down");
        this.binario(device, "Bateria Baixa", "bateria_baixa", "'BATERIA_BAIXA' in value_json.flags", "battery", null);
        this.binario(device, "Sobrecarga", "sobrecarga",
                "'CARGA_ALTA' in value_json.flags or 'FALHA_SOBRECARGA' in value_json.flags", "problem", null);
        this.binario(device, "Falha", "falha", "value_json.falha", "problem", null);
        this.binario(device, "Rede Sincronizada", "rede_sincronizada", "'REDE_SINCRONIZADA' in value_json.flags", null,
                "mdi:sine-wave");
    }

    @Override
    public void closePort() {
        this.serialPortIntegration.closePort();
    }

    private RagtechMemoria lerMemoria() {
        final var memoria = new RagtechMemoria();
        for (final var faixa : RagtechEasy.FAIXAS) {
            final var dados = this.serialPortIntegration.ler(faixa.inicio(), faixa.tamanho());
            if (Objects.isNull(dados)) {
                if (faixa.equals(RagtechEasy.FAIXA_PRINCIPAL)) {
                    log.warn("| Nobreak sem resposta na leitura principal (0x{}).", Integer.toHexString(faixa.inicio()));
                    return null;
                }
                log.debug("| Nobreak sem resposta na faixa 0x{} ({} bytes).", Integer.toHexString(faixa.inicio()), faixa.tamanho());
                continue;
            }
            memoria.carregar(faixa.inicio(), dados);
        }
        return memoria;
    }

    private void avisarModelo(final NobreakModel model) {
        if (Boolean.TRUE.equals(model.getModeloTestado()) || !this.modeloAvisado.compareAndSet(false, true)) {
            return;
        }
        if (EnumModeloEasy.porCodigo(model.getModelo()).isEmpty()) {
            log.warn("| Nobreak informou o modelo {}, fora da linha EASY. Usando as fórmulas do {} (testado); os valores podem estar errados.",
                    model.getModelo(), EnumModeloEasy.TESTADO.getNome());
            return;
        }
        log.warn("| Nobreak {} ainda não foi testado. O mapa é o da linha EASY e em teoria é compatível; confira os valores.",
                model.getModeloNome());
    }

    private boolean ligar(final Bit bit) {
        return this.serialPortIntegration.ligarBits(bit.endereco(), bit.mascara());
    }

    private boolean desligar(final Bit bit) {
        return this.serialPortIntegration.desligarBits(bit.endereco(), bit.mascara());
    }

    private boolean gravarContador(final int[] contador) {
        return this.serialPortIntegration.gravarByte(V_WAKEUPLOW, contador[2])
                && this.serialPortIntegration.gravarByte(V_WAKEUPMID, contador[1])
                && this.serialPortIntegration.gravarByte(V_WAKEUPHI, contador[0]);
    }

    private void validarContador(final long segundos) {
        if (segundos < 1 || segundos >= (long) (CONTADOR_MAXIMO * SEGUNDOS_POR_TICK)) {
            throw new IllegalArgumentException("Tempo inválido para o contador do nobreak: " + segundos + "s.");
        }
    }

    private void confirmar(final boolean ok, final String acao) {
        if (!ok) {
            log.error("| Nobreak não confirmou a ação {}.", acao);
        }
    }

    private HaConfigModel.HaConfigModelBuilder base(final HaDeviceModel device, final String dominio,
                                                     final String nomeVisivel, final String sufixoId) {
        return HaConfigModel.builder()
                .name(nomeVisivel)
                .uniqueId(this.deviceId + "_" + sufixoId)
                .defaultEntityId(dominio + "." + this.deviceId + "_" + sufixoId)
                .device(device)
                .stateTopic(this.stateTopic);
    }

    private void sensor(final HaDeviceModel device, final String nomeVisivel, final String sufixoId,
                        final String deviceClass, final String unidade, final String jsonKey, final String icone,
                        final Integer casasDecimais, final String categoria) {
        this.homeAssistantService.publicarConfig("sensor", this.base(device, "sensor", nomeVisivel, sufixoId)
                .deviceClass(deviceClass)
                .stateClass("measurement")
                .unitOfMeasurement(unidade)
                .suggestedDisplayPrecision(casasDecimais)
                .valueTemplate("{{ value_json." + jsonKey + " | default(None) }}")
                .icon(icone)
                .entityCategory(categoria)
                .build());
    }

    private void texto(final HaDeviceModel device, final String nomeVisivel, final String sufixoId,
                       final String jsonKey, final String icone, final String categoria) {
        this.homeAssistantService.publicarConfig("sensor", this.base(device, "sensor", nomeVisivel, sufixoId)
                .valueTemplate("{{ value_json." + jsonKey + " | default(None) }}")
                .icon(icone)
                .entityCategory(categoria)
                .build());
    }

    private void binario(final HaDeviceModel device, final String nomeVisivel, final String sufixoId,
                         final String condicao, final String deviceClass, final String icone) {
        this.homeAssistantService.publicarConfig("binary_sensor", this.base(device, "binary_sensor", nomeVisivel, sufixoId)
                .valueTemplate("{{ 'ON' if " + condicao + " else 'OFF' }}")
                .deviceClass(deviceClass)
                .icon(icone)
                .build());
    }

    private void publicarEstado(final NobreakModel model) {

        record NobreakStateRecord(
                Double input_volt,
                Double output_volt,
                Double battery_volt,
                Integer battery_pct,
                Double output_amp,
                Double real_power,
                Double frequency,
                Integer temperature,
                Integer load_pct,
                Integer shutdown_timer,
                String desligamento,
                Double wakeup_timer,
                Double nightoff_timer,
                Integer modelo,
                String modelo_nome,
                Boolean modelo_testado,
                Double firmware,
                Double nominal_input_volt,
                Double nominal_output_volt,
                Double nominal_frequency,
                Double nominal_power,
                Double nominal_battery_volt,
                String line_sense,
                Boolean ic_battery,
                Integer led_red,
                Integer led_green,
                Integer led_blue,
                List<String> flags,
                String alertas,
                Boolean falha,
                String status_desc,
                String status_enum
        ) { }

        final var estado = new NobreakStateRecord(
                model.getInputVoltage(),
                model.getOutputVoltage(),
                model.getBatteryVoltage(),
                model.getBatteryPercentage().intValue(),
                model.getOutputCurrent(),
                model.getRealPower(),
                model.getFrequency(),
                model.getTemperature(),
                model.getLoadPercentage(),
                model.getShutdownTimer(),
                Objects.isNull(model.getShutdownTimer()) ? "nenhum" : "em " + model.getShutdownTimer() + " s",
                model.getWakeupTimer(),
                model.getNightoffTimer(),
                model.getModelo(),
                model.getModeloNome(),
                model.getModeloTestado(),
                model.getFirmware(),
                model.getNominalInputVoltage(),
                model.getNominalOutputVoltage(),
                model.getNominalFrequency(),
                model.getNominalPower(),
                model.getNominalBatteryVoltage(),
                Boolean.TRUE.equals(model.getLineSense()) ? "ligada" : "desligada",
                model.getIcBattery(),
                model.getLedRed(),
                model.getLedGreen(),
                model.getLedBlue(),
                model.getFlags().stream().sorted().map(EnumFlagNobreak::name).toList(),
                model.alertasTexto(),
                model.getFlags().stream().anyMatch(EnumFlagNobreak::isFalha),
                model.getStatus().getDescricao(),
                model.getStatus().name()
        );

        try {
            // retained mantém o estado no broker para o Home Assistant não perder os sensores ao reiniciar
            this.mqttIntegration.publish(this.stateTopic, this.jsonMapper.writeValueAsString(estado), true);
        } catch (final Exception e) {
            log.warn("| Falha ao publicar a telemetria em {}: {}", this.stateTopic, e.getMessage());
        }
    }
}
