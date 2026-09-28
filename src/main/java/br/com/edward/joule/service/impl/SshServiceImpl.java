// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service.impl;

import br.com.edward.joule.config.SshConfig;
import br.com.edward.joule.integration.SshIntegration;
import br.com.edward.joule.service.SshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class SshServiceImpl implements SshService {

    private final SshConfig sshConfig;
    private final SshIntegration sshIntegration;

    @Override
    public void shutdownAllServers() {
        if (this.sshConfig.getServers().isEmpty()) {
            log.info("| Nenhum servidor remoto configurado para shutdown.");
            return;
        }

        log.warn("| INICIANDO SHUTDOWN REMOTO DE {} SERVIDORES...", this.sshConfig.getServers().size());

        for (final SshConfig.RemoteServer server : this.sshConfig.getServers()) {
            this.sshIntegration.executarComando(server, this.sshConfig.getShutdownCommand());
        }
    }
}
