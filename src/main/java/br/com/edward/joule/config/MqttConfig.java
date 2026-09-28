// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLSocketFactory;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.username}")
    private String username;

    @Value("${mqtt.broker.password}")
    private String password;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.state-topic}")
    private String stateTopic;

    private final JsonMapper jsonMapper;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        final var factory = new DefaultMqttPahoClientFactory();
        final var options = new MqttConnectOptions();
        options.setServerURIs(new String[]{this.brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        if (!this.username.isBlank()) {
            options.setUserName(this.username);
            options.setPassword(this.password.toCharArray());
        }

        // a fábrica SSL padrão só serve para ssl:// e quebra conexões tcp:// em texto puro
        if (this.brokerUrl.startsWith("ssl://") || this.brokerUrl.startsWith("wss://")) {
            options.setSocketFactory(SSLSocketFactory.getDefault());
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        final var messageHandler = new MqttPahoMessageHandler(this.clientId + "-out", mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(this.stateTopic);
        messageHandler.setConverter(new JsonMessageConverter(this.jsonMapper));
        return messageHandler;
    }
}
