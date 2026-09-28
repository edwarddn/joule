// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.scheduled;

import br.com.edward.joule.enums.EnumStatusNobreak;
import br.com.edward.joule.model.NobreakModel;
import br.com.edward.joule.service.NobreakService;
import br.com.edward.joule.service.SshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MonitorNobreakTests {

    private final NobreakService nobreakService = mock(NobreakService.class);
    private final SshService sshService = mock(SshService.class);

    private MonitorNobreak monitor(final int shutdownSteps) {
        return monitor(shutdownSteps, true);
    }

    private MonitorNobreak monitor(final int shutdownSteps, final boolean shutdownEnabled) {
        final var monitor = new MonitorNobreak(nobreakService, sshService);
        ReflectionTestUtils.setField(monitor, "shutdownEnabled", shutdownEnabled);
        ReflectionTestUtils.setField(monitor, "shutdownSteps", shutdownSteps);
        ReflectionTestUtils.setField(monitor, "criticalTemperature", 90);
        return monitor;
    }

    private NobreakModel.NobreakModelBuilder base() {
        return NobreakModel.builder()
                .inputVoltage(220.0)
                .batteryPercentage(80.0)
                .temperature(25)
                .status(EnumStatusNobreak.NORMAL);
    }

    @Test
    @DisplayName("Status null é ignorado sem desligar nada")
    void testStatusNuloNaoFazNada() {
        when(nobreakService.status()).thenReturn(null);

        monitor(3).monitorar();

        verify(nobreakService, never()).desliga();
        verifyNoInteractions(sshService);
    }

    @Test
    @DisplayName("Energia normal não dispara desligamento")
    void testEnergiaNormalNaoFazNada() {
        when(nobreakService.status()).thenReturn(base().build());

        monitor(3).monitorar();

        verify(nobreakService, never()).desliga();
        verifyNoInteractions(sshService);
    }

    @Test
    @DisplayName("Temperatura acima do limite força desligamento imediato sem religar")
    void testSuperaquecimentoDesligaImediato() {
        when(nobreakService.status()).thenReturn(base().temperature(95).build());

        monitor(3).monitorar();

        verify(nobreakService).desliga(10, false);
        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Status crítico força desligamento imediato")
    void testStatusCriticoDesligaImediato() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.BATERIA_CRITICA).build());

        monitor(3).monitorar();

        verify(nobreakService).desliga(10, false);
        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Em bateria: antes do limite de tentativas não desliga")
    void testEmBateriaAntesDoLimiteNaoDesliga() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());

        monitor(3).monitorar();

        verify(nobreakService, never()).desliga();
        verifyNoInteractions(sshService);
    }

    @Test
    @DisplayName("Em bateria: ao atingir o limite de tentativas, desliga")
    void testEmBateriaAtingeLimiteDesliga() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());

        monitor(1).monitorar();

        verify(nobreakService).desliga();
        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Falha no comando do nobreak não impede o desligamento dos servidores")
    void testFalhaNoNobreakNaoImpedeShutdownDosServidores() {
        when(nobreakService.status()).thenReturn(base().temperature(95).build());
        doThrow(new IllegalStateException("porta serial fora do ar"))
                .when(nobreakService).desliga(10, false);

        monitor(3).monitorar();

        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Falha ao desligar os servidores não impede o comando do nobreak")
    void testFalhaNosServidoresNaoImpedeComandoDoNobreak() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());
        doThrow(new IllegalStateException("rede fora do ar"))
                .when(sshService).shutdownAllServers();

        monitor(1).monitorar();

        verify(nobreakService).desliga();
    }

    @Test
    @DisplayName("Desligamento desativado: bateria esgotando o limite não desliga nada")
    void testDesativadoEmBateriaNaoDesliga() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());

        monitor(1, false).monitorar();

        verify(nobreakService, never()).desliga();
        verifyNoInteractions(sshService);
    }

    @Test
    @DisplayName("Desligamento desativado: temperatura crítica não desliga nada")
    void testDesativadoSuperaquecimentoNaoDesliga() {
        when(nobreakService.status()).thenReturn(base().temperature(95).build());

        monitor(3, false).monitorar();

        verify(nobreakService, never()).desliga(10, false);
        verifyNoInteractions(sshService);
    }

    @Test
    @DisplayName("Desligamento desativado: status crítico não desliga nada")
    void testDesativadoStatusCriticoNaoDesliga() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.BATERIA_CRITICA).build());

        monitor(3, false).monitorar();

        verify(nobreakService, never()).desliga(10, false);
        verifyNoInteractions(sshService);
    }

    @Test
    @DisplayName("SHUTDOWN_STEPS menor que 1 é rejeitado na inicialização")
    void testShutdownStepsInvalidoFalhaNaInicializacao() {
        assertThrows(IllegalStateException.class, () -> monitor(0).validar());
    }

    @Test
    @DisplayName("Rede voltando antes do limite zera o contador")
    void testContadorResetaQuandoRedeVolta() {
        final var monitor = monitor(3);

        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());
        monitor.monitorar();
        monitor.monitorar();

        when(nobreakService.status()).thenReturn(base().build());
        monitor.monitorar();

        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());
        monitor.monitorar();
        monitor.monitorar();

        verify(nobreakService, never()).desliga();
        verifyNoInteractions(sshService);
    }
}
