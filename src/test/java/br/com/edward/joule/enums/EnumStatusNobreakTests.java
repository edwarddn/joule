// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumStatusNobreakTests {

    @Test
    @DisplayName("Sem inversor, sem queda e sem alarme: REDE (Normal)")
    void testNormal() {
        assertThat(EnumStatusNobreak.computar(false, false, false, false)).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("Inversor ligado ou sem rede: EM BATERIA")
    void testEmBateria() {
        assertThat(EnumStatusNobreak.computar(true, false, false, false)).isEqualTo(EnumStatusNobreak.EM_BATERIA);
        assertThat(EnumStatusNobreak.computar(false, true, false, false)).isEqualTo(EnumStatusNobreak.EM_BATERIA);
    }

    @Test
    @DisplayName("Sobreaquecimento prevalece sobre operação em bateria")
    void testSobreaquecimento() {
        assertThat(EnumStatusNobreak.computar(false, false, false, true)).isEqualTo(EnumStatusNobreak.SOBREAQUECIMENTO);
    }

    @Test
    @DisplayName("Bateria baixa operando fora da rede: BATERIA CRÍTICA (precede sobreaquecimento)")
    void testBateriaCritica() {
        assertThat(EnumStatusNobreak.computar(true, false, true, false)).isEqualTo(EnumStatusNobreak.BATERIA_CRITICA);
        assertThat(EnumStatusNobreak.computar(true, false, true, true)).isEqualTo(EnumStatusNobreak.BATERIA_CRITICA);
    }

    @Test
    @DisplayName("Os estados críticos estão marcados como críticos")
    void testFlagsDeCriticidade() {
        assertThat(EnumStatusNobreak.NORMAL.isCritico()).isFalse();
        assertThat(EnumStatusNobreak.EM_BATERIA.isCritico()).isFalse();
        assertThat(EnumStatusNobreak.SOBREAQUECIMENTO.isCritico()).isTrue();
        assertThat(EnumStatusNobreak.BATERIA_CRITICA.isCritico()).isTrue();
    }
}
