'use strict';

// Runs the prepared build the way the app does (lib/php-server.js, the
// bundled PHP with its php.ini on Windows) and checks that the extensions
// load, the local server answers, and the sync script runs. Used by CI on
// the Windows runner before the installer is built.

const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const { execFileSync } = require('child_process');
const { PhpServer, phpIniArgs } = require('../lib/php-server');

const BUILD = path.resolve(__dirname, '..', 'build');
const win = process.platform === 'win32';
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'kassiron-smoke-'));

const cfg = {
    php: win ? path.join(BUILD, 'php', 'php.exe') : (process.env.KASSIRON_PHP || 'php'),
    phpIni: win ? path.join(BUILD, 'php', 'php.ini') : null,
    codeDir: path.join(BUILD, 'app-code'),
    sessionDir: tmp,
    errorLog: path.join(tmp, 'php-error.log'),
    caBundle: path.join(BUILD, 'cacert.pem'),
    env: {
        ...process.env,
        APP_MODE: 'desktop',
        DB_PATH: path.join(tmp, 'kassiron.db'),
        KASSIRON_SERVER: 'https://kassiron.uz',
        DEVICE_FINGERPRINT: 'a'.repeat(64),
        LICENSE_PUBLIC_KEY_B64: Buffer.from(fs.readFileSync(path.join(BUILD, 'license-public-key.pem'))).toString('base64'),
        DESKTOP_APP_VERSION: 'smoke',
    },
};

function get(url) {
    return new Promise((resolve, reject) => {
        http.get(url, (res) => {
            let body = '';
            res.on('data', (c) => { body += c; });
            res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
        }).on('error', reject);
    });
}

function check(condition, message) {
    if (!condition) {
        throw new Error('FAILED: ' + message);
    }
    console.log('ok -', message);
}

(async () => {
    const modules = execFileSync(cfg.php, [...phpIniArgs(cfg), '-m'], { encoding: 'utf8' });
    for (const ext of ['curl', 'openssl', 'pdo_sqlite', 'mbstring', 'zip', 'json']) {
        check(new RegExp(`^${ext}$`, 'mi').test(modules), `PHP extension ${ext} loads`);
    }

    const server = new PhpServer(cfg);
    await server.start();
    try {
        const status = await get(server.url + '/desktop/status');
        const parsed = JSON.parse(status.body);
        check(status.status === 200 && parsed.activated === false, 'local server answers, fresh install is not activated');
        const root = await get(server.url + '/');
        check(root.status === 302 && /\/desktop\/activate$/.test(root.headers.location), 'unactivated app goes to activation');
        const page = await get(server.url + '/desktop/activate');
        check(page.status === 200 && page.body.includes('name="login"'), 'activation page renders');
        check(fs.existsSync(cfg.env.DB_PATH), 'local database created and migrated');

        const sync = execFileSync(cfg.php, [...phpIniArgs(cfg), path.join(cfg.codeDir, 'bin', 'desktop-sync.php')], {
            cwd: cfg.codeDir, env: cfg.env, encoding: 'utf8',
        });
        check(JSON.parse(sync.trim().split('\n').pop()).result.error === 'not_activated', 'sync script runs');

        const log = fs.existsSync(cfg.errorLog) ? fs.readFileSync(cfg.errorLog, 'utf8') : '';
        check(!/Fatal|Warning/.test(log), 'no PHP errors in the log' + (log ? ':\n' + log : ''));
    } finally {
        server.stop();
    }
    console.log('SMOKE TEST PASSED');
})().catch((e) => {
    console.error(e.message);
    process.exit(1);
});
