package org.digit.health.sync.service;


import org.digit.health.sync.service.compressor.Compressor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class CompressionService {

    private final Compressor compressor;

    @Autowired
    public CompressionService(Compressor compressor){
        this.compressor = compressor;
    }

    public byte [] decompress(byte[] data) throws IOException {
        return compressor.decompress(data);
    }
}
