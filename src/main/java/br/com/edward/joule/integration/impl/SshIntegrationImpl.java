// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration.impl;

import br.com.edward.joule.config.SshConfig;
import br.com.edward.joule.integration.SshIntegration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class SshIntegrationImpl implements SshIntegration {

    private final SshConfig sshConfig;

    @Override
    public void executarComando(final SshConfig.RemoteServer server, final String comando) {
        log.info("| Conectando em {} ({}:{})...", server.getName(), server.getHost(), server.getPort());

        try (final var ssh = new SSHClient()) {

            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.setConnectTimeout(this.sshConfig.getSshTimeout());
            ssh.setTimeout(this.sshConfig.getSshTimeout());

            ssh.connect(server.getHost(), server.getPort());

            final var keyFile = new File(server.getPrivateKeyPath());
            if (!keyFile.exists()) {
                log.error("| Chave privada não encontrada para {}: {}", server.getName(), server.getPrivateKeyPath());
                return;
            }

            final var keys = ssh.loadKeys(server.getPrivateKeyPath());
            ssh.authPublickey(server.getUser(), keys);

            try (final var session = ssh.startSession()) {
                log.debug("| Enviando comando: {}", comando);
                final var cmd = session.exec(comando);
                cmd.join(2, TimeUnit.SECONDS);
                log.warn("| COMANDO ENVIADO PARA: {}", server.getName());
            }
        } catch (final IOException e) {
            log.error("| Falha ao executar comando em {}: {}", server.getName(), e.getMessage(), e);
        }
    }
}
