// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service;

import br.com.edward.joule.integration.MqttIntegration;
import br.com.edward.joule.model.HaConfigModel;
import br.com.edward.joule.service.impl.HomeAssistantServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeAssistantServiceTests {

    private final MqttIntegration mqttIntegration = mock(MqttIntegration.class);

    private HomeAssistantService homeAssistantService(final boolean discoveryEnabled) {
        return this.homeAssistantService(discoveryEnabled, true);
    }

    private HomeAssistantService homeAssistantService(final boolean discoveryEnabled, final boolean mqttAtivo) {
        when(mqttIntegration.ativo()).thenReturn(mqttAtivo);
        final var service = new HomeAssistantServiceImpl(mqttIntegration);
        ReflectionTestUtils.setField(service, "discoveryEnabled", discoveryEnabled);
        ReflectionTestUtils.setField(service, "discoveryPrefix", "homeassistant");
        return service;
    }

    @Test
    @DisplayName("A config é publicada no tópico de discovery do sensor")
    void testPublicaSensor() {
        final var config = HaConfigModel.builder()
                .uniqueId("nobreak_temp")
                .stateTopic("joule/nobreak/status")
                .build();

        homeAssistantService(true).publicarConfig(config);

        verify(mqttIntegration).publish(eq("homeassistant/sensor/nobreak_temp/config"), eq(config), eq(true));
    }

    @Test
    @DisplayName("Sem broker MQTT o discovery não é publicado nem enche o log")
    void testSemBrokerNaoPublicaDiscovery() {
        final var config = HaConfigModel.builder()
                .uniqueId("nobreak_temp")
                .stateTopic("joule/nobreak/status")
                .build();

        homeAssistantService(true, false).publicarConfig(config);

        verify(mqttIntegration, never()).publish(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Discovery desabilitado não publica nada")
    void testDiscoveryDesabilitado() {
        final var config = HaConfigModel.builder()
                .uniqueId("nobreak_temp")
                .stateTopic("joule/nobreak/status")
                .build();

        homeAssistantService(false).publicarConfig(config);

        verify(mqttIntegration, never()).publish(any(), any(), anyBoolean());
    }
}
