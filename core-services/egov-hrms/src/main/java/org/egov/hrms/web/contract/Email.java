package org.egov.hrms.web.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Set;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Email {

    @NotNull
    @Size(min = 1, message = "At least one recipient is required")
    private Set<String> emailTo;

    @NotNull(message = "Subject is required")
    private String subject;

    @NotNull(message = "Body is required")
    private String body;
    @JsonProperty("isHTML")
    private boolean isHTML;

}
