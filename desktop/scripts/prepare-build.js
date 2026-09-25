'use strict';

// Prepares desktop/build/ for electron-builder (run on the Windows CI runner,
// or locally): the KassirON PHP code, PHP 8.3 for Windows (same version line
// as the server) with a php.ini, the VC++ runtime DLLs PHP needs, the CA
// bundle for HTTPS, and the server's license public key.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..', '..');
const BUILD = path.resolve(__dirname, '..', 'build');
const SERVER = process.env.KASSIRON_SERVER || 'https://kassiron.uz';
const PHP_LINE = '8.3';

const CODE_ITEMS = ['app', 'public', 'bin', 'routes.php', path.join('database', 'migrations')];

async function download(url, dest) {
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`${url}: HTTP ${res.status}`);
    }
    fs.writeFileSync(dest, Buffer.from(await res.arrayBuffer()));
}

function sha256(file) {
    return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function copyCode() {
    const target = path.join(BUILD, 'app-code');
    fs.rmSync(target, { recursive: true, force: true });
    for (const item of CODE_ITEMS) {
        fs.cpSync(path.join(ROOT, item), path.join(target, item), { recursive: true });
    }
    console.log('code copied ->', target);
}

async function fetchPhp() {
    const target = path.join(BUILD, 'php');
    fs.rmSync(target, { recursive: true, force: true });
    fs.mkdirSync(target, { recursive: true });

    const releases = await (await fetch('https://windows.php.net/downloads/releases/releases.json')).json();
    const line = releases[PHP_LINE];
    const buildKey = Object.keys(line).find((k) => k.startsWith('nts-') && k.endsWith('-x64'));
    const zipInfo = line[buildKey].zip;
    const zipPath = path.join(BUILD, zipInfo.path);

    await download(`https://windows.php.net/downloads/releases/${zipInfo.path}`, zipPath);
    if (sha256(zipPath) !== zipInfo.sha256) {
        throw new Error(`${zipInfo.path}: sha256 mismatch`);
    }

    if (process.platform === 'win32') {
        execFileSync('tar', ['-xf', zipPath, '-C', target]);
    } else {
        execFileSync('unzip', ['-q', zipPath, '-d', target]);
    }
    fs.rmSync(zipPath);

    // Files only other extensions need (intl's ICU, enchant, PostgreSQL,
    // LDAP) and the debugger/CGI binaries — never loaded here. The DLLs the
    // enabled extensions depend on (OpenSSL, libssh2, nghttp2, brotli,
    // libsqlite3) stay.
    const KEEP_EXT = ['php_curl.dll', 'php_fileinfo.dll', 'php_mbstring.dll', 'php_openssl.dll',
        'php_pdo_sqlite.dll', 'php_sqlite3.dll', 'php_zip.dll', 'php_opcache.dll'];
    for (const file of fs.readdirSync(path.join(target, 'ext'))) {
        if (!KEEP_EXT.includes(file)) {
            fs.rmSync(path.join(target, 'ext', file), { recursive: true, force: true });
        }
    }
    for (const unused of ['dev', 'extras', 'lib', 'phpdbg.exe', 'php8phpdbg.dll', 'php-cgi.exe', 'deplister.exe',
        'icudt72.dll', 'icuin72.dll', 'icuio72.dll', 'icuuc72.dll', 'libenchant2.dll', 'glib-2.dll',
        'gmodule-2.dll', 'gobject-2.dll', 'libpq.dll', 'libsasl.dll']) {
        fs.rmSync(path.join(target, unused), { recursive: true, force: true });
    }
    for (const file of fs.readdirSync(target)) {
        if (/^icu.*\.dll$/i.test(file)) {
            fs.rmSync(path.join(target, file), { force: true });
        }
    }

    // Extensions are loaded from an absolute extension_dir passed at runtime
    // (see lib/php-server.js), since PHP resolves a relative one against the
    // working directory.
    fs.writeFileSync(path.join(target, 'php.ini'), [
        '; KassirON desktop',
        'extension=curl',
        'extension=fileinfo',
        'extension=mbstring',
        'extension=openssl',
        'extension=pdo_sqlite',
        'extension=sqlite3',
        'extension=zip',
        'zend_extension=opcache',
        'opcache.enable_cli=1',
        'date.timezone=Asia/Tashkent',
        'default_charset="UTF-8"',
        'max_execution_time=120',
        'memory_limit=256M',
        'post_max_size=64M',
        'upload_max_filesize=32M',
        'expose_php=Off',
        '',
    ].join('\r\n'));

    // PHP for Windows needs the Visual C++ runtime; shipping the DLLs next to
    // php.exe (app-local, allowed by the VC++ redistributable license) means
    // it runs even where the runtime was never installed.
    if (process.platform === 'win32') {
        for (const dll of ['vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll']) {
            const source = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', dll);
            if (fs.existsSync(source)) {
                fs.copyFileSync(source, path.join(target, dll));
            }
        }
    }
    console.log(`PHP ${line.version} (${buildKey}) ->`, target);
}

async function fetchCaBundle() {
    await download('https://curl.se/ca/cacert.pem', path.join(BUILD, 'cacert.pem'));
    console.log('CA bundle downloaded');
}

async function fetchPublicKey() {
    const res = await fetch(`${SERVER}/api/license/public-key`);
    const body = await res.json();
    if (!body.public_key || !body.public_key.includes('BEGIN PUBLIC KEY')) {
        throw new Error('license public key: unexpected answer');
    }
    fs.writeFileSync(path.join(BUILD, 'license-public-key.pem'), body.public_key);
    console.log('license public key from', SERVER);
}

(async () => {
    fs.mkdirSync(BUILD, { recursive: true });
    copyCode();
    fs.copyFileSync(path.join(ROOT, 'public', 'assets', 'img', 'icon-512.png'), path.join(BUILD, 'icon.png'));
    await fetchPublicKey();
    await fetchCaBundle();
    if (process.env.SKIP_PHP !== '1') {
        await fetchPhp();
    }
})().catch((e) => {
    console.error(e.message);
    process.exit(1);
});
