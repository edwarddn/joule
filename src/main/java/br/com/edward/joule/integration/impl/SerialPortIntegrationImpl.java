// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration.impl;

import br.com.edward.joule.integration.SerialPortIntegration;
import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

@Slf4j
@Component
public class SerialPortIntegrationImpl implements SerialPortIntegration {

    private static final int BAUD_RATE = 2560;
    private static final int TIMEOUT_MS = 2000;

    private static final int HEADER = 0xAA;
    private static final int OP_GRAVAR_BYTE = 0x01;
    private static final int OP_DESLIGAR_BITS = 0x02;
    private static final int OP_LIGAR_BITS = 0x03;
    private static final int OP_LER = 0x04;

    private static final byte[] CMD_HANDSHAKE = {
            (byte) 0xAA, (byte) 0x04, (byte) 0xFF, (byte) 0xE0, (byte) 0x08, (byte) 0x95
    };

    private static final int ENDERECO_INIT = 0x85;
    private static final int ENDERECO_CONTROLE = 0x92;
    private static final int BIT_SUPERVISAO = 0x02;

    @Value("${nobreak.serial-port:}")
    private String portName;

    private SerialPort serialPort;

    @Override
    public synchronized byte[] ler(final int endereco, final int tamanho) {
        if (this.desligado()) {
            return null;
        }
        try {
            final var resposta = this.trocar(this.getSerialPort(), comando(OP_LER, endereco, tamanho), tamanho + 1);
            return Objects.isNull(resposta) ? null : Arrays.copyOfRange(resposta, 1, resposta.length);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public synchronized boolean gravarByte(final int endereco, final int valor) {
        return this.escrever(OP_GRAVAR_BYTE, endereco, valor);
    }

    @Override
    public synchronized boolean ligarBits(final int endereco, final int mascara) {
        return this.escrever(OP_LIGAR_BITS, endereco, mascara);
    }

    @Override
    public synchronized boolean desligarBits(final int endereco, final int mascara) {
        return this.escrever(OP_DESLIGAR_BITS, endereco, ~mascara);
    }

    @Override
    public synchronized void closePort() {
        if (Objects.nonNull(this.serialPort) && this.serialPort.isOpen()) {
            this.serialPort.closePort();
        }
    }

    static byte[] comando(final int op, final int endereco, final int valor) {
        final var alto = (endereco >> 8) & 0xFF;
        final var baixo = endereco & 0xFF;
        final var dado = valor & 0xFF;
        return new byte[]{(byte) HEADER, (byte) op, (byte) alto, (byte) baixo, (byte) dado, (byte) (alto + baixo + dado)};
    }

    private boolean escrever(final int op, final int endereco, final int valor) {
        if (this.desligado()) {
            return false;
        }
        try {
            return Objects.nonNull(this.trocar(this.getSerialPort(), comando(op, endereco, valor), 1));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private byte[] trocar(final SerialPort port, final byte[] comando, final int esperado) {
        limparBuffer(port);
        port.writeBytes(comando, comando.length);

        final var resposta = new byte[esperado];
        final var lidos = port.readBytes(resposta, esperado);
        log.debug("| [serial] {} -> {}", hex(comando, comando.length), hex(resposta, Math.max(lidos, 0)));

        if (lidos != esperado) {
            log.debug("| [serial] Resposta incompleta para {}: {} de {} bytes.", hex(comando, comando.length), lidos, esperado);
            return null;
        }
        if ((resposta[0] & 0xFF) != HEADER) {
            log.warn("| [serial] Header inválido na resposta: {}", hex(resposta, lidos));
            return null;
        }
        return resposta;
    }

    private boolean desligado() {
        if (Objects.isNull(this.portName) || this.portName.isBlank()) {
            log.debug("| Porta serial não configurada (nobreak.serial-port vazio); nada a fazer.");
            return true;
        }
        return false;
    }

    private SerialPort getSerialPort() throws InterruptedException {
        if (Objects.nonNull(this.serialPort) && this.serialPort.isOpen()) {
            return this.serialPort;
        }

        this.serialPort = SerialPort.getCommPort(this.portName);
        this.serialPort.setBaudRate(BAUD_RATE);
        this.serialPort.setNumDataBits(8);
        this.serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        this.serialPort.setParity(SerialPort.NO_PARITY);
        this.serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, TIMEOUT_MS, 0);

        if (!this.serialPort.openPort()) {
            log.error("| ERRO CRÍTICO: não foi possível abrir {}", this.portName);
            log.error("| Verifique as permissões (grupo dialout/uucp) ou se o nobreak está conectado.");
            throw new IllegalStateException("Não foi possível abrir a porta serial " + this.portName);
        }

        this.handshake(this.serialPort);
        return this.serialPort;
    }

    private void handshake(final SerialPort port) throws InterruptedException {
        log.info("+----------------------------------------------------------");
        log.info("| EXECUTANDO HANDSHAKE (INICIALIZAÇÃO)");

        limparBuffer(port);
        port.writeBytes(CMD_HANDSHAKE, CMD_HANDSHAKE.length);
        Thread.sleep(200);
        final var disponivel = Math.max(port.bytesAvailable(), 0);
        final var resposta = new byte[disponivel];
        final var lidos = disponivel > 0 ? port.readBytes(resposta, disponivel) : 0;
        log.info("| [RETORNO Handshake (FF E0)]: {}", lidos > 0 ? hex(resposta, lidos) : "(Sem resposta)");

        final var init = this.trocar(port, comando(OP_LER, ENDERECO_INIT, 1), 2);
        log.info("| [RETORNO Init (ler 0x85)]: {}", Objects.nonNull(init) ? hex(init, init.length) : "(Sem resposta)");

        final var supervisao = this.trocar(port, comando(OP_LIGAR_BITS, ENDERECO_CONTROLE, BIT_SUPERVISAO), 1);
        log.info("| [RETORNO superviseOn (0x92)]: {}", Objects.nonNull(supervisao) ? "OK" : "(Sem resposta)");

        log.info("| HANDSHAKE CONCLUÍDO");
        log.info("+----------------------------------------------------------");
    }

    private static void limparBuffer(final SerialPort port) {
        final var bytesDisponiveis = port.bytesAvailable();
        if (bytesDisponiveis > 0) {
            final var lixo = new byte[bytesDisponiveis];
            port.readBytes(lixo, lixo.length);
            log.trace("| Buffer limpo: {} bytes descartados.", bytesDisponiveis);
        }
    }

    private static String hex(final byte[] bytes, final int length) {
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes, 0, length);
    }
}
