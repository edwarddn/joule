// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString
public class HaDeviceModel {
    
    private final List<String> identifiers;
    private final String name;
    private final String model;
    private final String manufacturer;
    private final String sw_version;
}
