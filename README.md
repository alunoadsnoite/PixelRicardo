# PixelRicardo

> Um mundinho pixel cheio de amiguinhos para capturar, criar e evoluir.

**PixelRicardo** é um jogo mobile **Android** feito em **Kotlin puro** (sem engine e sem assets de imagem — toda a arte é renderizada em pixel-art procedural). Você explora um mundo em blocos, captura criaturinhas fofas num minijogo de agulha, cuida delas até evoluírem e desbloqueia dezenas de **conquistas**.

```
                .:^^^.              .:^^^.
             .^!?????7^.          .^!77777~^.
           .^?7!^   ^7!.          ^7!^    ^7?7^.
          .^7!.       !7^        ^7!       .~7!^.
           ^7!.       ^7~        :7!        ^7!^
           .~7^.     .77:        .77:      :777:.
             ^77~^..^77^          .^77~^..^77~.
               .^~^^^.              .^~^^^.
            * PixelRicardo - seus amiguinhos pixel *
```

## ✨ Funcionalidades

- 🗺️ **Mundo pixel explorável** — grama, flores coloridas, lagoas, árvores e pedras gerados com terreno variado
- 🐾 **24 espécies** de criaturinhas com cores e elementos únicos (fogo, água, planta, elétrico, terra, ar, fada)
- 🎯 **Minijogo de captura** — pare a agulha na zona verde; criaturas **raras douradas** valem mais
- 💚 **Cuidados** — alimente, brinque e escove para aumentar o afeto
- ⬆️ **3 estágios de evolução** por criatura com animação de flash
- 🏆 **20 conquistas** — colecionadora, cuidadora, exploradora, streak diário, peixinho dourado e mais
- 💰 **Economia** — pedrinhas para comprar **6 visuais** para o personagem
- 🔥 **Streak diário** — jogue dias seguidos para provar que é a melhor amiga
- 🔥 **Sessão viciante (v1.1)** — **combos de captura** (até ×3), **jackpot de amor** (+10 surpresa nos cuidados), **primeira captura do dia**, bônus a cada 5 capturas, **brilhinhos coletáveis** no mapa e recompensas por explorar
- 📆 **Missão diária** (v1.1) — meta rotativa com recompensa em pedrinhas
- 🎁 **Bônus do dia** (v1.1) — pacotinho de +10 pedrinhas no primeiro login
- 🎵 **Música chiptune + efeitos por evento** (v1.1) — trilha e SFX 100% sintetizados em PCM, sem assets
- ✨ **Aviso de criatura dourada** (v1.1) — efeito e alerta quando uma rara aparece no mapa
- 🎚️ **3 dificuldades** — Fácil / Médio / Difícil (velocidade da agulha, zona de captura, quantidade de criaturas e recompensas)
- 💾 **Save automático** — progresso salvo a cada evento e ao sair do jogo
- 🔉 Efeitos sonoros com tons nativos (sem dependências de áudio)
- 📱 **100% offline** — sem internet, sem anúncios, sem permissões além do necessário

## 📲 Instalar

Baixe o APK assinado na aba [Releases](https://github.com/alunoadsnoite/PixelRicardo/releases) e instale.
No Android, toque no arquivo e permita **"fontes desconhecidas"** se necessário.

> Suporte: Android 8.0+ (API 26+), retrato, touch.

## 🛠️ Compilar do zero

Requisitos:
- JDK 17 ou superior
- Android SDK (compileSdk 36)

```bash
# debug (assinado com chave de debug local)
./gradlew assembleDebug

# release (assinado — requer keystore próprio, veja abaixo)
./gradlew assembleRelease
```

O APK sai em `app/build/outputs/apk/<tipo>/`.

### Keystore de release (opcional)

Os segredos **não** ficam no repositório. Para assinar o release crie um arquivo `keystore.properties`:

```properties
storeFile=keystores/pixelricardo-release.jks
storePassword=SUA_SENHA
keyAlias=pixelricardo
keyPassword=SUA_SENHA
```

E gere a keystore:

```bash
keytool -genkeypair -keystore keystores/pixelricardo-release.jks \
  -alias pixelricardo -keyalg RSA -keysize 2048 -validity 10000
```

### Instalar no celular

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 📁 Estrutura

```
app/src/main/kotlin/com/pixelricardo/game/
├── MainActivity.kt   # Activity fullscreen, imersiva
├── GameView.kt       # Loop, mundo, captura, cuidados, loja, conquistas e UI
├── Sprites.kt        # Pixel-art procedural (criaturas, tiles, personagem)
└── Models.kt         # Catálogo de espécies, conquistas, skins e save (JSON)
```

| Arquivo | Papel |
| --- | --- |
| `Sprites.kt` | Gera todos os `Bitmap` 16×16 em tempo real via tabela de padrões + paleta HSL. Sem nenhum PNG no projeto. |
| `Models.kt` | Dados: 24 espécies, 20 conquistas, 6 skins e persistência em `org.json` em `filesDir`. |
| `GameView.kt` | Motor do jogo num `View` custom: renderiza tudo em resolução lógica 320×240 escalada, com câmera, animações e touch. |

## 🧠 Como o jogo funciona

- A câmera segue o personagem por um mapa de `26×18` tiles pré-renderizado em um único `Bitmap` (performático).
- Tocar numa criatura inicia a **captura**: uma agulha oscila sobre uma zona-alvo; toque de novo para parar. Acertar = captura.
- Capturas e cuidados dão **pedrinhas**; cuidados acumulam **afeto**; ao atingir o limite a criatura **evolui** (nível 2 e 3).
- Toda mudança relevante dispara `checkAchievements()`, que desbloqueia conquistas com troféu + recompensa em pedrinhas.

## 🎮 Gameplay rápido

1. **Mundo**: toque para caminhar / toque numa criatura para capturá-la
2. **Coleta**: veja as espécies capturadas e suas fases
3. **Cuide**: no detalhe de uma criatura, use os botões de cuidado até evoluir
4. **Troféus**: acompanhe o progresso das 20 conquistas
5. **Loja**: troque pedrinhas por visuais para o personagem

## 🗺️ Roadmap / ideias

- [x] Som ambiente (loop de música chiptune)
- [x] Desafios diários com recompensa especial
- [x] Minigames extras de recompensa rápida (combo, jackpot, brilhinhos)
- [ ] Novas regiões do mapa (praia, vulcão, floresta noturna)
- [ ] Minigames extras (corrida de criaturas, dança)
- [ ] Troféu de conquista como adesivo colecionável

## 📜 Licença

MIT — veja o arquivo [LICENSE](LICENSE).

Feito com 💚 para uma adolescente de 13 anos que adora Pokémon.