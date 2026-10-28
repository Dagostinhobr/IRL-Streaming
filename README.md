# IRL Streaming — transmissão ao vivo para Android

App Android **independente** de transmissão ao vivo pelo celular, inspirado no
[Moblin](https://moblin.app/) (iOS). O objetivo é entregar o fluxo principal de IRL
streaming no Android: câmera + microfone → encoder → RTMP/RTMPS/SRT/WHIP, com chat,
gravação local, múltiplos destinos, VDO.Ninja (P2P) e importação de links `moblin://`.

> **Status:** beta (v0.7.0). O Moblin tem anos de desenvolvimento e dezenas de
> recursos avançados (cenas, widgets, SRTLA, OBS remoto, Apple Watch). Veja a
> seção [Diferenças em relação ao Moblin](#diferenças-em-relação-ao-moblin) antes de
> esperar paridade 1:1.

**Download:** [Releases do GitHub](https://github.com/Dagostinhobr/IRL-Streaming/releases) —
`IRL-Streaming-0.7.0.apk` (release assinado) ou `IRL-Streaming-0.7.0-debug.apk` (teste).

> **Nome:** o aplicativo aparece como **IRL Streaming** na launcher. O identificador
> interno do pacote continua `app.streamlink` para preservar instalações e upgrades.

---

## Funcionalidades implementadas

| Área | Recurso |
|---|---|
| Streaming | RTMP, RTMPS, SRT, RTSP e UDP (via [RootEncoder](https://github.com/pedroSG94/RootEncoder)) |
| WebRTC | WHIP nativo (experimental) e estúdio P2P do VDO.Ninja dentro do app (WebView) |
| VDO.Ninja | Sala **ou ID de ligação** (`?push=ID&password=...` / `?view=ID&p=...`), importação de links, estúdio P2P no app e destino P2P |
| Vídeo | H.264/AVC e H.265/HEVC, 480p/720p/1080p, 24/30/60 fps, bitrate configurável |
| Áudio | AAC, 64–192 kbps, 44.1/48 kHz, mono/estéreo, cancelamento de eco e supressão de ruído |
| VU meter | Medidor de sinal do microfone no ecrã principal (escala −60 a 0 dB, com ataque rápido e decaimento suave) |
| Microfone | Seleção de entrada em tempo real: automático (interno), **USB**, fone com microfone, Bluetooth; o microfone da câmera USB é escolhido automaticamente |
| Câmera | Traseira/frontal, seleção na lista de câmeras, **câmera USB UVC por OTG**, zoom por pinça, presets de zoom, toque para focar, lanterna |
| Orientação | **Automática pelo sensor** (vertical em pé, horizontal deitado) ou fixa em retrato/paisagem, com botão na tela principal e mudança ao vivo |
| Controle | Mudo, tela preta (stealth), manter tela ligada |
| Conexão | Status ao vivo, uptime, bitrate real, FPS real, frames perdidos, reconexão automática (5 tentativas) |
| Gravação | MP4 local simultâneo à transmissão, em `Android/data/app.streamlink/files/recordings` |
| Destinos | Lista de URLs com nome, protocolo, stream key e URL de backup; troca com um toque |
| Chat | Twitch via IRC/WebSocket: leitura anônima, envio com OAuth, badges, cores, remoção de mensagens, alertas de sub/raid |
| Chat extra | Overlay sobre o vídeo, timestamps, TTS (ler mensagens em voz alta), limpeza de histórico |
| Integração | Deep links `moblin://`, `streamlink://` e `irlstream://` para importar destinos |
| Segundo plano | Foreground service com notificação persistente e botão "Encerrar" |
| Persistência | Todas as configurações em SharedPreferences/JSON |

### Atalhos de uso

- **Toque** no preview → foco manual.
- **Pinça** → zoom; chips `1x/2x/3x/5x/8x` → zoom rápido.
- **VU meter** (acima do chip do destino) → mostra o sinal do microfone em tempo
  real; fica vermelho com "MUDO" quando o microfone está silenciado.
- **Botão central** → inicia/encerra a transmissão.
- **Chip do destino** (acima do botão) → troca de destino.
- **Ícone de chat** (barra lateral ou rodapé) → chat completo.
- **Botão de orientação** (barra lateral, ícone de rotação) → alterna
  **Auto → Vertical → Horizontal**. Em **Auto**, o vídeo acompanha o sensor:
  em pé publica em retrato (720×1280, estilo Instagram) e deitado em paisagem
  (1280×720, estilo YouTube). No P2P a mudança é aplicada ao vivo, sem encerrar
  a transmissão; nos destinos RTMP/SRT a nova resolução entra na próxima live.

---

## VDO.Ninja (P2P)

**Ajustes → VDO.Ninja** (ou **Destinos → VDO.Ninja**):

1. Em **Identificação**, informe um **ID de ligação** (ex.: `meu-stream-7f3a`) ou
   gere uma **sala** (ex.: `sala-exemplo-42`). O ID tem prioridade sobre a sala.
2. Preencha a **senha** se o stream for protegido (ex.: `minha-senha-ficticia`).
   O botão **Colar link do VDO.Ninja e preencher** importa ID, sala, senha e host
   de um link existente como
   `https://vdo.ninja/?view=meu-stream-7f3a&p=minha-senha-ficticia`.
3. **Links P2P**:
   - **Publicar** — `https://vdo.ninja/?push=SEU_ID&password=SUA_SENHA` para enviar
     câmera/mic.
   - **Assistir** — `https://vdo.ninja/?view=SEU_ID&p=SUA_SENHA` para receber.
4. **Usar como destino (P2P)** → cria o destino `VDO.Ninja (P2P)`. Ao tocar no
   botão de transmitir na tela principal, o app abre o estúdio WebRTC com esse
   link. Esse é o caminho correto para P2P — **não usa WHIP**.
5. **Abrir estúdio** → abre o estúdio do VDO.Ninja no WebView do próprio app e
   publica via WebRTC (P2P), usando a câmera e o microfone do aparelho. O link já
   inclui `&autostart&webcam&audiodevice=1`: a publicação começa sozinha e o
   microfone padrão é escolhido automaticamente, **sem pedir para selecionar de
   novo**. Se a página demorar a renderizar, o app clica em "Share your Camera"
   automaticamente.
6. **Abrir player** → visualiza o ID/sala sem sair do app.

> Os IDs, salas, senhas e links mostrados nesta documentação são **fictícios** e
> servem apenas de exemplo. Substitua pelos seus próprios dados.

O estúdio P2P é o caminho **recomendado e validado** para transmissões via
VDO.Ninja: o WebRTC é negociado pelo próprio VDO.Ninja, e o app só concede as
permissões de câmera/microfone.

### WHIP nativo (experimental)

O app também consegue publicar a câmera nativa via **WHIP** (WebRTC), mantendo a
qualidade e os controles da transmissão RTMP:

- Endpoint: `https://whip.vdo.ninja/TOKEN` (token = ID de ligação ou sala).
- Link de retorno para OBS: `https://vdo.ninja/?whipview=TOKEN`.
- O botão correspondente é **Usar como destino WHIP (experimental)** — separado
  do destino P2P para não confundir os dois caminhos.

> **Limitação atual:** o serviço `whip.vdo.ninja` respondeu **HTTP 504** em todos os
> testes de publicação, e o cliente WHIP não concluiu o ICE contra servidores
> locais. Enquanto isso não é estabilizado, trate esta opção como experimental e
> use o **estúdio P2P** para ir ao ar. A UI indica essa limitação.

### Microfone USB no estúdio P2P

Conecte o microfone USB (ou interface de áudio) **antes** de abrir o estúdio. O
Android passa a usá-lo como entrada padrão do WebRTC e o estúdio já abre com ele
selecionado. Para a transmissão RTMP/WHIP nativa, escolha o dispositivo em
**Ajustes → Áudio → Microfone**.

> O WebView/WebRTC precisa da permissão normal `MODIFY_AUDIO_SETTINGS` para
> selecionar o dispositivo de captura; sem ela o VDO.Ninja falhava com
> `NotReadableError` e não publicava áudio.

### Câmera USB (UVC)

O app reconhece câmeras USB ligadas por OTG e lista-as em
**Ajustes → Câmera → Câmera principal**. Não é necessária uma câmera exposta
pelo Camera2 do Android: o vídeo UVC é convertido para o mesmo pipeline
OpenGL/encoder da câmera nativa.

- Ao ligar a câmera, o Android pede autorização de acesso ao dispositivo USB;
  aceite uma vez. O app deteta a ligação e atualiza a lista automaticamente.
- A câmera USB aparece como **Câmera USB** com o nome do produto.
- Se a câmera tiver microfone integrado (UAC), o app seleciona esse microfone
  automaticamente quando a entrada está em **Automático**.
- A entrada de áudio pode ser mudada a qualquer momento, inclusive durante a
  transmissão: toque no medidor de microfone na tela principal ou use
  **Ajustes → Áudio → Microfone**. A troca é aplicada em tempo real.
- Com câmera USB, zoom, lanterna e PiP ficam indisponíveis (limitações do UVC).
- Se a câmera for desligada, o app volta para a câmera nativa e para o
  microfone interno na próxima preparação.

### PiP com duas câmeras

O app compõe **duas câmeras no mesmo vídeo**: a janela faz parte da transmissão e
da gravação (não é o PiP do sistema Android). A câmera principal continua em tela
cheia, com foco, zoom, lanterna e troca de lente; a segunda câmera é desenhada por
um filtro OpenGL próprio (`PipFilterRender`) sobre o vídeo, com:

- lente principal e lente da janela selecionáveis (traseiras/frontal);
- posição (cantos ou centro), tamanho (20/30/40%) e rotação (0/90/180/270);
- atalho para ligar/desligar na tela principal e em **Ajustes → PiP com duas câmeras**.

Requisito: o aparelho precisa anunciar duas câmeras simultâneas em
`CameraManager.getConcurrentCameraIds()` (Android 11+). O app detecta isso e
desabilita a opção quando não há suporte. O Redmi Note 9 Pro usado nos testes não
suporta (lista vazia; `MAX_CAMERAS_IN_USE`), então a interface mostra o aviso e o
modo fica desligado. A composição foi validada no mesmo aparelho injetando um
padrão de teste na Surface da janela (posição, tamanho, máscara e rotação).

Se a segunda câmera falhar (por exemplo, outro app usando a câmera), o app
remove a janela e mostra o motivo, sem interromper a transmissão principal.

Alternativa que funciona em qualquer aparelho: usar um **segundo celular**
como câmera via VDO.Ninja (`?push=SALA`) e posicionar essa fonte como PiP no OBS.

---

## Como compilar

### Requisitos

- Android Studio (Ladybug ou mais recente) **ou** JDK 17 + Android SDK 35
- Android 8.0 (API 26) ou superior
- Celular físico para testar câmera/encoder (emulador não tem câmera boa)

### Pelo Android Studio

1. Abra a pasta do projeto.
2. Aguarde o Gradle sincronizar (o RootEncoder vem do JitPack; a primeira build baixa dependências).
3. Conecte o celular com depuração USB e clique em **Run**.

### Por linha de comando

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

Instalar:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### APK de distribuição (release)

O build de release usa a keystore configurada em `keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Sem esse arquivo o
release compila, mas não é assinado.

```bash
./gradlew :app:assembleRelease
# APK em app/build/outputs/apk/release/app-release.apk
```

Para gerar uma keystore nova:

```bash
keytool -genkeypair -v -keystore .keys/irl-streaming.jks -alias irlstreaming \
  -keyalg RSA -keysize 4096 -validity 10000
```

> **Atenção:** a keystore é a identidade do app. Guarde o `.jks` e as senhas; sem
> ela não é possível atualizar instalações existentes. O `.gitignore` já exclui
> `.keys/`, `keystore.properties` e `dist/`.

Para disponibilizar o download na rede local:

```bash
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/IRL-Streaming-0.7.0.apk
cp app/build/outputs/apk/debug/app-debug.apk dist/IRL-Streaming-0.7.0-debug.apk
python3 -m http.server 8080 --bind 0.0.0.0 --directory dist
# abra http://SEU_IP:8080 no celular
```

---

## Configuração rápida

### 1. Destino

Abra **Destinos** (ícone de cast) → escolha ou crie um destino:

| Plataforma | URL de ingest | Stream key |
|---|---|---|
| Twitch | `rtmp://live.twitch.tv/app` | sua chave (Twitch Dashboard → Configurações → Transmissão) |
| YouTube | `rtmp://a.rtmp.youtube.com/live2` | chave do YouTube Studio |
| Kick | `rtmps://fa723fc1b171.global-contribute.live-video.net:443/app` | chave do Kick |
| SRT próprio | `srt://host:porta?streamid=...` | deixe vazio |
| WHIP | `https://whip.vdo.ninja/TOKEN` | deixe vazio |

> Para RTMP a URL final é `url/streamKey`. Para SRT/RTSP/WHIP com chave embutida,
> cole a URL completa e deixe a stream key vazia.

### 2. Chat da Twitch

**Ajustes → Chat**:

- **Canal**: nome do canal (sem `#`).
- **Leitura**: funciona sem login (modo anônimo).
- **Envio**: informe **usuário** e **token OAuth** com escopos `chat:read chat:edit`.
  Gere em <https://twitchapps.com/tmi/> (não é um serviço oficial da Twitch; use por sua conta e risco)
  ou via OAuth oficial.

O chat conecta automaticamente ao iniciar a live.

### 3. Importar do Moblin

O app aceita o formato do Moblin:

```
moblin://?%7B%22streams%22%3A%5B%7B%22name%22%3A%22Casa%22%2C%22url%22%3A%22rtmp%3A%2F%2Flive.twitch.tv%2Fapp%2FCHAVE%22%7D%5D%7D
```

Decodificado:

```json
{ "streams": [ { "name": "Casa", "url": "rtmp://live.twitch.tv/app/CHAVE" } ] }
```

Abra o link no celular (ele precisa estar registrado no navegador/telegram/etc.) ou
use `adb shell am start -a android.intent.action.VIEW -d "moblin://?..."`.
Cada stream vira um destino e o primeiro é selecionado.

---

## Arquitetura

```
app/src/main/java/app/streamlink/
├── MainActivity.kt              # entrada, deep links, botão "Encerrar" da notificação
├── data/
│   ├── Models.kt                # AppSettings, StreamTarget, VdoNinjaSettings, enums, defaults
│   └── SettingsRepository.kt    # persistência JSON (SharedPreferences + StateFlow)
├── stream/
│   ├── StreamEngine.kt          # StreamBase: WhipStream (WebRTC) ou GenericStream (RTMP/SRT/...)
│   ├── StreamUiState.kt         # estado observável da live
│   └── StreamService.kt         # foreground service + notificação
├── chat/
│   ├── ChatModels.kt
│   ├── TwitchChatClient.kt      # IRC sobre WebSocket (tags, comandos, clearmsg)
│   └── ChatManager.kt           # histórico, reconexão, moderação
├── ui/
│   ├── StreamViewModel.kt       # orquestra engine + chat + TTS + settings + VDO.Ninja
│   ├── StreamLinkApp.kt         # navegação (Home, Settings, Targets, VDO.Ninja, WebView)
│   ├── screens/                 # Home, Settings, Targets, VdoNinja, VdoNinjaWeb
│   ├── components/              # preview, gestos, chat, componentes Material 3
│   └── theme/
└── util/                        # VdoNinja (links), AudioDevices (USB), deep link, permissões
```

Stack: **Kotlin + Jetpack Compose + Material 3 + Camera2/RootEncoder + OkHttp + WebView**.
Sem telemetria, sem servidor próprio: o vídeo vai direto para o servidor de ingest
que você configurar (ou direto para o convidado, no caso do P2P).

### Permissões

| Permissão | Uso |
|---|---|
| `CAMERA` | Captura de vídeo |
| `RECORD_AUDIO` | Microfone |
| `INTERNET` | Enviar o stream, conectar ao chat e ao VDO.Ninja |
| `FOREGROUND_SERVICE` + `..._CAMERA` + `..._MICROPHONE` | Manter a live ativa em segundo plano |
| `POST_NOTIFICATIONS` | Notificação da live (Android 13+) |
| `WAKE_LOCK` | Evitar suspensão durante a transmissão |

---

## Diferenças em relação ao Moblin

Implementado neste beta: o núcleo de captura/encoders, destinos, chat Twitch,
gravação, estatísticas, reconexão, TTS, deep links, VDO.Ninja (P2P no WebView) e
seleção de microfone USB.

**Ainda não implementado** (roadmap sugerido, em ordem de impacto):

1. **SRTLA / bonding** de múltiplas conexões (WiFi + celular). O RootEncoder suporta
   SRT simples; SRTLA exige um cliente específico (ex.: libsrt + SRTLA) e servidor
   compatível (BELABOX).
2. **RIST** e WHIP estável contra o serviço do VDO.Ninja.
3. **Cenas e widgets** (texto, imagem, mapa, chat na tela, placar, alertas, LUTs,
   filtros de vídeo). É a maior parte do código do Moblin.
4. **Integrações Kick, YouTube e SOOP** (chat, viewers, título/categoria, alertas).
5. **OBS WebSocket** e controle remoto web.
6. **Migração para CameraX** com multi-câmera e troca de fonte de vídeo em tempo real.
7. **Streaming em segundo plano com áudio** (vídeo congelado), como no Moblin.
8. **Câmera UVC, gimbal, Apple Watch (equivalente Wear OS), leitor de chat com
   emotes BTTV/FFZ/7TV, moderação completa.**
9. **Múltiplos destinos simultâneos** (hoje a troca é manual, um por vez).
10. **Testes instrumentados** de encoder em dispositivos reais.

O Moblin é open source (Swift, iOS): <https://github.com/eerimoq/moblin>.
Se quiser paridade real, o caminho é contribuir/portar módulos específicos, não
reescrever tudo de uma vez.

---

## Solução de problemas

| Sintoma | Causa provável / solução |
|---|---|
| "Configuração de vídeo/áudio não suportada" | Resolução/FPS/codec não suportado pela câmera. Baixe para 720p30/H.264. |
| Conexão cai ao bloquear a tela | verifique se a notificação da live está ativa; desative otimização de bateria para o app. |
| Chat não conecta | confira o nome do canal; teste sem token primeiro; alguns canais exigem login para ler. |
| Não envia mensagens | usuário + token OAuth com `chat:edit`; o token deve ser do mesmo usuário. |
| Vídeo de cabeça para baixo | toque no botão de orientação da tela principal e escolha Vertical/Horizontal, ou use Auto para seguir o sensor. |
| APK não instala | desinstale a versão anterior (assinaturas diferentes) ou libere "instalar apps desconhecidos". |
| Estúdio VDO.Ninja pede "Share your Camera" | toque no botão e permita câmera/microfone; o app já concede as permissões do Android. |
| WHIP retorna erro/504 | serviço experimental do VDO.Ninja indisponível — use o estúdio P2P. |
| Microfone USB não aparece | conecte antes de abrir os Ajustes e toque em "Atualizar lista"; verifique o cabo OTG. |

---

## Aviso legal

IRL Streaming é um projeto independente, sem afiliação com Moblin, Twitch, YouTube,
Kick, VDO.Ninja, Meta ou Google. "Moblin" é citado apenas para compatibilidade de
formato de link. Respeite os termos de serviço de cada plataforma e as leis de
transmissão (privacidade, direitos de imagem, dirigir e transmitir etc.).

Licença do código deste repositório: MIT (sugerida). Dependências mantêm suas
próprias licenças, incluindo RootEncoder (Apache 2.0).
