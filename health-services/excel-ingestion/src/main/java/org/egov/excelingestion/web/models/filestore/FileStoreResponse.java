package org.egov.excelingestion.web.models.filestore;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileStoreResponse {

    @JsonProperty("fileStoreIds")
    private List<FileInfo> files;
    
    // Handle dynamic file ID to URL mappings (e.g., "eacd9e96-54f9-4317-a974-973db01262eb": "https://...")
    @Builder.Default
    private Map<String, String> fileIdToUrlMap = new HashMap<>();
    
    @JsonAnyGetter
    public Map<String, String> getFileIdToUrlMap() {
        return fileIdToUrlMap;
    }
    
    @JsonAnySetter
    public void setFileIdUrl(String fileId, String url) {
        if (fileIdToUrlMap == null) {
            fileIdToUrlMap = new HashMap<>();
        }
        // Only add if it's not a known property and looks like a UUID
        if (!"fileStoreIds".equals(fileId) && fileId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            fileIdToUrlMap.put(fileId, url);
        }
    }
}