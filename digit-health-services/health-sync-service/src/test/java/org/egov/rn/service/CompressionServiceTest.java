package org.egov.rn.service;


import org.digit.health.sync.service.compressionService.Compression;
import org.digit.health.sync.service.compressionService.compressor.GzipCompressor;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.zip.ZipException;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionServiceTest {

    @Test
    void throwExceptionWhenInputStreamIsNotGzip(){
        ClassLoader classLoader = getClass().getClassLoader();
        File badGzipfile = new File(classLoader.getResource("compressionTestFiles/bad_file.json.gz").getFile());
        File textFile = new File(classLoader.getResource("compressionTestFiles/testfile.txt").getFile());
        Compression compression = new Compression(new GzipCompressor());

        assertThrows(ZipException.class, () -> compression.decompress(new FileInputStream(badGzipfile)));
        assertThrows(ZipException.class, () -> compression.decompress(new FileInputStream(textFile)));
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
        Compression compression = new Compression(new GzipCompressor());

        BufferedReader decompressedFile = compression.decompress(new FileInputStream(gzipFile));
        BufferedReader originalFile = new BufferedReader(new InputStreamReader(new FileInputStream(original)));
        BufferedReader not_originalFile = new BufferedReader(new InputStreamReader(new FileInputStream(not_original)));

        assertEquals(convertBufferedReaderToString(decompressedFile), convertBufferedReaderToString(originalFile));
        assertNotEquals(convertBufferedReaderToString(decompressedFile), convertBufferedReaderToString(not_originalFile));
    }
}
