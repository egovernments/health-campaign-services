package org.egov.workerregistry.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.encryption.EncryptionService;
import org.egov.tracer.model.CustomException;
import org.egov.workerregistry.config.WorkerRegistryConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class EncryptionDecryptionUtil {

    private final EncryptionService encryptionService;
    private final WorkerRegistryConfiguration config;

    @Autowired
    public EncryptionDecryptionUtil(EncryptionService encryptionService, WorkerRegistryConfiguration config) {
        this.encryptionService = encryptionService;
        this.config = config;
    }

    public <T> T encryptObject(Object objectToEncrypt, String key, Class<T> classType) {
        try {
            if (objectToEncrypt == null) {
                return null;
            }
            T encryptedObject = encryptionService.encryptJson(
                objectToEncrypt,
                key,
                config.getStateLevelTenantId(),
                classType
            );
            if (encryptedObject == null) {
                throw new CustomException("ENCRYPTION_NULL_ERROR", "Null object found on performing encryption");
            }
            return encryptedObject;
        } catch (IOException | HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            log.error("Error occurred while encrypting", e);
            throw new CustomException("ENCRYPTION_ERROR", "Error occurred in encryption process");
        } catch (Exception e) {
            log.error("Unknown Error occurred while encrypting", e);
            throw new CustomException("UNKNOWN_ERROR", "Unknown error occurred in encryption process");
        }
    }

    public <E, P> P decryptObject(Object objectToDecrypt, String key, Class<E> classType, RequestInfo requestInfo) {
        try {
            boolean objectToDecryptNotList = false;
            if (objectToDecrypt == null) {
                return null;
            } else if (requestInfo == null || requestInfo.getUserInfo() == null) {
                User userInfo = User.builder().uuid("no uuid").type("EMPLOYEE").build();
                requestInfo = RequestInfo.builder().userInfo(userInfo).build();
            }

            if (!(objectToDecrypt instanceof List)) {
                objectToDecryptNotList = true;
                objectToDecrypt = Collections.singletonList(objectToDecrypt);

            }

            final User enrichedUserInfo = getEnrichedAndCopiedUserInfo(requestInfo.getUserInfo());
            requestInfo.setUserInfo(enrichedUserInfo);

            String purpose = "search";

            P decryptedObject = (P) encryptionService.decryptJson(requestInfo, objectToDecrypt, key, purpose, classType);

            if (decryptedObject == null) {
                throw new CustomException("DECRYPTION_NULL_ERROR", "Null object found on performing decryption");
            }

            if (objectToDecryptNotList) {
                decryptedObject = (P) ((List<E>) decryptedObject).get(0);
            }
            return decryptedObject;
        } catch (IOException | HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            log.error("Error occurred while decrypting", e);
            throw new CustomException("DECRYPTION_SERVICE_ERROR", "Error occurred in decryption process");
        } catch (Exception e) {
            log.error("Unknown Error occurred while decrypting", e);
            throw new CustomException("UNKNOWN_ERROR", "Unknown error occurred in decryption process");
        }
    }

    private User getEnrichedAndCopiedUserInfo(User userInfo) {
        List<Role> newRoleList = new ArrayList<>();
        if (userInfo.getRoles() != null) {
            for (Role role : userInfo.getRoles()) {
                Role newRole = Role.builder()
                    .code(role.getCode())
                    .name(role.getName())
                    .id(role.getId())
                    .build();
                newRoleList.add(newRole);
            }
        }
        if (newRoleList.stream().noneMatch(role ->
                role.getCode() != null && userInfo.getType() != null
                && role.getCode().equalsIgnoreCase(userInfo.getType()))) {
            Role roleFromType = Role.builder()
                .code(userInfo.getType())
                .name(userInfo.getType())
                .build();
            newRoleList.add(roleFromType);
        }

        return User.builder()
            .id(userInfo.getId())
            .userName(userInfo.getUserName())
            .name(userInfo.getName())
            .type(userInfo.getType())
            .mobileNumber(userInfo.getMobileNumber())
            .emailId(userInfo.getEmailId())
            .roles(newRoleList)
            .tenantId(userInfo.getTenantId())
            .uuid(userInfo.getUuid())
            .build();
    }
}
