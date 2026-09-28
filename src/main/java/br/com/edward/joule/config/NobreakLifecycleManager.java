// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.config;

import br.com.edward.joule.service.NobreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class NobreakLifecycleManager implements SmartLifecycle {

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final NobreakService nobreakService;

    @Override
    public void start() {
        if (!this.running.get()) {
            log.info("| SISTEMA INICIANDO...");

            // uma falha no broker nao pode impedir o monitoramento e o desligamento de subirem
            try {
                this.nobreakService.registrarNobreak();
            } catch (final Exception e) {
                log.warn("| Falha ao registrar o nobreak no Home Assistant: {}", e.getMessage());
            }

            this.running.set(true);
            log.info("| SISTEMA OPERACIONAL.");
        }
    }

    @Override
    public void stop() {
        if (this.running.get()) {
            log.info("| SISTEMA DESLIGANDO: fechando a porta serial...");

            this.nobreakService.closePort();

            this.running.set(false);
            log.info("| SISTEMA FINALIZADO com sucesso.");
        }
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    @Override
    public int getPhase() {
        return 1000;
    }
}
