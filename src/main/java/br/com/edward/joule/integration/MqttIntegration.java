// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration;

public interface MqttIntegration {

    boolean ativo();

    void publish(String topic, Object payload);

    void publish(String topic, Object payload, boolean retained);
}
