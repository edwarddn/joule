// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration;

public interface SerialPortIntegration {

    byte[] ler(int endereco, int tamanho);
    boolean gravarByte(int endereco, int valor);
    boolean ligarBits(int endereco, int mascara);
    boolean desligarBits(int endereco, int mascara);
    void closePort();
}
