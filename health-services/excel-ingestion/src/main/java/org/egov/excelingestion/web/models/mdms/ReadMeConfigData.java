package org.egov.excelingestion.web.models.mdms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * MDMS "HCM-ADMIN-CONSOLE.ReadMeConfig" entry: the instruction blocks rendered into the README sheet
 * for one resource type (user / facility / boundary / attendanceRegister).
 *
 * <p>Mirrors the shape project-factory already consumes, so a single MDMS master feeds both flows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadMeConfigData {

    /** Resource type this config belongs to, e.g. "user", "facility", "boundary". */
    @JsonProperty("type")
    private String type;

    @JsonProperty("texts")
    private List<ReadMeText> texts;

    public List<ReadMeText> getTexts() {
        return texts != null ? texts : Collections.emptyList();
    }

    /** One instruction block: a header line followed by its description lines. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadMeText {

        /** Localization key for the block header. */
        @JsonProperty("header")
        private String header;

        /** Whether the header line is rendered bold. */
        @JsonProperty("isHeaderBold")
        private Boolean isHeaderBold;

        /** Only blocks with inSheet=true are rendered; the rest are UI-only copy. */
        @JsonProperty("inSheet")
        private Boolean inSheet;

        @JsonProperty("descriptions")
        private List<ReadMeDescription> descriptions;

        public boolean isInSheet() {
            return Boolean.TRUE.equals(inSheet);
        }

        public boolean isBoldHeader() {
            return Boolean.TRUE.equals(isHeaderBold);
        }

        public List<ReadMeDescription> getDescriptions() {
            return descriptions != null ? descriptions : Collections.emptyList();
        }
    }

    /** A single description line; {@code text} is a localization key. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadMeDescription {

        @JsonProperty("text")
        private String text;
    }
}
