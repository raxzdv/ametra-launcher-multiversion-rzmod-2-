const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn, execFile } = require('child_process');

let win;
const INSTALL_DIR = path.join(process.env.LOCALAPPDATA || '', 'Programs', 'AmetraClient');
const APP_EXE = path.join(INSTALL_DIR, 'AmetraClient.exe');

function payloadExe() {
  const base = app.isPackaged ? path.join(process.resourcesPath, 'payload') : path.join(__dirname, 'payload');
  return path.join(base, 'setup.exe');
}
function expectedBytes() {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, 'payload-info.json'), 'utf8')).bytes || 0; }
  catch { return 0; }
}
function dirSize(dir) {
  let t = 0;
  try {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      try { t += e.isDirectory() ? dirSize(p) : fs.statSync(p).size; } catch {}
    }
  } catch {}
  return t;
}

function createWindow() {
  win = new BrowserWindow({
    width: 780, height: 480, frame: false, resizable: false, center: true,
    backgroundColor: '#0b0b10', show: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  win.removeMenu();
  win.loadFile('index.html');
  win.once('ready-to-show', () => win.show());
}

ipcMain.on('close', () => app.quit());
ipcMain.on('min', () => win && win.minimize());

ipcMain.on('launch', () => {
  try { spawn(APP_EXE, [], { detached: true, stdio: 'ignore' }).unref(); } catch {}
  setTimeout(() => app.quit(), 300);
});

ipcMain.on('install', () => {
  const exe = payloadExe();
  if (!fs.existsSync(exe)) {
    win.webContents.send('error', 'No se encontró el paquete de instalación (payload/setup.exe).');
    return;
  }
  const total = expectedBytes();
  const t0 = Date.now();
  let finished = false, shown = 0, lastSize = -1, stableSince = 0;

  const finish = (ok, msg) => {
    if (finished) return;
    finished = true; clearInterval(tick);
    if (ok) win.webContents.send('done', { path: INSTALL_DIR });
    else win.webContents.send('error', msg);
  };

  const tick = setInterval(() => {
    if (finished) return;
    const size = dirSize(INSTALL_DIR);
    const bySize = total ? size / total : 0;
    const byTime = 1 - Math.exp(-(Date.now() - t0) / 9000);
    shown = Math.max(shown, Math.min(0.96, Math.max(bySize, byTime * 0.6)));
    win.webContents.send('progress', shown);

    // Si ya se copiaron los archivos y no cambian durante unos segundos, la instalación terminó
    // aunque el instalador interno tarde en cerrarse.
    if (size === lastSize) { if (!stableSince) stableSince = Date.now(); } else { stableSince = 0; lastSize = size; }
    const copied = total ? size >= total * 0.97 : false;
    if (copied && fs.existsSync(APP_EXE) && stableSince && Date.now() - stableSince > 6000) finish(true);
    // Tope de seguridad: 4 minutos
    if (Date.now() - t0 > 240000) finish(fs.existsSync(APP_EXE), 'La instalación tardó demasiado. Cierra AmetraClient si está abierto y vuelve a intentarlo.');
  }, 500);

  // Si AmetraClient está abierto, el instalador se queda esperando: lo cerramos antes de actualizar.
  execFile('taskkill', ['/F', '/IM', 'AmetraClient.exe'], () => {
    if (finished) return;
    // Instalación limpia: si queda una versión anterior rota, su desinstalador falla ("Fallo al desinstalar archivos antiguos").
    // Tus datos (instancias, cuenta, ajustes) están en otras carpetas, no aquí.
    setTimeout(() => {
      if (finished) return;
      try { fs.rmSync(INSTALL_DIR, { recursive: true, force: true, maxRetries: 5, retryDelay: 400 }); } catch {}
      launchSetup();
    }, 800);
  });

  function launchSetup() {
    let child;
    try {
      child = spawn(exe, ['/S'], { stdio: 'ignore', windowsHide: true });
    } catch (e) { finish(false, 'No se pudo iniciar la instalación: ' + e.message); return; }
    child.on('error', (e) => finish(false, 'Error al instalar: ' + e.message));
    child.on('exit', (code) => {
      if (code === 0 || fs.existsSync(APP_EXE)) finish(true);
      else finish(false, 'La instalación terminó con el código ' + code + '.');
    });
  }
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
