package org.digit.health.sync.service.compressor;

import org.springframework.stereotype.Component;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;


@Component
public class GzipCompressor implements Compressor{
    @Override
    public BufferedReader decompress(InputStream stream) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(stream);
        BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInputStream));
        return reader;
    }
}
