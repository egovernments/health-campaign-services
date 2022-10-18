package org.digit.health.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Service
@Slf4j
public class FileStoreService {

    @Value("${egov.filestore.host}")
    private String fileStoreServiceHost;

    @Value("${egov.filestore.get.endpoint}")
    private String fileStoreGetEndpoint;

    private final ServiceRequestRepository serviceRequestRepository;

    @Autowired
    public FileStoreService(ServiceRequestRepository serviceRequestRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
    }

    public byte[] getFile(String fileStoreId, String tenantId) {
        log.info("Fetching file with id {} and tenantId {}", fileStoreId, tenantId);
        UriComponents builder = UriComponentsBuilder.fromHttpUrl(fileStoreServiceHost + fileStoreGetEndpoint)
                .queryParam("tenantId", tenantId)
                .queryParam("fileStoreId", fileStoreId)
                .build();
        ResponseEntity<byte[]> file = (ResponseEntity<byte[]>) serviceRequestRepository.fetchResult(
                                                                    new StringBuilder(builder.toUriString()),
                                                                    byte[].class
                                                            );
        Optional.ofNullable(file).ifPresent(f -> log.info("Fetched file with id {} and tenantId {}",
                fileStoreId, tenantId));
        return file.getBody();
    }

}
