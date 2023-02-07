package org.egov.project.service;

import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.service.IdGenService;
import org.egov.common.service.UserService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.validateEntities;
import static org.egov.common.utils.CommonUtils.validateIds;

@Service
@Slf4j
public class ProjectStaffService {

    private final IdGenService idGenService;

    private final ProjectStaffRepository projectStaffRepository;

    private final ProjectService projectService;

    private final UserService userService;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public ProjectStaffService(
            IdGenService idGenService,
            ProjectStaffRepository projectStaffRepository,
            ProjectService projectService,
            UserService userService,
            ProjectConfiguration projectConfiguration
    ) {
        this.idGenService = idGenService;
        this.projectStaffRepository = projectStaffRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.projectConfiguration = projectConfiguration;
    }

    public List<ProjectStaff> create(ProjectStaffRequest projectStaffRequest) throws Exception {
        List<ProjectStaff> projectStaffs = projectStaffRequest.getProjectStaff();
        String tenantId = getTenantId(projectStaffs);

        validateIds(getSet(projectStaffs, "getProjectId"),
                projectService::validateProjectIds);
        validateUserIds(projectStaffs, tenantId);

        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(projectStaffRequest.getRequestInfo(),
                getTenantId(projectStaffs),
                "project.staff.id", "", projectStaffs.size());
        log.info("IDs generated");

        enrichForCreate(projectStaffs, idList, projectStaffRequest.getRequestInfo());
        log.info("Enrichment done");
        projectStaffRepository.save(projectStaffs, projectConfiguration.getCreateProjectStaffTopic());
        log.info("Pushed to kafka");
        return projectStaffs;
    }


    public List<ProjectStaff> update(ProjectStaffRequest projectStaffRequest) {
        List<ProjectStaff> projectStaffs = projectStaffRequest.getProjectStaff();
        String tenantId = getTenantId(projectStaffs);

        identifyNullIds(projectStaffs);
        log.info("Checking existing projects");
        validateIds(getSet(projectStaffs, "getProjectId"),
                projectService::validateProjectIds);
        log.info("Checking existing project staffs");
        validateUserIds(projectStaffs, tenantId);

        Map<String, ProjectStaff> projectStaffMap = getIdToObjMap(projectStaffs);

        log.info("Checking if already exists");
        List<String> projectStaffIds = new ArrayList<>(projectStaffMap.keySet());
        List<ProjectStaff> existingProjectStaffs = projectStaffRepository
                .findById(projectStaffIds);

        validateEntities(projectStaffMap, existingProjectStaffs);

        checkRowVersion(projectStaffMap, existingProjectStaffs);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(projectStaffMap, existingProjectStaffs, projectStaffRequest);

        projectStaffRepository.save(projectStaffs, projectConfiguration.getUpdateProjectStaffTopic());
        log.info("Pushed to kafka");
        return projectStaffs;
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

    public List<ProjectStaff> search(ProjectStaffSearchRequest projectStaffSearchRequest,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(projectStaffSearchRequest.getProjectStaff())) {
            List<String> ids = projectStaffSearchRequest.getProjectStaff().getId();
            return projectStaffRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return projectStaffRepository.find(projectStaffSearchRequest.getProjectStaff(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

}
