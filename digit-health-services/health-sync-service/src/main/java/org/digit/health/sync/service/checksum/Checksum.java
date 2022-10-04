package org.digit.health.sync.service.checksum;

import java.security.NoSuchAlgorithmException;

public interface Checksum {
    void validate(byte[] data, String checksum) throws NoSuchAlgorithmException;
}
