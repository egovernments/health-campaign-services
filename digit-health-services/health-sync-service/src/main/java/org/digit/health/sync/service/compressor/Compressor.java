package org.digit.health.sync.service.compressor;

public interface Compressor {
    byte[] decompress(byte[] data);
}
