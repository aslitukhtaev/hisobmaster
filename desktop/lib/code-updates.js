'use strict';

// Code updates: every change deployed to kassiron.uz reaches the till as a
// signed package (see app/Sync/CodePackage.php).
//
//   <userData>/code/current.json     {version, created_at, previous}
//   <userData>/code/<version>/       an unpacked package (+ code-version.json,
//                                    update-notes.json)
//
// The code that runs is whichever is newer: the one bundled with the
// installer or the latest downloaded package. A package is accepted only if
// its signature checks out against the server's public key built into the
// app, and unpacked only into the allowed folders.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');
const { execFile } = require('child_process');
const { phpIniArgs } = require('./php-server');

const ALLOWED = ['app/', 'public/', 'bin/', 'database/migrations/'];
const ALLOWED_FILES = ['routes.php'];

function readJson(file) {
    try {
        return JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch (e) {
        return null;
    }
}

class CodeUpdates {
    constructor({ userData, bundledDir, publicKey }) {
        this.root = path.join(userData, 'code');
        this.bundledDir = bundledDir;
        this.publicKey = publicKey;
        fs.mkdirSync(this.root, { recursive: true });
    }

    /** The code to run: {dir, version, created_at}. */
    pick() {
        const bundled = { dir: this.bundledDir, ...(readJson(path.join(this.bundledDir, 'code-version.json')) || { version: 'bundled', created_at: 0 }) };
        const current = readJson(path.join(this.root, 'current.json'));
        if (current && current.version) {
            const dir = path.join(this.root, current.version);
            const info = readJson(path.join(dir, 'code-version.json'));
            if (info && info.version === current.version && info.created_at > bundled.created_at) {
                return { dir, ...info };
            }
        }
        return bundled;
    }

    /** Runs bin/desktop-update.php with the code that is running now. */
    runCli(cfg, args) {
        return new Promise((resolve) => {
            execFile(cfg.php, [...phpIniArgs(cfg), path.join(cfg.codeDir, 'bin', 'desktop-update.php'), ...args], {
                cwd: cfg.codeDir,
                env: cfg.env,
                windowsHide: true,
                timeout: 6 * 60 * 1000,
                maxBuffer: 1024 * 1024,
            }, (error, stdout) => {
                try {
                    resolve(JSON.parse(String(stdout).trim().split('\n').pop()));
                } catch (e) {
                    resolve({ error: 'client_error' });
                }
            });
        });
    }

    /**
     * Checks the server and, when there's newer code, downloads, verifies
     * and unpacks it. Resolves to {version, notes} of a package ready to
     * switch to, or null.
     */
    async fetchUpdate(cfg) {
        const check = await this.runCli(cfg, ['check']);
        if (!check || check.error || !check.available || !/^[a-f0-9]{16}$/.test(check.version || '')) {
            return null;
        }

        const target = path.join(this.root, check.version);
        if (!fs.existsSync(path.join(target, 'code-version.json'))) {
            const download = await this.runCli(cfg, ['download', this.root, check.version]);
            if (!download || download.error || !download.file) {
                return null;
            }
            try {
                const bytes = fs.readFileSync(download.file);
                const hash = crypto.createHash('sha256').update(bytes).digest('hex');
                const signed = crypto.verify('sha256', bytes, this.publicKey, Buffer.from(check.signature || '', 'base64'));
                if (hash !== check.sha256 || !signed) {
                    throw new Error('package signature/hash mismatch');
                }
                this.unpack(bytes, check.version, target);
                fs.writeFileSync(path.join(target, 'update-notes.json'), JSON.stringify(check.notes || []));
            } finally {
                fs.rmSync(download.file, { force: true });
            }
        }

        return { version: check.version, notes: readJson(path.join(target, 'update-notes.json')) || [] };
    }

    unpack(bytes, version, target) {
        const bundle = JSON.parse(zlib.gunzipSync(bytes).toString('utf8'));
        if (bundle.format !== 1 || bundle.version !== version || typeof bundle.files !== 'object') {
            throw new Error('unexpected package');
        }

        const tmp = target + '.tmp';
        fs.rmSync(tmp, { recursive: true, force: true });
        for (const [relative, content] of Object.entries(bundle.files)) {
            const normalized = path.posix.normalize(relative);
            const allowed = ALLOWED_FILES.includes(normalized) || ALLOWED.some((prefix) => normalized.startsWith(prefix));
            if (!allowed || normalized.includes('..') || path.isAbsolute(normalized)) {
                throw new Error('package path not allowed: ' + relative);
            }
            const file = path.join(tmp, ...normalized.split('/'));
            fs.mkdirSync(path.dirname(file), { recursive: true });
            fs.writeFileSync(file, Buffer.from(content, 'base64'));
        }
        fs.writeFileSync(path.join(tmp, 'code-version.json'), JSON.stringify({ version, created_at: bundle.created_at }));
        fs.rmSync(target, { recursive: true, force: true });
        fs.renameSync(tmp, target);
    }

    /** Makes `version` the code to run from now on (the previous one is kept for a rollback). */
    activate(version) {
        const current = readJson(path.join(this.root, 'current.json'));
        fs.writeFileSync(path.join(this.root, 'current.json'), JSON.stringify({
            version,
            previous: current ? current.version : null,
        }));
        this.cleanup(version, current ? current.version : null);
    }

    /** Back to the previous package (or the bundled code) after a failed switch. */
    rollback() {
        const current = readJson(path.join(this.root, 'current.json'));
        if (current && current.previous) {
            fs.writeFileSync(path.join(this.root, 'current.json'), JSON.stringify({ version: current.previous, previous: null }));
        } else {
            fs.rmSync(path.join(this.root, 'current.json'), { force: true });
        }
    }

    cleanup(keep, previous) {
        for (const entry of fs.readdirSync(this.root)) {
            if (/^[a-f0-9]{16}(\.tmp)?$/.test(entry) && entry !== keep && entry !== previous) {
                fs.rmSync(path.join(this.root, entry), { recursive: true, force: true });
            }
        }
    }
}

/** Copies the database aside (the app keeps the last `keep` copies). */
function backupDatabase(dbPath, backupDir, label, keep = 10) {
    if (!fs.existsSync(dbPath)) {
        return null;
    }
    fs.mkdirSync(backupDir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19);
    const target = path.join(backupDir, `kassiron-${stamp}-${label}.db`);
    fs.copyFileSync(dbPath, target);
    const copies = fs.readdirSync(backupDir).filter((f) => /^kassiron-.*\.db$/.test(f)).sort();
    for (const old of copies.slice(0, Math.max(0, copies.length - keep))) {
        fs.rmSync(path.join(backupDir, old), { force: true });
    }
    return target;
}

module.exports = { CodeUpdates, backupDatabase };
