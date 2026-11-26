package org.egov.pgr.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.repository.IdGenRepository;
import org.egov.pgr.util.PGRUtils;
import org.egov.pgr.util.TenantAccessUtil;
import org.egov.pgr.web.models.AuditDetails;
import org.egov.pgr.web.models.Idgen.IdResponse;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.pgr.web.models.Service;
import org.egov.pgr.web.models.ServiceRequest;
import org.egov.pgr.web.models.Workflow;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.egov.pgr.util.PGRConstants.USERTYPE_CITIZEN;
import static org.egov.pgr.util.PGRConstants.USERTYPE_EMPLOYEE;
@org.springframework.stereotype.Service
public class EnrichmentService {


    private PGRUtils utils;

    private IdGenRepository idGenRepository;

    private PGRConfiguration config;

    private UserService userService;

    @Autowired
    public EnrichmentService(PGRUtils utils, IdGenRepository idGenRepository, PGRConfiguration config, UserService userService) {
        this.utils = utils;
        this.idGenRepository = idGenRepository;
        this.config = config;
        this.userService = userService;
    }


    /**
     * Enriches the create request with auditDetails. uuids and custom ids from idGen service
     * @param serviceRequest The create request
     */
    public void enrichCreateRequest(ServiceRequest serviceRequest){

        RequestInfo requestInfo = serviceRequest.getRequestInfo();
        Service service = serviceRequest.getService();
        Workflow workflow = serviceRequest.getWorkflow();
        String tenantId = service.getTenantId();
        // Enrich accountId of the logged in citizen or employee
        if(requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_CITIZEN)
                || requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_EMPLOYEE))
            serviceRequest.getService().setAccountId(requestInfo.getUserInfo().getUuid());

        userService.callUserService(serviceRequest);

        //AuditDetails auditDetails = utils.getAuditDetails(requestInfo.getUserInfo().getUuid(), service,true);

        //service.setAuditDetails(auditDetails);
        service.setId(UUID.randomUUID().toString());
        service.getAddress().setId(UUID.randomUUID().toString());
        service.getAddress().setTenantId(tenantId);
        service.setActive(true);

        if(workflow.getVerificationDocuments()!=null){
            workflow.getVerificationDocuments().forEach(document -> {
                document.setId(UUID.randomUUID().toString());
            });
        }

        if(StringUtils.isEmpty(service.getAccountId()))
            service.setAccountId(service.getUser().getUuid());

        List<String> customIds = getIdList(requestInfo,tenantId,config.getServiceRequestIdGenName(),config.getServiceRequestIdGenFormat(),1);

        service.setServiceRequestId(customIds.get(0));
    }


    /**
     * Enriches the update request (updates the lastModifiedTime in auditDetails0
     * @param serviceRequest The update request
     */
    public void enrichUpdateRequest(ServiceRequest serviceRequest){

        RequestInfo requestInfo = serviceRequest.getRequestInfo();
        Service service = serviceRequest.getService();
        AuditDetails auditDetails = utils.getAuditDetails(requestInfo.getUserInfo().getUuid(), service,false);

        service.setAuditDetails(auditDetails);

        userService.callUserService(serviceRequest);
    }

    /**
     * Enriches the search criteria in case of default search and enriches the userIds from mobileNumber in case of seach based on mobileNumber.
     * Also sets the default limit and offset if none is provided
     * @param requestInfo
     * @param criteria
     */
    public void enrichSearchRequest(RequestInfo requestInfo, RequestSearchCriteria criteria){

        if(criteria.isEmpty()
                && (requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_CITIZEN)
                || requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_EMPLOYEE))){
            String userMobileNumber = requestInfo.getUserInfo().getUserName();
            criteria.setMobileNumber(userMobileNumber);
        }

        criteria.setAccountId(requestInfo.getUserInfo().getUuid());

        restrictToOwnComplaints(requestInfo, criteria);

        String tenantId = (criteria.getTenantId()!=null) ? criteria.getTenantId() : requestInfo.getUserInfo().getTenantId();

        if(criteria.getMobileNumber()!=null){
            userService.enrichUserIds(tenantId, criteria);
        }

        if(criteria.getLimit()==null)
            criteria.setLimit(config.getDefaultLimit());

        if(criteria.getOffset()==null)
            criteria.setOffset(config.getDefaultOffset());

        if(criteria.getLimit()!=null && criteria.getLimit() > config.getMaxLimit())
            criteria.setLimit(config.getMaxLimit());

    }


    /**
     * Restricts the search to the complaints raised or created by the logged in user, unless the user has
     * one of the roles configured in pgr.search.all.complaints.roles (the complaint handlers).
     * The restriction is set by the server only, mobileNumber and userIds sent by the client can only narrow it further.
     * @param requestInfo
     * @param criteria
     */
    public void restrictToOwnComplaints(RequestInfo requestInfo, RequestSearchCriteria criteria){

        // Internal calls from other services have no user roles and are not restricted, same as the tenant check
        if(TenantAccessUtil.isInternalCall(requestInfo))
            return;

        List<Role> roles = requestInfo.getUserInfo().getRoles();
        String searchAllRolesStr = config.getSearchAllComplaintsRoles();

        if(StringUtils.hasText(searchAllRolesStr) && StringUtils.hasText(criteria.getTenantId())){
            Set<String> searchAllRoles = Arrays.stream(searchAllRolesStr.split(",")).map(String::trim).collect(Collectors.toSet());
            // A handler role counts only at the searched tenant or at its state level parent.
            // A search without a tenant is always restricted.
            if(roles.stream().anyMatch(role -> role != null && searchAllRoles.contains(role.getCode())
                    && TenantAccessUtil.roleCoversTenant(role, criteria.getTenantId())))
                return;
        }

        String userUuid = requestInfo.getUserInfo().getUuid();

        if(StringUtils.isEmpty(userUuid))
            throw new CustomException("INVALID_SEARCH","Search cannot be restricted as the logged in user has no uuid");

        criteria.restrictToUser(userUuid);
    }


    /**
     * Returns a list of numbers generated from idgen
     *
     * @param requestInfo RequestInfo from the request
     * @param tenantId    tenantId of the city
     * @param idKey       code of the field defined in application properties for which ids are generated for
     * @param idformat    format in which ids are to be generated
     * @param count       Number of ids to be generated
     * @return List of ids generated using idGen service
     */
    private List<String> getIdList(RequestInfo requestInfo, String tenantId, String idKey,
                                   String idformat, int count) {
        List<IdResponse> idResponses = idGenRepository.getId(requestInfo, tenantId, idKey, idformat, count).getIdResponses();

        if (CollectionUtils.isEmpty(idResponses))
            throw new CustomException("IDGEN ERROR", "No ids returned from idgen Service");

        return idResponses.stream()
                .map(IdResponse::getId).collect(Collectors.toList());
    }


}
