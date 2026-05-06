package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.CustomException;
import org.springframework.validation.annotation.Validated;

/**
 * Pagination details
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-04-02T17:49:59.877+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pagination {

    @JsonProperty("limit")
    @Valid
    private Integer limit;

    @JsonProperty("offSet")
    @JsonAlias("offset") // accept both offset and offSet for backward compatibility
    @Valid
    private Integer offSet;

    @JsonProperty("totalCount")
    @Valid
    private Integer totalCount;

    @JsonProperty("sortBy")
    private String sortBy;

    @JsonProperty("order")
    private OrderEnum order;

    /**
     * Sorting order
     */
    public enum OrderEnum {
    	
        ASC("ASC"),

        DESC("DESC");

        private String value;

        OrderEnum(String value) {
            this.value = value;
        }

        @JsonCreator
        public static OrderEnum fromValue(String text) {
            for (OrderEnum b : OrderEnum.values()) {
                if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                    return b;
                }
            }
			throw new CustomException("EG_EXPENSE_SEARCH_INVALID_ORDER",
					"The order value provided : " + text + " is wrong. it can only be ASC or DESC");
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

}
