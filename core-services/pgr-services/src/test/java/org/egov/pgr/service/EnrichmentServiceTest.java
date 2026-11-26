package org.egov.pgr.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.repository.IdGenRepository;
import org.egov.pgr.util.PGRUtils;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class EnrichmentServiceTest {

    private static final List<String> HANDLER_ROLES = Arrays.asList("SUPERUSER", "SYSTEM_ADMINISTRATOR", "PGR-ADMIN",
            "PGR_ADMIN", "HELPDESK_USER", "L2_SUPPORT", "GRO", "PGR_LME", "CSR", "DGRO", "PGR_VIEWER");

    private PGRConfiguration config;
    private UserService userService;
    private EnrichmentService enrichmentService;

    @BeforeEach
    public void setUp() {
        config = new PGRConfiguration();
        config.setSearchAllComplaintsRoles(String.join(",", HANDLER_ROLES));
        config.setDefaultLimit(100);
        config.setDefaultOffset(0);
        config.setMaxLimit(200);
        userService = mock(UserService.class);
        enrichmentService = new EnrichmentService(mock(PGRUtils.class), mock(IdGenRepository.class), config, userService);
    }

    private RequestInfo requestInfo(String type, String uuid, String... roleCodes) {
        List<Role> roles = new ArrayList<>();
        for (String code : roleCodes)
            roles.add(Role.builder().code(code).tenantId("pg").build());
        return RequestInfo.builder().userInfo(User.builder().uuid(uuid).userName("9999999999").type(type).tenantId("pg").roles(roles).build()).build();
    }

    private RequestInfo requestInfoWithRoleAt(String roleCode, String roleTenantId) {
        return RequestInfo.builder().userInfo(User.builder().uuid("emp-1").type("EMPLOYEE").tenantId("pg")
                .roles(Collections.singletonList(Role.builder().code(roleCode).tenantId(roleTenantId).build())).build()).build();
    }

    private RequestSearchCriteria criteria() {
        return RequestSearchCriteria.builder().tenantId("pg.citya").build();
    }

    private RequestSearchCriteria criteriaFor(String tenantId) {
        return RequestSearchCriteria.builder().tenantId(tenantId).build();
    }

    @Test
    public void citizenIsRestrictedToOwnComplaints() {
        RequestSearchCriteria criteria = criteria();

        enrichmentService.enrichSearchRequest(requestInfo("CITIZEN", "citizen-1", "CITIZEN"), criteria);

        assertEquals("citizen-1", criteria.getRestrictedToUserId());
        assertEquals(100, criteria.getLimit());
    }

    @Test
    public void citizenWithEmptyCriteriaIsRestrictedAndKeepsMobileNumberBehaviour() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().build();

        enrichmentService.enrichSearchRequest(requestInfo("CITIZEN", "citizen-1", "CITIZEN"), criteria);

        assertEquals("citizen-1", criteria.getRestrictedToUserId());
        assertEquals("9999999999", criteria.getMobileNumber());
        verify(userService).enrichUserIds("pg", criteria);
    }

    @Test
    public void clientSentMobileNumberAndUserIdsDoNotChangeTheRestriction() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg.citya").mobileNumber("8888888888")
                .userIds(new HashSet<>(Arrays.asList("victim-1", "victim-2"))).build();

        enrichmentService.enrichSearchRequest(requestInfo("CITIZEN", "citizen-1", "CITIZEN"), criteria);

        assertEquals("citizen-1", criteria.getRestrictedToUserId());
        assertEquals("8888888888", criteria.getMobileNumber());
    }

    @Test
    public void employeeWithoutHandlerRoleIsRestricted() {
        for (String roleCode : Arrays.asList("DISTRIBUTOR", "REGISTRAR", "WAREHOUSE_MANAGER")) {
            RequestSearchCriteria criteria = criteria();

            enrichmentService.enrichSearchRequest(requestInfo("EMPLOYEE", "emp-1", roleCode, "EMPLOYEE"), criteria);

            assertEquals("emp-1", criteria.getRestrictedToUserId(), roleCode);
        }
    }

    @Test
    public void employeeWithAnyHandlerRoleIsNotRestricted() {
        for (String handlerRole : HANDLER_ROLES) {
            RequestSearchCriteria criteria = criteria();

            enrichmentService.enrichSearchRequest(requestInfo("EMPLOYEE", "emp-1", "DISTRIBUTOR", handlerRole), criteria);

            assertNull(criteria.getRestrictedToUserId(), handlerRole);
        }
    }

    @Test
    public void handlerRoleAmongManyRolesIsNotRestricted() {
        RequestSearchCriteria criteria = criteria();

        enrichmentService.restrictToOwnComplaints(requestInfo("EMPLOYEE", "emp-1", "EMPLOYEE", "REGISTRAR", "GRO"), criteria);

        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void userWithNoRolesIsNotRestrictedAsInternalCall() {
        RequestSearchCriteria criteria = criteria();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("emp-1").type("EMPLOYEE").build()).build();

        enrichmentService.restrictToOwnComplaints(requestInfo, criteria);

        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void userWithEmptyRolesIsNotRestrictedAsInternalCall() {
        RequestSearchCriteria criteria = criteria();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("emp-1").type("EMPLOYEE")
                .roles(Collections.emptyList()).build()).build();

        enrichmentService.restrictToOwnComplaints(requestInfo, criteria);

        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void nullRequestInfoOrUserInfoIsNotRestrictedAsInternalCall() {
        RequestSearchCriteria withNullRequestInfo = criteria();
        RequestSearchCriteria withNullUserInfo = criteria();

        enrichmentService.restrictToOwnComplaints(null, withNullRequestInfo);
        enrichmentService.restrictToOwnComplaints(RequestInfo.builder().build(), withNullUserInfo);

        assertNull(withNullRequestInfo.getRestrictedToUserId());
        assertNull(withNullUserInfo.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleAtAnotherTenantIsRestricted() {
        RequestSearchCriteria criteria = criteriaFor("pb");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "mz"), criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleAtTenantWithSamePrefixWithoutDotIsRestricted() {
        RequestSearchCriteria criteria = criteriaFor("pbx.city");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "pb"), criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleAtCityTenantSearchingItsStateIsRestricted() {
        RequestSearchCriteria criteria = criteriaFor("pb");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "pb.amritsar"), criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleWithoutTenantIsRestricted() {
        RequestSearchCriteria criteria = criteriaFor("pb");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", null), criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleAtParentTenantIsNotRestricted() {
        RequestSearchCriteria criteria = criteriaFor("pb.amritsar");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "pb"), criteria);

        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleAtSameTenantIsNotRestricted() {
        RequestSearchCriteria criteria = criteriaFor("pb.amritsar");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "pb.amritsar"), criteria);

        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleIsRestrictedWhenSearchTenantIsBlank() {
        RequestSearchCriteria noTenant = criteriaFor(null);
        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "mz"), noTenant);
        assertEquals("emp-1", noTenant.getRestrictedToUserId());

        RequestSearchCriteria emptyTenant = criteriaFor("");
        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("GRO", "mz"), emptyTenant);
        assertEquals("emp-1", emptyTenant.getRestrictedToUserId());
    }

    @Test
    public void handlerRoleAtAnotherTenantDoesNotWinOverNonHandlerRoleAtThisTenant() {
        RequestSearchCriteria criteria = criteriaFor("pb");
        List<Role> roles = Arrays.asList(Role.builder().code("GRO").tenantId("mz").build(),
                Role.builder().code("EMPLOYEE").tenantId("pb").build());
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("emp-1").type("EMPLOYEE").roles(roles).build()).build();

        enrichmentService.restrictToOwnComplaints(requestInfo, criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void pgrAdminIsAHandlerWhenItIsInTheConfiguredList() {
        config.setSearchAllComplaintsRoles("PGR_ADMIN");
        RequestSearchCriteria criteria = criteriaFor("pb.amritsar");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("PGR_ADMIN", "pb"), criteria);

        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void pgrAdminIsRestrictedWhenItIsNotInTheConfiguredList() {
        config.setSearchAllComplaintsRoles("GRO");
        RequestSearchCriteria criteria = criteriaFor("pb.amritsar");

        enrichmentService.restrictToOwnComplaints(requestInfoWithRoleAt("PGR_ADMIN", "pb"), criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void emptyConfiguredRoleListRestrictsEveryone() {
        config.setSearchAllComplaintsRoles("");
        RequestSearchCriteria criteria = criteria();

        enrichmentService.restrictToOwnComplaints(requestInfo("EMPLOYEE", "emp-1", "SUPERUSER"), criteria);

        assertEquals("emp-1", criteria.getRestrictedToUserId());
    }

    @Test
    public void spacesAroundConfiguredRoleCodesAreIgnoredAndMatchIsExact() {
        config.setSearchAllComplaintsRoles(" GRO , CSR ");

        RequestSearchCriteria handler = criteria();
        enrichmentService.restrictToOwnComplaints(requestInfo("EMPLOYEE", "emp-1", "CSR"), handler);
        assertNull(handler.getRestrictedToUserId());

        RequestSearchCriteria notHandler = criteria();
        enrichmentService.restrictToOwnComplaints(requestInfo("EMPLOYEE", "emp-1", "GRO_ASSISTANT", "csr"), notHandler);
        assertEquals("emp-1", notHandler.getRestrictedToUserId());
    }

    @Test
    public void restrictedUserWithoutUuidIsRejectedNotLeftUnrestricted() {
        RequestSearchCriteria criteria = criteria();

        CustomException e = assertThrows(CustomException.class,
                () -> enrichmentService.restrictToOwnComplaints(requestInfo("CITIZEN", null, "CITIZEN"), criteria));

        assertEquals("INVALID_SEARCH", e.getCode());
        assertNull(criteria.getRestrictedToUserId());
    }

    @Test
    public void defaultRoleListInApplicationPropertiesMatchesTheApprovedList() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }

        assertEquals(String.join(",", HANDLER_ROLES), properties.getProperty("pgr.search.all.complaints.roles"));
    }
}
