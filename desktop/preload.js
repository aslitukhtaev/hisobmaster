'use strict';

// The only bridge between the KassirON pages and the desktop shell: sync on
// demand and live sync status, updates, and the receipt printer. Nothing
// else from Node is exposed.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('kassironDesktop', {
    syncNow: () => ipcRenderer.send('sync-now'),
    onSyncStatus: (callback) => ipcRenderer.on('sync-status', (_event, status) => callback(status)),
    printerList: () => ipcRenderer.invoke('printer-list'),
    printerSettings: () => ipcRenderer.invoke('printer-settings'),
    savePrinterSettings: (settings) => ipcRenderer.invoke('printer-save', settings),
    printPage: (request) => ipcRenderer.invoke('printer-print', request),
    onUpdateReady: (callback) => ipcRenderer.on('update-ready', (_event, info) => callback(info)),
    onUpdateApplied: (callback) => ipcRenderer.on('update-applied', (_event, info) => callback(info)),
    onUpdateFailed: (callback) => ipcRenderer.on('update-failed', (_event, info) => callback(info)),
    onShellUpdateReady: (callback) => ipcRenderer.on('shell-update-ready', (_event, info) => callback(info)),
    applyUpdate: () => ipcRenderer.send('apply-update'),
    restartForShellUpdate: () => ipcRenderer.send('shell-update-restart'),
});
