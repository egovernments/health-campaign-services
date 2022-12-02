package org.egov.project.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.egov.project.web.models.AdditionalFields;
import org.egov.project.web.models.Address;
import org.egov.project.web.models.AuditDetails;
import org.egov.project.web.models.Document;
import org.egov.project.web.models.Target;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* The purpose of this object to define the Project for a geography and period
*/
    @ApiModel(description = "The purpose of this object to define the Project for a geography and period")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project   {
        @JsonProperty("id")
    


    private String id = null;

        @JsonProperty("tenantId")
      @NotNull



    private String tenantId = null;

        @JsonProperty("projectTypeId")
      @NotNull



    private String projectTypeId = null;

        @JsonProperty("subProjectTypeId")
    


    private String subProjectTypeId = null;

        @JsonProperty("address")
    
  @Valid


    private Address address = null;

        @JsonProperty("startDate")
    


    private Long startDate = null;

        @JsonProperty("endDate")
    


    private Long endDate = null;

        @JsonProperty("isTaskEnabled")
    


    private Boolean isTaskEnabled = false;

        @JsonProperty("parent")
    

    @Size(min=2,max=64) 

    private String parent = null;

        @JsonProperty("targets")
    
  @Valid


    private List<Target> targets = null;

        @JsonProperty("department")
    

    @Size(min=2,max=64) 

    private String department = null;

        @JsonProperty("description")
    

    @Size(min=2) 

    private String description = null;

        @JsonProperty("referenceId")
    

    @Size(min=2,max=100) 

    private String referenceId = null;

        @JsonProperty("documents")
    
  @Valid


    private List<Document> documents = null;

        @JsonProperty("additionalFields")
    
  @Valid


    private AdditionalFields additionalFields = null;

        @JsonProperty("isDeleted")
    


    private Boolean isDeleted = null;

        @JsonProperty("rowVersion")
    


    private Integer rowVersion = null;

        @JsonProperty("auditDetails")
    
  @Valid


    private AuditDetails auditDetails = null;


        public Project addTargetsItem(Target targetsItem) {
            if (this.targets == null) {
            this.targets = new ArrayList<>();
            }
        this.targets.add(targetsItem);
        return this;
        }

        public Project addDocumentsItem(Document documentsItem) {
            if (this.documents == null) {
            this.documents = new ArrayList<>();
            }
        this.documents.add(documentsItem);
        return this;
        }

}

