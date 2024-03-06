package digit.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
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
    private UUID id = null;

    @JsonProperty("filestoreId")
    @NotNull
    @Size(min = 1, max = 32)
    private String filestoreId = null;

    @JsonProperty("inputFileType")
    @NotNull
    private InputFileTypeEnum inputFileType = null;

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;


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
