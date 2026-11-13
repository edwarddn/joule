// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
@AllArgsConstructor
public enum EnumModeloEasy {

    EASY_600_TI(0, "EASY 600 TI", 600, false, false, 3.20),
    EASY_600_M2(1, "EASY 600 M2", 600, true, false, 1.70),
    EASY_700_TI(2, "EASY 700 TI", 700, false, false, 3.20),
    EASY_700_M2(3, "EASY 700 M2", 700, true, false, 2.30),
    EASY_900_TI(4, "EASY 900 TI", 900, false, false, 4.94),
    EASY_900_M2(5, "EASY 900 M2", 900, true, false, 2.64),
    EASY_1200_TI(6, "EASY 1200 TI", 1200, false, false, 6.37),
    EASY_1200_M2(7, "EASY 1200 M2", 1200, true, false, 3.41),
    EASY_1300_TI(8, "EASY 1300 TI", 1300, false, true, 9.56),
    EASY_1300_M2(9, "EASY 1300 M2", 1300, true, true, 5.11),
    EASY_1400_TI(10, "EASY 1400 TI", 1400, false, true, 11.15),
    EASY_1400_M2(11, "EASY 1400 M2", 1400, true, true, 5.96),
    EASY_1600_TI(12, "EASY 1600 TI", 1600, false, true, 12.80),
    EASY_1600_M2(13, "EASY 1600 M2", 1600, true, true, 6.80),
    EASY_1800_TI(14, "EASY 1800 TI", 1800, false, true, 14.40),
    EASY_1800_M2(15, "EASY 1800 M2", 1800, true, true, 7.65),
    EASY_2000_TI(16, "EASY 2000 TI", 2000, false, true, 15.93),
    EASY_2000_M2(17, "EASY 2000 M2", 2000, true, true, 8.52),
    EASY_2200_TI(18, "EASY 2200 TI", 2200, false, true, 17.52),
    EASY_2200_M2(19, "EASY 2200 M2", 2200, true, true, 9.37);

    public static final EnumModeloEasy TESTADO = EASY_1200_TI;

    private final int codigo;
    private final String nome;
    private final int potenciaNominalVa;
    private final boolean saida220;
    private final boolean bateria24;
    private final double fatorCorrente;

    public double fatorTensaoSaida() {
        return this.saida220 ? 1.03 : 0.555;
    }

    public double tensaoSaidaNominal() {
        return this.saida220 ? 220.0 : 115.0;
    }

    public double fatorTensaoBateria() {
        return this.bateria24 ? 0.134 : 0.067;
    }

    public double tensaoBateriaNominal() {
        return this.bateria24 ? 24.0 : 12.0;
    }

    public static Optional<EnumModeloEasy> porCodigo(final int codigo) {
        return Arrays.stream(values()).filter(m -> m.codigo == codigo).findFirst();
    }
}
