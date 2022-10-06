package org.digit.health.sync.service.checksum;

import org.digit.health.sync.service.CompressionService;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.*;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;


import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
class MD5ChecksumTest {

    @Test
    @DisplayName("should validate the byte data and checksum")
    void shouldValidateByteDataAndChecksum() throws IOException, NoSuchAlgorithmException {
        String validChecksum = "2cad20c19a8eb9bb11a9f76527aec9bc";
        byte[] bytes = getFileData("compressionTestFiles/testfile.txt");
        assertEquals(new MD5Checksum().validate(bytes,validChecksum),true);
    }

    @Test
    @DisplayName("should throw custom exception when checksum validation fails")
    void throwExceptionWhenChecksumValidationFails() throws IOException, NoSuchAlgorithmException {
        String validChecksum = "2cad20c19a8eb9bb11a9f76527aec";
        byte[] bytes = getFileData("compressionTestFiles/testfile.txt");
        assertThrows(CustomException.class, () -> new MD5Checksum().validate(bytes,validChecksum));
    }

    private byte[] getFileData(String file) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(classLoader.getResource(file).getFile());
        byte[] bytes = Files.readAllBytes(gzipFile.toPath());
        return bytes;
    }

}