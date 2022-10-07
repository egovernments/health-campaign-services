package org.digit.health.sync.service.checksum;

public interface ChecksumValidator {
    boolean validate(byte[] data, String checksum);
}
