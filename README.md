# RzClient (launcher + mod)

## 1) Correr el launcher (VS Code)
1. Instala Node.js (https://nodejs.org). Java no hace falta: el launcher detecta qué Java necesita cada versión (21 para 1.21.x, 17 para 1.18–1.20.4, 8 para 1.16 y anteriores), usa uno que ya tengas o descarga el correcto solo.
2. En esta carpeta, en la terminal:
   ```
   npm install
   npm start
   ```
3. Instalador .exe: `npm run build` (sale en `dist/`).

## 1b) Pack de optimización (todas las versiones)
Se activa solo al pulsar **Jugar** (Ajustes → "Pack de optimización automático") o a mano con **Cliente → Pack de optimización → Instalar ahora**.

| Versión de Minecraft | Loader | Qué se instala | Java |
|---|---|---|---|
| 1.20.5 en adelante (incluida la numeración 26.x) | Fabric | Sodium, Lithium, FerriteCore, ModernFix, Entity Culling, ImmediatelyFast, More Culling, Krypton, Dynamic FPS | lo que pida Mojang (21, 25…) |
| 1.18 – 1.20.4 | Fabric | lo mismo (+ Starlight hasta 1.20.1) | **sube de 17 a 21** |
| 1.17 | Fabric | Sodium, Lithium, Starlight, FerriteCore, ModernFix… | **sube de 16 a 17** |
| 1.14 – 1.16.5 | Fabric | Lithium + Phosphor + LazyDFU (1.14–1.15); desde 1.16.x también Sodium, Starlight, FerriteCore, ModernFix, Entity Culling, Krypton, Dynamic FPS | **sube de 8 a 17** |
| 1.3.2 – 1.13.2 | Legacy Fabric | Legacy Fabric API + los mods de la categoría "optimization" más descargados que existan para esa versión exacta | Java 8 (el más nuevo; mínimo 8u312) |
| 1.0 – 1.3.1 | ninguno | solo flags de JVM y ajustes de vídeo | Java 8 (mínimo 8u312) |

Notas importantes:
- **Por qué las versiones ≤ 1.13.2 no suben de Java 8:** su arranque (LaunchWrapper/LWJGL 2) se rompe con Java 9 o más nuevo. Lo que sí se hace es exigir un Java 8 reciente y descargar uno si el del PC es viejo.
- Cada mod se pide a Modrinth para la versión exacta; si no existe build, se salta sin error. Siempre se prefiere la versión estable (release).
- Si algo falla con el Java subido, desmarca **Java moderno** en Ajustes.
- Si apagas el pack, al jugar se quitan solo los mods que el pack instaló (los tuyos no se tocan). Registro en `instances/<id>/ametra-opt.json`.
- En instancias ≤ 1.12.2 sin `options.txt` se crea uno con ajustes de vídeo ligeros (distancia 8, sin vsync, gráficos rápidos). Si ya existe, no se toca.

## 2) El menú dentro del juego (RzClient)
Es un mod de Fabric que está en `ametra-mod/` (solo **Minecraft 1.21.1** por ahora).

**Shift derecho** dentro de un mundo o servidor abre el menú (la tecla se cambia en Controles → "RzClient").

- **Barra lateral (RzClientClient)**: marca, cuántos módulos tienes activos y tus **perfiles** (Principal, PvP, Survival de ejemplo, hasta 6). Clic para cambiar de perfil, lápiz para renombrar, × (dos clics) para borrar.
  - **+ Nuevo perfil**: crea una copia de lo que tienes ahora.
  - **Copiar código**: copia al portapapeles un código `AMETRA1:...` con todo el perfil (módulos activos, posiciones, colores, escala, acento).
  - **Pegar código**: lee el código del portapapeles y lo agrega como perfil nuevo ("Importado"). Sirve para pasárselo a un amigo o llevarlo a otro PC.
- **Pestañas arriba**: MÓDULOS · TEMA · INSTALADOS · EDITOR HUD (esta última te deja arrastrar el HUD, necesita estar dentro de un mundo).
- **Módulos** (tarjetas estilo Lunar): buscador, chips (Todos, Activos, HUD, Info, Combate, Visual), filtro de favoritos con el corazón, y cada tarjeta con su ícono, corazón, engranaje (ajustes: color, chroma, escala, fondo, sombra, mover y reiniciar posición) y botón **Activado / Desactivado**.
- **14 módulos**: Logo, FPS, CPS, Teclas, Ping, Coordenadas, Reloj, Velocidad, Dirección, Memoria, Servidor, Armadura, **Zoom** (mantén C) y **Fullbright**.
- **Tema**: selector de color completo para el acento, animaciones, bienvenida y efectos del menú principal.
- **Instalados**: lista de todos los mods de la instancia.
- **Mover HUD**: arrastra cada módulo, rueda para el tamaño, clic derecho para reiniciar.

Animaciones: apertura/cierre del menú, partículas, logo con pulso, tarjeta de bienvenida al entrar a un mundo o servidor, módulos que se deslizan al activarse, teclas que se iluminan, logo animado en el menú principal de Minecraft.

### Opción rápida en Windows: compilar-mod.bat
Haz doble clic en `compilar-mod.bat`. Descarga JDK 21 y Gradle solos (dentro de `.build-tools`), compila el mod y crea `mod/rzmod.jar`. La primera vez tarda varios minutos.

### Sacar la v1.0.0 sin instalar nada (GitHub Actions)
1. Sube esta carpeta a un repositorio de GitHub.
2. Pestaña **Actions → Release → Run workflow**. Compila el mod y el instalador `.exe` en la nube y los deja como descargas.
3. Para publicarlo como release: `git tag v1.0.0 && git push --tags`.

### Compilar el mod a mano (alternativa)
Necesitas **Java 21** y **Gradle**. La forma más fácil y segura:
1. Entra a https://fabricmc.net/develop/template , elige Minecraft **1.21.1**, nombre de mod `ametra`, y **desmarca** "split client and common sources". Descarga y descomprime.
2. Copia mi carpeta `ametra-mod/src` encima de la `src` de esa plantilla (reemplaza todo).
3. En esa carpeta ejecuta `gradlew build` (Windows: `gradlew.bat build`).
4. Copia `build/libs/rzmod-1.2.0.jar` (el que NO termina en `-sources`) a `mod/rzmod.jar` aquí en el launcher.

(La carpeta `ametra-mod` también trae `build.gradle` y `gradle.properties` de referencia; si tienes Gradle instalado puedes ejecutar `gradle wrapper` dentro y luego `gradlew build`.)

### Cómo se conectan
- Al jugar con Fabric, el launcher copia `mod/rzmod.jar` a la carpeta `mods` de la instancia (se puede desactivar en Ajustes) e instala Fabric API.
- Ambos comparten `instances/<id>/config/ametra.json`: lo que cambies en el juego (módulos, color) se ve en la pestaña Cliente del launcher y viceversa.

## Cosméticos, HUD y viewmodel (mod 1.2.0)
- **Cosméticos**: elígelos en el launcher (pestaña Cosméticos). Dentro del juego se ven en tercera persona (F5) y en el inventario. Las texturas están en `ametra-mod/src/main/resources/assets/ametra/textures/cosmetics/`; si quieres cambiarlas o agregar más, edita `tools/gen_cosmetics.py` y córrelo (`pip install pillow`, luego `python tools/gen_cosmetics.py`): vuelve a crear los PNG y `CosmeticData.java`.
- **Letra del HUD**: `assets/ametra/font/hud.json` + `hud.ttf` (Poppins Medium, licencia OFL). Para otra letra, reemplaza `hud.ttf` (se puede desactivar en Tema → "Letra propia").
- **Viewmodel**: módulo "Viewmodel" en la pestaña Módulos (engranaje): posición X, altura, profundidad y tamaño.

## Cuentas, regalos y panel developer (servidor `server/`)

El launcher guarda las cuentas en un servidor propio (carpeta `server/`, Node 18+, **sin dependencias**).

**Probarlo en tu PC**
1. En una terminal: `cd server` y `node server.js` (escucha en `http://127.0.0.1:8787`).
2. En otra: `npm start` en la carpeta del launcher. En la pestaña **Cuentas** crea tu cuenta.
3. Hazte developer (con el servidor **parado**): `node server.js admin TuUsuario on`, o arráncalo con la variable `ADMIN_USERS=TuUsuario`.
4. Con esa cuenta iniciada: la tarjeta *Panel developer* de la pestaña Cuentas (el botón *Cuentas…* de Servidores → Developer también te lleva) para dar/quitar cosméticos y gemas a cualquier cuenta.

**Ponerlo para todos los jugadores**
- Sube la carpeta `server/` a un VPS/hosting con Node y ponle **https** (Caddy, nginx o un túnel de Cloudflare delante). Variables: `PORT`, `HOST`, `DATA_DIR` (donde se guarda `accounts.json`: haz copia de seguridad), `ADMIN_USERS`, `TRUST_PROXY=1` si va detrás de un proxy.
- En `main.js` cambia `ACCOUNTS_URL` por tu dirección https (o define `AMETRA_ACCOUNTS_URL`) **antes** de generar el instalador. Con `http` solo se acepta localhost.
- Si cambias precios o añades cosméticos (`tools/gen_cosmetics.py`), reinicia el servidor para que lea el catálogo nuevo.
- Comandos útiles (servidor parado): `node server.js list`, `node server.js reset <usuario> <contraseña nueva>` (no hay recuperación por correo), `node server.js admin <usuario> on|off`.

### Sin VPS ni tarjeta: servidor en tu PC con túnel de Cloudflare
1. Instala Node.js y cloudflared (`winget install --id Cloudflare.cloudflared`).
2. Doble clic a `server/tunel.bat`: arranca el servidor y el túnel. Sale una dirección `https://algo.trycloudflare.com`.
3. Sube `accounts-url.json` a un repositorio de GitHub, pon ahí esa dirección y copia el enlace **Raw** del archivo.
4. En `main.js` pega ese enlace en `ACCOUNTS_URL_FILE` **antes** de generar el instalador. El launcher lee la dirección de ese archivo; si no puede, usa la última que le funcionó.
5. Cada vez que reinicies el túnel la dirección cambia: edita `accounts-url.json` en GitHub con la nueva. Los jugadores no necesitan instalar nada.
- Solo funciona con tu PC encendida y conectada. Las cuentas quedan en `server/data/accounts.json` en tu disco: haz copias.
- Con un dominio propio (en Cloudflare) puedes tener un túnel con dirección fija y no hace falta lo del archivo.

**Qué hace el servidor y qué no**
- Contraseñas con scrypt, sesiones de 30 días, límite de intentos de login y de registros por IP. Compras, regalos, bono diario y oferta del día se validan en el servidor.
- Las gemas por jugar las reporta el launcher: el servidor solo las da por tiempo real transcurrido y con tope diario, pero un cliente modificado aún podría cobrar hasta ese tope.
- Lo que el mod enseña en el juego sale del `ametra.json` local, que el jugador puede editar; ahora mismo el servidor decide qué *posee* cada cuenta, pero no valida lo equipado en partida.

## Qué sigue
- Otras versiones de Minecraft: el mod depende de los nombres internos de cada versión, hay que portarlo.
