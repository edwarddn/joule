// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.service;

import br.com.edward.joule.model.NobreakModel;

public interface NobreakService {

    NobreakModel status();
    void desliga();
    void desliga(int segundos);
    void desliga(int segundos, boolean religarAutomaticamente);

    void registrarNobreak();
    void closePort();
}
