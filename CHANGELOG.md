## RzMod en todas las versiones (preparación)
- El mod se llama **RzMod** (`rzmod-<mc>.jar`). El workflow compila las 13 versiones 1.19.2 → 1.21.11 y el launcher baja el jar de cada una.
- Nuevas familias en `ametra-mod/src/compat/`: `v1_19` (1.19.x), `v1_21_2` (1.21.3/4), `v1_21_5`, `v1_21_6` (1.21.8), `v1_21_9` (1.21.11). Son copias de la familia más cercana: **aún sin portar**; cada error de GitHub Actions indica qué ajustar en su `Compat.java`.
# Changelog

## Mod multi-versión: 1.20 → 1.21.1 con código portado
- El código del mod que cambia entre versiones de Minecraft sale del código común y pasa a `ametra-mod/src/compat/<familia>/` (`Compat`, `ScreenBase` y los mixins del HUD y de la skin). Cada `versions/<mc>.properties` elige su familia con `compat=`.
- Familias portadas: **1.20/1.20.1**, **1.20.2–1.20.4**, **1.20.5/1.20.6** y **1.21/1.21.1** (esta última, idéntica a como era). El workflow compila 5 jars: 1.20.1, 1.20.2, 1.20.4, 1.20.6 y 1.21.1.
- Pendientes: 1.19.x (sin `DrawContext`) y 1.21.2+ (render nuevo). El launcher avisa que el menú aún no existe para esas versiones.
- Sin verificar contra Minecraft real: el port de 1.20.x se escribió sin poder compilar; los errores de GitHub Actions dirán qué ajustar en el `Compat.java` de cada familia.

## Mapa de versiones 1.19 → 1.21.11
- `mod-compat.json` + 13 archivos `ametra-mod/versions/*.properties`: cada versión de Minecraft de la 1.19 a la 1.21.11 apunta al jar de Ametra de su familia. El launcher elige el jar correcto, lo actualiza solo desde GitHub y avisa si falta.
- El workflow compila todos (resolviendo yarn/loader/Fabric API solo) y publica los que compilen.

## Servidores dentro del juego + pulido del Inicio
- Menú del juego (Shift derecho): nueva pestaña **SERVIDORES** con los partners de Ametra y tus servidores del launcher, con botón ENTRAR. Además se añaden solos a la lista de Multijugador de Minecraft (sin borrar los tuyos). El launcher los escribe en `config/ametra.json` y se actualiza al abrir el juego.
- Inicio: orbes con texto siempre derecho y efecto de profundidad, banner sin pisar el panel de perfiles, nombres de perfil sin cortar.
- Tus fondos animados ahora se ven también en el Inicio (casilla en Ajustes) y hay botones en la escena para cambiar de fondo o añadir uno.

## Inicio "Santuario"
- Nuevo Inicio: el jugador 3D ocupa toda la escena sobre anillos giratorios; tiempo jugado, perfiles y último juego **orbitan** a su alrededor; botón JUGAR central con brillo; perfiles en un dock de cristal a la izquierda y novedades que rotan solas a la derecha. Todo toma los colores del ADN de tu skin.

## ADN de skin (exclusivo de Ametra)
- El launcher lee los píxeles de tu skin, saca su paleta de 3 colores y colorea todo con ella: acento, fondos animados (la Aurora usa los 2 primeros) y el HUD del juego (se sincroniza como color de acento). Se actualiza solo al cambiar de skin; ajustes → Color del cliente para desactivarlo o recalcular. Si cambias el color a mano, se desactiva.

## Launcher — Skin sin conexión y fondos animados
- **Skin para cuentas no premium**: en Cuenta eliges tu PNG (64×64 o 64×32), tipo de brazos y "Aplicar a mi cuenta". El launcher la guarda y al pulsar Jugar la copia a `ametra/skin.png` de la instancia; el mod (`OfflineSkin` + `SkinMixin`) la muestra en tu jugador dentro del juego. Con Microsoft (premium) se sigue subiendo a Mojang y el archivo local se borra de la instancia.
- **Fondos animados del launcher** (Ajustes → Fondo del launcher): Aurora, Estrellas, Ondas y Partículas con el color de acento, o tus propios GIF / imágenes / vídeos MP4-WebM (carpeta `fondos-launcher`). Control de oscurecimiento y se pausa al minimizar.
- Nombre: no quedan textos "Hovy" visibles; solo se conserva el código que migra la carpeta y los datos del nombre anterior para no perder instancias.

## Launcher — Estudio 3D de cosméticos
- Botón **＋ Crear cosmético 3D** en Cosméticos: editor de bloques en 3D (colocar, borrar, cuentagotas, línea, caja rellena, simetría, paleta, deshacer/rehacer, plano de trabajo) sobre un muñeco de referencia. Máx. 512 bloques por cosmético.
- Al guardar (y al pulsar Jugar) se copian a `ametra/cosmeticos/` de cada instancia. En el juego: Shift derecho → PERSONALIZAR → **COSMÉTICOS 3D PROPIOS** para ponértelos o quitártelos; se dibujan sobre tu personaje (tercera persona/F5).
- Alas, botas y puños: construye solo el lado izquierdo (X ≥ 0); se reflejan solos. La nariz del muñeco marca el frente (+Z).
- Pendiente: que otros jugadores los vean y subirlos al servidor con aprobación.

## Ametra Client — pestaña PERSONALIZAR
- **Mira propia**: suelta PNG en `.minecraft/ametra/miras/`, elígela en Shift derecho → PERSONALIZAR y se activa sola (estilo «Imagen propia» del Crosshair; el tamaño se ajusta en el módulo).
- **Cursor propio**: PNG en `.minecraft/ametra/cursores/` (máx. 32 px, punto de clic arriba a la izquierda), visible en los menús.

## Ametra Client — módulos estilo Feather (tanda 1)
- **Reach**: distancia del último golpe (de tus ojos a la caja del objetivo).
- **Saturación**, **Contador de objetos** (cuántos del objeto de tu mano tienes en el inventario) y **Efectos** (pociones activas con color y tiempo restante).
- **Freelook**: mantén la tecla (Controles → Ametra Client, por defecto Alt izquierdo) y mira alrededor sin girar tu personaje; al soltar vuelve tu vista.
- **Agacharse alternado** y **Sin bamboleo**: usan las opciones del propio juego y las devuelven a como estaban al apagarlos.
- **Sin sacudida al recibir daño** y **Fuego bajo** (con ajuste de cuánto baja).
- Los mixins nuevos llevan `require = 0`: si una versión cambia un método, ese módulo no hace nada pero el juego sigue abriendo.
- Pendiente de las siguientes tandas: minimapa, waypoints, desenfoque de movimiento, color de contorno de bloque, cambiar hora/clima del cliente, multiplicador de partículas y chat mejorado.

## Pagos con Tebex
- Botón **+ Gemas** en la Tienda: paquetes de gemas que se pagan en Tebex. Las gemas las acredita el servidor al recibir el webhook firmado (una vez por pago; reembolsos y contracargos las quitan). Ver `server/TEBEX.md`.

## Launcher — arreglo "La sesión no es válida" y tienda mejorada
- **Sesión de Microsoft**: antes de cada Jugar se comprueba el token con Mojang y, si caducó, se renueva solo. Si no se puede, te lleva a Cuenta con un mensaje claro.
- **Cuenta sin conexión + servidor premium**: avisa una vez por servidor (esa combinación causa el error) y ofrece cambiar a Microsoft.
- **Compra**: diálogo propio (saldo, total, te quedan, cuánto falta), equipar al comprar opcional, sin doble clic, reintentos sin doble cobro, rechazo si el precio cambió, **sets con −15 %** e **historial de compras**. Reinicia el servidor de cuentas para aplicar los cambios.

## Cosméticos — sets Ángel / Demonio / Vacío y Payaso
- **19 cosméticos nuevos** (48 en total). Tres sets con la misma idea: **Ángel** (blanco y oro), **Demonio / Soberano del Infierno** (negro, rojo y lava) y **Vacío** (obsidiana y violeta). Cada set trae **alas, sombrero, espalda, botas, puños y aura**.
- **Categorías nuevas**: Espalda, Botas, Puños, Auras y Caras (también en el menú de Shift derecho dentro del juego; las pestañas saltan de fila si no caben).
- **Alas grandes de varias capas**: plumas con coberteras (ángel), membrana de murciélago con venas de lava y brasas (demonio) y cuchillas de cristal con esquirlas (vacío).
- **Animaciones por pieza** en el mod: órbitas, vaivén, pulso, balanceo (la cola del demonio), partículas que suben y se desvanecen, giros. Las botas y los puños siguen el movimiento de piernas y brazos.
- **Payaso** (cara): maquillaje blanco con rombos azules y sonrisa roja que deja ver tus ojos, nariz, peluca de colores y pajarita.
- Generador: hojas de textura de tamaño variable (128 px para las alas), animación por caja y `tools/preview_cosmetics.py` para ver una hoja de contacto sin abrir el juego.

## Launcher — cuentas Ametra, regalos y panel developer
- **Cuentas**: en la nueva pestaña **Cuentas** puedes **crear una cuenta** o **iniciar sesión** (usuario + contraseña). Con la sesión iniciada, tus **gemas y cosméticos comprados viven en el servidor**, así que los tienes en cualquier PC. La sesión se guarda cifrada con el almacén de Windows. Sin cuenta todo sigue funcionando como antes, solo en ese PC.
- **Regalar**: en la Tienda, elige un cosmético → **🎁 Regalar**, escribe el usuario de tu amigo y lo recibe al instante (lo pagas tú con tus gemas). A quien lo recibe le sale un aviso en la Tienda y un punto rojo en el menú.
- **Panel developer** (pestaña **Cuentas** (aparece solo si tu cuenta es developer)): **dar o quitar cosméticos** (uno o todos los de pago) y **sumar o restar gemas** a cualquier cuenta, y ver lo que tiene. El servidor comprueba que tu cuenta sea developer; nadie puede darse cosas editando el launcher.
- Bono diario, gemas por jugar y oferta del día pasan a calcularse en el servidor cuando hay cuenta (las gemas por jugar solo cuentan por tiempo real y con un tope diario).
- La dirección del servidor puede leerse de un archivo online (`ACCOUNTS_URL_FILE`) para usar un túnel gratis que cambia de dirección; `server/tunel.bat` arranca servidor + túnel en Windows.
- Nuevo servidor de cuentas en `server/` (Node 18+, sin dependencias). Mira el README para ponerlo en marcha.

## Ametra Client v1.6.1 — Soup PvP
- **Módulo Soup PvP** (Combate): cuenta las sopas (estofado de champiñón) de todo tu inventario con el item real, barra de vida verde→roja debajo y aviso que parpadea en rojo cuando quedan pocas (**POCAS**) o ninguna (**SIN SOPAS**).
- **Visual al curarte**: al comer una sopa y subir tu vida, el item rebota, la barra destella, sale un corazón con el **+vida** y los bordes de la pantalla dan un destello verde.
- Ajustes: *Aviso con pocas sopas* (0–16) y *Destello al curarte* (0 = apagado). Se mueve y se le cambia color, fondo, sombra y escala como cualquier módulo. Viene apagado: actívalo en Shift derecho.

## Ametra Client v1.6.0 — Autotext, Auto GG, zoom con rueda y vista previa de módulos
- **Autotext**: 4 mensajes guardados que se mandan con una tecla cada uno (asígnalas en Controles → Ametra Client; vienen sin tecla para no pisar las tuyas). Los textos se editan en el panel del módulo. Si empiezan con `/` se mandan como comando. Hay un mínimo de 1 s entre envíos para que el servidor no te expulse por spam.
- **Auto GG**: escribe tu mensaje (por defecto `gg`) solo cuando el **servidor** anuncia el final de la partida (mensajes del sistema; el chat de otros jugadores no lo activa). Las frases del final y la espera (0–5 s) se editan en el panel; solo se manda una vez cada 30 s.
- **Zoom suave**: el acercamiento y el nivel siguen al valor con suavidad. Con la tecla de zoom mantenida, la **rueda del ratón** cambia el nivel (sin cambiar de ranura) y se guarda al soltar.
- **Vista previa al lado**: al abrir los ajustes de un módulo del HUD aparece, a la izquierda del panel, el módulo dibujado en vivo con su color, fondo, sombra y escala.
- **Launcher — mods por versión**: ahora las instancias se pueden editar (✎ al pasar el cursor): nombre y versión de Minecraft. Al cambiar la versión (y también al pulsar Jugar con Fabric) el launcher vuelve a bajar, para la versión nueva, los mods que tenías instalados desde la Biblioteca, borra los jar de la versión vieja y avisa de los que no existen para la nueva.

## Ametra Client v1.5.0 — fondos animados y cosméticos dentro del juego
- **Pestaña Fondos** (Shift derecho): elige el fondo del menú principal. Arriba a la izquierda hay una **vista previa en vivo** (el menú principal en miniatura, con el fondo animándose), porque el menú de Ametra es muy grande para ver el resultado. Al pasar el cursor por un fondo, la vista previa lo muestra antes de elegirlo; el clic lo aplica.
- **Mete cualquier fondo**: suelta en `ametra/fondos/` de la instancia imágenes **PNG / JPG / BMP**, **GIF animados** o una **carpeta de frames** (`img_001.png`, `img_002.png`…; pon `@30` al final del nombre de la carpeta para 30 fps). Aparecen solos (botón *Recargar*). Para un video: conviértelo a GIF o a frames con ffmpeg.
- Los GIF y los frames se leen en un hilo aparte, se reducen a 640 px de ancho y tienen un tope de memoria (96 MB por fondo): un GIF enorme no congela el juego, solo usa menos fotogramas.
- **Fondos integrados** dibujados por código: Clásico (el de antes), Aurora, Ondas y Estrellas; usan tu color de acento.
- Control **Oscurecer imagen / GIF** para que el logo y los botones se lean bien. El fondo elegido también se ve detrás del menú de Ametra cuando lo abres desde el menú principal.
- **Pestaña Cosméticos** (Shift derecho): equipa capas, alas, gorros, gafas y mochilas sin salir del juego. Tu personaje se ve en 3D en el panel (arrastra para girarlo, botones Frente / Espalda) y al **pasar el cursor por un cosmético se ve cómo queda** antes de equiparlo. Lo bloqueado se compra en la Tienda del launcher; con la sesión developer iniciada todo está desbloqueado.
- El launcher recoge lo que equipes dentro del juego al abrir su pantalla de Cosméticos, y tiene un botón nuevo **Carpeta de fondos del menú** (pestaña Mods → Cliente).
- Las pestañas del menú ajustan su separación para que quepan las 6 sin pisar el buscador.

## Launcher v1.4.1 — modo Bedrock
- **Instancias Bedrock** (Windows 10/11): Jugar abre Minecraft Bedrock; Unirse entra directo a un servidor.
- **Dirección Bedrock** opcional en servidores y partners (puerto 19132 por defecto).
- **Pestaña Mods en Bedrock**: instalar packs/add-ons/mundos, instalar el pack de Ametra, ver y borrar lo instalado, y copia de seguridad de mundos en `.mcworld`.
- Pack de recursos de Ametra para Bedrock en `bedrock/Ametra-Bedrock.mcpack` (logo del título, mira, frases).

## Launcher — logo y "entrar al pulsar Jugar" también en tus servidores
- Los servidores de la pestaña Servidores (no solo los partner) ahora se pueden **editar** (✎) y tienen **logo propio** (PNG, JPG o WebP, se recorta cuadrado a 128×128) y la casilla **"Entrar aquí al pulsar Jugar"**.
- Si el jugador marca uno de sus servidores como principal, ese manda; si no, entra al partner principal que marcó el developer. Solo uno puede ser principal en cada lista.
- El logo propio tiene prioridad sobre el favicon del servidor.

## Launcher — Jugar entra directo al servidor principal
- En Developer, al añadir o editar un partner hay una casilla **"Servidor principal: entrar aquí al pulsar Jugar"**. Solo puede haber uno; al marcar otro se desmarca el anterior. La tarjeta muestra la insignia ENTRA AL JUGAR.
- Con eso, el botón Jugar (el de abajo y el de Inicio) lanza el juego y conecta solo a ese servidor. Va dentro del `partners.json`, así que todos los jugadores lo reciben.
- Cada jugador puede apagarlo en Ajustes → Cliente → "Entrar al servidor principal al pulsar Jugar". Los botones Unirse de la pestaña Servidores siguen entrando al servidor que elijas.

## Launcher — logo propio para los servidores partner
- Cada partner puede tener su **propio logo**: en Developer → Añadir/Editar partner, pulsa "Elegir imagen" (PNG, JPG o WebP). Se recorta cuadrado, se reduce a 128×128 y se guarda dentro del `partners.json`, así que se ve igual para todos los jugadores (también si subes el archivo a `PARTNERS_URL`) y no depende de que el servidor esté encendido.
- Si un partner no tiene logo propio, sigue usando el favicon del servidor y, si tampoco hay, su letra con el color elegido.

## Ametra Client v1.3.0 — menú principal propio, crosshair, hitbox y más rápido
- **Menú principal de Minecraft personalizado**: pantalla propia con fondo degradado, brillos y partículas, logo animado y botones planos con tu color de acento (Un jugador, Multijugador, Opciones, Ametra, Salir). No usa el panorama de Minecraft, así que gasta menos GPU. Se puede volver al de siempre en Tema → "Menú principal de Ametra".
- **Módulo Crosshair**: reemplaza la mira de Minecraft. Estilos Cruz, Cruz + punto, Punto, Círculo y Cuadro; ajustes de largo, hueco y grosor; color propio/chroma; se abre un poco al correr o saltar; barrita de recarga del golpe.
- **Módulo Hitbox**: cajas de colisión de colores (solo jugadores, jugadores y mobs, o todo) con distancia ajustable. La entidad que apuntas se pinta en rojo.
- **Módulos nuevos**: Combo (golpes seguidos sin recibir daño), Día (día y hora del mundo) y Sprint automático.
- Los módulos con color (Crosshair, Hitbox) tienen selector de color en su panel; los ajustes por pasos (estilo, qué mostrar) muestran su nombre en vez de un número.
- **Optimización**: los textos y sus anchos se guardan en caché (antes se creaban de nuevo cada fotograma), los módulos de texto recalculan su valor cada 50 ms en vez de cada fotograma, el contador de CPS solo se consulta si CPS o Teclas están activos, las referencias a Zoom/Fullbright/Viewmodel ya no se buscan por nombre en cada fotograma y la animación del zoom no corre cuando está quieto.

## Launcher — logo, MOTD y estado de los servidores
- Cada servidor (partner y propios) muestra ahora su **logo** (el favicon del servidor), el **MOTD** con colores, jugadores conectados/máximo, ping, versión y un punto verde/rojo de conexión. Se consulta al abrir la pestaña Servidores y se guarda 30 s para no saturar.
- Soporta servidores con registro SRV (sin escribir el puerto).

## Launcher — servidores partner y modo developer
- Nueva sección **Servidores partner** arriba en la pestaña Servidores (tarjeta destacada con insignia PARTNER, descripción, enlace a su web/Discord y botón Unirse).
- **Developer**: inicia sesión con tu contraseña (la creas la primera vez) y podrás añadir, editar, quitar y reordenar partners (↑ ↓), exportar el `partners.json` y restablecer la lista.
- Para que todos los jugadores vean tus cambios: sube el `partners.json` exportado a un enlace https y pon la dirección en `PARTNERS_URL` (arriba del apartado partners de `main.js`), o inclúyelo en `partners.json` del proyecto y saca una versión nueva.

## Launcher — cambiar skin desde dentro
- La pestaña Cuenta ahora deja elegir un `.png` de skin (64×64), ver la vista previa en 3D, escoger brazos clásicos o slim y aplicarla a tu cuenta premium sin salir del launcher.

## Ametra Client v1.2.0 — cosméticos en el juego, letra nueva y viewmodel
- **Cosméticos dentro del juego**: capas, alas, sombreros, gafas y mochilas del launcher ahora se dibujan sobre tu jugador (F5 y vista previa del inventario). El mod lee `cosmetics` de `config/ametra.json` y se actualiza solo si cambias algo en el launcher.
- **Texturas nuevas** (píxel-art de 64x64 con transparencia): capas con tela, patrones y borde festoneado; alas con plumas, membrana de dragón y mariposa; gorra, copa, orejas, gorro de mago y corona con detalles; gafas con reflejos; mochilas con costuras y jetpack con llama.
- **Animaciones**: la capa se mueve al caminar, correr y caer; las alas se pliegan al agacharte y aletean en el aire; el jetpack enciende su llama al saltar. Aurora, Neón, Visor y la corona brillan (no se oscurecen en cuevas).
- **HUD nuevo**: letra propia (Poppins Medium) en el HUD y en el menú, estilo compacto tipo Feather (pastilla oscura sin barra de color) y el HUD se oculta cuando abres F3. El logo ya no aparece por defecto. Todo se activa/desactiva en la pestaña Tema.
- **Módulo Viewmodel**: mueve (X, altura, profundidad) y cambia el tamaño de la mano y del objeto en primera persona, con botón para restablecer.
- Los textos de las texturas se generan con `tools/gen_cosmetics.py` (`pip install pillow`); no hace falta correrlo para compilar.

## Launcher v1.2.0 — rediseño estilo Feather
- Barra lateral izquierda (Inicio, Mods, Cosméticos, Servidores, Ajustes), cuenta abajo y barra de lanzamiento con botón Jugar siempre visible.
- Mods: tarjetas con filtros (Rendimiento, HUD, Visual, Utilidad), buscador, perfiles y pack de rendimiento en una sola pantalla.
- Cosméticos: capas, alas, sombreros, gafas y mochilas (con rareza) y vista previa 3D que gira. Se guardan en `config/ametra.json` (`cosmetics`).
- Servidores: lista propia con botón Unirse (entra directo al servidor).
- Color de acento aplicado a todo el launcher; ventana 1100×700.
- Los cosméticos se ven dentro del juego a partir del mod 1.2.0.

## Ametra Client v1.1.0 — menú nuevo, perfiles y códigos
- Menú rediseñado: tarjetas estilo Lunar (ícono, corazón, engranaje, botón Activado/Desactivado), chips de filtro, buscador y pestañas arriba estilo Real Client.
- Barra lateral "AmetraClient" con perfiles (hasta 6): crear, cambiar, renombrar y borrar.
- **Copiar código / Pegar código**: comparte un perfil completo como texto `AMETRA1:...`.
- Íconos de píxel-art para los 14 módulos.
- El "Editor HUD" ahora es una pestaña. Los perfiles se guardan en `config/ametra.json` (`mprofiles`, `mactive`) sin tocar lo que escribe el launcher.

## v1.1.0 — Pack de optimización para todas las versiones
- Nuevo `optimizer.js`: decide loader, mods, Java y flags de JVM según la versión de Minecraft.
- 1.14 en adelante: Fabric + Sodium, Lithium, FerriteCore, ModernFix, Entity Culling, ImmediatelyFast, More Culling, Krypton, Dynamic FPS (y Phosphor/Starlight/LazyDFU donde corresponde). Cada mod se instala solo si existe build para esa versión exacta.
- 1.3.2 – 1.13.2: Legacy Fabric + Legacy Fabric API + los mods de rendimiento más descargados que haya en Modrinth para esa versión.
- 1.0 – 1.3.1: sin loader; solo Java, flags y ajustes de vídeo.
- Java moderno: 1.14–1.17 suben de Java 8/16 a 17, 1.18–1.20.4 de 17 a 21; 1.20.5+ usa lo que pide Mojang (21, 25…). Versiones ≤ 1.13.2 siguen en Java 8 (con Java 9+ no arrancan) pero se exige 8u312 o más nuevo y, si el del PC es más viejo, se descarga el último.
- Flags de JVM (G1 afinado) para todas las versiones.
- Ajustes de vídeo ligeros en la primera ejecución de instancias ≤ 1.12.2 (solo si no existe options.txt).
- Interruptores en Ajustes: pack automático y Java moderno. Tarjeta del pack en la pestaña Cliente con botón "Instalar ahora".
- Los mods se instalan prefiriendo versiones estables (release) sobre beta/alpha.

## v1.0.0 — primera versión
**Launcher**
- Instancias, cuenta Microsoft u offline, Fabric + Fabric API automáticos, perfiles, skin 3D.
- Instala el mod Ametra Client y sincroniza su configuración (módulos y color de acento).

**Ametra Client (Minecraft 1.21.1)**
- Menú in-game con Shift derecho: rejilla de módulos, buscador, categorías, favoritos y panel de ajustes por módulo.
- 14 módulos: Logo, FPS, CPS, Teclas, Ping, Coordenadas, Reloj, Velocidad, Dirección, Memoria, Servidor, Armadura, Zoom y Fullbright.
- Color por módulo, chroma, escala, fondo, sombra; editor para mover el HUD con el ratón.
- Tema: color de acento con selector completo; animaciones, bienvenida y efectos en el menú principal.
- Lista de mods instalados.
