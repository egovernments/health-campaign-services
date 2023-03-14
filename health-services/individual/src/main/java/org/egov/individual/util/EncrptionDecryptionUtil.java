package org.egov.individual.util;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Asymmetric encryption / decryption - Util class to fetch the encrypted / decrypted text
 *
 */
public class EncrptionDecryptionUtil {

    private static final String RSA = "RSA";

    /**
     * Encrypts the plainText using the publicKey and returns the cipherText
     * @param plainText
     * @param publicKey
     * @return
     * @throws Exception
     */
    public static String encrypt(String plainText, byte[] publicKey) throws  Exception {

        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKey);

        Cipher cipher = Cipher.getInstance(RSA);
        cipher.init(Cipher.ENCRYPT_MODE, keyFactory.generatePublic(publicKeySpec));
        byte[] encryptedMessageInBytes = cipher.doFinal(plainText.getBytes());
        //convert the encryptedMessageInBytes to string for readability / to store in DB
        String cipherText  = Base64.getEncoder().encodeToString(encryptedMessageInBytes);
        return cipherText;
    }

    /**
     * Decrypts the cipherText using the privateKey and returns the plainText
     * @param cipherText
     * @param privateKey
     * @return
     * @throws Exception
     */
    public static String decrypt(String cipherText, byte[] privateKey) throws Exception {

        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey);

        Cipher cipher = Cipher.getInstance(RSA);
        cipher.init(Cipher.DECRYPT_MODE, keyFactory.generatePrivate(keySpec));
        byte[] decryptedMessageBytes = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        String plainText = new String(decryptedMessageBytes);
        return plainText;
    }
}
