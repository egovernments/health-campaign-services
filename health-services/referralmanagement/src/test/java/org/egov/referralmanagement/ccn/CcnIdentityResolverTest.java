package org.egov.referralmanagement.ccn;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Outbound identity resolution: the emitted ABHA healthId value must be the individual's
 * UNIQUE_BENEFICIARY_ID — read from additionalFields when already present, else looked up via
 * project-beneficiary → individual and cached back onto the referral.
 */
class CcnIdentityResolverTest {

    private ServiceRequestClient client;
    private CcnIdentityResolver resolver;
    private final RequestInfo ri = RequestInfo.builder().build();

    @BeforeEach
    void setup() {
        client = mock(ServiceRequestClient.class);
        CcnProperties props = new CcnProperties();
        props.setInboundTenantId("sl");
        resolver = new CcnIdentityResolver(client, mock(ReferralManagementConfiguration.class), props);
    }

    @Test
    void usesAbhaFromAdditionalFieldsWithoutAnyLookup() {
        Referral r = Referral.builder()
                .tenantId("sl")
                .additionalFields(AdditionalFields.builder()
                        .fields(List.of(Field.builder().key("abhaId").value("0690003741962").build()))
                        .build())
                .build();

        assertEquals("0690003741962", resolver.resolveOutboundAbha(r, ri));
        verifyNoInteractions(client);   // short-circuits at step 1
    }

    @Test
    void fallbackLooksUpIndividualUniqueBeneficiaryIdAndCachesIt() throws Exception {
        Referral r = Referral.builder().tenantId("sl").projectBeneficiaryId("pb-1").build();

        // project-beneficiary search → beneficiary linked to individual "ind-1"
        BeneficiaryBulkResponse pbResp = BeneficiaryBulkResponse.builder()
                .projectBeneficiaries(List.of(ProjectBeneficiary.builder().id("pb-1").beneficiaryId("ind-1").build()))
                .build();
        when(client.fetchResult(any(), any(), eq(BeneficiaryBulkResponse.class))).thenReturn(pbResp);

        // individual search → carries the canonical UNIQUE_BENEFICIARY_ID identifier
        Individual ind = Individual.builder().id("ind-1")
                .identifiers(List.of(Identifier.builder()
                        .identifierType("UNIQUE_BENEFICIARY_ID").identifierId("0690003741962").build()))
                .build();
        IndividualBulkResponse indResp = IndividualBulkResponse.builder().individual(List.of(ind)).build();
        when(client.fetchResult(any(), any(), eq(IndividualBulkResponse.class))).thenReturn(indResp);

        assertEquals("0690003741962", resolver.resolveOutboundAbha(r, ri));
        // cached back onto the referral so subsequent sends skip the lookup
        assertEquals("0690003741962", resolver.readAbhaField(r));
    }

    @Test
    void returnsNullWhenNothingResolvable() {
        Referral r = Referral.builder().tenantId("sl").build();   // no additionalFields, no beneficiary link
        assertNull(resolver.resolveOutboundAbha(r, ri));
    }
}
