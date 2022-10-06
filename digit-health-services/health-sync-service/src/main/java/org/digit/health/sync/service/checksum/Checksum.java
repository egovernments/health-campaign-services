package org.digit.health.sync.service.checksum;

import java.security.NoSuchAlgorithmException;

public interface Checksum {
    boolean validate(byte[] data, String checksum) throws NoSuchAlgorithmException;
}
