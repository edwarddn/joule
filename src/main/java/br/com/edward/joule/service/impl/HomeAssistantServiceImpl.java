// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service.impl;

import br.com.edward.joule.integration.MqttIntegration;
import br.com.edward.joule.model.HaConfigModel;
import br.com.edward.joule.service.HomeAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class HomeAssistantServiceImpl implements HomeAssistantService {

    private final MqttIntegration mqttIntegration;

    @Value("${homeassistant.discovery.enabled}")
    private boolean discoveryEnabled;

    @Value("${homeassistant.discovery.prefix}")
    private String discoveryPrefix;

    @Override
    public void publicarConfig(final HaConfigModel config) {
        if (!this.discoveryEnabled || !this.mqttIntegration.ativo()) {
            log.debug("| Discovery não publicado para {}.", config.getUniqueId());
            return;
        }

        final var topic = String.format("%s/sensor/%s/config",
                this.discoveryPrefix,
                config.getUniqueId());

        this.mqttIntegration.publish(topic, config, true);
    }
}
