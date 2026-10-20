package org.egov.common.models.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;


/**
 * Pagination details
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pagination {
    @JsonProperty("limit")
    @Valid
    private Integer limit = 10;

    @JsonProperty("offset")
    @Valid
    private Integer offset = 0;

    @JsonProperty("totalCount")
    @Valid
    private Long totalCount = null;

    @JsonProperty("sortBy")
    private String sortBy = null;

    /**
     * Sorting order
     */
    public enum OrderEnum {
        ASC("asc"),

        DESC("desc");

        private String value;

        OrderEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static OrderEnum fromValue(String text) {
            for (OrderEnum b : OrderEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    @JsonProperty("order")
    private OrderEnum order = null;


}
