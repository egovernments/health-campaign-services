package org.egov.fileProcessor.planProcessor;
import org.egov.fileProcessor.apiClient.planAPIClient;
import org.egov.fileProcessor.apiResponses.PlanConfigurationResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class microplanProcessor
{
    private String planId;
    private PlanConfigurationResponse planConfig;

    public microplanProcessor(String planId) {
        this.planId = planId;
    }

    public void GetInfo(){
        if(this.planId!=null) {
            Map<String, Object> response =  planAPIClient.planSearchAPI(this.planId);
            Object planConfiguration = response.get("PlanConfiguration");
            LinkedHashMap<String, Object> planConfigurationInfo = (LinkedHashMap<String, Object>) planConfiguration;
            ArrayList files = (ArrayList) planConfigurationInfo.get("files");

            for (Object file :files){
                LinkedHashMap<String, String> fileMap = (LinkedHashMap<String, String>) file;
                System.out.println("Filestore ID: " + fileMap.get("filestoreId"));
                System.out.println("Input file type: " + fileMap.get("inputFileType"));
                System.out.println("Template identifier: " + fileMap.get("templateIdentifier"));
            }

           //TODO: Parse the JSON correctly
          /*  List<File> files = List.of(planConfiguration.getFiles());
            for (File file : files) {
                System.out.println("Filestore ID: " + file.getFilestoreId());
                System.out.println("Input file type: " + file.getInputFileType());
                System.out.println("Template identifier: " + file.getTemplateIdentifier());
            }*/
        }


    }
}
