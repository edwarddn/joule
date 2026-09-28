// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration.impl;

import br.com.edward.joule.integration.MqttIntegration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class MqttIntegrationImpl implements MqttIntegration {

    private final MessageChannel mqttOutboundChannel;

    @Override
    public boolean ativo() {
        return true;
    }

    @Override
    public void publish(final String topic, final Object payload) {
        this.publish(topic, payload, false);
    }

    @Override
    public void publish(final String topic, final Object payload, final boolean retained) {
        mqttOutboundChannel.send(MessageBuilder
                .withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, topic)
                .setHeader(MqttHeaders.RETAINED, retained)
                .build());
    }
}
