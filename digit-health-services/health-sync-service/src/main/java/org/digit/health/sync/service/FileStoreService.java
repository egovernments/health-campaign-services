package org.digit.health.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class FileStoreService {

    @Value("${egov.filestore.host}")
    private String fileStoreServiceHost;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    public FileStoreService(ServiceRequestRepository serviceRequestRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
    }

    public byte[] getFile(String fileStoreId, String tenantId) {
        UriComponents builder = UriComponentsBuilder.fromHttpUrl(fileStoreServiceHost + "/filestore/v1/files/id")
                .queryParam("tenantId", tenantId)
                .queryParam("fileStoreId", fileStoreId)
                .build();
        ResponseEntity<byte[]> file = (ResponseEntity<byte[]>) serviceRequestRepository.fetchResult(new StringBuilder(builder.toUriString()), byte[].class);
        return file.getBody();
    }

}
