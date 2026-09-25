'use strict';

// This computer's identity for the license: sha256 of the Windows
// MachineGuid (set when Windows is installed; copying the app or its data to
// another computer changes it). Linux/macOS equivalents are for development.

const crypto = require('crypto');
const fs = require('fs');
const { execFileSync } = require('child_process');

function machineId() {
    try {
        if (process.platform === 'win32') {
            const out = execFileSync('reg', ['query', 'HKLM\\SOFTWARE\\Microsoft\\Cryptography', '/v', 'MachineGuid'], {
                encoding: 'utf8',
                windowsHide: true,
            });
            const match = out.match(/MachineGuid\s+REG_SZ\s+([^\s]+)/i);
            if (match) {
                return match[1].trim();
            }
        } else if (process.platform === 'linux') {
            for (const file of ['/etc/machine-id', '/var/lib/dbus/machine-id']) {
                if (fs.existsSync(file)) {
                    return fs.readFileSync(file, 'utf8').trim();
                }
            }
        } else if (process.platform === 'darwin') {
            const out = execFileSync('ioreg', ['-rd1', '-c', 'IOPlatformExpertDevice'], { encoding: 'utf8' });
            const match = out.match(/"IOPlatformUUID" = "([^"]+)"/);
            if (match) {
                return match[1];
            }
        }
    } catch (e) {
        // fall through
    }
    return null;
}

function fingerprint() {
    const id = machineId();
    if (!id) {
        throw new Error('Kompyuter identifikatorini aniqlab bo\'lmadi (MachineGuid).');
    }
    return crypto.createHash('sha256').update(id.toLowerCase()).digest('hex');
}

module.exports = { fingerprint };
