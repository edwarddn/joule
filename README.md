# Joule

Monitor de nobreak via USB que publica a telemetria no Home Assistant por MQTT e, quando a
energia não volta, desliga os servidores com segurança antes que a bateria acabe.

Foi escrito para uso doméstico, roda em container e não depende de nuvem nem de serviço externo.

## Aviso importante, leia antes de usar

Este projeto foi feito por **engenharia reversa** do protocolo serial do nobreak, observando os
bytes trocados na porta USB.

* Eu **nunca tive acesso** a documentação, especificação, SDK, firmware ou qualquer material
  técnico da Ragtech.
* Este projeto **não tem nenhum vínculo** com a Ragtech Eletrônica Industrial Ltda. Não é
  autorizado, homologado, patrocinado nem revisado por ela. "Ragtech" e "Easy Pro" são marcas de
  seus titulares e aparecem aqui apenas para indicar com qual equipamento o software foi testado.
* Os valores de calibração, os bits de status e os comandos de desligamento foram **deduzidos por
  observação**. Eles funcionam no meu equipamento. Podem estar errados no seu, mesmo que o modelo
  pareça igual.

**Use por sua conta e risco.** Este software envia comandos que cortam a saída do nobreak e
desligam máquinas. Um comando interpretado de forma diferente pelo seu equipamento pode causar
desligamento inesperado, perda de dados, corrupção de sistemas de arquivos ou dano a hardware. O
autor não se responsabiliza por nada disso. O software é fornecido **sem qualquer garantia**, nos
termos da GPL-3.0 (veja a seção [Licença](#licença)).

Antes de confiar nele, teste com carga sem importância, acompanhe os logs e confirme que os
valores lidos batem com o que o painel do seu nobreak mostra.

## Equipamento testado

| Fabricante | Modelo                       | Conexão    | Situação                |
| ---------- | ---------------------------- | ---------- | ----------------------- |
| Ragtech    | 4162 Easy Pro NEP 1200       | USB (CDC)  | Testado em uso diário   |

Outros modelos da mesma linha **talvez** funcionem, porque provavelmente compartilham o protocolo.
Ninguém confirmou. Se você testar em outro modelo, abra uma issue contando o resultado, com ou sem
sucesso, e cole o pacote cru lido do equipamento. Para vê-lo, suba com log em debug:

```yaml
    environment:
      LOGGING_LEVEL_BR_COM_EDWARD_JOULE: DEBUG
```

Cada leitura passa a sair assim, e é isso que ajuda a mapear o protocolo:

```
| Pacote lido: AA 1E 00 80 ...
```

## O que ele faz

1. Abre a porta USB do nobreak, faz o handshake e passa a consultar o status em intervalo
   configurável.
2. Decodifica o pacote de 31 bytes e extrai tensão de entrada e saída, corrente, potência,
   frequência, tensão e carga da bateria, temperatura interna e os bits de status.
3. Publica o estado em um tópico MQTT retido e registra os sensores no Home Assistant por MQTT
   Discovery, sem precisar editar nenhum YAML do HA.
4. Se a rede elétrica cai e não volta dentro do limite configurado, arma o timer de desligamento do
   nobreak e manda `poweroff` nos servidores cadastrados, via SSH.
5. Em situação crítica, bateria no fim ou temperatura acima do limite, desliga imediatamente e sem
   religamento automático.

Cada etapa é opcional. Dá para usar só a telemetria, só o desligamento, ou os dois.

## Entidades criadas no Home Assistant

Com o discovery ligado, aparece um dispositivo chamado "Nobreak" com estes sensores:

| Entidade                     | Descrição          | Unidade |
| ---------------------------- | ------------------ | ------- |
| `sensor.nobreak_in_volt`     | Tensão de entrada  | V       |
| `sensor.nobreak_out_volt`    | Tensão de saída    | V       |
| `sensor.nobreak_power`       | Potência real      | W       |
| `sensor.nobreak_freq`        | Frequência         | Hz      |
| `sensor.nobreak_bat_volt`    | Tensão da bateria  | V       |
| `sensor.nobreak_bat_level`   | Nível da bateria   | %       |
| `sensor.nobreak_temp`        | Temperatura interna| °C      |
| `sensor.nobreak_load`        | Carga de saída     | %       |
| `sensor.nobreak_status`      | Status operacional | texto   |

O prefixo `nobreak` vem de `HA_DEVICE_ID` e pode ser trocado, útil se você tiver mais de um
equipamento.

## Pré-requisitos

* Um nobreak compatível ligado por USB na máquina que vai rodar o Joule.
* Docker e Docker Compose. Para build local, Java 25 e Maven 4 (o wrapper `./mvnw` já resolve o
  Maven).
* Um broker MQTT, por exemplo Mosquitto, **somente se** você quiser a telemetria. Sem broker,
  basta desligar o MQTT.

A porta costuma aparecer como `/dev/ttyACM0`. Confirme com:

```bash
ls -l /dev/ttyACM* /dev/ttyUSB*
dmesg | grep -i tty
```

## Como rodar

### Docker Compose

A imagem publicada é `edwarddn/joule`.

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

      NOBREAK_PORT: /dev/ttyACM0
      MONITOR_INTERVAL_SECONDS: "30"

      MQTT_URL: tcp://mosquitto:1883
      MQTT_USER: usuario_mqtt
      MQTT_PASS: senha_mqtt
    devices:
      - /dev/ttyACM0:/dev/ttyACM0
    volumes:
      - ./logs:/workspace/logs
```

Suba com `docker compose up -d` e acompanhe com `docker compose logs -f joule`.

O arquivo [`docker-compose.yml`](docker-compose.yml) deste repositório traz um exemplo completo,
com Mosquitto junto e com o desligamento de servidores configurado. O
[`.env.example`](.env.example) lista todas as variáveis com comentários.

### Sem Home Assistant e sem broker MQTT

Se você quer apenas o desligamento automático dos servidores e não usa Home Assistant, desligue o
MQTT. A aplicação não tenta se conectar a broker nenhum e a telemetria vai para o log da aplicação.

```yaml
    environment:
      MQTT_ENABLED: "false"
```

Se você usa MQTT com outro consumidor, mas não usa Home Assistant, mantenha o MQTT ligado e desligue
só o discovery. O estado continua sendo publicado em `joule/nobreak/status`.

```yaml
    environment:
      HA_DISCOVERY_ENABLED: "false"
```

### Sem desligar servidor nenhum

É o comportamento padrão. Enquanto você não cadastrar nenhum servidor em `SSH_SERVERS_*`, o Joule
só monitora, publica a telemetria e, na falta de energia prolongada, arma o desligamento do próprio
nobreak. Nenhuma máquina recebe comando.

Para não desligar nem o nobreak, desative o desligamento automático:

```yaml
    environment:
      SHUTDOWN_ENABLED: "false"
```

Isso vale para todos os gatilhos, inclusive a temperatura crítica e o status crítico. O Joule continua
monitorando e publicando a telemetria, e registra no log quando teria desligado.

### Desligando servidores por SSH

Cada servidor é um bloco numerado a partir de zero. A ordem importa: os servidores são desligados na
ordem em que aparecem, então **deixe por último a máquina que hospeda o Joule**, senão ela cai antes
de mandar o comando nas outras.

```yaml
    environment:
      SSH_SERVERS_0_NAME: nas
      SSH_SERVERS_0_HOST: 192.168.1.50
      SSH_SERVERS_0_PORT: "22"
      SSH_SERVERS_0_USER: admin
      SSH_SERVERS_0_PRIVATE_KEY_PATH: /workspace/keys/id_ed25519

      SSH_SERVERS_1_NAME: host
      SSH_SERVERS_1_HOST: 192.168.1.10
      SSH_SERVERS_1_PORT: "22"
      SSH_SERVERS_1_USER: admin
      SSH_SERVERS_1_PRIVATE_KEY_PATH: /workspace/keys/id_ed25519
    volumes:
      - ./keys/id_ed25519:/workspace/keys/id_ed25519:ro
```

Em cada servidor de destino, o usuário precisa poder desligar sem senha. Crie o arquivo
`/etc/sudoers.d/joule` com:

```
admin ALL=(ALL) NOPASSWD: /sbin/poweroff
```

Valide com `sudo visudo -c` e teste manualmente antes de contar com isso:

```bash
ssh -i ./keys/id_ed25519 admin@192.168.1.50 'sudo -n poweroff --help'
```

Se um servidor falhar, o erro é registrado no log e os demais continuam sendo desligados.

A verificação de host key está desativada, porque em um desligamento de emergência não há como
tratar chave desconhecida. Use isso apenas em rede que você controla.

## Variáveis de ambiente

### Nobreak

| Variável                   | Padrão          | Descrição                                                                       |
| -------------------------- | --------------- | ------------------------------------------------------------------------------- |
| `NOBREAK_PORT`             | `/dev/ttyACM0`  | Porta serial do nobreak dentro do container. Vazio desliga o monitoramento.      |
| `MONITOR_INTERVAL_SECONDS` | `30`            | Intervalo entre leituras de status.                                             |
| `SHUTDOWN_ENABLED`         | `true`          | `false` desativa todo desligamento automático, inclusive os de emergência.      |
| `SHUTDOWN_STEPS`           | `3`             | Quantas leituras seguidas em bateria antes de iniciar o desligamento. Mínimo 1. |
| `SHUTDOWN_DELAY`           | `60`            | Segundos entre o comando e o corte da saída do nobreak. Faixa aceita: 1 a 90.    |
| `CRITICAL_TEMPERATURE`      | `90`            | Acima disso o desligamento é imediato e sem religamento automático.             |

Com os padrões, a energia precisa ficar fora por cerca de 90 segundos (3 leituras a cada 30) para o
desligamento começar, e as máquinas ainda têm 60 segundos antes de o nobreak cortar a saída.

### MQTT

| Variável           | Padrão                  | Descrição                                                          |
| ------------------ | ----------------------- | ------------------------------------------------------------------ |
| `MQTT_ENABLED`     | `true`                  | `false` desliga o MQTT por completo e joga a telemetria no log.    |
| `MQTT_URL`         | `tcp://localhost:1883`  | URL do broker. Use `ssl://` para TLS.                              |
| `MQTT_USER`        | vazio                   | Usuário do broker. Vazio conecta sem autenticação.                 |
| `MQTT_PASS`        | vazio                   | Senha do broker.                                                   |
| `MQTT_STATE_TOPIC` | `joule/nobreak/status`  | Tópico onde o estado é publicado, com retained ligado.             |

### Home Assistant

| Variável                | Padrão          | Descrição                                                            |
| ----------------------- | --------------- | -------------------------------------------------------------------- |
| `HA_DISCOVERY_ENABLED`  | `true`          | `false` não registra as entidades, mas segue publicando o estado.    |
| `HA_DISCOVERY_PREFIX`   | `homeassistant` | Prefixo de discovery, o mesmo configurado no HA.                     |
| `HA_DEVICE_ID`          | `nobreak`       | Identificador do dispositivo e prefixo das entidades.                |

### SSH

| Variável                          | Padrão             | Descrição                                          |
| --------------------------------- | ------------------ | -------------------------------------------------- |
| `SSH_TIMEOUT`                     | `5000`             | Timeout de conexão e leitura, em milissegundos.    |
| `SSH_SHUTDOWN_COMMAND`            | `sudo -n poweroff` | Comando enviado a cada servidor.                   |
| `SSH_SERVERS_<n>_NAME`            |                    | Nome do servidor, só para aparecer no log.         |
| `SSH_SERVERS_<n>_HOST`            |                    | IP ou hostname.                                    |
| `SSH_SERVERS_<n>_PORT`            | `22`               | Porta SSH.                                         |
| `SSH_SERVERS_<n>_USER`            |                    | Usuário que executa o comando.                     |
| `SSH_SERVERS_<n>_PRIVATE_KEY_PATH`|                    | Caminho da chave privada dentro do container.      |

## Formato da telemetria

Payload publicado em `MQTT_STATE_TOPIC`:

```json
{
  "input_volt": 127.0,
  "output_volt": 119.9,
  "battery_volt": 13.3,
  "battery_pct": 98,
  "output_amp": 2.85,
  "real_power": 239.2,
  "frequency": 60.0,
  "temperature": 31,
  "load_pct": 24,
  "status_desc": "REDE (Normal)",
  "status_enum": "NORMAL"
}
```

Valores possíveis de `status_enum`: `NORMAL`, `EM_BATERIA`, `SOBREAQUECIMENTO`, `BATERIA_CRITICA`.
Os dois últimos são tratados como críticos e disparam desligamento imediato.

## Permissão da porta serial

No host, o usuário precisa pertencer ao grupo dono do dispositivo, em geral `dialout` no Debian e
Ubuntu ou `uucp` no Arch:

```bash
ls -l /dev/ttyACM0
sudo usermod -aG dialout $USER
```

No container, a porta é entregue pela diretiva `devices:`, que é suficiente e evita rodar em modo
privilegiado.

## Build local

```bash
./mvnw clean verify
./mvnw spring-boot:run
./mvnw clean package -DskipTests jib:dockerBuild
```

O último comando gera a imagem local `docker.io/edwarddn/joule` sem precisar de Dockerfile.

Para publicar em um registro próprio, ajuste as propriedades `docker.registry` e `docker.namespace`
no `pom.xml` e exporte `DOCKER_REGISTRY_USER` e `DOCKER_REGISTRY_PASSWORD`.

## Contribuindo

Relatos de compatibilidade com outros modelos são muito bem-vindos, principalmente com o pacote
cru que sai no log em debug. É assim que o mapa do protocolo cresce.

## Licença

[GPL-3.0](LICENSE). Copyright (C) 2026 Edward.
