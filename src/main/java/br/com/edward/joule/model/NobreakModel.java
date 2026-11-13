// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.model;

import br.com.edward.joule.enums.EnumFlagNobreak;
import br.com.edward.joule.enums.EnumStatusNobreak;
import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NobreakModel {

    private Integer modelo;
    private String modeloNome;
    private Boolean modeloTestado;
    private Double firmware;

    private Double inputVoltage;
    private Double frequency;

    private Double outputVoltage;
    private Double outputCurrent;
    private Double realPower;
    private Integer loadPercentage;

    private Double batteryVoltage;
    private Double batteryPercentage;

    private Integer temperature;

    private Integer shutdownTimer;
    private Double wakeupTimer;
    private Double nightoffTimer;

    private Boolean lineSense;
    private Boolean icBattery;

    private Integer ledRed;
    private Integer ledGreen;
    private Integer ledBlue;

    private Double nominalInputVoltage;
    private Double nominalOutputVoltage;
    private Double nominalFrequency;
    private Double nominalPower;
    private Double nominalBatteryVoltage;

    @Builder.Default
    private Set<EnumFlagNobreak> flags = Set.of();

    private EnumStatusNobreak status;

    public List<EnumFlagNobreak> alertas() {
        return this.flags.stream().filter(EnumFlagNobreak::isAlerta).sorted().toList();
    }

    public String alertasTexto() {
        final var alertas = this.alertas();
        return alertas.isEmpty()
                ? "nenhum"
                : String.join(", ", alertas.stream().map(EnumFlagNobreak::getDescricao).toList());
    }
}
