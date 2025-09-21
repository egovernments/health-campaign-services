import crypto from 'crypto';
import config from '../config';

const BASE_SECRET: any = config.basesecret;
const ALGORITHM = 'aes-256-cbc';
const IV_LENGTH = 16; // AES block size

// Create a fixed-length key from BASE_SECRET
const KEY = crypto.createHash('sha256').update(BASE_SECRET).digest();

export function encrypt(plainText: string): string {
    const iv = crypto.randomBytes(IV_LENGTH);
    const cipher = crypto.createCipheriv(ALGORITHM, KEY, iv);
    
    const encrypted = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);
    
    // Format: iv:encrypted (both base64)
    return iv.toString('base64') + ':' + encrypted.toString('base64');
}

export function decrypt(encryptedText: string): string {
    const [ivB64, encryptedB64] = encryptedText.split(':');
    
    const iv = Buffer.from(ivB64, 'base64');
    const encrypted = Buffer.from(encryptedB64, 'base64');
    
    const decipher = crypto.createDecipheriv(ALGORITHM, KEY, iv);
    const decrypted = Buffer.concat([decipher.update(encrypted), decipher.final()]);
    
    return decrypted.toString('utf8');
}

// Bulk decrypt function for performance - max 500 strings
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
