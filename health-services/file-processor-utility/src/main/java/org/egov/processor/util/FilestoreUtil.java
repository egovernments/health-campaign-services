package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import static org.egov.processor.config.ServiceConstants.FILESTORE_ID_REPLACER;
import static org.egov.processor.config.ServiceConstants.TENANTID_REPLACER;

@Component
@Slf4j
public class FilestoreUtil {

    private Configuration config;

    private ServiceRequestRepository serviceRequestRepository;

    private ObjectMapper mapper;

    public FilestoreUtil(Configuration config, ServiceRequestRepository serviceRequestRepository, ObjectMapper mapper) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
        this.mapper = mapper;
    }

    //https://editor.swagger.io/filestore/v1/files/id?tenantId=mz&fileStoreId=a8d2607b-0f5a-45f0-a9e7-5a95cb98a992
    public byte[] getFile(String tenantId, String fileStoreId) {
        String fileStoreServiceLink = config.getFileStoreHost() + config.getFileStoreEndpoint();
        fileStoreServiceLink = fileStoreServiceLink.replace(TENANTID_REPLACER, tenantId);
        fileStoreServiceLink = fileStoreServiceLink.replace(FILESTORE_ID_REPLACER, fileStoreId);
        byte[] responseInByteArray = null;
        Object response = null;
        try {
            response = serviceRequestRepository.fetchResultWithGET(new StringBuilder(fileStoreServiceLink));
            responseInByteArray = (byte[]) response;
        } catch (Exception ex) {
            log.error("File store id response error!!", ex);
            throw new CustomException("FILESTORE_EXCEPTION", "File Store response can not parsed!!!");
        }
        return responseInByteArray;
    }
}
