package org.digit.health.sync.service.checksum;

import java.security.NoSuchAlgorithmException;

public interface ChecksumValidator {
    boolean validate(byte[] data, String checksum) throws NoSuchAlgorithmException;
}
