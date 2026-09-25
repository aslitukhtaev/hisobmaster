'use strict';

// The only bridge between the KassirON pages and the desktop shell: sync on
// demand and live sync status. Nothing else from Node is exposed.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('kassironDesktop', {
    syncNow: () => ipcRenderer.send('sync-now'),
    onSyncStatus: (callback) => ipcRenderer.on('sync-status', (_event, status) => callback(status)),
    printSilently: () => ipcRenderer.send('print-silently'),
});
