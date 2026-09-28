// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.integration.impl;

import br.com.edward.joule.integration.SerialPortIntegration;
import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HexFormat;
import java.util.Objects;

@Slf4j
@Component
public class SerialPortIntegrationImpl implements SerialPortIntegration {

    private static final int BAUD_RATE = 2560;
    private static final int TAMANHO_PACOTE_STATUS = 31;

    private static final byte[] CMD_STATUS = {
            (byte) 0xAA, (byte) 0x04, (byte) 0x00, (byte) 0x80, (byte) 0x1E, (byte) 0x9E
    };

    private static final byte[] CMD_HANDSHAKE = {
            (byte) 0xAA, (byte) 0x04, (byte) 0xFF, (byte) 0xE0, (byte) 0x08, (byte) 0x95
    };
    private static final byte[] CMD_INIT_1 = {
            (byte) 0xAA, (byte) 0x04, (byte) 0x00, (byte) 0x85, (byte) 0x01, (byte) 0x86
    };
    private static final byte[] CMD_INIT_2 = {
            (byte) 0xAA, (byte) 0x03, (byte) 0x00, (byte) 0x92, (byte) 0x02, (byte) 0x94
    };

    @Value("${nobreak.serial-port:}")
    private String portName;

    private SerialPort serialPort;

    @Override
    public byte[] lerStatusBruto() {
        if (this.desligado()) {
            return null;
        }
        try {
            final var port = this.getSerialPort();
            cleanPort(port);

            port.writeBytes(CMD_STATUS, CMD_STATUS.length);
            Thread.sleep(200);

            if (port.bytesAvailable() == TAMANHO_PACOTE_STATUS) {
                final var data = new byte[port.bytesAvailable()];
                final var length = port.readBytes(data, data.length);
                if (length == 0) {
                    return null;
                }
                return data;
            }

            log.warn("| Aguardando dados completos... (bytes disponíveis: {})", port.bytesAvailable());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public String enviarComando(final byte[] comando, final String descricao) {
        if (this.desligado()) {
            return null;
        }
        try {
            final var port = this.getSerialPort();
            cleanPort(port);

            port.writeBytes(comando, comando.length);
            Thread.sleep(200);

            if (port.bytesAvailable() > 0) {
                final var resp = new byte[port.bytesAvailable()];
                final var len = port.readBytes(resp, resp.length);
                final var hex = bytesToHex(resp, len);
                log.info("| [RETORNO {}]: {}", descricao, hex);
                return hex;
            }

            log.info("| [RETORNO {}]: (Sem resposta)", descricao);
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void closePort() {
        if (Objects.nonNull(this.serialPort) && this.serialPort.isOpen()) {
            this.serialPort.closePort();
        }
    }

    private boolean desligado() {
        if (Objects.isNull(this.portName) || this.portName.isBlank()) {
            log.debug("| Porta serial não configurada (nobreak.serial-port vazio); nada a fazer.");
            return true;
        }
        return false;
    }

    private SerialPort getSerialPort() {
        if (Objects.nonNull(this.serialPort) && this.serialPort.isOpen()) {
            return this.serialPort;
        }

        this.serialPort = SerialPort.getCommPort(this.portName);
        this.serialPort.setBaudRate(BAUD_RATE);
        this.serialPort.setNumDataBits(8);
        this.serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        this.serialPort.setParity(SerialPort.NO_PARITY);
        this.serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 2000, 0);

        if (!this.serialPort.openPort()) {
            log.error("| ERRO CRÍTICO: não foi possível abrir {}", this.portName);
            log.error("| Verifique as permissões (grupo dialout/uucp) ou se o nobreak está conectado.");
            throw new IllegalStateException("Não foi possível abrir a porta serial " + this.portName);
        }

        log.info("+----------------------------------------------------------");
        log.info("| EXECUTANDO HANDSHAKE (INICIALIZAÇÃO)");
        this.enviarComando(CMD_HANDSHAKE, "Handshake (FF E0)");
        this.enviarComando(CMD_INIT_1, "Init Config 1 (00 85)");
        this.enviarComando(CMD_INIT_2, "Init Config 2 (00 92)");
        log.info("| HANDSHAKE CONCLUÍDO");
        log.info("+----------------------------------------------------------");

        return this.serialPort;
    }

    private void cleanPort(final SerialPort port) {
        final var bytesDisponiveis = port.bytesAvailable();
        if (bytesDisponiveis > 0) {
            final var lixo = new byte[bytesDisponiveis];
            port.readBytes(lixo, lixo.length);
            log.trace("| Buffer limpo: {} bytes descartados.", bytesDisponiveis);
        }
    }

    private static String bytesToHex(final byte[] bytes, final int length) {
        return HexFormat.ofDelimiter(" ")
                .withUpperCase()
                .formatHex(bytes, 0, length);
    }
}
