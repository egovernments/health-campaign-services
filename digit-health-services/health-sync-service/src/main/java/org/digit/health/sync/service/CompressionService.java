package org.digit.health.sync.service;


import org.digit.health.sync.service.compressor.Compressor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

public class CompressionService {
    Compressor compressor;

    public CompressionService(Compressor compressor){
        this.compressor = compressor;
    }

    public BufferedReader decompress(InputStream stream) throws IOException {
        return compressor.decompress(stream);
    }
}
