package org.digit.health.sync.service.checksum;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class MD5Checksum implements Checksum {

    @Override
    public void validate(byte[] data, String checksum) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            byte[] digest = md.digest();
            String generatedChecksum = DatatypeConverter.printHexBinary(digest);
            if (!generatedChecksum.equalsIgnoreCase(checksum)) {
                throw new CustomException("INVALID_CHECKSUM", "Checksum did not match");
            }
        } catch (NoSuchAlgorithmException exception) {
            log.error("NoSuchAlgorithmException", exception.getMessage());
        }
    }
}
