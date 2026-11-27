package org.egov.workerregistry.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.workerregistry.util.EncryptionDecryptionUtil;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkerEncryptionService {

    private final EncryptionDecryptionUtil encryptionDecryptionUtil;

    @Autowired
    public WorkerEncryptionService(EncryptionDecryptionUtil encryptionDecryptionUtil) {
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
    }

    public List<Worker> encrypt(List<Worker> entities, String key) {
        return (List<Worker>) encryptionDecryptionUtil
                .encryptObject(entities, key, Worker.class);
    }

    public WorkerSearch encrypt(WorkerSearch search, String key) {
        return (WorkerSearch) encryptionDecryptionUtil
                .encryptObject(search, key, WorkerSearch.class);
    }

    public List<Worker> decrypt(List<Worker> entities, String key, RequestInfo requestInfo) {
        List<Worker> encryptedEntities = entities.stream()
                .filter(e -> isCipherText(e.getName()))
                .collect(Collectors.toList());

        if (encryptedEntities.isEmpty()) {
            return entities;
        }

        List<Worker> decryptedEntities = (List<Worker>) encryptionDecryptionUtil
                .decryptObject(encryptedEntities, key, Worker.class, requestInfo);

        if (entities.size() > decryptedEntities.size()) {
            List<String> decryptedIds = decryptedEntities.stream()
                    .map(Worker::getId)
                    .collect(Collectors.toList());
            for (Worker entity : entities) {
                if (!decryptedIds.contains(entity.getId())) {
                    decryptedEntities.add(entity);
                }
            }
        }
        return decryptedEntities;
    }

    private boolean isCipherText(String text) {
        if (StringUtils.isNotBlank(text) && text.contains("|")) {
            String base64Data = text.split("\\|")[1];
            return StringUtils.isNotBlank(base64Data)
                    && (base64Data.length() % 4 == 0 || base64Data.endsWith("="));
        }
        return false;
    }
}
