// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.enums;

import br.com.edward.joule.enums.EnumFlagNobreak.Categoria;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public enum EnumStatusNobreak {

    SOBREAQUECIMENTO("SOBREAQUECIMENTO", Acao.EMERGENCIA),
    FALHA_CRITICA("FALHA CRÍTICA", Acao.EMERGENCIA),
    BATERIA_CRITICA("BATERIA CRÍTICA", Acao.EMERGENCIA),
    FALHA("FALHA", Acao.CALMA),
    EM_BATERIA("EM BATERIA", Acao.CONTAR_FALTA),
    AUTOTESTE("AUTOTESTE", Acao.NENHUMA),
    CARGA_ALTA("CARGA ALTA", Acao.NENHUMA),
    REDE_INSTAVEL("REDE INSTÁVEL", Acao.NENHUMA),
    BATERIA_COM_DEFEITO("BATERIA COM DEFEITO", Acao.NENHUMA),
    NORMAL("REDE (Normal)", Acao.NENHUMA);

    public enum Acao {
        NENHUMA, CONTAR_FALTA, CALMA, EMERGENCIA
    }

    private final String descricao;
    private final Acao acao;

    public static EnumStatusNobreak computar(final Set<EnumFlagNobreak> flags,
                                             final int temperatura,
                                             final int temperaturaCritica) {
        if (flags.contains(EnumFlagNobreak.FALHA_SOBRETEMPERATURA) || temperatura > temperaturaCritica) {
            return SOBREAQUECIMENTO;
        }
        if (flags.stream().anyMatch(f -> f.getCategoria() == Categoria.FALHA_EMERGENCIA)) {
            return FALHA_CRITICA;
        }

        final var semRede = flags.contains(EnumFlagNobreak.EM_BATERIA) || flags.contains(EnumFlagNobreak.SEM_ENTRADA);
        if (semRede && flags.contains(EnumFlagNobreak.BATERIA_BAIXA)) {
            return BATERIA_CRITICA;
        }
        if (flags.stream().anyMatch(f -> f.getCategoria() == Categoria.FALHA_CALMA)) {
            return FALHA;
        }

        if (flags.contains(EnumFlagNobreak.SEM_ENTRADA)) {
            return EM_BATERIA;
        }
        if (flags.contains(EnumFlagNobreak.AUTOTESTE)) {
            return AUTOTESTE;
        }
        if (flags.contains(EnumFlagNobreak.EM_BATERIA)) {
            return EM_BATERIA;
        }

        if (flags.contains(EnumFlagNobreak.CARGA_ALTA)) {
            return CARGA_ALTA;
        }
        if (flags.contains(EnumFlagNobreak.ENTRADA_BAIXA) || flags.contains(EnumFlagNobreak.ENTRADA_ALTA)) {
            return REDE_INSTAVEL;
        }
        if (flags.contains(EnumFlagNobreak.SEM_BATERIA) || flags.contains(EnumFlagNobreak.BATERIA_VELHA)) {
            return BATERIA_COM_DEFEITO;
        }
        return NORMAL;
    }
}
