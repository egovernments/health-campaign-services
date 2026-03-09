import crypto from 'crypto';
import { promisify } from 'util';
import config from '../config';

const pbkdf2Async = promisify(crypto.pbkdf2);

const BASE_SECRET: any = config.basesecret;
const SALT_LENGTH = 16; // bytes
const IV_LENGTH = 12;   // recommended for GCM
const KEY_LENGTH = 32;  // AES-256
const LEGACY_PBKDF2_ITERATIONS = 100_000;
const PBKDF2_ITERATIONS = 10_000;

function deriveKeySync(salt: Buffer, iterations: number = LEGACY_PBKDF2_ITERATIONS): Buffer {
    return crypto.pbkdf2Sync(BASE_SECRET, salt, iterations, KEY_LENGTH, 'sha512');
}

async function deriveKeyAsync(salt: Buffer, iterations: number = LEGACY_PBKDF2_ITERATIONS): Promise<Buffer> {
    return pbkdf2Async(BASE_SECRET, salt, iterations, KEY_LENGTH, 'sha512');
}

export function encrypt(plainText: string): string {
    const salt = crypto.randomBytes(SALT_LENGTH); // unique per message
    const iv = crypto.randomBytes(IV_LENGTH);
    const key = deriveKeySync(salt, PBKDF2_ITERATIONS);

    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);
    const authTag = cipher.getAuthTag();

    // Format: salt:iv:authTag:encrypted:iterations (all base64, iterations as plain number)
    return [
        salt.toString('base64'),
        iv.toString('base64'),
        authTag.toString('base64'),
        encrypted.toString('base64'),
        String(PBKDF2_ITERATIONS),
    ].join(':');
}

export function decrypt(encryptedText: string): string {
    const parts = encryptedText.split(':');
    const [saltB64, ivB64, authTagB64, encryptedB64] = parts;
    const iterations = parts.length >= 5 ? parseInt(parts[4], 10) : LEGACY_PBKDF2_ITERATIONS;

    const salt = Buffer.from(saltB64, 'base64');
    const iv = Buffer.from(ivB64, 'base64');
    const authTag = Buffer.from(authTagB64, 'base64');
    const encrypted = Buffer.from(encryptedB64, 'base64');

    const key = deriveKeySync(salt, iterations);
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([decipher.update(encrypted), decipher.final()]);
    return decrypted.toString('utf8');
}

export async function decryptAsync(encryptedText: string): Promise<string> {
    const parts = encryptedText.split(':');
    const [saltB64, ivB64, authTagB64, encryptedB64] = parts;
    const iterations = parts.length >= 5 ? parseInt(parts[4], 10) : LEGACY_PBKDF2_ITERATIONS;

    const salt = Buffer.from(saltB64, 'base64');
    const iv = Buffer.from(ivB64, 'base64');
    const authTag = Buffer.from(authTagB64, 'base64');
    const encrypted = Buffer.from(encryptedB64, 'base64');

    const key = await deriveKeyAsync(salt, iterations);
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([decipher.update(encrypted), decipher.final()]);
    return decrypted.toString('utf8');
}

const DECRYPT_CONCURRENCY = parseInt(process.env.UV_THREADPOOL_SIZE || '4', 10);

export async function decryptBatch(encryptedTexts: string[]): Promise<string[]> {
    // Process in chunks matching thread pool size to avoid excessive queueing
    const results: string[] = new Array(encryptedTexts.length);
    for (let i = 0; i < encryptedTexts.length; i += DECRYPT_CONCURRENCY) {
        const chunk = encryptedTexts.slice(i, i + DECRYPT_CONCURRENCY);
        const decrypted = await Promise.all(chunk.map(decryptAsync));
        for (let j = 0; j < decrypted.length; j++) {
            results[i + j] = decrypted[j];
        }
    }
    return results;
}
