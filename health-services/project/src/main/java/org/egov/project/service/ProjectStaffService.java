package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ProjectStaffService {

    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final ProjectStaffRepository projectStaffRepository;
    private final ProjectRepository projectRepository;
    private final IdGenService idGenService;

    @Autowired
    public ProjectStaffService(
            Producer producer,
            ObjectMapper objectMapper,
            IdGenService idGenService,
            ProjectStaffRepository projectStaffRepository,
            ProjectRepository projectRepository
    ) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.idGenService = idGenService;
        this.projectStaffRepository = projectStaffRepository;
        this.projectRepository = projectRepository;
    }

    public List<ProjectStaff> create(ProjectStaffRequest projectStaffRequest) throws Exception {

        List<ProjectStaff> projectStaffs = projectStaffRequest.getProjectStaff();

        validateProjectId(projectStaffs);

        String tenantId = getTenantId(projectStaffs);
        List<String> idList = generateProjectStaffIds(projectStaffRequest, projectStaffs, tenantId);

        // TODO  - Check if project staff data is valid or not
        validateProjectStaff(projectStaffs);

        // TODO - Check if project staff exists in redis or db
        checkIfExists(projectStaffs);

        // TODO - Check if this user is present in the user service
        checkIfUserIsValid(projectStaffs);


        AuditDetails auditDetails = createAuditDetailsForInsert(projectStaffRequest.getRequestInfo());

        IntStream.range(0, projectStaffs.size())
                .forEach(i -> {
                    final ProjectStaff projectStaff = projectStaffs.get(i);
                    projectStaff.setId(idList.get(i));
                    projectStaff.setAuditDetails(auditDetails);
                    projectStaff.setRowVersion(1);
                    projectStaff.setIsDeleted(Boolean.FALSE);
                });
        log.info("Enrichment done");
        saveProjectStaff(projectStaffs);

        return projectStaffs;
    }

    private String getTenantId(List<ProjectStaff> projectStaffs) {
        String tenantId = null;

        Optional<ProjectStaff> anyProjectStaff = projectStaffs.stream().findAny();
        if (anyProjectStaff.isPresent()) {
            tenantId = anyProjectStaff.get().getTenantId();
        }
        return tenantId;
    }

    private List<String> generateProjectStaffIds(ProjectStaffRequest projectStaffRequest, List<ProjectStaff> projectStaffs, String tenantId) throws Exception {
        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(
                projectStaffRequest.getRequestInfo(),
                tenantId,
        "project.staff.id",
                "",
                projectStaffs.size());
        log.info("IDs generated");
        return idList;
    }

    private AuditDetails createAuditDetailsForInsert(RequestInfo requestInfo) {
        User userInfo = requestInfo.getUserInfo();
        return AuditDetails.builder()
                .createdBy(userInfo.getUuid())
                .lastModifiedBy(userInfo.getUuid())
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();

    }
    private void saveProjectStaff(List<ProjectStaff> projectStaffs) {
        projectStaffRepository.save(projectStaffs);
    }


    private void validateProjectId(List<ProjectStaff> projectStaff) {

        List<String> projectIds = new ArrayList<>(projectStaff.stream().map(ProjectStaff::getProjectId).collect(Collectors.toSet()));
        List<String> validProjectIds = projectRepository.validateProjectId(projectIds);
        log.info("project ids {}",validProjectIds);
        if (validProjectIds.size() != projectIds.size()) {
            List<String> invalidProjectIds = new ArrayList<>(projectIds);
            invalidProjectIds.removeAll(validProjectIds);
            log.error("Invalid projectId");
            throw new CustomException("INVALID_PROJECT_ID", invalidProjectIds.toString());
        }
    }

    private void checkIfUserIsValid(List<ProjectStaff> projectStaff) {
    }

    private void checkIfExists(List<ProjectStaff> projectStaff) {
    }

    private void validateProjectStaff(List<ProjectStaff> projectStaff) {
    }

}
