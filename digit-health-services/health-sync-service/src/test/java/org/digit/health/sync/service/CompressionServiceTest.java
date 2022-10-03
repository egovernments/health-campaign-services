package org.digit.health.sync.service;


import org.digit.health.sync.service.compressor.GzipCompressor;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.ZipException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CompressionServiceTest {

    @Test
    void throwExceptionWhenInputStreamIsNotGzip(){
        ClassLoader classLoader = getClass().getClassLoader();
        File badGzipfile = new File(classLoader.getResource("compressionTestFiles/bad_file.json.gz").getFile());
        File textFile = new File(classLoader.getResource("compressionTestFiles/testfile.txt").getFile());
        CompressionService compressionService = new CompressionService(new GzipCompressor());

        assertThrows(ZipException.class, () -> compressionService.decompress(new FileInputStream(badGzipfile)));
        assertThrows(ZipException.class, () -> compressionService.decompress(new FileInputStream(textFile)));
    }

    String convertBufferedReaderToString(BufferedReader br) throws IOException{
        String response = new String();
        for (String line; (line = br.readLine()) != null; response += line);
        return response;
    }

    @Test
    void checkIfDecompressedGzipFileIsSimilarToOriginalFile() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(classLoader.getResource("compressionTestFiles/original.json.gz").getFile());
        File original = new File(classLoader.getResource("compressionTestFiles/original.json").getFile());
        File not_original = new File(classLoader.getResource("compressionTestFiles/not_original.json").getFile());
        CompressionService compression = new CompressionService(new GzipCompressor());

        BufferedReader decompressedFile = compression.decompress(new FileInputStream(gzipFile));
        BufferedReader originalFile = new BufferedReader(new InputStreamReader(new FileInputStream(original)));
        BufferedReader not_originalFile = new BufferedReader(new InputStreamReader(new FileInputStream(not_original)));

        assertEquals(convertBufferedReaderToString(decompressedFile), convertBufferedReaderToString(originalFile));
        assertNotEquals(convertBufferedReaderToString(decompressedFile), convertBufferedReaderToString(not_originalFile));
    }
}
