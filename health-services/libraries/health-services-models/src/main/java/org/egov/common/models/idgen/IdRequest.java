package org.egov.common.models.idgen;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * <h1>IdRequest</h1>
 * 
 * @author Narendra
 *
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class IdRequest {

	@Size(max = 200)
	@JsonProperty("idName")
	@NotNull
	private String idName;

	@NotNull
	@Size(max=200)
	@JsonProperty("tenantId")
	private String tenantId;

	@Size(max = 200)
	@JsonProperty("format")
	private String format;

	@JsonProperty("count")
	private Integer count;

}
