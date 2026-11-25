const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('inst', {
  install: () => ipcRenderer.send('install'),
  launch: () => ipcRenderer.send('launch'),
  close: () => ipcRenderer.send('close'),
  minimize: () => ipcRenderer.send('min'),
  on: (ch, fn) => ipcRenderer.on(ch, (_e, d) => fn(d))
});
