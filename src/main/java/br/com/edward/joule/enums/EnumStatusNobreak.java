// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EnumStatusNobreak {

    NORMAL("REDE (Normal)", false),
    EM_BATERIA("EM BATERIA", false),
    SOBREAQUECIMENTO("SOBREAQUECIMENTO", true),
    BATERIA_CRITICA("BATERIA CRÍTICA", true);

    private final String descricao;
    private final boolean isCritico;

    public static EnumStatusNobreak computar(final boolean inverterOn,
                                             final boolean noInput,
                                             final boolean lowBattery,
                                             final boolean overheat) {
        if ((inverterOn || noInput) && lowBattery) {
            return BATERIA_CRITICA;
        }
        if (overheat) {
            return SOBREAQUECIMENTO;
        }
        if (inverterOn || noInput) {
            return EM_BATERIA;
        }
        return NORMAL;
    }
}
