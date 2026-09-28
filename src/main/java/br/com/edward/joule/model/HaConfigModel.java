// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class HaConfigModel {

    private final String name;

    @JsonProperty("unique_id")
    private final String uniqueId;

    @JsonProperty("default_entity_id")
    private final String defaultEntityId;

    private final HaDeviceModel device;

    private final String icon;

    @JsonProperty("device_class")
    private final String deviceClass;

    @JsonProperty("unit_of_measurement")
    private final String unitOfMeasurement;

    @JsonProperty("state_class")
    private final String stateClass;

    @JsonProperty("state_topic")
    private final String stateTopic;

    @JsonProperty("value_template")
    private final String valueTemplate;
}
