package org.egov.common.helpers;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherObject {
    @NotNull
    private String someOtherField;
    @Builder.Default
    private Boolean hasErrors = false;
}
