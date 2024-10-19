package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

import java.util.Arrays;

/**
 * File
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class File {
    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("filestoreId")
    @NotNull
    @Size(min = 1, max = 128)
    @Pattern(regexp = "^(?!\\p{Punct}+$).*$", message = "Filestore Id must not contain only special characters")
    private String filestoreId = null;

    @JsonProperty("inputFileType")
    @NotNull
    private InputFileTypeEnum inputFileType = null;

    @JsonProperty("templateIdentifier")
    @NotNull
    @Size(min = 2, max = 128)
    @Pattern(regexp = "^(?!\\p{Punct}+$).*$", message = "Name must not contain only special characters")
    private String templateIdentifier = null;

    @JsonProperty("active")
    @NotNull
    private Boolean active = true;

    /**
     * The original file type of the Input
     */
    public enum InputFileTypeEnum {
        EXCEL("Excel"),

        SHAPEFILE("Shapefile"),

        GEOJSON("GeoJSON");

        private String value;

        InputFileTypeEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static InputFileTypeEnum fromValue(String text) {
            return Arrays.stream(InputFileTypeEnum.values())
                    .filter(b -> String.valueOf(b.value).equals(text))
                    .findFirst()
                    .orElse(null); // Return null if no matching enum value is found
        }
    }

}
