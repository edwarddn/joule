// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service;

import br.com.edward.joule.model.HaConfigModel;

public interface HomeAssistantService {

    void publicarConfig(HaConfigModel config);
}
