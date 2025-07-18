import crypto from 'crypto';

const BASE_SECRET = process.env.BASE_SECRET || 'egov-admin-console-enc';
const SALT_LENGTH = 16; // bytes
const IV_LENGTH = 12;   // recommended for GCM
const KEY_LENGTH = 32;  // AES-256
const PBKDF2_ITERATIONS = 100_000; // higher = more secure

function deriveKey(salt: Buffer): Buffer {
    return crypto.pbkdf2Sync(BASE_SECRET, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha512');
}

export function encrypt(plainText: string): string {
    const salt = crypto.randomBytes(SALT_LENGTH); // unique per message
    const iv = crypto.randomBytes(IV_LENGTH);
    const key = deriveKey(salt);

    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);
    const authTag = cipher.getAuthTag();

    // Format: salt:iv:authTag:encrypted (all base64)
    return [
        salt.toString('base64'),
        iv.toString('base64'),
        authTag.toString('base64'),
        encrypted.toString('base64'),
    ].join(':');
}

export function decrypt(encryptedText: string): string {
    const [saltB64, ivB64, authTagB64, encryptedB64] = encryptedText.split(':');

    const salt = Buffer.from(saltB64, 'base64');
    const iv = Buffer.from(ivB64, 'base64');
    const authTag = Buffer.from(authTagB64, 'base64');
    const encrypted = Buffer.from(encryptedB64, 'base64');

    const key = deriveKey(salt);
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([decipher.update(encrypted), decipher.final()]);
    return decrypted.toString('utf8');
}
