import crypto from 'crypto';

// Define constants for encryption
const ALGORITHM = 'aes-256-cbc'; // Symmetric encryption algorithm
const KEY = crypto.randomBytes(32); // 256-bit key (32 bytes)

// Function to encrypt a password
export function encryptPassword(password: string): string {
    const iv = crypto.randomBytes(16); // Generate a unique IV for each encryption
    const cipher = crypto.createCipheriv(ALGORITHM, KEY, iv);
    let encrypted = cipher.update(password, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    // Combine the IV and encrypted data as a single string, separated by a delimiter
    return `${iv.toString('hex')}:${encrypted}`;
}

// Function to decrypt a password
export function decryptPassword(encryptedString: string): string {
    // Split the string to extract IV and encrypted data
    const [iv, encryptedData] = encryptedString.split(':');
    const decipher = crypto.createDecipheriv(ALGORITHM, KEY, Buffer.from(iv, 'hex'));
    let decrypted = decipher.update(encryptedData, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
}
