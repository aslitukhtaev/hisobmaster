'use strict';

// Independent check of the license the server issues (see
// app/Sync/License.php): the PHP side checks it too, this is the shell's own
// second opinion. Token = base64url(payload JSON) "." base64url(DER ECDSA
// P-256 / SHA-256 signature of the first part).

const crypto = require('crypto');

function base64urlDecode(text) {
    return Buffer.from(String(text).replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

/**
 * @returns {{state: string, payload: object|null, daysLeft: number|null}}
 *   state: ok | missing | invalid (bad signature / other computer) | expired
 */
function checkLicense(token, publicKeyPem, fingerprint, nowSeconds = Math.floor(Date.now() / 1000)) {
    if (!token) {
        return { state: 'missing', payload: null, daysLeft: null };
    }
    const parts = String(token).split('.');
    if (parts.length !== 2) {
        return { state: 'invalid', payload: null, daysLeft: null };
    }

    let valid = false;
    try {
        valid = crypto.verify('sha256', Buffer.from(parts[0]), publicKeyPem, base64urlDecode(parts[1]));
    } catch (e) {
        valid = false;
    }
    if (!valid) {
        return { state: 'invalid', payload: null, daysLeft: null };
    }

    let payload;
    try {
        payload = JSON.parse(base64urlDecode(parts[0]).toString('utf8'));
    } catch (e) {
        return { state: 'invalid', payload: null, daysLeft: null };
    }

    if (payload.fingerprint !== fingerprint) {
        return { state: 'invalid', payload, daysLeft: null };
    }

    const daysLeft = Math.ceil((payload.valid_until - nowSeconds) / 86400);
    if (nowSeconds > payload.valid_until) {
        return { state: 'expired', payload, daysLeft };
    }

    return { state: 'ok', payload, daysLeft };
}

module.exports = { checkLicense };
