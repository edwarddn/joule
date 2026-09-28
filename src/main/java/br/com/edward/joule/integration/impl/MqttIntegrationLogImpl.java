// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration.impl;

import br.com.edward.joule.integration.MqttIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "false")
public class MqttIntegrationLogImpl implements MqttIntegration {

    @Override
    public boolean ativo() {
        return false;
    }

    @Override
    public void publish(final String topic, final Object payload) {
        this.publish(topic, payload, false);
    }

    @Override
    public void publish(final String topic, final Object payload, final boolean retained) {
        log.info("| [MQTT desabilitado] {}: {}", topic, payload);
    }
}
