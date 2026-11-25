const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('studio', {
  list: () => ipcRenderer.invoke('studio:list'),
  load: (n) => ipcRenderer.invoke('studio:load', n),
  save: (d) => ipcRenderer.invoke('studio:save', d),
  remove: (n) => ipcRenderer.invoke('studio:remove', n),
  folder: () => ipcRenderer.invoke('studio:folder')
});
