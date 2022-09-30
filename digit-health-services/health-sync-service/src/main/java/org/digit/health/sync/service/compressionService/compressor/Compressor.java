package org.digit.health.sync.service.compressionService.compressor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface Compressor {
    BufferedReader decompress(InputStream stream) throws IOException;
}
