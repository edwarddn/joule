// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SerialPortIntegrationImplTests {

    private static String hex(final byte[] bytes) {
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes);
    }

    @ParameterizedTest(name = "{3}")
    @DisplayName("Monta os mesmos bytes que o nobreak já aceitou")
    @CsvSource({
            "4, 128, 30, AA 04 00 80 1E 9E",
            "4, 133, 1, AA 04 00 85 01 86",
            "3, 146, 2, AA 03 00 92 02 94",
            "3, 128, 1, AA 03 00 80 01 81",
            "1, 152, 60, AA 01 00 98 3C D4",
            "4, 514, 2, AA 04 02 02 02 06"
    })
    void testComando(final int op, final int endereco, final int valor, final String esperado) {
        assertThat(hex(SerialPortIntegrationImpl.comando(op, endereco, valor))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Desligar bits manda o inverso da máscara: F_AUTOSTART=0 vira 80 FE")
    void testDesligarBitsInverteMascara() {
        assertThat(hex(SerialPortIntegrationImpl.comando(0x02, 0x80, ~0x01))).isEqualTo("AA 02 00 80 FE 7E");
    }

    @Test
    @DisplayName("Sem porta configurada, nada é lido nem gravado")
    void testSemPorta() {
        final var integracao = new SerialPortIntegrationImpl();

        assertThat(integracao.ler(0x80, 30)).isNull();
        assertThat(integracao.gravarByte(0x98, 60)).isFalse();
        assertThat(integracao.ligarBits(0x80, 0x01)).isFalse();
        assertThat(integracao.desligarBits(0x80, 0x01)).isFalse();
    }
}
