// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.model;

import br.com.edward.joule.enums.EnumStatusNobreak;
import lombok.*;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NobreakModel {

    private String rawHex;

    private Double inputVoltage;
    private Double frequency;

    private Double outputVoltage;
    private Double outputCurrent;
    private Double realPower;

    private Double batteryVoltage;
    private Double batteryPercentage;

    private Integer loadPercentage;
    private Integer temperature;

    private EnumStatusNobreak status;
}
