// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.scheduled;

import br.com.edward.joule.enums.EnumFlagNobreak;
import br.com.edward.joule.enums.EnumStatusNobreak;
import br.com.edward.joule.model.NobreakModel;
import br.com.edward.joule.service.NobreakService;
import br.com.edward.joule.service.SshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

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
        ReflectionTestUtils.setField(monitor, "shutdownDelaySeconds", 60);
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
    @DisplayName("Sobreaquecimento: relê, desliga em 30 s sem religar e desliga os servidores")
    void testSobreaquecimentoDesligaEm30s() {
        when(nobreakService.status()).thenReturn(base()
                .temperature(95)
                .status(EnumStatusNobreak.SOBREAQUECIMENTO)
                .build());

        monitor(3).monitorar();

        verify(nobreakService, times(2)).status();
        verify(nobreakService).desliga(30, false);
        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Falha crítica e bateria crítica também desligam em 30 s sem religar")
    void testOutrasEmergencias() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.FALHA_CRITICA).build());
        monitor(3).monitorar();
        verify(nobreakService).desliga(30, false);

        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.BATERIA_CRITICA).build());
        monitor(3).monitorar();
        verify(nobreakService, times(2)).desliga(30, false);
    }

    @Test
    @DisplayName("Falha do inversor: desliga no SHUTDOWN_DELAY, sem religar")
    void testFalhaCalma() {
        when(nobreakService.status()).thenReturn(base()
                .flags(Set.of(EnumFlagNobreak.FALHA_INVERSOR))
                .status(EnumStatusNobreak.FALHA)
                .build());

        monitor(3).monitorar();

        verify(nobreakService).desliga(60, false);
        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Falha que some na releitura não desliga nada")
    void testFalhaNaoConfirmada() {
        when(nobreakService.status()).thenReturn(
                base().status(EnumStatusNobreak.FALHA_CRITICA).build(),
                base().build());

        monitor(3).monitorar();

        verify(nobreakService, never()).desliga(anyInt(), anyBoolean());
        verifyNoInteractions(sshService);
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
    @DisplayName("Em bateria: ao atingir o limite de tentativas, desliga com religamento")
    void testEmBateriaAtingeLimiteDesliga() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.EM_BATERIA).build());

        monitor(1).monitorar();

        verify(nobreakService).desliga();
        verify(nobreakService, never()).desliga(anyInt(), anyBoolean());
        verify(sshService).shutdownAllServers();
    }

    @Test
    @DisplayName("Entrada abaixo de 50 V conta como falta de energia mesmo sem a flag")
    void testEntradaZeradaSemFlag() {
        when(nobreakService.status()).thenReturn(base().inputVoltage(5.0).build());

        monitor(1).monitorar();

        verify(nobreakService).desliga();
    }

    @Test
    @DisplayName("Falha no comando do nobreak não impede o desligamento dos servidores")
    void testFalhaNoNobreakNaoImpedeShutdownDosServidores() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.SOBREAQUECIMENTO).build());
        doThrow(new IllegalStateException("porta serial fora do ar"))
                .when(nobreakService).desliga(30, false);

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
    @DisplayName("Desligamento desativado: emergência não desliga nada")
    void testDesativadoEmergenciaNaoDesliga() {
        when(nobreakService.status()).thenReturn(base().status(EnumStatusNobreak.SOBREAQUECIMENTO).build());

        monitor(3, false).monitorar();

        verify(nobreakService, never()).desliga(anyInt(), anyBoolean());
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
