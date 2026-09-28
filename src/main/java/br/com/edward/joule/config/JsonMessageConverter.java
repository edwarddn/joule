// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.config;

import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

public class JsonMessageConverter extends DefaultPahoMessageConverter {

    private final JsonMapper jsonMapper;

    public JsonMessageConverter(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected byte[] messageToMqttBytes(final Message<?> message) {
        final var payload = message.getPayload();
        if (!(payload instanceof String) && !(payload instanceof byte[])) {
            return super.messageToMqttBytes(
                    MessageBuilder.withPayload(jsonMapper.writeValueAsString(payload))
                            .copyHeaders(message.getHeaders())
                            .build());
        }
        return super.messageToMqttBytes(message);
    }
}
