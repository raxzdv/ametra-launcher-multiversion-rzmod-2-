// Pack de optimización de Ametra: decide, para CUALQUIER versión de Minecraft, qué loader,
// qué mods, qué Java y qué flags de JVM usar. No depende de Electron, así que se puede probar con node.
'use strict';

// "1.16.5" -> { minor: 16, patch: 5 }.  "26.1.1" (esquema nuevo de Mojang) -> { minor: Infinity }.
function parseMc(v) {
  const m = /^1\.(\d+)(?:\.(\d+))?/.exec(String(v));
  if (!m) return { minor: Infinity, patch: 0 };
  return { minor: Number(m[1]), patch: Number(m[2] || 0) };
}
// -1 / 0 / 1 comparando "1.x.y" con otra versión.
function cmp(v, minor, patch = 0) {
  const a = parseMc(v);
  if (a.minor !== minor) return a.minor < minor ? -1 : 1;
  return a.patch === patch ? 0 : a.patch < patch ? -1 : 1;
}

// Loader según la versión:
//  - modern: Fabric (1.14 en adelante, incluida la numeración nueva 26.x)
//  - legacy: Legacy Fabric (1.3.2 – 1.13.2)
//  - vanilla: 1.0 – 1.3.1 (ningún loader las soporta; solo Java + JVM + ajustes)
function eraOf(version) {
  if (cmp(version, 14) >= 0) return 'modern';
  if (cmp(version, 3, 2) >= 0) return 'legacy';
  return 'vanilla';
}

// ---------- Java ----------
// Mojang pide un mínimo (8 / 16 / 17 / 21 / 25). Aquí se decide a cuál SUBIR sin romper nada:
//  - 1.14 – 1.16.5  (piden Java 8)  -> 17   Fabric lo soporta bien y G1 mejora mucho.
//  - 1.17           (pide 16)       -> 17
//  - 1.18 – 1.20.4  (piden 17)      -> 21
//  - 1.20.5 en adelante             -> lo que pida Mojang (21, 25…)
//  - 1.13.2 o menos (LaunchWrapper): NO se puede pasar de Java 8 (en Java 9+ el juego ni arranca),
//    así que se exige al menos la actualización 8u312 y, si el Java 8 del PC es más viejo, se descarga el último.
const MIN_JAVA8_UPDATE = 312;
function javaPlan(version, required) {
  const req = Number(required) || 8;
  let target = req, upgraded = false, why = '';
  if (cmp(version, 14) >= 0 && cmp(version, 17) < 0) { target = Math.max(req, 17); }
  else if (cmp(version, 17) >= 0 && cmp(version, 18) < 0) { target = Math.max(req, 17); }
  else if (cmp(version, 18) >= 0 && (cmp(version, 20, 5) < 0)) { target = Math.max(req, 21); }
  upgraded = target > req;
  if (upgraded) why = `Java ${req} → ${target} (más rápido y con mejor recolector de basura)`;
  else if (target === 8) why = `Java 8 (las versiones antiguas no arrancan con Java 9+); se exige 8u${MIN_JAVA8_UPDATE} o más nuevo`;
  else why = `Java ${target}`;
  return { required: req, target, upgraded, minUpdate: target === 8 ? MIN_JAVA8_UPDATE : 0, why };
}

// Banderas de JVM válidas desde Java 8 hasta 25. Pensadas para cliente (pausas cortas, sin AlwaysPreTouch).
function jvmArgs(javaMajor, ramGB, cdsFile) {
  const heap = Number(ramGB) || 4;
  const a = [
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:+UseG1GC',
    '-XX:+ParallelRefProcEnabled',
    '-XX:MaxGCPauseMillis=50',
    '-XX:+DisableExplicitGC',
    '-XX:G1NewSizePercent=30',
    '-XX:G1MaxNewSizePercent=40',
    `-XX:G1HeapRegionSize=${heap >= 8 ? 16 : 8}M`,
    '-XX:G1ReservePercent=20',
    '-XX:G1HeapWastePercent=5',
    '-XX:G1MixedGCCountTarget=4',
    '-XX:InitiatingHeapOccupancyPercent=15',
    '-XX:G1MixedGCLiveThresholdPercent=90',
    '-XX:G1RSetUpdatingPauseTimePercent=5',
    '-XX:SurvivorRatio=32',
    '-XX:+PerfDisableSharedMem',
    '-XX:MaxTenuringThreshold=1'
  ];
  if (javaMajor >= 9) a.push('-Xshare:auto');
  // Menos RAM: en G1 las cadenas repetidas (nombres de mods, texturas, JSON) se guardan una sola vez.
  if (javaMajor >= 9) a.push('-XX:+UseStringDeduplication');
  // Arranque más rápido: la JVM guarda un archivo de clases ya cargadas y lo reutiliza en el siguiente inicio.
  // Solo Java 19+ lo crea solo; en versiones viejas se omite para no generar errores.
  if (javaMajor >= 19 && cdsFile) a.push('-XX:+AutoCreateSharedArchive', `-XX:SharedArchiveFile=${cdsFile}`);
  return a;
}

// ---------- Mods ----------
// slug de Modrinth + rango de versiones donde tiene sentido pedirlo (Modrinth decide si hay build real;
// si no la hay, se salta sin error). from/to = [minor, patch]; to omitido = sin tope.
const FABRIC_MODS = [
  { slug: 'sodium',         name: 'Sodium',         from: [16, 3],  why: 'Motor de render: el mayor salto de FPS' },
  { slug: 'lithium',        name: 'Lithium',        from: [14, 0],  why: 'Lógica del juego, IA y ticks más rápidos' },
  { slug: 'phosphor',       name: 'Phosphor',       from: [14, 0], to: [16, 1], why: 'Iluminación rápida (1.14–1.16.1)' },
  { slug: 'starlight',      name: 'Starlight',      from: [16, 2], to: [20, 1], why: 'Iluminación rápida (1.16.2–1.20.1)' },
  { slug: 'lazydfu',        name: 'LazyDFU',        from: [14, 0], to: [15, 2], why: 'Arranque mucho más rápido' },
  { slug: 'ferrite-core',   name: 'FerriteCore',    from: [16, 2],  why: 'Menos uso de RAM' },
  { slug: 'modernfix',      name: 'ModernFix',      from: [16, 5],  why: 'Carga más rápida y menos memoria' },
  { slug: 'entityculling',  name: 'Entity Culling', from: [16, 5],  why: 'No dibuja lo que no se ve' },
  { slug: 'immediatelyfast',name: 'ImmediatelyFast',from: [19, 4],  why: 'Dibujado de HUD y texto más rápido' },
  { slug: 'moreculling',    name: 'More Culling',   from: [19, 0],  why: 'Menos caras ocultas por dibujar' },
  { slug: 'krypton',        name: 'Krypton',        from: [16, 5],  why: 'Red más eficiente' },
  { slug: 'dynamic-fps',    name: 'Dynamic FPS',    from: [16, 5],  why: 'Baja el consumo con el juego en segundo plano' }
];
// Legacy Fabric: casi no hay mods de rendimiento con slug fijo, así que se descubren en Modrinth
// (categoría "optimization" + loader legacy-fabric + la versión exacta) y se instalan los más descargados.
const LEGACY_REQUIRED = [{ slug: 'legacy-fabric-api', name: 'Legacy Fabric API', why: 'Base para los mods de Legacy Fabric' }];
const LEGACY_DISCOVER_MAX = 5;

function inRange(version, m) {
  if (m.from && cmp(version, m.from[0], m.from[1]) < 0) return false;
  if (m.to && cmp(version, m.to[0], m.to[1]) > 0) return false;
  return true;
}

// ---------- Ajustes de vídeo (solo ≤ 1.12.2: ahí options.txt es plano y no tiene reparador de datos) ----------
function videoOptions(version) {
  if (cmp(version, 12, 2) > 0) return null;
  return {
    renderDistance: 8, maxFps: 260, enableVsync: 'false', fancyGraphics: 'false', particles: 2,
    entityShadows: 'false', mipmapLevels: 2, useVbo: 'true', fboEnable: 'true', clouds: 0
  };
}

// Todo junto: qué se le hace a esta versión.
function plan(version, requiredJava) {
  const era = eraOf(version);
  const java = javaPlan(version, requiredJava);
  const loader = era === 'modern' ? 'fabric' : era === 'legacy' ? 'legacy-fabric' : null;
  const mods = era === 'modern' ? FABRIC_MODS.filter((m) => inRange(version, m))
    : era === 'legacy' ? LEGACY_REQUIRED.slice() : [];
  return { version, era, loader, java, mods, discover: era === 'legacy', video: videoOptions(version) };
}

module.exports = { parseMc, cmp, eraOf, javaPlan, jvmArgs, plan, videoOptions, inRange, FABRIC_MODS, LEGACY_REQUIRED, LEGACY_DISCOVER_MAX, MIN_JAVA8_UPDATE };
