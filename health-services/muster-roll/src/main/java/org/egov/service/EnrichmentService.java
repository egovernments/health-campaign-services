package org.egov.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.idgen.IdResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.repository.IdGenRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.MusterRollServiceUtil;
import org.egov.web.models.*;
import org.egov.works.services.common.models.expense.Pagination;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.egov.util.MusterRollServiceConstants.ACTION_REJECT;

@Service
@Slf4j
public class EnrichmentService {

    private final MusterRollServiceUtil musterRollServiceUtil;

    private final MusterRollServiceConfiguration config;

    private final IdGenRepository idGenRepository;

    private final ObjectMapper mapper;

    @Autowired
    public EnrichmentService(MusterRollServiceUtil musterRollServiceUtil, MusterRollServiceConfiguration config, IdGenRepository idGenRepository, ObjectMapper mapper) {
        this.musterRollServiceUtil = musterRollServiceUtil;
        this.config = config;
        this.idGenRepository = idGenRepository;
        this.mapper = mapper;
    }


    /**
     * Enrich muster roll on estimate
     * @param musterRollRequest
     *
     */
    public void enrichMusterRollOnEstimate(MusterRollRequest musterRollRequest) {
        log.info("EnrichmentService::enrichMusterRollOnEstimate");

        RequestInfo requestInfo = musterRollRequest.getRequestInfo();
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();

        //Audit details
        AuditDetails auditDetails = musterRollServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), musterRoll, true);
        musterRoll.setAuditDetails(auditDetails);

        //status
        musterRoll.setStatus(Status.ACTIVE);

        //contract service code
        musterRoll.setServiceCode(config.getContractServiceCode());

    }

    /**
     * Enrich muster roll on create
     * @param musterRollRequest
     *
     */
    public void enrichMusterRollOnCreate(MusterRollRequest musterRollRequest) {
        log.info("EnrichmentService::enrichMusterRollOnCreate");

        RequestInfo requestInfo = musterRollRequest.getRequestInfo();
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();

        //Id
        musterRoll.setId(UUID.randomUUID().toString());

        //Audit details
        AuditDetails auditDetails = musterRollServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), musterRoll, true);
        musterRoll.setAuditDetails(auditDetails);

        //musterRollNumber - Idgen
        String rootTenantId = musterRoll.getTenantId();
        List<String> musterNumbers = getIdList(requestInfo, rootTenantId
                , config.getIdgenMusterRollNumberName(), "", 1); //idformat will be fetched by idGen service
        if (musterNumbers != null && !musterNumbers.isEmpty()) {
            String musterRollNumber = musterNumbers.get(0);
            musterRoll.setMusterRollNumber(musterRollNumber);
        } else {
            throw new CustomException("MUSTER_NUMBER_NOT_GENERATED","Error occurred while generating muster roll numbers from IdGen service");
        }

        //status
        musterRoll.setStatus(Status.ACTIVE);

        //contract service code
        musterRoll.setServiceCode(config.getContractServiceCode());

    }

    /**
     * Enrich the muster roll on update
     * @param musterRollRequest
     */
    public void enrichMusterRollOnUpdate(MusterRollRequest musterRollRequest, MusterRoll existingMusterRoll, Object mdmsData) {
        log.info("EnrichmentService::enrichMusterRollOnUpdate");

        RequestInfo requestInfo = musterRollRequest.getRequestInfo();
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();
        Workflow workflow = musterRollRequest.getWorkflow();

        log.info("EnrichmentService::enrichMusterRollOnUpdate::Workflow action is "+workflow.getAction());


            //update totalAttendance and skill details if modified
            List<IndividualEntry> individualEntries = existingMusterRoll.getIndividualEntries();
            List<IndividualEntry> modifiedIndividualEntries = musterRoll.getIndividualEntries();
            if (!CollectionUtils.isEmpty(modifiedIndividualEntries)) {
                for (IndividualEntry individualEntry : individualEntries) {
                    for (IndividualEntry modifiedIndividualEntry : modifiedIndividualEntries)  {
                        if (modifiedIndividualEntry.getId().equalsIgnoreCase(individualEntry.getId())) {
                            //update the total attendance
                            if (modifiedIndividualEntry.getModifiedTotalAttendance() != null) {
                                individualEntry.setModifiedTotalAttendance(modifiedIndividualEntry.getModifiedTotalAttendance());
                            }
                            if (modifiedIndividualEntry.getAdditionalDetails() != null) {
                                try {
                                    JsonNode node = mapper.readTree(mapper.writeValueAsString(modifiedIndividualEntry.getAdditionalDetails()));
                                    if (node.findValue("code") != null  && StringUtils.isNotBlank(node.findValue("code").textValue())) {
                                        String skillCode = node.findValue("code").textValue();
                                        //Update the skill value based on the code from request
                                        musterRollServiceUtil.updateAdditionalDetails(mdmsData,individualEntry,skillCode);
                                    }
                                } catch (IOException e) {
                                    log.info("EnrichmentService::enrichMusterRollOnUpdate::Failed to parse additionalDetail object from request"+e);
                                    throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object from request on update");
                                }

                            }
                            break;
                        }
                    }
                }
            }

        musterRoll = existingMusterRoll;
        musterRollRequest.setMusterRoll(existingMusterRoll);

        //AuditDetails
        musterRoll.setAuditDetails(existingMusterRoll.getAuditDetails());
        AuditDetails auditDetails = musterRollServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), musterRoll, false);
        musterRoll.setAuditDetails(auditDetails);

        //populate auditDetails in IndividualEntry and AttendanceEntry
        populateAuditDetailsIndividualEntry(musterRoll);

        if (workflow.getAction().equals(ACTION_REJECT)) {
            enrichUpdateMusterWorkFlowForActionReject(musterRollRequest);
        }

    }

    /**
     * If the workflow action is 'REJECT' then assignee will be updated
     * with user id that is having role as 'ORG_STAFF' or 'ORG_ADMIN' (i.e the 'auditDetails.createdBy')
     *
     * @param request
     */
    private void enrichUpdateMusterWorkFlowForActionReject(MusterRollRequest request) {
        Workflow workflow = request.getWorkflow();
        AuditDetails auditDetails = request.getMusterRoll().getAuditDetails();
        if (auditDetails != null && StringUtils.isNotBlank(auditDetails.getCreatedBy())) {
            List<String> updatedAssignees = new ArrayList<>();
            updatedAssignees.add(auditDetails.getCreatedBy());
            workflow.setAssignes(updatedAssignees);
        }
    }

    /**
     * Enrich MusterRollSearchCriteria with default offset and limit
     *
     * @param searchCriteria
     */
    public void enrichSearchRequest(MusterRollSearchCriteria searchCriteria) {

        if (searchCriteria.getLimit() == null)
            searchCriteria.setLimit(config.getMusterDefaultLimit());

        if (searchCriteria.getOffset() == null)
            searchCriteria.setOffset(config.getMusterDefaultOffset());

        if (searchCriteria.getLimit() != null && searchCriteria.getLimit() > config.getMusterMaxLimit())
            searchCriteria.setLimit(config.getMusterMaxLimit());

        if (searchCriteria.getSortBy() == null)
            searchCriteria.setSortBy("createdTime");

        if (searchCriteria.getOrder() == null)
            searchCriteria.setOrder(Pagination.OrderEnum.DESC);
    }

    /**
     * Populate audit details for individualEntry
     *
     * @param musterRoll
     */
    private void populateAuditDetailsIndividualEntry(MusterRoll musterRoll) {
       if (musterRoll.getIndividualEntries() != null) {
           for (IndividualEntry individualEntry : musterRoll.getIndividualEntries()) {
               individualEntry.setAuditDetails(musterRoll.getAuditDetails());
               populateAuditDetailsAttendanceEntry(individualEntry);
           }
       }

    }

    /**
     * Populate audit details for attendanceEntry
     *
     * @param individualEntry
     */
    private void populateAuditDetailsAttendanceEntry(IndividualEntry individualEntry) {
        if (individualEntry.getAttendanceEntries() != null) {
            for (AttendanceEntry attendanceEntry : individualEntry.getAttendanceEntries()) {
                attendanceEntry.setAuditDetails(individualEntry.getAuditDetails());
            }
        }

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
