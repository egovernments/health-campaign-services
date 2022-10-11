package org.digit.health.sync.service;


import org.apache.commons.io.IOUtils;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressionServiceTest {

    @Test
    @DisplayName("throw exception when input stream is not gzip")
    void throwExceptionWhenInputStreamIsNotGzip(){
        ClassLoader classLoader = getClass().getClassLoader();
        File badGzipfile = new File(Objects.requireNonNull(classLoader
                .getResource("compressionTestFiles/bad_file.json.gz")).getFile());
        File textFile = new File(Objects.requireNonNull(classLoader
                .getResource("compressionTestFiles/testfile.txt")).getFile());
        CompressionService compressionService = new CompressionService(new GzipCompressor());

        assertThrows(CustomException.class, () -> compressionService.decompress(IOUtils
                .toByteArray(Files.newInputStream(badGzipfile.toPath()))));
        assertThrows(CustomException.class, () -> compressionService.decompress(IOUtils
                .toByteArray(Files.newInputStream(textFile.toPath()))));
    }

    String convertByteArrayToString(byte[] br) throws IOException{
        return IOUtils.toString(br, String.valueOf(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("check that the file after decompression is same as file before compression")
    void checkIfDecompressedGzipFileIsSimilarToOriginalFile() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(Objects.requireNonNull(classLoader
                .getResource("compressionTestFiles/original.json.gz")).getFile());
        File original = new File(Objects.requireNonNull(classLoader
                .getResource("compressionTestFiles/original.json")).getFile());
        File not_original = new File(Objects.requireNonNull(classLoader
                .getResource("compressionTestFiles/not_original.json")).getFile());
        CompressionService compression = new CompressionService(new GzipCompressor());

        byte[] decompressedFile = compression.decompress(IOUtils
                .toByteArray(Files.newInputStream(gzipFile.toPath())));
        byte[] originalFile =   IOUtils
                .toByteArray(Files.newInputStream(original.toPath()));
        byte[] notOriginalFile =  IOUtils
                .toByteArray(Files.newInputStream(not_original.toPath()));

        assertEquals(convertByteArrayToString(decompressedFile), convertByteArrayToString(originalFile));
        assertNotEquals(convertByteArrayToString(decompressedFile), convertByteArrayToString(notOriginalFile));
    }
}