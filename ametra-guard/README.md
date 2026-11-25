# AmetraGuard (plugin de servidor)

Plugin para Paper 1.21.x que hace tres cosas junto con el cliente Ametra:

1. **Exige el cliente**: quien no responda al reto de entrada en `verify-timeout-seconds` es expulsado.
2. **Revisa el cliente**: firma del jar (SHA-256), mods cargados dentro del juego, clases de clientes de hacks conocidos y agentes Java.
3. **Analiza el comportamiento de combate** con IA (detector de anomalías + modelo opcional entrenado con tus casos). Por defecto solo avisa al staff.

El cliente no muestra nada en pantalla ni escribe en el log. Solo mira el propio proceso del juego (no archivos personales ni otros programas).

## Puesta en marcha

1. **Compilar el plugin**: `cd ametra-guard && gradle build` (o el trabajo `guard` de GitHub Actions). Copia `build/libs/AmetraGuard-1.0.0.jar` a `plugins/`.
2. **Arrancar el servidor una vez**. Se genera `plugins/AmetraGuard/config.yml` con un `secret` nuevo.
3. **Compilar el cliente con ese mismo secreto**:
   - Local: `cd ametra-mod && gradle build -Pguard_secret=<el secret>`
   - GitHub Actions: Settings → Secrets → crea `AMETRA_GUARD_SECRET` con el valor.
   Un cliente compilado sin secreto (o con otro) no puede entrar. **No subas el secreto al repositorio.**
4. **Entrar con el cliente**: la consola imprime `cliente sha256=...`. Copia ese hash a `allowed-jar-hashes` para exigir exactamente esa versión (uno por cada versión de Minecraft que publiques) y haz `/guard reload`.
5. Dar `ametraguard.bypass` al staff que entre sin cliente. Los jugadores de Bedrock (Geyser/Floodgate) entran por el prefijo `.` de `exempt-name-prefixes`.

## La IA, paso a paso

- **Qué mide** (17 características por ventana de ~5 s): ritmo de clics (media, variación, repetición), giros de cámara, **giro de cámara sin movimiento de ratón**, correlación ratón↔cámara, y lo que ve el servidor (golpes, precisión, alcance, cambios de objetivo, error de puntería al golpear).
- **Fase 1, sin datos**: usa una línea base que se aprende sola con tus jugadores (castiga solo en la dirección sospechosa y exige varias características a la vez) más reglas duras (intervalos idénticos, alcance > 4.5, giro sin ratón, puntería inhumana).
- **Fase 2, con datos**: cuando el staff confirma casos, `/guard label <jugador> cheat|legit` guarda sus últimas ventanas en `dataset.csv`. Con ≥ 30 ejemplos de cada clase:
  `python3 tools/train_model.py plugins/AmetraGuard/dataset.csv plugins/AmetraGuard/model.json` y luego `/guard reload`.
- **Qué hace al detectar**: acumula "VL" por jugador, avisa al staff (chat, consola y Discord si pones `discord-webhook`). `ai.action: kick` expulsa a `vl-action`; el ban siempre lo decide una persona.

Comandos: `/guard status <jugador>`, `/guard label <jugador> <cheat|legit>`, `/guard alerts`, `/guard reload`.

## Límites (importa saberlo)

- Un cliente modificado para falsificar sus respuestas puede pasar la verificación: el secreto va dentro del jar. Por eso el análisis de comportamiento se hace en el servidor.
- Cheats que imitan a un humano (clickers con aleatoriedad realista, aim assist suave) pueden pasar desapercibidos. Usa también Grim o Vulcan para movimiento (fly, speed, velocity).
- Hacks externos que no se inyectan en el juego no se ven desde un mod.
- Los umbrales y valores de partida son heurísticos. Empieza en modo aviso, revisa los casos y etiquétalos.

## Transparencia con los jugadores

El cliente no muestra nada, pero conviene que las **reglas del servidor** (o la página del launcher) digan que el cliente oficial comprueba su integridad y los mods cargados en el juego, y envía datos de juego (clics, cámara) para detectar trampas. Es lo que evita problemas de confianza y de privacidad.

## Pruebas

`ametra-guard/test/SelfTest.java` prueba el protocolo (firmas, manipulación, secretos distintos) y el detector con jugadores simulados:

    javac -d /tmp/gt src/main/java/com/ametra/guard/{Protocol,Features,Detector}.java ../ametra-mod/src/main/java/com/ametra/net/Wire.java test/SelfTest.java
    java -cp /tmp/gt com.ametra.guard.SelfTest
