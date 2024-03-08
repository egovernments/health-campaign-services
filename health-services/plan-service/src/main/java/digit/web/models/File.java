package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    private String filestoreId = null;

    @JsonProperty("inputFileType")
    @NotNull
    @Size(min = 2, max = 64)
    private InputFileTypeEnum inputFileType = null;

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
            for (InputFileTypeEnum b : InputFileTypeEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

}
