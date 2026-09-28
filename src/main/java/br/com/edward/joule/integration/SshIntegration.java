// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration;

import br.com.edward.joule.config.SshConfig;

public interface SshIntegration {

    void executarComando(SshConfig.RemoteServer server, String comando);
}
