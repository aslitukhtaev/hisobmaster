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

/** PHP -d options shared by the web server and the sync process. */
function phpIniArgs(cfg) {
    const args = [
        '-d', `session.save_path=${cfg.sessionDir}`,
        '-d', 'display_errors=0',
        '-d', 'log_errors=1',
        '-d', `error_log=${cfg.errorLog}`,
        '-d', 'memory_limit=256M',
    ];
    if (cfg.caBundle) {
        args.push('-d', `curl.cainfo=${cfg.caBundle}`, '-d', `openssl.cafile=${cfg.caBundle}`);
    }
    if (cfg.phpIni) {
        // The bundled PHP: its own php.ini, and extensions from its own ext/
        // folder (an absolute path — a relative extension_dir would be
        // resolved against the working directory).
        args.unshift('-c', cfg.phpIni, '-d', `extension_dir=${path.join(path.dirname(cfg.phpIni), 'ext')}`);
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
        this.child = spawn(this.cfg.php, args, {
            cwd: this.cfg.codeDir,
            env: this.cfg.env,
            windowsHide: true,
            stdio: 'ignore',
        });
        this.child.on('exit', (code) => {
            this.child = null;
            if (!this.stopping && this.cfg.onCrash) {
                this.cfg.onCrash(code);
            }
        });
        await waitUntilUp(`${this.url}/desktop/status`, 15000);
    }

    stop() {
        this.stopping = true;
        if (this.child) {
            this.child.kill();
            this.child = null;
        }
    }
}

module.exports = { PhpServer, phpIniArgs };
