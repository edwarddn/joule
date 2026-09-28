// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule;

import br.com.edward.joule.service.NobreakService;
import br.com.edward.joule.service.SshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JouleApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private NobreakService nobreakService;

    @Autowired
    private SshService sshService;

    @Test
    @DisplayName("Contexto sobe sem broker MQTT e sem porta serial configurada")
    void testContextoSobeSemHardwareEBroker() {
        assertThat(this.nobreakService).isNotNull();
        assertThat(this.sshService).isNotNull();
        assertThat(this.context.containsBean("mqttOutboundChannel")).isFalse();
        assertThat(this.context.getBeanNamesForType(br.com.edward.joule.scheduled.MonitorNobreak.class)).isEmpty();
    }
}
