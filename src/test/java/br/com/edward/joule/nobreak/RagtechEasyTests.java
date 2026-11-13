// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.nobreak;

import br.com.edward.joule.enums.EnumFlagNobreak;
import br.com.edward.joule.enums.EnumModeloEasy;
import br.com.edward.joule.enums.EnumStatusNobreak;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RagtechEasyTests {

    static final String NORMAL = "09 00 0C 59 09 A7 D1 FF 00 EC C6 C8 04 03 2F 00 00 00 03 86 C2 01 A3 3B 00 C7 06 0E 00 C9";
    static final String BATERIA_INICIO = "09 00 0C 59 09 A7 D1 FF 00 DB C1 05 05 06 2F 00 48 00 02 82 80 10 A2 3A 00 05 06 0E 00 D1";
    static final String BATERIA_30S = "09 00 0C 59 09 A7 D1 FF 00 E7 BB 05 05 05 36 00 48 00 02 82 80 11 A1 3A 00 05 06 0E 00 D0";
    static final String RELIGADO = "09 00 0C 59 09 A7 D1 FF 00 E1 D4 C1 04 03 3E FF 00 00 03 82 82 01 9C 3B 00 C1 06 0E 3F D5";

    private static final int TEMPERATURA_CRITICA = 90;

    static byte[] bytes(final String hex) {
        return HexFormat.ofDelimiter(" ").parseHex(hex);
    }

    private static RagtechMemoria memoria(final String hex) {
        return new RagtechMemoria().carregar(RagtechEasy.FAIXA_PRINCIPAL.inicio(), bytes(hex));
    }

    private static RagtechMemoria memoriaComModelo(final int modelo) {
        final var dados = bytes(NORMAL);
        dados[RagtechEasy.V_MODEL - 0x80] = (byte) modelo;
        return new RagtechMemoria().carregar(0x80, dados);
    }

    @Test
    @DisplayName("Rede normal no 4162: valores pelas fórmulas do fabricante, modelo 6 testado")
    void testNormal() {
        final var n = RagtechEasy.decodificar(memoria(NORMAL), TEMPERATURA_CRITICA);

        assertThat(n.getModelo()).isEqualTo(6);
        assertThat(n.getModeloNome()).isEqualTo("EASY 1200 TI");
        assertThat(n.getModeloTestado()).isTrue();
        assertThat(n.getFirmware()).isEqualTo(1.4);
        assertThat(n.getInputVoltage()).isCloseTo(212.0, offset(0.01));
        assertThat(n.getOutputVoltage()).isCloseTo(111.555, offset(0.01));
        assertThat(n.getBatteryVoltage()).isCloseTo(13.266, offset(0.01));
        assertThat(n.getBatteryPercentage()).isEqualTo(100.0);
        assertThat(n.getLoadPercentage()).isEqualTo(3);
        assertThat(n.getRealPower()).isCloseTo(25.2, offset(0.01));
        assertThat(n.getTemperature()).isEqualTo(47);
        assertThat(n.getNominalInputVoltage()).isEqualTo(220.0);
        assertThat(n.getNominalOutputVoltage()).isEqualTo(115.0);
        assertThat(n.getNominalPower()).isEqualTo(1200.0);
        assertThat(n.getNominalBatteryVoltage()).isEqualTo(12.0);
        assertThat(n.getShutdownTimer()).isNull();
        assertThat(n.getWakeupTimer()).isNull();
        assertThat(n.getLineSense()).isFalse();
        assertThat(n.getFlags()).containsExactly(EnumFlagNobreak.REDE_SINCRONIZADA);
        assertThat(n.alertasTexto()).isEqualTo("nenhum");
        assertThat(n.getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("Sem as faixas de calibração, corrente, frequência e LED ficam null")
    void testSemCalibracao() {
        final var n = RagtechEasy.decodificar(memoria(NORMAL), TEMPERATURA_CRITICA);

        assertThat(n.getOutputCurrent()).isNull();
        assertThat(n.getFrequency()).isNull();
        assertThat(n.getLedRed()).isNull();
    }

    @Test
    @DisplayName("Falta de energia: sem entrada e em bateria, sem sincronismo com a rede")
    void testEmBateria() {
        final var inicio = RagtechEasy.decodificar(memoria(BATERIA_INICIO), TEMPERATURA_CRITICA);

        assertThat(inicio.getFlags()).containsExactlyInAnyOrder(EnumFlagNobreak.SEM_ENTRADA, EnumFlagNobreak.EM_BATERIA);
        assertThat(inicio.getStatus()).isEqualTo(EnumStatusNobreak.EM_BATERIA);
        assertThat(inicio.getInputVoltage()).isCloseTo(5.3, offset(0.01));
        assertThat(inicio.getBatteryVoltage()).isCloseTo(12.931, offset(0.01));
        assertThat(inicio.getOutputVoltage()).isCloseTo(115.995, offset(0.01));

        final var depois = RagtechEasy.decodificar(memoria(BATERIA_30S), TEMPERATURA_CRITICA);
        assertThat(depois.getBatteryVoltage()).isCloseTo(12.529, offset(0.01));
        assertThat(depois.getTemperature()).isEqualTo(54);
        assertThat(depois.getStatus()).isEqualTo(EnumStatusNobreak.EM_BATERIA);
    }

    @Test
    @DisplayName("Religado: bateria carregando, rede de volta e sincronizada")
    void testReligado() {
        final var n = RagtechEasy.decodificar(memoria(RELIGADO), TEMPERATURA_CRITICA);

        assertThat(n.getBatteryVoltage()).isCloseTo(14.204, offset(0.01));
        assertThat(n.getInputVoltage()).isCloseTo(204.58, offset(0.01));
        assertThat(n.getTemperature()).isEqualTo(62);
        assertThat(n.getFlags()).containsExactly(EnumFlagNobreak.REDE_SINCRONIZADA);
        assertThat(n.getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("Temperatura acima do limite configurado vira SOBREAQUECIMENTO")
    void testTemperaturaCriticaConfiguravel() {
        assertThat(RagtechEasy.decodificar(memoria(RELIGADO), 60).getStatus()).isEqualTo(EnumStatusNobreak.SOBREAQUECIMENTO);
        assertThat(RagtechEasy.decodificar(memoria(RELIGADO), 62).getStatus()).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("Com as faixas de calibração: corrente pelo fator do modelo, frequência pelos osciladores e LED")
    void testComCalibracao() {
        final var m = memoria(NORMAL)
                .carregar(0x00F3, new byte[]{45})
                .carregar(0x0171, new byte[]{10, 20, 30, 0})
                .carregar(0x0202, new byte[]{50, 60});

        final var n = RagtechEasy.decodificar(m, TEMPERATURA_CRITICA);

        assertThat(n.getOutputCurrent()).isCloseTo(4 * 6.37 / 45, offset(0.0001));
        assertThat(n.getFrequency()).isCloseTo((53.0 + 4.0 * (59 - 50) / 10) * 0.9806 + 1.46, offset(0.0001));
        assertThat(n.getLedRed()).isEqualTo(10);
        assertThat(n.getLedGreen()).isEqualTo(20);
        assertThat(n.getLedBlue()).isEqualTo(30);
    }

    @Test
    @DisplayName("Calibração zerada deixa corrente e frequência null")
    void testCalibracaoZerada() {
        final var m = memoria(NORMAL)
                .carregar(0x00F3, new byte[]{0})
                .carregar(0x0202, new byte[]{50, 50});

        final var n = RagtechEasy.decodificar(m, TEMPERATURA_CRITICA);

        assertThat(n.getOutputCurrent()).isNull();
        assertThat(n.getFrequency()).isNull();
    }

    @Test
    @DisplayName("EASY 2200 M2 (não testado): saída 220 V, bateria de 24 V e potência nominal do modelo")
    void testModeloM2Bateria24() {
        final var m = memoriaComModelo(EnumModeloEasy.EASY_2200_M2.getCodigo()).carregar(0x00F3, new byte[]{45});

        final var n = RagtechEasy.decodificar(m, TEMPERATURA_CRITICA);

        assertThat(n.getModeloNome()).isEqualTo("EASY 2200 M2");
        assertThat(n.getModeloTestado()).isFalse();
        assertThat(n.getOutputVoltage()).isCloseTo(201 * 1.03, offset(0.01));
        assertThat(n.getBatteryVoltage()).isCloseTo(198 * 0.134, offset(0.01));
        assertThat(n.getOutputCurrent()).isCloseTo(4 * 9.37 / 45, offset(0.0001));
        assertThat(n.getNominalOutputVoltage()).isEqualTo(220.0);
        assertThat(n.getNominalBatteryVoltage()).isEqualTo(24.0);
        assertThat(n.getNominalPower()).isEqualTo(2200.0);
        assertThat(n.getRealPower()).isCloseTo(0.03 * 2200 * 0.7, offset(0.01));
    }

    @Test
    @DisplayName("Modelo fora da linha EASY usa as fórmulas do testado e aparece como desconhecido")
    void testModeloDesconhecido() {
        final var n = RagtechEasy.decodificar(memoriaComModelo(42), TEMPERATURA_CRITICA);

        assertThat(n.getModeloNome()).isEqualTo("desconhecido (42)");
        assertThat(n.getModeloTestado()).isFalse();
        assertThat(n.getOutputVoltage()).isCloseTo(111.555, offset(0.01));
    }

    @Test
    @DisplayName("Os 20 modelos da linha EASY têm códigos de 0 a 19, sem repetir")
    void testCodigosDosModelos() {
        assertThat(EnumModeloEasy.values()).hasSize(20)
                .extracting(EnumModeloEasy::getCodigo)
                .containsExactlyElementsOf(IntStream.range(0, 20).boxed().toList());
    }

    @Test
    @DisplayName("Timer de desligamento armado aparece em segundos")
    void testTimerDesligamento() {
        final var dados = bytes(NORMAL);
        dados[RagtechEasy.V_SHUTDOWNTIMER - 0x80] = 60;

        final var n = RagtechEasy.decodificar(new RagtechMemoria().carregar(0x80, dados), TEMPERATURA_CRITICA);

        assertThat(n.getShutdownTimer()).isEqualTo(60);
    }

    @Test
    @DisplayName("O contador do wakeUp volta, decodificado, aos segundos pedidos")
    void testContadorWakeUp() {
        final var contador = RagtechEasy.contador(3600, 1.0);
        assertThat(contador).containsExactly(0xFF, 0xFD, 0x9E);

        final var dados = bytes(NORMAL);
        dados[RagtechEasy.V_WAKEUPHI - 0x80] = (byte) contador[0];
        dados[RagtechEasy.V_WAKEUPMID - 0x80] = (byte) contador[1];
        dados[RagtechEasy.V_WAKEUPLOW - 0x80] = (byte) contador[2];
        dados[0x92 - 0x80] |= (byte) 0x80;

        final var n = RagtechEasy.decodificar(new RagtechMemoria().carregar(0x80, dados), TEMPERATURA_CRITICA);

        assertThat(n.getWakeupTimer()).isCloseTo(3600.0, offset(RagtechEasy.SEGUNDOS_POR_TICK));
        assertThat(n.getNightoffTimer()).isEqualTo(n.getWakeupTimer());
    }
}
