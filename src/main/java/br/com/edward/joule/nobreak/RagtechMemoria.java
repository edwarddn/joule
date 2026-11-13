// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.nobreak;

import java.util.Objects;

public final class RagtechMemoria {

    private static final int TAMANHO = 0x0204;

    private final int[] valores = new int[TAMANHO];
    private final boolean[] lidos = new boolean[TAMANHO];

    public RagtechMemoria carregar(final int inicio, final byte[] dados) {
        if (Objects.isNull(dados)) {
            return this;
        }
        for (var i = 0; i < dados.length; i++) {
            this.valores[inicio + i] = dados[i] & 0xFF;
            this.lidos[inicio + i] = true;
        }
        return this;
    }

    public boolean lido(final int endereco) {
        return this.lidos[endereco];
    }

    public int v(final int endereco) {
        return this.valores[endereco];
    }

    public boolean bit(final int endereco, final int bit) {
        return (this.valores[endereco] & (1 << bit)) != 0;
    }

    public boolean bit(final RagtechEasy.Bit bit) {
        return this.bit(bit.endereco(), bit.bit());
    }
}
