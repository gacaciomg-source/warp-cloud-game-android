# WARP Cloud Game (Android)

Versão do [Moonlight para Android](https://github.com/moonlight-stream/moonlight-android)
adaptada para o Cloud Game da WARP SERVER. Licença GPL-3.0, a mesma do Moonlight
(ver `LICENSE.txt`): quem recebe o APK tem direito ao código deste repositório.

## O que muda em relação ao Moonlight

- O app abre na conta da WARP (`warp/WarpEntrarActivity`), não na lista de PCs.
- Catálogo com os jogos do painel da WARP (`warp/WarpJogosActivity`).
- Sessão sem IP nem código (`warp/WarpSessaoActivity`): a WARP reserva a máquina
  (ou põe na fila), o app cadastra o endereço sozinho e o pareamento é feito pela
  WARP, que entrega o código ao Sunshine da máquina.
- A lista de PCs do Moonlight continua no menu, como "modo avançado".
- `applicationId` próprio: `br.com.warpserver.cloudgame`.

O vídeo, o áudio, os controles e a decodificação são os do Moonlight, sem mudança.

## Compilar

O GitHub compila a cada envio na branch `warp` (`.github/workflows/apk-warp.yml`)
e deixa o APK em Actions → artefato `warp-cloud-game-teste`.

O APK de teste é instalado por cima do anterior: a assinatura é sempre a mesma.
