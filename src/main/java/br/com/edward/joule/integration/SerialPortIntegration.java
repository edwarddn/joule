// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration;

public interface SerialPortIntegration {

    byte[] lerStatusBruto();

    String enviarComando(byte[] comando, String descricao);

    void closePort();
}
