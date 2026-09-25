'use strict';

// Runs the KassirON PHP code on PHP's built-in web server, bound to
// 127.0.0.1 on a free port — only this computer can reach it — and restarts
// it if it ever exits while the app is open.

const net = require('net');
const http = require('http');
const path = require('path');
const { spawn } = require('child_process');

function freePort() {
    return new Promise((resolve, reject) => {
        const srv = net.createServer();
        srv.unref();
        srv.on('error', reject);
        srv.listen(0, '127.0.0.1', () => {
            const { port } = srv.address();
            srv.close(() => resolve(port));
        });
    });
}

function waitUntilUp(url, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    return new Promise((resolve, reject) => {
        const attempt = () => {
            const req = http.get(url, (res) => {
                res.resume();
                resolve();
            });
            req.on('error', () => {
                if (Date.now() > deadline) {
                    reject(new Error('PHP server did not start'));
                } else {
                    setTimeout(attempt, 150);
                }
            });
            req.setTimeout(2000, () => req.destroy());
        };
        attempt();
    });
}

/**
 * A -d value in INI syntax. Paths are quoted: unquoted, "C:\Users\RUNNER~1"
 * is a syntax error ("~" is an INI operator), and so would be spaces.
 */
function iniPath(value) {
    return `"${String(value).replace(/"/g, '')}"`;
}

/** PHP -d options shared by the web server and the sync process. */
function phpIniArgs(cfg) {
    const args = [
        '-d', `session.save_path=${iniPath(cfg.sessionDir)}`,
        '-d', 'display_errors=0',
        '-d', 'log_errors=1',
        '-d', `error_log=${iniPath(cfg.errorLog)}`,
        '-d', 'memory_limit=256M',
    ];
    if (cfg.caBundle) {
        args.push('-d', `curl.cainfo=${iniPath(cfg.caBundle)}`, '-d', `openssl.cafile=${iniPath(cfg.caBundle)}`);
    }
    if (cfg.phpIni) {
        // The bundled PHP: its own php.ini, and extensions from its own ext/
        // folder (an absolute path — a relative extension_dir would be
        // resolved against the working directory).
        args.unshift('-c', cfg.phpIni, '-d', `extension_dir=${iniPath(path.join(path.dirname(cfg.phpIni), 'ext'))}`);
    }
    return args;
}

class PhpServer {
    constructor(cfg) {
        this.cfg = cfg; // { php, codeDir, env, sessionDir, errorLog, caBundle, onCrash }
        this.child = null;
        this.port = null;
        this.stopping = false;
    }

    get url() {
        return `http://127.0.0.1:${this.port}`;
    }

    async start() {
        this.port = this.port || await freePort();
        const args = [
            ...phpIniArgs(this.cfg),
            '-S', `127.0.0.1:${this.port}`,
            '-t', path.join(this.cfg.codeDir, 'public'),
        ];
        this.stopping = false;
        const child = spawn(this.cfg.php, args, {
            cwd: this.cfg.codeDir,
            env: this.cfg.env,
            windowsHide: true,
            stdio: 'ignore',
        });
        this.child = child;
        child.on('exit', (code) => {
            // Only the current process exiting on its own is a crash; one
            // we stopped (or already replaced) is expected to go.
            if (this.child !== child) {
                return;
            }
            this.child = null;
            if (!this.stopping && this.cfg.onCrash) {
                this.cfg.onCrash(code);
            }
        });
        await waitUntilUp(`${this.url}/desktop/status`, 15000);
    }

    /** Resolves once the process has really exited (and freed the port). */
    stop() {
        this.stopping = true;
        const child = this.child;
        this.child = null;
        if (!child || child.exitCode !== null) {
            return Promise.resolve();
        }
        return new Promise((resolve) => {
            child.once('exit', () => resolve());
            child.kill();
            setTimeout(resolve, 5000);
        });
    }
}

module.exports = { PhpServer, phpIniArgs };
