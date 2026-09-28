// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service.impl;

import br.com.edward.joule.enums.EnumStatusNobreak;
import br.com.edward.joule.integration.MqttIntegration;
import br.com.edward.joule.integration.SerialPortIntegration;
import br.com.edward.joule.model.HaConfigModel;
import br.com.edward.joule.model.HaDeviceModel;
import br.com.edward.joule.model.NobreakModel;
import br.com.edward.joule.service.HomeAssistantService;
import br.com.edward.joule.service.NobreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class NobreakServiceImpl implements NobreakService {

    private final HomeAssistantService homeAssistantService;

    private final MqttIntegration mqttIntegration;

    private final SerialPortIntegration serialPortIntegration;

    private final JsonMapper jsonMapper;

    @Value("${spring.application.version}")
    private String version;

    @Value("${nobreak.shutdown-delay-seconds}")
    private int shutdownDelaySeconds;

    @Value("${mqtt.state-topic}")
    private String stateTopic;

    @Value("${homeassistant.device-id}")
    private String deviceId;

    @Override
    public NobreakModel status() {
        final var data = this.serialPortIntegration.lerStatusBruto();
        if (Objects.isNull(data)) {
            return null;
        }

        if ((data[0] & 0xFF) != 0xAA) {
            log.warn("| Pacote inválido recebido (header incorreto). Raw: {}", bytesToHex(data, data.length));
            return null;
        }

        return status(data, data.length);
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

        final byte[] cmdArmar;
        if (religarAutomaticamente) {
            cmdArmar = new byte[]{(byte) 0xAA, 0x03, 0x00, (byte) 0x80, 0x01, (byte) 0x81};
        } else {
            cmdArmar = new byte[]{(byte) 0xAA, 0x02, 0x00, (byte) 0x80, (byte) 0xFE, (byte) 0x7E};
        }

        this.serialPortIntegration.enviarComando(cmdArmar, "Armar (80 FE)");

        final var header = (byte) 0xAA;
        final var len = 0x01;
        final var cmdA = 0x00;
        final var cmdB = (byte) 0x98;
        final var time = (byte) segundos;

        final var soma = (len & 0xFF) + (cmdA & 0xFF) + (cmdB & 0xFF) + (time & 0xFF);
        final var checksum = (byte) (soma - (len & 0xFF));

        final byte[] cmdTimer = {header, len, cmdA, cmdB, time, checksum};

        log.info("| Enviando timer: {}", bytesToHex(cmdTimer, cmdTimer.length));
        this.serialPortIntegration.enviarComando(cmdTimer, "Timer (98)");

        log.warn("| COMANDO ENVIADO! Saída do nobreak cai em {} segundos.", segundos);
    }

    @Override
    public void registrarNobreak() {
        final var device = HaDeviceModel.builder()
                .identifiers(List.of(this.deviceId))
                .name("Nobreak")
                .manufacturer("Ragtech")
                .model("Easy Pro 1200VA (4162)")
                .sw_version(this.version)
                .build();

        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Tensão Entrada", "in_volt", "voltage", "V", "input_volt", "mdi:flash-outline")
        );
        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Tensão Saída", "out_volt", "voltage", "V", "output_volt", "mdi:flash")
        );
        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Potência Real", "power", "power", "W", "real_power", "mdi:lightning-bolt")
        );
        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Frequência", "freq", "frequency", "Hz", "frequency", "mdi:sine-wave")
        );

        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Tensão Bateria", "bat_volt", "voltage", "V", "battery_volt", "mdi:car-battery")
        );
        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Nível Bateria", "bat_level", "battery", "%", "battery_pct", null)
        );

        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Temperatura Interna", "temp", "temperature", "°C", "temperature", "mdi:thermometer")
        );
        this.homeAssistantService.publicarConfig(
                criarSensor(device, "Carga de Saída", "load", null, "%", "load_pct", "mdi:gauge")
        );
        this.homeAssistantService.publicarConfig(
                criarSensorTexto(device, "Status Operacional", "status", "status_desc", "mdi:information-outline")
        );
    }

    @Override
    public void closePort() {
        this.serialPortIntegration.closePort();
    }

    private NobreakModel status(final byte[] data, final int length) {

        final var rawHex = bytesToHex(data, length);
        log.debug("| Pacote lido: {}", rawHex);

        final var rawBatCharge = data[8] & 0xFF;
        final var rawBatVolt = data[11] & 0xFF;
        final var rawOutAmp = data[13] & 0xFF;
        final var rawLoad = data[14] & 0xFF;
        final var rawTemp = data[15] & 0xFF;
        final var rawFreq = data[24] & 0xFF;
        final var flags90 = data[17] & 0xFF;
        final var flags91 = data[18] & 0xFF;
        final var rawInVolt = data[26] & 0xFF;
        final var rawOutVolt = data[30] & 0xFF;

        var inputVolt = (rawInVolt * 1.0757);
        if (inputVolt < 20.0) {
            inputVolt = 0.0;
        }

        final var outputVolt = rawOutVolt * 0.545;
        final var batVolt = rawBatVolt * 0.0665;
        final var outputAmp = rawOutAmp * 0.1425;
        final var frequency = Double.valueOf(rawFreq);
        final var batCharge = Math.min(100, rawBatCharge * 0.393);
        final var powerReal = outputVolt * outputAmp * 0.7;

        final var inverterOn = (flags90 & 0x40) > 0;
        final var noInput = (flags90 & 0x08) > 0;
        final var lowBattery = (flags91 & 0x01) > 0;
        final var overheat = rawTemp > 100;

        final var model = NobreakModel.builder()
                .rawHex(rawHex)
                .inputVoltage(inputVolt)
                .frequency(frequency)
                .outputVoltage(outputVolt)
                .outputCurrent(outputAmp)
                .realPower(powerReal)
                .batteryVoltage(batVolt)
                .batteryPercentage(batCharge)
                .loadPercentage(rawLoad)
                .temperature(rawTemp)
                .status(EnumStatusNobreak.computar(inverterOn, noInput, lowBattery, overheat))
                .build();

        publicarEstado(model);
        return model;
    }

    private HaConfigModel criarSensor(final HaDeviceModel device,
                                      final String nomeVisivel,
                                      final String sufixoId,
                                      final String deviceClass,
                                      final String unidade,
                                      final String jsonKey,
                                      final String icone) {
        return HaConfigModel.builder()
                .name(nomeVisivel)
                .uniqueId(this.deviceId + "_" + sufixoId)
                .defaultEntityId("sensor." + this.deviceId + "_" + sufixoId)
                .device(device)
                .deviceClass(deviceClass)
                .stateClass("measurement")
                .unitOfMeasurement(unidade)
                .stateTopic(this.stateTopic)
                .valueTemplate("{{ value_json." + jsonKey + " }}")
                .icon(icone)
                .build();
    }

    private HaConfigModel criarSensorTexto(final HaDeviceModel device,
                                           final String nomeVisivel,
                                           final String sufixoId,
                                           final String jsonKey,
                                           final String icone) {
        return HaConfigModel.builder()
                .name(nomeVisivel)
                .uniqueId(this.deviceId + "_" + sufixoId)
                .defaultEntityId("sensor." + this.deviceId + "_" + sufixoId)
                .device(device)
                .stateTopic(this.stateTopic)
                .valueTemplate("{{ value_json." + jsonKey + " }}")
                .icon(icone)
                .build();
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

    private static String bytesToHex(final byte[] bytes, final int length) {
        return HexFormat.ofDelimiter(" ")
                .withUpperCase()
                .formatHex(bytes, 0, length);
    }
}
