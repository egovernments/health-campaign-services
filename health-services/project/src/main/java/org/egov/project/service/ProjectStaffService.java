package org.egov.project.service;

import digit.models.coremodels.AuditDetails;
import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.service.IdGenService;
import org.egov.common.service.UserService;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffSearch;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ProjectStaffService {

    public static final String SAVE_KAFKA_TOPIC = "save-project-staff-topic";

    public static final String UPDATE_KAFKA_TOPIC = "update-project-staff-topic";

    private final IdGenService idGenService;

    private final ProjectStaffRepository projectStaffRepository;

    private final ProjectService projectService;

    private final UserService userService;

    @Autowired
    public ProjectStaffService(
            IdGenService idGenService,
            ProjectStaffRepository projectStaffRepository,
            ProjectService projectService,
            UserService userService
    ) {
        this.idGenService = idGenService;
        this.projectStaffRepository = projectStaffRepository;
        this.projectService = projectService;
        this.userService = userService;
    }

    public List<ProjectStaff> create(ProjectStaffRequest projectStaffRequest) throws Exception {
        List<ProjectStaff> projectStaffs = projectStaffRequest.getProjectStaff();
        String tenantId = getTenantId(projectStaffs);

        validateProjectId(projectStaffs);
        validateUserIds(projectStaffs, tenantId);

        List<String> idList = generateProjectStaffIds(projectStaffRequest, projectStaffs, tenantId);
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

        projectStaffRepository.save(projectStaffs,SAVE_KAFKA_TOPIC);
        log.info("Pushed to kafka");

        return projectStaffs;
    }


    public List<ProjectStaff> update(ProjectStaffRequest projectStaffRequest) {

        List<ProjectStaff> projectStaffs = projectStaffRequest.getProjectStaff();
        String tenantId = getTenantId(projectStaffs);

        log.info("Checking existing projects");
        validateProjectId(projectStaffs);
        log.info("Checking existing project staffs");
        validateUserIds(projectStaffs, tenantId);

        Map<String, ProjectStaff> projectStaffMap =
                projectStaffs.stream()
                        .collect(Collectors.toMap(ProjectStaff::getId, item -> item));

        List<String> projectStaffIds = new ArrayList<>(projectStaffMap.keySet());

        log.info("Checking existing product variants");
        List<ProjectStaff> existingProjectStaffs = projectStaffRepository
                .findById(projectStaffIds);

        if (projectStaffs.size() != existingProjectStaffs.size()) {
            List<String> existingProjectStaffIds = existingProjectStaffs.stream().map(ProjectStaff::getId).collect(Collectors.toList());
            List<String> invalidProjectStaffIds = projectStaffMap.keySet().stream().filter(id -> !existingProjectStaffIds.contains(id))
                    .collect(Collectors.toList());
            log.error("Invalid project staff");
            throw new CustomException("INVALID_PROJECT_STAFF", invalidProjectStaffIds.toString());
        }

        checkRowVersion(projectStaffMap, existingProjectStaffs);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        IntStream.range(0, existingProjectStaffs.size()).forEach(i -> {
            ProjectStaff p = projectStaffMap.get(existingProjectStaffs.get(i).getId());
            if (projectStaffRequest.getApiOperation().equals(ApiOperation.DELETE)) {
                p.setIsDeleted(true);
            }
            p.setRowVersion(p.getRowVersion() + 1);
            AuditDetails existingAuditDetails = existingProjectStaffs.get(i).getAuditDetails();
            p.setAuditDetails(getAuditDetailsForUpdate(existingAuditDetails,
                    projectStaffRequest.getRequestInfo().getUserInfo().getUuid()));
        });


        projectStaffRepository.save(projectStaffs, UPDATE_KAFKA_TOPIC);
        log.info("Pushed to kafka");
        return projectStaffs;
    }

    private AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails, String uuid) {
        log.info("Generating Audit Details for project staff");

        return AuditDetails.builder()
                .createdBy(existingAuditDetails.getCreatedBy())
                .createdTime(existingAuditDetails.getCreatedTime())
                .lastModifiedBy(uuid)
                .lastModifiedTime(System.currentTimeMillis()).build();
    }

    private String getTenantId(List<ProjectStaff> projectStaffs) {
        String tenantId = null;
        Optional<ProjectStaff> anyProjectStaff = projectStaffs.stream().findAny();
        if (anyProjectStaff.isPresent()) {
            tenantId = anyProjectStaff.get().getTenantId();
        }
        log.info("Using tenantId {}",tenantId);
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
        log.info("IDs generated {} ",idList);
        return idList;
    }

    private AuditDetails createAuditDetailsForInsert(RequestInfo requestInfo) {
        Long time = System.currentTimeMillis();
        return AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .createdTime(time)
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedTime(time).build();
    }

    public List<String> validateProjectId(List<ProjectStaff> projectStaff) {

        List<String> projectIds = new ArrayList<>(projectStaff.stream().map(ProjectStaff::getProjectId).collect(Collectors.toSet()));
        List<String> validProjectIds = projectService.validateProjectIds(projectIds);
        log.info("project ids {}",validProjectIds);
        if (validProjectIds.size() != projectIds.size()) {
            List<String> invalidProjectIds = new ArrayList<>(projectIds);
            invalidProjectIds.removeAll(validProjectIds);
            log.error("Invalid projectId",invalidProjectIds);
            throw new CustomException("INVALID_PROJECT_ID", invalidProjectIds.toString());
        }
        return validProjectIds;
    }

    private void validateUserIds(List<ProjectStaff> projectStaff,String tenantId) {
        List<String> userIds = new ArrayList<>(projectStaff.stream().map(ProjectStaff::getUserId).collect(Collectors.toSet()));
        UserSearchRequest userSearchRequest = new UserSearchRequest();
        userSearchRequest.setTenantId(tenantId);
        userSearchRequest.setUuid(userIds);
        List<String> validUserIds = userService
                                        .search(userSearchRequest)
                                        .stream()
                                        .map(User::getUuid)
                                        .collect(Collectors.toList());

        if (validUserIds.size() != userIds.size()) {
            List<String> invalidUserIds = new ArrayList<>(userIds);
            invalidUserIds.removeAll(validUserIds);
            log.error("Invalid userIds", invalidUserIds);
            throw new CustomException("INVALID_USER_ID", invalidUserIds.toString());
        }
    }

    private void checkRowVersion(Map<String, ProjectStaff> idToPsMap,
                                 List<ProjectStaff> existingProjectStaffs) {
        Set<String> rowVersionMismatch = existingProjectStaffs.stream()
                                            .filter(existingPv -> !Objects.equals(existingPv.getRowVersion(),
                                                    idToPsMap.get(existingPv.getId()).getRowVersion()))
                .map(ProjectStaff::getId).collect(Collectors.toSet());
        if (!rowVersionMismatch.isEmpty()) {
            throw new CustomException("ROW_VERSION_MISMATCH", rowVersionMismatch.toString());
        }
    }

    public List<ProjectStaff> search(ProjectStaffSearchRequest projectStaffSearchRequest,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(projectStaffSearchRequest)) {
            List<String> ids = new ArrayList<>();
            ids.add(projectStaffSearchRequest.getProjectStaff().getId());
            return projectStaffRepository.findById(ids);
        }

        return projectStaffRepository.find(
                projectStaffSearchRequest.getProjectStaff(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    private boolean isSearchByIdOnly(ProjectStaffSearchRequest projectStaffSearchRequest) {
        ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder()
                .id(projectStaffSearchRequest.getProjectStaff()
                        .getId()).build();
        String projectStaffSearchHash = projectStaffSearch.toString();
        String hashFromRequest = projectStaffSearchRequest.getProjectStaff().toString();
        return projectStaffSearchHash.equals(hashFromRequest);
    }

}
