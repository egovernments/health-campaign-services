package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
* IndividualRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {
    @JsonProperty("ResponseInfo")
    @NotNull

    @Valid


    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

        @JsonProperty("Address")
      @NotNull

  @Valid

    @Size(min=1) 

    private List<Address> address = new ArrayList<>();


        public AddressResponse addAddressItem(Address addressItem) {
        this.address.add(addressItem);
        return this;
        }

}

