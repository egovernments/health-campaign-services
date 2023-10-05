package org.egov.transformer.utils;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Component
public class CommonUtils {

    private final TransformerProperties properties;

    public CommonUtils(TransformerProperties properties){
        this.properties = properties;
    }

    public String getTimeStampFromEpoch(long epochTime){
        String timeStamp = "";
        String timeZone = properties.getTimeZone();
        try {
            Date date = new Date(epochTime);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(java.util.TimeZone.getTimeZone(timeZone));
            timeStamp = dateFormat.format(date);
        }catch (Exception e){
            log.error("EpochTime to be transformed :"+epochTime);
            log.error("Exception while transforming epochTime to timestamp: ",e);
        }
        return timeStamp;
    }
}
