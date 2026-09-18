package org.egov.excelingestion.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Applies Apache POI global safety limits once at startup so that very large
 * (but legitimate) .xlsx files are not rejected by POI's built-in zip-bomb /
 * byte-array protections.
 *
 * These are static, JVM-wide POI settings, so they are set a single time during
 * bean initialization — before any workbook is opened (download or generation).
 */
@Configuration
@Slf4j
public class PoiConfig {

    /** Max byte-array POI will allocate for a single record. POI default is 100MB. */
    @Value("${poi.byte.array.max.bytes:300000000}")
    private int byteArrayMaxOverride;

    /** Minimum compressed/uncompressed ratio POI allows before treating a zip as a bomb. POI default is 0.01. */
    @Value("${poi.zip.min.inflate.ratio:0.001}")
    private double minInflateRatio;

    @PostConstruct
    public void configurePoiLimits() {
        IOUtils.setByteArrayMaxOverride(byteArrayMaxOverride);
        ZipSecureFile.setMinInflateRatio(minInflateRatio);
        log.info("POI limits configured: byteArrayMaxOverride={} bytes, minInflateRatio={}",
                byteArrayMaxOverride, minInflateRatio);
    }
}
