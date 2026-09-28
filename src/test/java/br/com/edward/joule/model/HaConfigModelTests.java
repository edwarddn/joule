// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HaConfigModelTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("O discovery é serializado com as chaves que o Home Assistant espera")
    void testChavesDoDiscovery() {
        final var config = HaConfigModel.builder()
                .name("Tensão Entrada")
                .uniqueId("nobreak_in_volt")
                .defaultEntityId("sensor.nobreak_in_volt")
                .device(HaDeviceModel.builder()
                        .identifiers(List.of("nobreak"))
                        .name("Nobreak")
                        .manufacturer("Ragtech")
                        .build())
                .deviceClass("voltage")
                .stateClass("measurement")
                .unitOfMeasurement("V")
                .stateTopic("joule/nobreak/status")
                .valueTemplate("{{ value_json.input_volt }}")
                .build();

        final var json = jsonMapper.writeValueAsString(config);

        assertThat(json)
                .contains("\"unique_id\":\"nobreak_in_volt\"")
                .contains("\"default_entity_id\":\"sensor.nobreak_in_volt\"")
                .contains("\"device_class\":\"voltage\"")
                .contains("\"state_class\":\"measurement\"")
                .contains("\"unit_of_measurement\":\"V\"")
                .contains("\"state_topic\":\"joule/nobreak/status\"")
                .contains("\"value_template\":\"{{ value_json.input_volt }}\"")
                .doesNotContain("uniqueId")
                .doesNotContain("stateTopic");
    }
}
