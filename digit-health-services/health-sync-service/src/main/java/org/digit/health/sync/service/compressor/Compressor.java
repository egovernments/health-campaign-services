package org.digit.health.sync.service.compressor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface Compressor {
    byte[] decompress(byte[] data) throws IOException;
}
