package org.egov.transformer.utils;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
    public static int calculateAgeInMonthsFromDOB(Date dob) {
        // Create a Calendar instance and set it to the current date
        Calendar currentDate = Calendar.getInstance();

        // Create a Calendar instance and set it to the date of birth
        Calendar dobCalendar = Calendar.getInstance();
        dobCalendar.setTime(dob);

        // Calculate the difference in years and months
        int years = currentDate.get(Calendar.YEAR) - dobCalendar.get(Calendar.YEAR);
        int months = currentDate.get(Calendar.MONTH) - dobCalendar.get(Calendar.MONTH);

        // Adjust for cases where the birth date hasn't occurred yet in the current year
        if (months < 0) {
            years--;
            months += 12;
        }

        // Calculate the age in months
        int ageInMonths = years * 12 + months;

        return ageInMonths;
    }
}
