'use strict';

// KassirON desktop app: the same PHP application as kassiron.uz, running
// locally on this computer with its own database, so the till works without
// internet and syncs with the server whenever there is a connection.
//
//   resources/php/            PHP for Windows (bundled)
//   resources/app-code/       the KassirON PHP code (bundled; newer signed
//                             code packages from the server go to
//                             <userData>/code/<version>/ — see lib/code-updates.js)
//   <userData>/kassiron.db    this computer's database

const { app, BrowserWindow, ipcMain, shell, dialog, Menu } = require('electron');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { PhpServer } = require('./lib/php-server');
const { SyncRunner } = require('./lib/sync-runner');
const { checkLicense } = require('./lib/license');
const { fingerprint } = require('./lib/fingerprint');

const SERVER = process.env.KASSIRON_SERVER || 'https://kassiron.uz';
const DEV = !app.isPackaged;

if (!app.requestSingleInstanceLock()) {
    app.quit();
}

let mainWindow = null;
let phpServer = null;
let syncRunner = null;
let lastStatus = null;

function resourcePath(...parts) {
    return DEV ? path.join(__dirname, 'build', ...parts) : path.join(process.resourcesPath, ...parts);
}

function phpBinary() {
    if (process.env.KASSIRON_PHP) {
        return process.env.KASSIRON_PHP;
    }
    if (DEV && process.platform !== 'win32') {
        return 'php';
    }
    return resourcePath('php', process.platform === 'win32' ? 'php.exe' : 'php');
}

function codeDir() {
    if (process.env.KASSIRON_CODE_DIR) {
        return process.env.KASSIRON_CODE_DIR;
    }
    return DEV ? path.resolve(__dirname, '..') : resourcePath('app-code');
}

function publicKeyPem() {
    const file = resourcePath('license-public-key.pem');
    return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : (process.env.KASSIRON_PUBLIC_KEY || '');
}

function buildConfig() {
    const userData = app.getPath('userData');
    const sessionDir = path.join(userData, 'sessions');
    const logDir = path.join(userData, 'logs');
    fs.mkdirSync(sessionDir, { recursive: true });
    fs.mkdirSync(logDir, { recursive: true });

    const caBundle = resourcePath('cacert.pem');
    const pem = publicKeyPem();
    const env = {
        ...process.env,
        APP_MODE: 'desktop',
        DB_PATH: process.env.KASSIRON_DB_PATH || path.join(userData, 'kassiron.db'),
        KASSIRON_SERVER: SERVER,
        DEVICE_FINGERPRINT: fingerprint(),
        LICENSE_PUBLIC_KEY_B64: Buffer.from(pem).toString('base64'),
        DESKTOP_APP_VERSION: app.getVersion(),
        DESKTOP_COMPUTER_NAME: os.hostname(),
        SSL_CERT_FILE: fs.existsSync(caBundle) ? caBundle : '',
    };

    const bundledIni = resourcePath('php', 'php.ini');

    return {
        php: phpBinary(),
        phpIni: !process.env.KASSIRON_PHP && fs.existsSync(bundledIni) ? bundledIni : null,
        codeDir: codeDir(),
        env,
        sessionDir,
        errorLog: path.join(logDir, 'php-error.log'),
        caBundle: fs.existsSync(caBundle) ? caBundle : null,
        publicKey: pem,
    };
}

let lastStatusAt = 0;
let navigationStartedAt = 0;

function sendStatus(status) {
    lastStatus = status;
    lastStatusAt = Date.now();
    if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('sync-status', status);
    }
}

function createWindow(url) {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 820,
        minWidth: 900,
        minHeight: 600,
        show: false,
        title: 'KassirON',
        autoHideMenuBar: true,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: true,
        },
    });
    Menu.setApplicationMenu(null);
    mainWindow.once('ready-to-show', () => {
        mainWindow.maximize();
        mainWindow.show();
    });

    const local = new URL(url).origin;
    // Anything outside the local app (the website, WhatsApp/Telegram links)
    // opens in the computer's browser, never inside the till window.
    mainWindow.webContents.setWindowOpenHandler(({ url: target }) => {
        if (!target.startsWith(local)) {
            shell.openExternal(target);
        }
        return { action: 'deny' };
    });
    mainWindow.webContents.on('will-navigate', (event, target) => {
        if (!target.startsWith(local)) {
            event.preventDefault();
            shell.openExternal(target);
        }
    });
    // A sync that finished while a page was still loading was announced
    // before the page could listen — announce it again once it has loaded.
    mainWindow.webContents.on('did-start-navigation', (details) => {
        if (details.isMainFrame) {
            navigationStartedAt = Date.now();
        }
    });
    mainWindow.webContents.on('did-finish-load', () => {
        if (lastStatus && lastStatusAt >= navigationStartedAt) {
            mainWindow.webContents.send('sync-status', lastStatus);
        }
    });
    // A finished sale (its receipt page) goes to the server right away.
    mainWindow.webContents.on('did-navigate', (_event, target) => {
        if (/\/sales\/\d+$/.test(new URL(target).pathname) && syncRunner) {
            syncRunner.run();
        }
    });

    mainWindow.loadURL(url);
}

async function boot() {
    let cfg;
    try {
        cfg = buildConfig();
    } catch (e) {
        dialog.showErrorBox('KassirON', e.message);
        app.quit();
        return;
    }

    phpServer = new PhpServer({
        ...cfg,
        onCrash: () => {
            // Restart on the same port so the open window keeps working.
            setTimeout(() => phpServer.start().catch(() => {}), 1000);
        },
    });

    try {
        await phpServer.start();
    } catch (e) {
        dialog.showErrorBox('KassirON', 'Dasturni ishga tushirib bo\'lmadi (PHP). ' + e.message);
        app.quit();
        return;
    }

    syncRunner = new SyncRunner({
        ...cfg,
        intervalMs: 60000,
        onResult: (outcome) => {
            const license = checkLicense(outcome && outcome.license, cfg.publicKey, cfg.env.DEVICE_FINGERPRINT);
            sendStatus({ ...(outcome || {}), shellLicense: license.state, daysLeft: license.daysLeft });
        },
    });
    syncRunner.start();

    createWindow(phpServer.url + '/');
}

ipcMain.on('sync-now', () => syncRunner && syncRunner.run());
ipcMain.on('print-silently', (event) => {
    event.sender.print({ silent: true, printBackground: true });
});

app.on('second-instance', () => {
    if (mainWindow) {
        if (mainWindow.isMinimized()) {
            mainWindow.restore();
        }
        mainWindow.focus();
    }
});

app.whenReady().then(boot);

app.on('window-all-closed', () => {
    app.quit();
});

app.on('before-quit', () => {
    if (syncRunner) {
        syncRunner.stop();
    }
    if (phpServer) {
        phpServer.stop();
    }
});
