// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.enums;

import br.com.edward.joule.enums.EnumStatusNobreak.Acao;
import br.com.edward.joule.nobreak.RagtechMemoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static br.com.edward.joule.enums.EnumFlagNobreak.*;
import static org.assertj.core.api.Assertions.assertThat;

class EnumStatusNobreakTests {

    private static EnumStatusNobreak computar(final EnumFlagNobreak... flags) {
        return EnumStatusNobreak.computar(flags.length == 0 ? Set.of() : EnumSet.of(flags[0], flags), 40, 90);
    }

    @Test
    @DisplayName("Sem flags, ou só rede sincronizada: REDE (Normal)")
    void testNormal() {
        assertThat(computar()).isEqualTo(EnumStatusNobreak.NORMAL);
        assertThat(computar(REDE_SINCRONIZADA)).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("Sem entrada e/ou em bateria: EM BATERIA, contando para a falta de energia")
    void testEmBateria() {
        assertThat(computar(SEM_ENTRADA, EM_BATERIA)).isEqualTo(EnumStatusNobreak.EM_BATERIA);
        assertThat(computar(SEM_ENTRADA)).isEqualTo(EnumStatusNobreak.EM_BATERIA);
        assertThat(EnumStatusNobreak.EM_BATERIA.getAcao()).isEqualTo(Acao.CONTAR_FALTA);
    }

    @Test
    @DisplayName("Autoteste com rede não é falta de energia")
    void testAutoteste() {
        assertThat(computar(AUTOTESTE, EM_BATERIA, REDE_SINCRONIZADA)).isEqualTo(EnumStatusNobreak.AUTOTESTE);
        assertThat(computar(AUTOTESTE, EM_BATERIA, SEM_ENTRADA)).isEqualTo(EnumStatusNobreak.EM_BATERIA);
    }

    @Test
    @DisplayName("Sobretemperatura pela flag ou acima do limite configurado")
    void testSobreaquecimento() {
        assertThat(computar(FALHA_SOBRETEMPERATURA)).isEqualTo(EnumStatusNobreak.SOBREAQUECIMENTO);
        assertThat(EnumStatusNobreak.computar(Set.of(), 91, 90)).isEqualTo(EnumStatusNobreak.SOBREAQUECIMENTO);
        assertThat(EnumStatusNobreak.computar(Set.of(), 90, 90)).isEqualTo(EnumStatusNobreak.NORMAL);
    }

    @ParameterizedTest
    @EnumSource(names = {"FALHA_FIM_DE_BATERIA", "FALHA_SOBRECARGA", "FALHA_SAIDA_ANORMAL", "FALHA_CURTO_CIRCUITO"})
    @DisplayName("Falhas de emergência: FALHA CRÍTICA")
    void testFalhaCritica(final EnumFlagNobreak falha) {
        assertThat(computar(falha)).isEqualTo(EnumStatusNobreak.FALHA_CRITICA);
        assertThat(EnumStatusNobreak.FALHA_CRITICA.getAcao()).isEqualTo(Acao.EMERGENCIA);
    }

    @ParameterizedTest
    @EnumSource(names = {"FALHA_BATERIA_ANORMAL", "FALHA_INVERSOR"})
    @DisplayName("Inversor e bateria anormal: FALHA, desligamento com calma")
    void testFalhaCalma(final EnumFlagNobreak falha) {
        assertThat(computar(falha)).isEqualTo(EnumStatusNobreak.FALHA);
        assertThat(EnumStatusNobreak.FALHA.getAcao()).isEqualTo(Acao.CALMA);
    }

    @Test
    @DisplayName("Bateria baixa só é crítica fora da rede; emergência vence a falha calma")
    void testBateriaCritica() {
        assertThat(computar(BATERIA_BAIXA, EM_BATERIA, SEM_ENTRADA)).isEqualTo(EnumStatusNobreak.BATERIA_CRITICA);
        assertThat(computar(BATERIA_BAIXA)).isEqualTo(EnumStatusNobreak.NORMAL);
        assertThat(computar(BATERIA_BAIXA, EM_BATERIA, FALHA_INVERSOR)).isEqualTo(EnumStatusNobreak.BATERIA_CRITICA);
    }

    @Test
    @DisplayName("Avisos: carga alta, rede instável e bateria com defeito, sem ação")
    void testAvisos() {
        assertThat(computar(CARGA_ALTA)).isEqualTo(EnumStatusNobreak.CARGA_ALTA);
        assertThat(computar(ENTRADA_ALTA)).isEqualTo(EnumStatusNobreak.REDE_INSTAVEL);
        assertThat(computar(BATERIA_VELHA)).isEqualTo(EnumStatusNobreak.BATERIA_COM_DEFEITO);
        assertThat(EnumStatusNobreak.CARGA_ALTA.getAcao()).isEqualTo(Acao.NENHUMA);
    }

    @Test
    @DisplayName("Entrada baixa não conta quando não há entrada nenhuma")
    void testEntradaBaixaSemEntrada() {
        final var semEntrada = new RagtechMemoria().carregar(0x90, new byte[]{0x18});
        assertThat(EnumFlagNobreak.ativas(semEntrada)).containsExactly(SEM_ENTRADA);

        final var comEntrada = new RagtechMemoria().carregar(0x90, new byte[]{0x10});
        assertThat(EnumFlagNobreak.ativas(comEntrada)).containsExactly(ENTRADA_BAIXA);
    }

    @Test
    @DisplayName("As 7 falhas do byte 0x91 são falhas; flags de operação não são alerta")
    void testCategorias() {
        final var falhas = new RagtechMemoria().carregar(0x91, new byte[]{(byte) 0xFE});
        assertThat(EnumFlagNobreak.ativas(falhas)).hasSize(7).allMatch(EnumFlagNobreak::isFalha);
        assertThat(EM_BATERIA.isAlerta()).isFalse();
        assertThat(BATERIA_VELHA.isAlerta()).isTrue();
    }
}
