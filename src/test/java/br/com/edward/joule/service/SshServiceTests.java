// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service;

import br.com.edward.joule.config.SshConfig;
import br.com.edward.joule.integration.SshIntegration;
import br.com.edward.joule.service.impl.SshServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SshServiceTests {

    private final SshConfig sshConfig = mock(SshConfig.class);
    private final SshIntegration sshIntegration = mock(SshIntegration.class);
    private final SshService sshService = new SshServiceImpl(sshConfig, sshIntegration);

    @Test
    @DisplayName("Não tenta desligar quando não há servidores configurados")
    void testSemServidores() {
        when(sshConfig.getServers()).thenReturn(List.of());

        sshService.shutdownAllServers();

        verifyNoInteractions(sshIntegration);
    }

    @Test
    @DisplayName("Envia o comando de shutdown para cada servidor configurado")
    void testDesligaServidores() {
        final var primeiro = new SshConfig.RemoteServer();
        primeiro.setName("nas");
        final var segundo = new SshConfig.RemoteServer();
        segundo.setName("host");

        when(sshConfig.getServers()).thenReturn(List.of(primeiro, segundo));
        when(sshConfig.getShutdownCommand()).thenReturn("sudo -n poweroff");

        sshService.shutdownAllServers();

        verify(sshIntegration).executarComando(primeiro, "sudo -n poweroff");
        verify(sshIntegration).executarComando(segundo, "sudo -n poweroff");
    }
}
