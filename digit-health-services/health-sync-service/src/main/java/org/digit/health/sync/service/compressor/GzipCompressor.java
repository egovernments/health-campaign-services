package org.digit.health.sync.service.compressor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;


@Slf4j
@Component
public class GzipCompressor implements Compressor{
    @Override
    public byte[] decompress(byte[] data) {
        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data));
            log.info("Decompressing file");
            return IOUtils.toByteArray(new BufferedInputStream(gzipInputStream));
        } catch (IOException exception) {
            log.error("Could not decompress file", exception);
            throw new CustomException(SyncErrorCode.ERROR_IN_DECOMPRESSION.name(),
                    SyncErrorCode.ERROR_IN_DECOMPRESSION.message());
        }
    }
}
