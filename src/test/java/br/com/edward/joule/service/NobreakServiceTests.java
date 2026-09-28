// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service;

import br.com.edward.joule.enums.EnumStatusNobreak;
import br.com.edward.joule.integration.MqttIntegration;
import br.com.edward.joule.integration.SerialPortIntegration;
import br.com.edward.joule.model.HaConfigModel;
import br.com.edward.joule.service.impl.NobreakServiceImpl;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NobreakServiceTests {

    private static final String STATE_TOPIC = "joule/nobreak/status";

    private final HomeAssistantService homeAssistantService = mock(HomeAssistantService.class);
    private final MqttIntegration mqttIntegration = mock(MqttIntegration.class);
    private final SerialPortIntegration serialPortIntegration = mock(SerialPortIntegration.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final NobreakService nobreakService = nobreakService();

    private NobreakService nobreakService() {
        final var service = new NobreakServiceImpl(homeAssistantService, mqttIntegration, serialPortIntegration, jsonMapper);
        ReflectionTestUtils.setField(service, "version", "1.0.0");
        ReflectionTestUtils.setField(service, "shutdownDelaySeconds", 60);
        ReflectionTestUtils.setField(service, "stateTopic", STATE_TOPIC);
        ReflectionTestUtils.setField(service, "deviceId", "nobreak");
        return service;
    }

    private static byte[] pacoteValido() {
        final var data = new byte[31];
        data[0] = (byte) 0xAA;
        data[8] = (byte) 200;
        data[11] = (byte) 200;
        data[13] = (byte) 20;
        data[14] = (byte) 30;
        data[15] = (byte) 25;
        data[17] = (byte) 0x00;
        data[18] = (byte) 0x00;
        data[24] = (byte) 60;
        data[26] = (byte) 120;
        data[30] = (byte) 220;
        return data;
    }

    @Test
    @DisplayName("status() devolve null quando a serial não responde")
    void testStatusNuloQuandoSerialNull() {
        when(serialPortIntegration.lerStatusBruto()).thenReturn(null);

        assertThat(nobreakService.status()).isNull();
        verifyNoInteractions(mqttIntegration);
    }

    @Test
    @DisplayName("status() devolve null quando o header do pacote é inválido")
    void testStatusHeaderInvalido() {
        final var data = pacoteValido();
        data[0] = 0x00;
        when(serialPortIntegration.lerStatusBruto()).thenReturn(data);

        assertThat(nobreakService.status()).isNull();
        verifyNoInteractions(mqttIntegration);
    }

    @Test
    @DisplayName("status() decodifica o pacote e publica o estado retido")
    void testStatusDecodificaPacote() {
        when(serialPortIntegration.lerStatusBruto()).thenReturn(pacoteValido());

        final var model = nobreakService.status();

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
        assertThat(model.getTemperature()).isEqualTo(25);
        assertThat(model.getLoadPercentage()).isEqualTo(30);
        assertThat(model.getFrequency()).isEqualTo(60.0);
        assertThat(model.getOutputVoltage()).isCloseTo(119.9, Offset.offset(0.1));
        assertThat(model.getInputVoltage()).isGreaterThan(20.0);
        assertThat(model.getBatteryPercentage()).isCloseTo(78.6, Offset.offset(0.1));

        final var payload = ArgumentCaptor.forClass(String.class);
        verify(mqttIntegration).publish(eq(STATE_TOPIC), payload.capture(), eq(true));
        assertThat(payload.getValue()).contains("\"status_enum\":\"NORMAL\"");
    }

    @Test
    @DisplayName("status() em queda de rede com bateria baixa vira BATERIA CRÍTICA")
    void testStatusBateriaCritica() {
        final var data = pacoteValido();
        data[17] = (byte) 0x40;
        data[18] = (byte) 0x01;
        data[26] = (byte) 0x00;
        when(serialPortIntegration.lerStatusBruto()).thenReturn(data);

        final var model = nobreakService.status();

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(EnumStatusNobreak.BATERIA_CRITICA);
        assertThat(model.getStatus().isCritico()).isTrue();
        assertThat(model.getInputVoltage()).isZero();
    }

    @Test
    @DisplayName("desliga() rejeita tempos fora da faixa de 1 a 90 segundos")
    void testDesligaTempoInvalido() {
        assertThatThrownBy(() -> nobreakService.desliga(0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nobreakService.desliga(91, true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(serialPortIntegration);
    }

    @Test
    @DisplayName("desliga() envia o comando de armar e o de timer pela serial")
    void testDesligaEnviaComandos() {
        nobreakService.desliga();

        verify(serialPortIntegration, times(2)).enviarComando(any(byte[].class), any());
    }

    @Test
    @DisplayName("registrarNobreak() publica o discovery de todos os sensores")
    void testRegistrarNobreak() {
        nobreakService.registrarNobreak();

        final var configs = ArgumentCaptor.forClass(HaConfigModel.class);
        verify(homeAssistantService, atLeastOnce()).publicarConfig(configs.capture());

        assertThat(configs.getAllValues()).hasSize(9);
        assertThat(configs.getAllValues())
                .extracting(HaConfigModel::getUniqueId)
                .contains("nobreak_in_volt", "nobreak_status");
        assertThat(configs.getAllValues())
                .extracting(HaConfigModel::getStateTopic)
                .containsOnly(STATE_TOPIC);
    }

    @Test
    @DisplayName("Falha ao publicar no broker não derruba a leitura de status")
    void testFalhaDePublicacaoNaoQuebraLeitura() {
        when(serialPortIntegration.lerStatusBruto()).thenReturn(pacoteValido());
        doThrow(new IllegalStateException("broker fora do ar"))
                .when(mqttIntegration).publish(any(), any(), anyBoolean());

        final var model = nobreakService.status();

        assertThat(model).isNotNull();
        assertThat(model.getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("closePort() delega para a integração serial")
    void testClosePort() {
        nobreakService.closePort();

        verify(serialPortIntegration).closePort();
    }
}
