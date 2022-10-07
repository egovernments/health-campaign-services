package org.digit.health.sync.service.checksum;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Md5ChecksumValidatorTest {

    @Test
    @DisplayName("should validate the byte data and checksum")
    void shouldValidateByteDataAndChecksum() throws IOException, NoSuchAlgorithmException {
        String validChecksum = "2cad20c19a8eb9bb11a9f76527aec9bc";
        byte[] bytes = getFileData();
        assertTrue(new Md5ChecksumValidator().validate(bytes, validChecksum));
    }

    @Test
    @DisplayName("should throw custom exception when checksum validation fails")
    void throwExceptionWhenChecksumValidationFails() throws IOException, NoSuchAlgorithmException {
        String validChecksum = "2cad20c19a8eb9bb11a9f76527aec";
        byte[] bytes = getFileData();
        assertThrows(CustomException.class, () -> new Md5ChecksumValidator()
                .validate(bytes, validChecksum));
    }

    private byte[] getFileData() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(Objects.requireNonNull(classLoader
                .getResource("compressionTestFiles/testfile.txt")).getFile());
        return Files.readAllBytes(gzipFile.toPath());
    }

}