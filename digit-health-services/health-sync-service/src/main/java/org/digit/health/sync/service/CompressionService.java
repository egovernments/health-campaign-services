package org.digit.health.sync.service;


import org.digit.health.sync.service.compressor.Compressor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

@Service
public class CompressionService {
    @Autowired
    private final Compressor compressor;

    public CompressionService(Compressor compressor){
        this.compressor = compressor;
    }

    public BufferedReader decompress(InputStream stream) throws IOException {
        return compressor.decompress(stream);
    }
}
