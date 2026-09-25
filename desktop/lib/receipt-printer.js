'use strict';

// Silent receipt printing — straight to the receipt printer, no dialog.
//
// The printer is this computer's own setting (each till has its own), kept
// in <userData>/printer.json:
//   { "deviceName": "XP-80C", "autoPrint": true, "copies": 1 }
//
// A receipt is printed from its print page (/sales/{id}/print: the receipt
// alone, the paper's width — layouts/receipt-print.php) loaded in a hidden
// window of that width. The printed page is made exactly as long as the
// receipt, so a roll printer doesn't feed half a metre of blank paper.

const { BrowserWindow } = require('electron');
const fs = require('fs');
const path = require('path');

/** The only pages that may be printed this way. */
const PAGES = /^\/(?:sales\/\d+\/print|desktop\/printer\/test)$/;
const MICRONS_PER_PX = 25400 / 96;
const MIN_HEIGHT_MICRONS = 20000;
const LOAD_TIMEOUT_MS = 20000;

function normalize(input) {
    const s = input && typeof input === 'object' ? input : {};
    return {
        deviceName: typeof s.deviceName === 'string' ? s.deviceName.slice(0, 256) : '',
        autoPrint: s.autoPrint !== false,
        copies: Math.min(3, Math.max(1, parseInt(s.copies, 10) || 1)),
    };
}

function withTimeout(promise, ms, error) {
    let timer;
    return Promise.race([
        promise,
        new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(error)), ms); }),
    ]).finally(() => clearTimeout(timer));
}

class ReceiptPrinter {
    /**
     * @param {{userData: string, origin: () => string, logError: Function, pdfDir?: string|null}} opts
     *   origin: the local app's address (it can change when PHP restarts).
     *   pdfDir: development only — "print" to PDF files there instead.
     */
    constructor(opts) {
        this.file = path.join(opts.userData, 'printer.json');
        this.origin = opts.origin;
        this.logError = opts.logError;
        this.pdfDir = opts.pdfDir || null;
        this.queue = Promise.resolve();
    }

    settings() {
        try {
            return normalize(JSON.parse(fs.readFileSync(this.file, 'utf8')));
        } catch (e) {
            return normalize(null);
        }
    }

    /** A printer that isn't connected right now may still be saved — it will be again. */
    save(input) {
        const settings = normalize(input);
        fs.writeFileSync(this.file, JSON.stringify(settings, null, 2));
        return settings;
    }

    async list(webContents) {
        const printers = await webContents.getPrintersAsync();
        return printers.map((p) => ({ name: p.name, displayName: p.displayName || p.name, isDefault: !!p.isDefault }));
    }

    /**
     * Prints one of the print pages. Resolves to {ok: true} or {ok: false,
     * error} ("no_printer", or why it failed) — never rejects. Jobs run one
     * at a time, so a double click can't interleave two receipts.
     */
    print(request) {
        const job = this.queue.then(() => this.printNow(request));
        this.queue = job.catch(() => {});
        return job.catch((e) => {
            this.logError('print', e);
            return { ok: false, error: e.message || String(e) };
        });
    }

    async printNow(request) {
        const pagePath = String((request && request.path) || '');
        if (!PAGES.test(pagePath)) {
            return { ok: false, error: 'invalid_page' };
        }
        const paper = Number(request.paper) === 58 ? 58 : 80;
        const settings = this.settings();
        if (!settings.deviceName) {
            return { ok: false, error: 'no_printer' };
        }

        const win = new BrowserWindow({
            show: false,
            width: Math.ceil((paper / 25.4) * 96),
            height: 800,
            useContentSize: true,
            webPreferences: { sandbox: true, contextIsolation: true, nodeIntegration: false },
        });
        try {
            let status = 0;
            win.webContents.on('did-navigate', (_event, _url, httpResponseCode) => { status = httpResponseCode; });
            const url = this.origin() + pagePath;
            await withTimeout(win.loadURL(url), LOAD_TIMEOUT_MS, 'page_timeout');

            // Signed out, the receipt gone...: the page isn't the receipt —
            // never print whatever came instead.
            const page = await win.webContents.executeJavaScript(
                'document.fonts.ready.then(function () { return { receipt: !!document.querySelector(".receipt"), '
                + 'height: Math.ceil(document.documentElement.getBoundingClientRect().height) }; })'
            );
            if (status !== 200 || win.webContents.getURL() !== url || !page.receipt) {
                return { ok: false, error: 'page_unavailable' };
            }

            const heightMicrons = Math.max(Math.ceil(page.height * MICRONS_PER_PX), MIN_HEIGHT_MICRONS);
            if (this.pdfDir) {
                const pdf = await win.webContents.printToPDF({
                    printBackground: true,
                    margins: { top: 0, bottom: 0, left: 0, right: 0 },
                    pageSize: { width: paper / 25.4, height: heightMicrons / 25400 },
                });
                const file = path.join(this.pdfDir, `receipt-${Date.now()}.pdf`);
                fs.writeFileSync(file, pdf);
                return { ok: true, pdf: file, height: heightMicrons, copies: settings.copies, deviceName: settings.deviceName };
            }

            const result = await new Promise((resolve) => {
                win.webContents.print({
                    silent: true,
                    deviceName: settings.deviceName,
                    printBackground: true,
                    color: false,
                    margins: { marginType: 'none' },
                    pageSize: { width: paper * 1000, height: heightMicrons },
                    copies: settings.copies,
                }, (success, failureReason) => {
                    resolve(success ? { ok: true } : { ok: false, error: failureReason || 'print_failed' });
                });
            });
            if (!result.ok) {
                this.logError(`print to "${settings.deviceName}"`, result.error);
            }
            return result;
        } finally {
            win.destroy();
        }
    }
}

module.exports = { ReceiptPrinter, normalize };
