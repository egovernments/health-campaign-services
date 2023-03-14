package org.egov.individual.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Asymmetric encryption - Generates the publicKey and privateKey for encryption and decryption respectively
 */
public class EncryptionKeyGenerator {

    private static final String RSA = "RSA";
    private static KeyPair keyPair;

    /**
     * Generates the publicKey and privateKey for encryption and decryption respectively
     * @return
     * @throws Exception
     */
    private static KeyPair generateKeyPair() throws Exception{

        SecureRandom secureRandom = new SecureRandom();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA);
        keyPairGenerator.initialize(1024, secureRandom);

        keyPair =  keyPairGenerator.generateKeyPair();
        return keyPair;

    }

    public static String getPublicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public static String getPrivateKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

}
