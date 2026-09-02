import crypto from 'crypto';
import config from '../config';

const BASE_SECRET: any = config.basesecret;
const ALGORITHM = 'aes-256-cbc';
const IV_LENGTH = 16; // AES block size

// Create a fixed-length key from BASE_SECRET
const KEY = crypto.createHash('sha256').update(BASE_SECRET).digest();

export function encrypt(plainText: string): string {
    const iv = crypto.randomBytes(IV_LENGTH);
    const cipher = crypto.createCipheriv(ALGORITHM, KEY as Uint8Array, iv as Uint8Array);
    
    const encrypted = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()] as Uint8Array[]);

    // Format: iv:encrypted (both base64) — decrypt() relies on this shape.
    return iv.toString('base64') + ':' + encrypted.toString('base64');
}

export function decrypt(encryptedText: string): string {
    // Defensive: only values in encrypt()'s "ivB64:cipherB64" format are decryptable.
    // Adopted/existing-user rows carry a plaintext username and no generated password, so
    // decrypt() is called with a non-encrypted or undefined value — return it as-is instead
    // of crashing on Buffer.from(undefined) (which previously failed credential-sheet generation).
    if (!encryptedText || !encryptedText.includes(':')) {
        return encryptedText ?? '';
    }
    const [ivB64, encryptedB64] = encryptedText.split(':');

    const iv = Buffer.from(ivB64, 'base64');
    const encrypted = Buffer.from(encryptedB64, 'base64');
    
    const decipher = crypto.createDecipheriv(ALGORITHM, KEY as Uint8Array, iv as Uint8Array);
    const decrypted = Buffer.concat([decipher.update(encrypted as Uint8Array), decipher.final()] as Uint8Array[] );
    
    return decrypted.toString('utf8');
}

/** Decrypt many values in one call; capped at 500 to bound per-request work. */
export function bulkDecrypt(encryptedTexts: string[]): string[] {
    if (encryptedTexts.length > 500) {
        throw new Error('Cannot decrypt more than 500 strings at once');
    }
    
    return encryptedTexts.map(encryptedText => {
        try {
            return decrypt(encryptedText);
        } catch (error : any) {
            throw new Error(`Failed to decrypt string: ${error.message}`);
        }
    });
}
