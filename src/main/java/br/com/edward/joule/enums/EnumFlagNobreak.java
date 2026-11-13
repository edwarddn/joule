// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.enums;

import br.com.edward.joule.nobreak.RagtechEasy;
import br.com.edward.joule.nobreak.RagtechEasy.Bit;
import br.com.edward.joule.nobreak.RagtechMemoria;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum EnumFlagNobreak {

    EM_BATERIA("opBattery", "operando em bateria", Categoria.OPERACAO, RagtechEasy.F_OPBATTERY),
    AUTOTESTE("opCheckup", "em autoteste (descarga da bateria)", Categoria.OPERACAO, RagtechEasy.F_OPCHECKUP),
    SEM_ENTRADA("noVInput", "sem energia na entrada", Categoria.OPERACAO, RagtechEasy.F_NOVINPUT),
    REDE_SINCRONIZADA("syncInput", "sincronizado com a rede", Categoria.OPERACAO, RagtechEasy.F_SYNCIN),
    CONTROLE_REMOTO("remoteControlActive", "controle remoto ativo", Categoria.OPERACAO, RagtechEasy.F_RCTRLON),

    ENTRADA_BAIXA("loVInput", "tensão de entrada baixa", Categoria.AVISO, RagtechEasy.F_LOVINPUT),
    ENTRADA_ALTA("hiVInput", "tensão de entrada alta", Categoria.AVISO, RagtechEasy.F_HIVINPUT),
    CARGA_ALTA("hiPOutput", "potência de saída alta", Categoria.AVISO, RagtechEasy.F_HIPOUTPUT),
    BATERIA_BAIXA("loBattery", "bateria baixa", Categoria.AVISO, RagtechEasy.F_LOBATTERY),
    SEM_BATERIA("noBattery", "bateria ausente ou desconectada", Categoria.AVISO, RagtechEasy.F_NOBAT),
    BATERIA_VELHA("oldBattery", "bateria velha (troca recomendada)", Categoria.AVISO, RagtechEasy.F_OLDBAT),
    MAIS_BATERIA("moreBattery", "capacidade de bateria maior que a registrada", Categoria.AVISO, RagtechEasy.F_MOREBAT),
    MENOS_BATERIA("lessBattery", "capacidade de bateria menor que a registrada", Categoria.AVISO, RagtechEasy.F_LESSBAT),

    FALHA_SOBRETEMPERATURA("failOvertemp", "falha: sobretemperatura", Categoria.FALHA_EMERGENCIA, RagtechEasy.F_FOVERTEMP),
    FALHA_FIM_DE_BATERIA("failEndBattery", "falha: fim de bateria", Categoria.FALHA_EMERGENCIA, RagtechEasy.F_FENDBATTERY),
    FALHA_SOBRECARGA("failOverload", "falha: sobrecarga", Categoria.FALHA_EMERGENCIA, RagtechEasy.F_FOVERLOAD),
    FALHA_SAIDA_ANORMAL("failAbnormalVOutput", "falha: tensão de saída anormal", Categoria.FALHA_EMERGENCIA, RagtechEasy.F_FABNORMALVOUT),
    FALHA_CURTO_CIRCUITO("failShortcircuit", "falha: curto-circuito na saída", Categoria.FALHA_EMERGENCIA, RagtechEasy.F_FSHORTCIRCUIT),
    FALHA_BATERIA_ANORMAL("failAbnormalVBattery", "falha: tensão de bateria anormal", Categoria.FALHA_CALMA, RagtechEasy.F_FABNORMALVBAT),
    FALHA_INVERSOR("failInverter", "falha do inversor", Categoria.FALHA_CALMA, RagtechEasy.F_FINVERTER);

    public enum Categoria {
        OPERACAO, AVISO, FALHA_EMERGENCIA, FALHA_CALMA
    }

    private final String nomeRagtech;
    private final String descricao;
    private final Categoria categoria;
    private final Bit bit;

    public boolean isFalha() {
        return this.categoria == Categoria.FALHA_EMERGENCIA || this.categoria == Categoria.FALHA_CALMA;
    }

    public boolean isAlerta() {
        return this.categoria != Categoria.OPERACAO;
    }

    public static Set<EnumFlagNobreak> ativas(final RagtechMemoria memoria) {
        final var ativas = Arrays.stream(values())
                .filter(f -> memoria.bit(f.bit))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EnumFlagNobreak.class)));
        if (ativas.contains(SEM_ENTRADA)) {
            ativas.remove(ENTRADA_BAIXA);
        }
        return ativas;
    }
}
