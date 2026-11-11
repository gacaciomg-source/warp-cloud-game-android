# WARP Cloud Game (Android)

App do Cloud Game da WARP SERVER. O motor de transmissão (vídeo, áudio,
controles, decodificação) vem do [Moonlight para Android](https://github.com/moonlight-stream/moonlight-android);
tudo o que a pessoa vê é da WARP. Licença GPL-3.0, a mesma do Moonlight (ver
`LICENSE.txt`): quem recebe o APK tem direito ao código deste repositório. Por
isso o app mostra o aviso em Configurações → Sobre → Licenças de código aberto.

## Telas (pacote `com.limelight.warp`)

| Tela | Arquivo | O que faz |
|---|---|---|
| Entrar | `WarpEntrarActivity` | conta da WARP (cookies guardados no aparelho) |
| Início | `WarpJogosActivity` | jogo em destaque com a arte de fundo; fileiras "Continue jogando", "Jogue agora", "Em breve"; no controle/TV o destaque segue o foco |
| Jogo | `WarpJogoActivity` | descrição, loja (escolha entre as cadastradas), como se joga, qualidade e salvamento, botão Jogar/Continuar jogando |
| Configurações | `WarpConfigActivity` | qualidade, teste de conexão, fluidez, estatísticas, controle na tela (modo, tamanho, transparência, posição), toque, conta, licenças |
| Sessão | `WarpSessaoActivity` | fila → máquina → acesso (pareamento sem código) → jogo; partida pausada com reserva de 3 min; reconexão ao trocar a qualidade; "Salvando seu progresso" no fim |
| Durante o jogo | `WarpCamadaDoJogo` | por cima do `Game`: tela de conexão, botão e menu da partida, estatísticas, aviso de conexão ruim com sugestão de bitrate |

Peças de apoio: `WarpUi` (botões, opções, diálogo), `WarpImagens` (capas),
`WarpPreferencias` (qualidade → resolução/fps/bitrate do motor),
`WarpApi` (API da WARP e o teste de ping/velocidade), `WarpEstatisticas`.

## Menu da partida (sem sair da sessão)

Abre pelo botão da WARP no canto, pelo voltar do aparelho ou, no controle,
por Start + Select + LB + RB. Muda na hora: estatísticas, controle na tela,
tamanho e transparência dos botões, posição dos botões, toque direto/trackpad,
teclado. A qualidade (resolução e taxa de vídeo) é combinada no começo da
transmissão: "Aplicar" reconecta à mesma máquina em poucos segundos — o jogo
continua aberto lá e ninguém volta para a fila.

## Mudanças no motor

- `Game`: a camada da WARP no lugar da caixinha de conexão, dos diálogos de
  erro e do aviso de conexão ruim; voltar abre o menu; com o menu aberto, o
  controle navega no menu; resultados para a sessão (`RESULTADO_*`).
- `MediaCodecDecoderRenderer` + `VideoStats`: estatísticas a cada segundo,
  sempre, com a taxa de vídeo recebida (Mbps).
- `VirtualControllerConfigurationLoader`: botões em tamanho físico (no tablet
  ficavam enormes), presos embaixo, com três tamanhos. `VirtualController` e
  elementos: visual da WARP, sem a engrenagem; ajuste pela camada.
- `ControllerHandler`: a combinação de sair abre o menu da WARP.
- `NvHTTP`: nome do aparelho no pareamento = nome da sessão (a WARP acha o
  pedido certo no Sunshine).

As telas antigas de lista de PCs continuam no código, sem caminho até elas.

## Compilar

O GitHub compila a cada envio na branch `warp` (`.github/workflows/apk-warp.yml`)
e deixa o APK em Actions → artefato `warp-cloud-game-teste`. O APK de teste é
instalado por cima do anterior: a assinatura é sempre a mesma.

No PC: Android Studio (JDK 21 em `jbr`) e o SDK com a plataforma 37 e o NDK
29.0.14206865; `gradlew :app:assembleNonRootDebug`.
