// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.nobreak;

import br.com.edward.joule.enums.EnumFlagNobreak;
import br.com.edward.joule.enums.EnumModeloEasy;
import br.com.edward.joule.enums.EnumStatusNobreak;
import br.com.edward.joule.model.NobreakModel;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Objects;

@UtilityClass
public class RagtechEasy {

    public record Faixa(int inicio, int tamanho) { }

    public record Bit(int endereco, int bit) {
        public int mascara() {
            return 1 << this.bit;
        }
    }

    public static final Faixa FAIXA_PRINCIPAL = new Faixa(0x0080, 30);
    public static final List<Faixa> FAIXAS = List.of(
            FAIXA_PRINCIPAL,
            new Faixa(0x00F3, 1),
            new Faixa(0x0136, 1),
            new Faixa(0x0171, 4),
            new Faixa(0x0202, 2));

    public static final int V_PPOWERLO = 0x0081;
    public static final int V_TPOWERLO = 0x0082;
    public static final int V_CAPBATREF = 0x0083;
    public static final int V_WAKEUPHI = 0x0084;
    public static final int V_WAKEUPMID = 0x0085;
    public static final int V_WAKEUPLOW = 0x0086;
    public static final int V_CBATTERY = 0x0087;
    public static final int V_VBATTERY = 0x008A;
    public static final int V_VINPUT = 0x008B;
    public static final int V_IOUTPUT = 0x008C;
    public static final int V_POUTPUT = 0x008D;
    public static final int V_TEMPER = 0x008E;
    public static final int V_FOUTPUT = 0x0097;
    public static final int V_SHUTDOWNTIMER = 0x0098;
    public static final int V_MODEL = 0x009A;
    public static final int V_VERSION = 0x009B;
    public static final int V_VOUTPUT = 0x009D;
    public static final int V_IOUTCALIB = 0x00F3;
    public static final int V_CAPBATNEW = 0x0136;
    public static final int V_LEDRED = 0x0171;
    public static final int V_LEDGREEN = 0x0172;
    public static final int V_LEDBLUE = 0x0173;
    public static final int V_RANDOM = 0x0174;
    public static final int V_OSC53 = 0x0202;
    public static final int V_OSC57 = 0x0203;

    public static final Bit F_AUTOSTART = new Bit(0x0080, 0);
    public static final Bit F_ICBATTERY = new Bit(0x0080, 2);
    public static final Bit F_LINESENS = new Bit(0x0080, 7);
    public static final Bit F_NOBAT = new Bit(0x0090, 0);
    public static final Bit F_OLDBAT = new Bit(0x0090, 1);
    public static final Bit F_OPCHECKUP = new Bit(0x0090, 2);
    public static final Bit F_NOVINPUT = new Bit(0x0090, 3);
    public static final Bit F_LOVINPUT = new Bit(0x0090, 4);
    public static final Bit F_HIVINPUT = new Bit(0x0090, 5);
    public static final Bit F_OPBATTERY = new Bit(0x0090, 6);
    public static final Bit F_HIPOUTPUT = new Bit(0x0090, 7);
    public static final Bit F_LOBATTERY = new Bit(0x0091, 0);
    public static final Bit F_FOVERTEMP = new Bit(0x0091, 1);
    public static final Bit F_FENDBATTERY = new Bit(0x0091, 2);
    public static final Bit F_FOVERLOAD = new Bit(0x0091, 3);
    public static final Bit F_FABNORMALVOUT = new Bit(0x0091, 4);
    public static final Bit F_FABNORMALVBAT = new Bit(0x0091, 5);
    public static final Bit F_FINVERTER = new Bit(0x0091, 6);
    public static final Bit F_FSHORTCIRCUIT = new Bit(0x0091, 7);
    public static final Bit F_SYNCIN = new Bit(0x0092, 0);
    public static final Bit F_SUPERVON = new Bit(0x0092, 1);
    public static final Bit F_MOREBAT = new Bit(0x0092, 2);
    public static final Bit F_LESSBAT = new Bit(0x0092, 3);
    public static final Bit F_RCTRLON = new Bit(0x0092, 5);
    public static final Bit F_POWERLOON = new Bit(0x0092, 6);
    public static final Bit F_NIGHTOFFON = new Bit(0x0092, 7);
    public static final Bit F_TRANSFINV = new Bit(0x0095, 7);

    public static final double C_FINNOMINAL = 60.0;
    public static final double C_VINNOMINAL = 115.0;
    public static final double C_VINNOMINAL2 = 220.0;
    public static final double FATOR_POTENCIA = 0.7;

    public static final double SEGUNDOS_POR_TICK = 5.904144;
    public static final int CONTADOR_MAXIMO = 0x1000000;

    public static EnumModeloEasy modelo(final RagtechMemoria m) {
        return EnumModeloEasy.porCodigo(m.v(V_MODEL)).orElse(EnumModeloEasy.TESTADO);
    }

    public static NobreakModel decodificar(final RagtechMemoria m, final int temperaturaCritica) {
        final var codigo = m.v(V_MODEL);
        final var modelo = modelo(m);
        final var conhecido = EnumModeloEasy.porCodigo(codigo).isPresent();
        final var flags = EnumFlagNobreak.ativas(m);
        final var vInput = m.v(V_VINPUT) * 1.06;
        final var temperatura = m.v(V_TEMPER);
        final var carga = m.v(V_POUTPUT);
        final var frequencia = frequencia(m);
        final var wakeup = wakeupTimer(m);

        return NobreakModel.builder()
                .modelo(codigo)
                .modeloNome(conhecido ? modelo.getNome() : "desconhecido (" + codigo + ")")
                .modeloTestado(modelo == EnumModeloEasy.TESTADO && conhecido)
                .firmware(m.v(V_VERSION) / 10.0)
                .inputVoltage(vInput)
                .frequency(frequencia)
                .outputVoltage(m.v(V_VOUTPUT) * modelo.fatorTensaoSaida())
                .outputCurrent(corrente(m, modelo))
                .loadPercentage(carga)
                .realPower(carga / 100.0 * modelo.getPotenciaNominalVa() * FATOR_POTENCIA)
                .batteryVoltage(m.v(V_VBATTERY) * modelo.fatorTensaoBateria())
                .batteryPercentage(Math.min(100.0, m.v(V_CBATTERY) * 0.393))
                .temperature(temperatura)
                .shutdownTimer(m.v(V_SHUTDOWNTIMER) != 0 ? m.v(V_SHUTDOWNTIMER) : null)
                .wakeupTimer(wakeup)
                .nightoffTimer(Objects.isNull(wakeup) ? null : wakeup / fatorFrequencia(frequencia))
                .lineSense(m.bit(F_LINESENS))
                .icBattery(m.bit(F_ICBATTERY))
                .ledRed(m.lido(V_LEDRED) ? m.v(V_LEDRED) : null)
                .ledGreen(m.lido(V_LEDGREEN) ? m.v(V_LEDGREEN) : null)
                .ledBlue(m.lido(V_LEDBLUE) ? m.v(V_LEDBLUE) : null)
                .nominalInputVoltage(vInput > 150.0 ? C_VINNOMINAL2 : C_VINNOMINAL)
                .nominalOutputVoltage(modelo.tensaoSaidaNominal())
                .nominalFrequency(C_FINNOMINAL)
                .nominalPower((double) modelo.getPotenciaNominalVa())
                .nominalBatteryVoltage(modelo.tensaoBateriaNominal())
                .flags(flags)
                .status(EnumStatusNobreak.computar(flags, temperatura, temperaturaCritica))
                .build();
    }

    public static Double corrente(final RagtechMemoria m, final EnumModeloEasy modelo) {
        if (!m.lido(V_IOUTCALIB) || m.v(V_IOUTCALIB) <= 0) {
            return null;
        }
        return m.v(V_IOUTPUT) * modelo.getFatorCorrente() / m.v(V_IOUTCALIB);
    }

    public static Double frequencia(final RagtechMemoria m) {
        if (!m.lido(V_OSC53) || !m.lido(V_OSC57)) {
            return null;
        }
        final var faixa = m.v(V_OSC57) - m.v(V_OSC53);
        if (faixa == 0) {
            return null;
        }
        return (53.0 + (4.0 * (m.v(V_FOUTPUT) - m.v(V_OSC53)) / faixa)) * 0.9806 + 1.46;
    }

    public static Double wakeupTimer(final RagtechMemoria m) {
        if (!m.bit(F_NIGHTOFFON)) {
            return null;
        }
        final var contador = m.v(V_WAKEUPLOW) | (m.v(V_WAKEUPMID) << 8) | (m.v(V_WAKEUPHI) << 16);
        return (CONTADOR_MAXIMO - contador) * SEGUNDOS_POR_TICK;
    }

    public static double fatorFrequencia(final Double frequencia) {
        if (Objects.isNull(frequencia) || frequencia / 59.4 <= 0.0) {
            return 1.0;
        }
        return frequencia / 59.4;
    }

    public static int[] contador(final long segundos, final double fator) {
        final var valor = (long) (CONTADOR_MAXIMO - (segundos / SEGUNDOS_POR_TICK) * fator);
        return new int[]{(int) ((valor & 0xFF0000) >> 16), (int) ((valor & 0xFF00) >> 8), (int) (valor & 0xFF)};
    }
}
