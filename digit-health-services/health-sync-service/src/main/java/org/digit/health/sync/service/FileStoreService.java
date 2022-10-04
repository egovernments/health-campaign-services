package org.digit.health.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;

@Service
@Slf4j
public class FileStoreService {

    @Value("${egov.filestore.host}")
    private String fileStoreServiceHost;

    private final RestTemplate restTemplate;

    @Autowired
    public FileStoreService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public byte[] getFile(String fileStoreId, String tenantId) {
        UriComponents builder = UriComponentsBuilder.fromHttpUrl(fileStoreServiceHost + "/filestore/v1/files/id")
                .queryParam("tenantId", tenantId)
                .queryParam("fileStoreId", fileStoreId)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> file = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, byte[].class);
        return file.getBody();
    }

}
