// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service;

import br.com.edward.joule.enums.EnumStatusNobreak;
import br.com.edward.joule.integration.MqttIntegration;
import br.com.edward.joule.integration.SerialPortIntegration;
import br.com.edward.joule.model.HaConfigModel;
import br.com.edward.joule.service.impl.NobreakServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NobreakServiceTests {

    private static final String STATE_TOPIC = "joule/nobreak/status";

    private static final byte[] NORMAL = HexFormat.ofDelimiter(" ").parseHex("09 00 0C 59 09 A7 D1 FF 00 EC C6 C8 04 03 2F 00 00 00 03 86 C2 01 A3 3B 00 C7 06 0E 00 C9");

    private final HomeAssistantService homeAssistantService = mock(HomeAssistantService.class);
    private final MqttIntegration mqttIntegration = mock(MqttIntegration.class);
    private final SerialPortIntegration serial = mock(SerialPortIntegration.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final NobreakService nobreakService = nobreakService();

    private NobreakService nobreakService() {
        final var service = new NobreakServiceImpl(homeAssistantService, mqttIntegration, serial, jsonMapper);
        ReflectionTestUtils.setField(service, "version", "1.0.0");
        ReflectionTestUtils.setField(service, "shutdownDelaySeconds", 60);
        ReflectionTestUtils.setField(service, "criticalTemperature", 90);
        ReflectionTestUtils.setField(service, "stateTopic", STATE_TOPIC);
        ReflectionTestUtils.setField(service, "deviceId", "nobreak");
        return service;
    }

    @BeforeEach
    void setUp() {
        when(serial.gravarByte(anyInt(), anyInt())).thenReturn(true);
        when(serial.ligarBits(anyInt(), anyInt())).thenReturn(true);
        when(serial.desligarBits(anyInt(), anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("status() devolve null quando a leitura principal não responde, sem ler o resto")
    void testStatusNuloSemLeituraPrincipal() {
        assertThat(nobreakService.status()).isNull();

        verify(serial).ler(0x80, 30);
        verify(serial, times(1)).ler(anyInt(), anyInt());
        verifyNoInteractions(mqttIntegration);
    }

    @Test
    @DisplayName("status() lê as 5 faixas, decodifica e publica o estado retido")
    void testStatusLeTodasAsFaixas() {
        when(serial.ler(0x80, 30)).thenReturn(NORMAL);

        final var model = nobreakService.status();

        assertThat(model.getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
        assertThat(model.getModeloNome()).isEqualTo("EASY 1200 TI");
        assertThat(model.getInputVoltage()).isCloseTo(212.0, offset(0.01));

        verify(serial).ler(0x80, 30);
        verify(serial).ler(0xF3, 1);
        verify(serial).ler(0x136, 1);
        verify(serial).ler(0x171, 4);
        verify(serial).ler(0x202, 2);

        final var payload = ArgumentCaptor.forClass(Object.class);
        verify(mqttIntegration).publish(eq(STATE_TOPIC), payload.capture(), eq(true));
        assertThat((String) payload.getValue())
                .contains("\"status_enum\":\"NORMAL\"")
                .contains("\"modelo_nome\":\"EASY 1200 TI\"")
                .contains("\"modelo_testado\":true")
                .contains("\"flags\":[\"REDE_SINCRONIZADA\"]")
                .contains("\"alertas\":\"nenhum\"")
                .contains("\"desligamento\":\"nenhum\"")
                .contains("\"falha\":false");
    }

    @Test
    @DisplayName("status() usa a calibração de corrente quando a faixa 0xF3 responde")
    void testStatusComCalibracao() {
        when(serial.ler(0x80, 30)).thenReturn(NORMAL);
        when(serial.ler(0xF3, 1)).thenReturn(new byte[]{45});

        assertThat(nobreakService.status().getOutputCurrent()).isCloseTo(4 * 6.37 / 45, offset(0.0001));
    }

    @Test
    @DisplayName("Falha ao publicar no broker não derruba a leitura de status")
    void testFalhaDePublicacaoNaoQuebraLeitura() {
        when(serial.ler(0x80, 30)).thenReturn(NORMAL);
        doThrow(new IllegalStateException("broker fora do ar"))
                .when(mqttIntegration).publish(any(), any(), anyBoolean());

        assertThat(nobreakService.status().getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("desliga() rejeita tempos fora da faixa de 1 a 90 segundos")
    void testDesligaTempoInvalido() {
        assertThatThrownBy(() -> nobreakService.desliga(0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nobreakService.desliga(91, true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(serial);
    }

    @Test
    @DisplayName("desliga(60, true): liga o religamento automático e depois grava o timer")
    void testDesligaReligando() {
        nobreakService.desliga(60, true);

        final InOrder ordem = inOrder(serial);
        ordem.verify(serial).ligarBits(0x80, 0x01);
        ordem.verify(serial).gravarByte(0x98, 60);
    }

    @Test
    @DisplayName("desliga(30, false): desliga o religamento automático e grava o timer")
    void testDesligaSemReligar() {
        nobreakService.desliga(30, false);

        verify(serial).desligarBits(0x80, 0x01);
        verify(serial).gravarByte(0x98, 30);
    }

    @Test
    @DisplayName("desliga() sem argumento usa o SHUTDOWN_DELAY e religa")
    void testDesligaPadrao() {
        nobreakService.desliga();

        verify(serial).ligarBits(0x80, 0x01);
        verify(serial).gravarByte(0x98, 60);
    }

    @Test
    @DisplayName("Timer não confirmado pelo nobreak vira exceção, para o monitor registrar a falha")
    void testDesligaTimerNaoConfirmado() {
        when(serial.gravarByte(0x98, 60)).thenReturn(false);

        assertThatThrownBy(() -> nobreakService.desliga(60, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ações do fabricante gravam os bits e bytes do mapa da linha EASY")
    void testAcoes() {
        nobreakService.definirSensibilidadeRede(true);
        verify(serial).ligarBits(0x80, 0x80);

        nobreakService.alterarCorLed(1, 2, 3, 0);
        verify(serial).gravarByte(0x171, 1);
        verify(serial).gravarByte(0x172, 2);
        verify(serial).gravarByte(0x173, 3);
        verify(serial).gravarByte(0x174, 0);

        nobreakService.iniciarDescargaCompleta();
        verify(serial).desligarBits(0x90, 0x02);
        verify(serial).ligarBits(0x90, 0x04);
        verify(serial).ligarBits(0x95, 0x80);

        nobreakService.cancelarDescargaCompleta();
        verify(serial).desligarBits(0x90, 0x04);

        nobreakService.supervisao(false);
        verify(serial).desligarBits(0x92, 0x02);
    }

    @Test
    @DisplayName("wakeUp grava o contador de 24 bits e liga F_NIGHTOFFON; nightOff desliga")
    void testContadores() {
        nobreakService.agendarReligamento(3600);
        verify(serial).gravarByte(0x86, 0x9E);
        verify(serial).gravarByte(0x85, 0xFD);
        verify(serial).gravarByte(0x84, 0xFF);
        verify(serial).ligarBits(0x92, 0x80);

        nobreakService.agendarDesligamentoNoturno(3600);
        verify(serial, times(2)).gravarByte(0x86, 0x9E);
        verify(serial).desligarBits(0x92, 0x80);

        assertThatThrownBy(() -> nobreakService.agendarReligamento(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registrarNobreak() publica sensores e binários com o prefixo do dispositivo")
    void testRegistrarNobreak() {
        nobreakService.registrarNobreak();

        final var componente = ArgumentCaptor.forClass(String.class);
        final var configs = ArgumentCaptor.forClass(HaConfigModel.class);
        verify(homeAssistantService, times(22)).publicarConfig(componente.capture(), configs.capture());
        verify(homeAssistantService, never()).publicarConfig(any(HaConfigModel.class));

        assertThat(componente.getAllValues()).filteredOn("binary_sensor"::equals).hasSize(5);
        assertThat(configs.getAllValues())
                .extracting(HaConfigModel::getUniqueId)
                .contains("nobreak_in_volt", "nobreak_status", "nobreak_out_amp", "nobreak_falha", "nobreak_desligamento");
        assertThat(configs.getAllValues())
                .extracting(HaConfigModel::getStateTopic)
                .containsOnly(STATE_TOPIC);
        assertThat(configs.getAllValues()).filteredOn(c -> "nobreak_bat_volt".equals(c.getUniqueId()))
                .singleElement().extracting(HaConfigModel::getSuggestedDisplayPrecision).isEqualTo(1);
        assertThat(configs.getAllValues()).filteredOn(c -> "nobreak_falha".equals(c.getUniqueId()))
                .singleElement().extracting(HaConfigModel::getDefaultEntityId).isEqualTo("binary_sensor.nobreak_falha");
    }

    @Test
    @DisplayName("closePort() delega para a integração serial")
    void testClosePort() {
        nobreakService.closePort();

        verify(serial).closePort();
    }
}
