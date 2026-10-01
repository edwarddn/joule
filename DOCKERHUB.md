# Joule

Monitor de nobreak via USB que publica a telemetria no Home Assistant por MQTT e, quando a
energia não volta, desliga os servidores com segurança antes que a bateria acabe. Quando a energia
volta, o nobreak religa a saída e os servidores sobem sozinhos.

Feito para uso doméstico. Roda em container, sem nuvem e sem serviço externo.

* **Código-fonte:** [github.com/edwarddn/joule](https://github.com/edwarddn/joule)
* **Documentação completa:** [README no GitHub](https://github.com/edwarddn/joule#readme)
* **Problemas e relatos de compatibilidade:** [issues](https://github.com/edwarddn/joule/issues)

## Aviso importante

O protocolo do nobreak foi obtido por **engenharia reversa**, observando os bytes trocados na porta
USB. Este projeto **não tem nenhum vínculo** com a Ragtech Eletrônica Industrial Ltda. "Ragtech",
"Easy Pro" e "Supervise" são marcas de seus titulares e aparecem aqui apenas para indicar com qual
equipamento o software foi testado e qual software ele substitui.

**Use por sua conta e risco.** O Joule envia comandos que cortam a saída do nobreak e desligam
máquinas. Teste com carga sem importância antes de confiar nele. Sem qualquer garantia, nos termos
da GPL-3.0.

## Uma alternativa ao Supervise 8

O software oficial da Ragtech, o **Supervise 8**, existe só para Windows e para Linux em pacote
`.deb`. Não há versão para **ARM** (Raspberry Pi e similares) nem para **macOS**.

O Joule é uma alternativa com imagem para `linux/amd64` e `linux/arm64`, e traz também
monitoramento contínuo e integração com o Home Assistant.

Até agora só foi testado em **Linux com Docker**. Se usar no macOS ou no Windows, conte como foi numa
[issue](https://github.com/edwarddn/joule/issues).

## Equipamento testado

| Fabricante | Modelo                 | Conexão   |
| ---------- | ---------------------- | --------- |
| Ragtech    | 4162 Easy Pro NEP 1200 | USB (CDC) |

Outros modelos da mesma linha talvez funcionem. Se testar em outro, conte o resultado numa
[issue](https://github.com/edwarddn/joule/issues).

## O que ele faz

* Lê tensão de entrada e saída, potência, frequência, bateria, temperatura, carga e status.
* Publica o estado por MQTT e cria os sensores no Home Assistant via MQTT Discovery, sem editar YAML.
* Se a energia não volta, manda `poweroff` nos servidores via SSH e arma o nobreak para cortar a
  saída e religá-la quando a energia voltar.
* Em situação crítica (bateria no fim ou temperatura alta), desliga na hora, sem religamento
  automático.

Cada parte é opcional: dá para usar só a telemetria, só o desligamento, ou os dois.

No Home Assistant, os sensores aparecem assim:

![Dispositivo Nobreak no Home Assistant](https://raw.githubusercontent.com/edwarddn/joule/main/docs/home-assistant.png)

## Tags

| Tag      | Descrição                         |
| -------- | --------------------------------- |
| `latest` | Última versão publicada.          |
| `x.y.z`  | Versão fixa, por exemplo `0.0.2`. |

Arquiteturas: `linux/amd64` e `linux/arm64`.

## Uso rápido

Exemplo com telemetria no Home Assistant e desligamento de dois servidores por SSH:

```yaml
services:
  joule:
    image: edwarddn/joule:latest
    container_name: joule
    restart: unless-stopped
    environment:
      TZ: America/Sao_Paulo
      JAVA_TOOL_OPTIONS: >-
        -Duser.language=pt -Duser.country=BR
        -Duser.timezone=America/Sao_Paulo -Dfile.encoding=UTF-8
        --enable-native-access=ALL-UNNAMED

      # MQTT
      MQTT_URL: ${MQTT_URL}
      MQTT_USER: ${MQTT_USER}
      MQTT_PASS: ${MQTT_PASS}

      # Configuração do nobreak
      NOBREAK_PORT: /dev/ttyACM0
      SHUTDOWN_STEPS: 2

      # Servidor 1: AdGuard, DNS da rede (índice 0)
      SSH_SERVERS_0_NAME: AdGuard
      SSH_SERVERS_0_HOST: 192.168.1.20
      SSH_SERVERS_0_PORT: 22
      SSH_SERVERS_0_USER: usuario
      SSH_SERVERS_0_PRIVATE_KEY_PATH: /keys/id_ed25519

      # Servidor 2: Homelab (índice 1), a máquina que hospeda o Joule, por isso vai por último
      SSH_SERVERS_1_NAME: Homelab
      SSH_SERVERS_1_HOST: 192.168.1.30
      SSH_SERVERS_1_PORT: 22
      SSH_SERVERS_1_USER: usuario
      SSH_SERVERS_1_PRIVATE_KEY_PATH: /keys/id_ed25519
    devices:
      - /dev/ttyACM0:/dev/ttyACM0
    volumes:
      - ./logs:/workspace/logs
      - /home/usuario/.ssh/id_ed25519:/keys/id_ed25519:ro
```

E um `.env` ao lado com as credenciais do broker:

```bash
MQTT_URL=tcp://192.168.1.10:1883
MQTT_USER=usuario_mqtt
MQTT_PASS=senha_mqtt
```

O MQTT funciona com ou sem criptografia. Use `tcp://` para texto puro ou `ssl://` para TLS, por
exemplo `MQTT_URL=ssl://mqtt.minhacasa.com.br:8883`. O certificado é validado pela cadeia de
confiança padrão do Java, então um certificado do Let's Encrypt funciona sem configuração extra.

Suba com `docker compose up -d` e acompanhe com `docker compose logs -f joule`.

Sem Home Assistant nem broker, use `MQTT_ENABLED: "false"` e a telemetria vai para o log. Para só
monitorar, sem desligar nada, use `SHUTDOWN_ENABLED: "false"`.

Nos servidores de destino, o usuário precisa rodar `sudo -n poweroff` sem senha. O passo a passo
está no [README](https://github.com/edwarddn/joule#desligando-servidores-por-ssh).

## Religamento quando a energia volta

O nobreak religa a saída sozinho quando a energia volta, mas cada servidor precisa estar
configurado para ligar ao receber energia. Na BIOS/UEFI, procure a opção **Restore on AC Power
Loss**, **AC Power Recovery**, **After Power Failure** ou **State After G3** e deixe em **Power On**
(ou **S0 State**). Não use **Last State**: como o servidor foi desligado por `poweroff`, ele não
vai ligar.

O Raspberry Pi não precisa de nada, ele liga sempre que recebe energia. Detalhes no
[README](https://github.com/edwarddn/joule#religamento-automático-quando-a-energia-volta).

## Principais variáveis

| Variável                   | Padrão                 | Descrição                                               |
| -------------------------- | ---------------------- | ------------------------------------------------------- |
| `NOBREAK_PORT`             | `/dev/ttyACM0`         | Porta serial do nobreak dentro do container.            |
| `MONITOR_INTERVAL_SECONDS` | `30`                   | Intervalo entre leituras.                               |
| `SHUTDOWN_ENABLED`         | `true`                 | `false` desativa todo desligamento automático.          |
| `SHUTDOWN_STEPS`           | `3`                    | Leituras seguidas em bateria antes de desligar.         |
| `SHUTDOWN_DELAY`           | `60`                   | Segundos até o nobreak cortar a saída (1 a 90).         |
| `CRITICAL_TEMPERATURE`     | `90`                   | Temperatura, em °C, que dispara desligamento imediato.  |
| `MQTT_ENABLED`             | `true`                 | `false` desliga o MQTT.                                 |
| `MQTT_URL`                 | `tcp://localhost:1883` | `tcp://` sem criptografia ou `ssl://` com TLS.          |
| `HA_DISCOVERY_ENABLED`     | `true`                 | `false` não cria as entidades no Home Assistant.        |
| `HA_DEVICE_ID`             | `nobreak`              | Prefixo das entidades (`sensor.nobreak_in_volt`).       |

A lista completa, incluindo o desligamento de servidores por SSH, está no
[README](https://github.com/edwarddn/joule#variáveis-de-ambiente).

## Licença

[GPL-3.0](https://github.com/edwarddn/joule/blob/main/LICENSE). Copyright (C) 2026 Edward.
