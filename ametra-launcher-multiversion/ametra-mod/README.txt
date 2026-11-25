Código fuente del mod Ametra Client (Fabric, Minecraft 1.21.1).
Mira el README de la carpeta de arriba para compilarlo.
Fuentes: src/main/java/com/ametra  ·  Recursos: src/main/resources


FONDOS DEL MENU PRINCIPAL (v1.5.0)
- Carpeta: .minecraft/<instancia>/ametra/fondos/  (el launcher la abre con "Carpeta de fondos del menu").
- Acepta PNG/JPG/BMP, GIF animados y carpetas con frames numerados (img_001.png...). "@30" al final del nombre de la
  carpeta = 30 fps (por defecto 20).
- Video a GIF:    ffmpeg -i video.mp4 -vf "fps=20,scale=640:-1" fondo.gif
- Video a frames: mkdir fondo@24 && ffmpeg -i video.mp4 -vf "fps=24,scale=640:-1" fondo@24/img_%04d.png
- Dentro del juego: Shift derecho -> FONDOS (vista previa en vivo) y COSMETICOS (equipar y ver como queda).


VERSIONES DE MINECRAFT
- Un jar por "familia" de versiones. El mapa versión -> jar está en  mod-compat.json  (raíz del proyecto).
- COMPILAN HOY (código portado):
    1.20/1.20.1 -> rzmod-1.20.1.jar   1.20.2 -> 1.20.2   1.20.3/1.20.4 -> 1.20.4
    1.20.5/1.20.6 -> 1.20.6                   1.21/1.21.1 -> 1.21.1
- PENDIENTES (el launcher avisa en vez de fallar):
    1.19.x  -> no existe DrawContext: hay que añadir una capa de dibujo con MatrixStack/DrawableHelper.
    1.21.2+ -> render nuevo: los cosméticos 3D (FeatureRenderer) pasan a usar "render state", drawTexture pide RenderLayer,
               1.21.6+ cambia la pila de matrices del GUI y 1.21.9+ los eventos de ratón/teclado.
- Cómo está organizado el código por versión:
    src/main/java            -> código común a TODAS las versiones.
    src/compat/<familia>/java -> lo que cambia entre versiones: Compat.java (Identifier, HUD, vértices, efectos, servidores,
                                 vista previa del jugador, skin), ScreenBase.java (rueda del ratón) y los mixins InGameHudMixin y SkinMixin.
    versions/<mc>.properties  -> clave  compat=<familia>  que elige qué carpeta se añade al compilar.
  Si una versión cambia una llamada, se toca SOLO el Compat.java de su familia (no el código común).
- Cada versión tiene su archivo  versions/<mc>.properties  (rango mc_dep, Java y compat). yarn, loader y Fabric API se buscan solos
  en GitHub Actions con  tools/resolve.sh  (workflow "Release", un trabajo por versión; si una no compila, las demás se publican igual).
- Para compilar una versión en tu PC:  cd ametra-mod && bash tools/resolve.sh 1.20.4 && gradle build
  (cambia gradle.properties; restáuralo después con git o vuelve a copiar el original).
- Minecraft 26.x usa otros nombres y Java 25: necesita un port aparte.
