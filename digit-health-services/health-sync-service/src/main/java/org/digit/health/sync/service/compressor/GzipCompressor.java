package org.digit.health.sync.service.compressor;

import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;


@Component
public class GzipCompressor implements Compressor{
    @Override
    public byte[] decompress(byte[] data) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data));
        return IOUtils.toByteArray(new BufferedInputStream(gzipInputStream));
    }
}
