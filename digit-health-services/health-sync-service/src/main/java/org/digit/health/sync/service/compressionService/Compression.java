package org.digit.health.sync.service.compressionService;

import org.digit.health.sync.service.compressionService.compressor.Compressor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

public class Compression {
    Compressor compressor;

    public Compression(Compressor compressor){
        this.compressor = compressor;
    }

    public BufferedReader decompress(InputStream stream) throws IOException {
        return compressor.decompress(stream);
    }
}
