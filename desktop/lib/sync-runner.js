'use strict';

// Runs bin/desktop-sync.php in its own PHP process — never inside the web
// server, so a slow or dead connection can't freeze the till — every minute
// and on demand (after a sale, the "Sinxronlash" button). Runs never overlap.

const path = require('path');
const { execFile } = require('child_process');
const { phpIniArgs } = require('./php-server');

class SyncRunner {
    constructor(cfg) {
        this.cfg = cfg; // { php, codeDir, env, sessionDir, errorLog, caBundle, onResult, intervalMs }
        this.running = false;
        this.again = false;
        this.timer = null;
    }

    start() {
        this.timer = setInterval(() => this.run(), this.cfg.intervalMs || 60000);
        this.run();
    }

    stop() {
        clearInterval(this.timer);
        this.timer = null;
        this.again = false;
    }

    /** Resolves when no run is in progress (e.g. before switching code). */
    idle() {
        return new Promise((resolve) => {
            const wait = () => (this.running ? setTimeout(wait, 200) : resolve());
            wait();
        });
    }

    /** Starts a run now, or right after the current one if one is running. */
    run() {
        if (this.running) {
            this.again = true;
            return;
        }
        this.running = true;

        const script = path.join(this.cfg.codeDir, 'bin', 'desktop-sync.php');
        execFile(this.cfg.php, [...phpIniArgs(this.cfg), script], {
            cwd: this.cfg.codeDir,
            env: this.cfg.env,
            windowsHide: true,
            timeout: 5 * 60 * 1000,
            maxBuffer: 4 * 1024 * 1024,
        }, (error, stdout) => {
            this.running = false;
            let parsed = null;
            try {
                parsed = JSON.parse(String(stdout).trim().split('\n').pop());
            } catch (e) {
                parsed = { result: { ok: false, error: 'client_error' }, status: null, license: null };
            }
            if (this.cfg.onResult) {
                this.cfg.onResult(parsed);
            }
            if (this.again && this.timer) {
                this.again = false;
                setTimeout(() => this.run(), 500);
            }
        });
    }
}

module.exports = { SyncRunner };
