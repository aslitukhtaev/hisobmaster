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
const { CodeUpdates, backupDatabase } = require('./lib/code-updates');
const { ReceiptPrinter } = require('./lib/receipt-printer');

const SERVER = process.env.KASSIRON_SERVER || 'https://kassiron.uz';
const DEV = !app.isPackaged;

if (!app.requestSingleInstanceLock()) {
    app.quit();
}

/** Main-process problems go to <userData>/logs/main.log (and the console). */
function logError(context, error) {
    const line = `[${new Date().toISOString()}] ${context}: ${error && error.stack ? error.stack : error}\n`;
    try {
        const dir = path.join(app.getPath('userData'), 'logs');
        fs.mkdirSync(dir, { recursive: true });
        fs.appendFileSync(path.join(dir, 'main.log'), line);
    } catch (e) {
        // nowhere to log
    }
    console.error(line.trim());
}
process.on('uncaughtException', (e) => logError('uncaught', e));
process.on('unhandledRejection', (e) => logError('unhandled', e));

let mainWindow = null;
let phpServer = null;
let syncRunner = null;
let lastStatus = null;
let codeUpdates = null;
let runningCode = null;
let pendingUpdate = null;
let appliedUpdate = null;
let cfgShared = null;
let receiptPrinter = null;

function resourcePath(...parts) {
    return DEV ? path.join(__dirname, 'build', ...parts) : path.join(process.resourcesPath, ...parts);
}

/** The PHP shipped with the app (Windows) — as opposed to a system PHP in development. */
function usesBundledPhp() {
    return !process.env.KASSIRON_PHP && !(DEV && process.platform !== 'win32');
}

function phpBinary() {
    if (process.env.KASSIRON_PHP) {
        return process.env.KASSIRON_PHP;
    }
    return usesBundledPhp() ? resourcePath('php', process.platform === 'win32' ? 'php.exe' : 'php') : 'php';
}

function codeDir() {
    if (process.env.KASSIRON_CODE_DIR) {
        return process.env.KASSIRON_CODE_DIR;
    }
    return DEV ? path.resolve(__dirname, '..') : resourcePath('app-code');
}

function publicKeyPem() {
    if (DEV && process.env.KASSIRON_PUBLIC_KEY) {
        return process.env.KASSIRON_PUBLIC_KEY;
    }
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
    const dbPath = process.env.KASSIRON_DB_PATH || path.join(userData, 'kassiron.db');
    const env = {
        ...process.env,
        APP_MODE: 'desktop',
        DB_PATH: dbPath,
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
        phpIni: usesBundledPhp() && fs.existsSync(bundledIni) ? bundledIni : null,
        codeDir: codeDir(),
        env,
        sessionDir,
        errorLog: path.join(logDir, 'php-error.log'),
        caBundle: fs.existsSync(caBundle) ? caBundle : null,
        publicKey: pem,
        dbPath,
        backupDir: path.join(userData, 'backups'),
        userData,
    };
}

function send(channel, payload) {
    if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send(channel, payload);
    }
}

// ---------- Code updates (from kassiron.uz) ----------

async function checkForCodeUpdate() {
    if (!codeUpdates || !cfgShared) {
        return;
    }
    try {
        const ready = await codeUpdates.fetchUpdate({ ...cfgShared, codeDir: runningCode.dir });
        if (ready && ready.version !== runningCode.version) {
            pendingUpdate = ready;
            send('update-ready', ready);
        }
    } catch (e) {
        // A failed check is simply tried again later.
    }
}

/**
 * Switches to the downloaded code: stop PHP and the sync, back up the
 * database, start PHP on the new code (same port), reload the window. If
 * the new code doesn't come up, everything goes back to the old one.
 */
async function applyCodeUpdate() {
    if (!pendingUpdate) {
        return;
    }
    const update = pendingUpdate;
    const previous = runningCode;

    syncRunner.stop();
    await syncRunner.idle();
    await phpServer.stop();
    backupDatabase(cfgShared.dbPath, cfgShared.backupDir, 'before-' + update.version);
    codeUpdates.activate(update.version);
    const next = codeUpdates.pick();

    try {
        await restartOn(next);
        runningCode = next;
        pendingUpdate = null;
        appliedUpdate = update;
        mainWindow.reload();
    } catch (e) {
        codeUpdates.rollback();
        await phpServer.stop();
        await restartOn(previous).catch(() => {});
        send('update-failed', { version: update.version });
    }
    syncRunner.start();
}

async function restartOn(code) {
    phpServer.cfg.codeDir = code.dir;
    syncRunner.cfg.codeDir = code.dir;
    await phpServer.start();
}

// ---------- Desktop shell updates (GitHub Releases) ----------

function setupShellUpdates() {
    if (DEV) {
        return;
    }
    let autoUpdater;
    try {
        ({ autoUpdater } = require('electron-updater'));
    } catch (e) {
        return;
    }
    // Downloads quietly; installs when the app is closed, or right away
    // when the user chooses to restart from the banner.
    autoUpdater.autoDownload = true;
    autoUpdater.autoInstallOnAppQuit = true;
    autoUpdater.on('update-downloaded', (info) => send('shell-update-ready', { version: info.version }));
    ipcMain.on('shell-update-restart', () => autoUpdater.quitAndInstall());
    const check = () => autoUpdater.checkForUpdates().catch(() => {});
    setTimeout(check, 60 * 1000);
    setInterval(check, 6 * 60 * 60 * 1000);
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
        // Tell every page about an update that's waiting / just happened.
        if (pendingUpdate) {
            mainWindow.webContents.send('update-ready', pendingUpdate);
        }
        if (appliedUpdate) {
            mainWindow.webContents.send('update-applied', appliedUpdate);
            appliedUpdate = null;
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
        logError('config', e);
        dialog.showErrorBox('KassirON', e.message);
        app.quit();
        return;
    }

    // The code to run: bundled with the installer, or a newer package from
    // the server. Starting on a new version = the update is applied now
    // (nobody is in the middle of a sale at startup) — so back up first and
    // show what's new once the window is up.
    codeUpdates = new CodeUpdates({ userData: cfg.userData, bundledDir: cfg.codeDir, publicKey: cfg.publicKey });
    runningCode = codeUpdates.pick();
    cfg.codeDir = runningCode.dir;
    cfgShared = cfg;

    const lastRunFile = path.join(cfg.userData, 'last-run.json');
    let lastRun = null;
    try { lastRun = JSON.parse(fs.readFileSync(lastRunFile, 'utf8')); } catch (e) { lastRun = null; }
    const today = new Date().toISOString().slice(0, 10);
    if (lastRun && lastRun.version !== runningCode.version) {
        backupDatabase(cfg.dbPath, cfg.backupDir, 'before-' + runningCode.version);
        const notesFile = path.join(runningCode.dir, 'update-notes.json');
        appliedUpdate = { version: runningCode.version, notes: fs.existsSync(notesFile) ? JSON.parse(fs.readFileSync(notesFile, 'utf8')) : [] };
    } else if (!lastRun || lastRun.backup_day !== today) {
        backupDatabase(cfg.dbPath, cfg.backupDir, 'daily');
    }
    fs.writeFileSync(lastRunFile, JSON.stringify({ version: runningCode.version, backup_day: today }));

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
        logError('php start', e);
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

    receiptPrinter = new ReceiptPrinter({
        userData: cfg.userData,
        origin: () => phpServer.url,
        logError,
        pdfDir: DEV ? process.env.KASSIRON_PRINT_TO_PDF || null : null,
    });

    createWindow(phpServer.url + '/');

    setTimeout(checkForCodeUpdate, 15 * 1000);
    setInterval(checkForCodeUpdate, 60 * 60 * 1000);
    setupShellUpdates();
}

ipcMain.on('sync-now', () => syncRunner && syncRunner.run());
ipcMain.on('apply-update', () => { applyCodeUpdate(); });
ipcMain.on('check-update', () => { checkForCodeUpdate(); });
// Receipt printer (lib/receipt-printer.js) — only for the till window's own
// pages, never for anything else that might end up in a window.
function fromTillWindow(event) {
    return Boolean(mainWindow && !mainWindow.isDestroyed() && event.sender === mainWindow.webContents
        && phpServer && receiptPrinter && event.senderFrame && event.senderFrame.url.startsWith(phpServer.url + '/'));
}
ipcMain.handle('printer-list', (event) => (fromTillWindow(event) ? receiptPrinter.list(event.sender) : []));
ipcMain.handle('printer-settings', (event) => (fromTillWindow(event) ? receiptPrinter.settings() : null));
ipcMain.handle('printer-save', (event, input) => {
    if (!fromTillWindow(event)) {
        return { ok: false, error: 'forbidden' };
    }
    try {
        return { ok: true, settings: receiptPrinter.save(input) };
    } catch (e) {
        logError('printer save', e);
        return { ok: false, error: e.message };
    }
});
ipcMain.handle('printer-print', (event, request) => (fromTillWindow(event) ? receiptPrinter.print(request) : { ok: false, error: 'forbidden' }));

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
