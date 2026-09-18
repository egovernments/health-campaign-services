package org.egov.referralmanagement.web.controllers;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.service.DownsyncPregenService;
import org.egov.referralmanagement.service.DownsyncService;
import org.egov.referralmanagement.web.models.DownsyncFileLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeneficiaryDownsyncController — egov.downsync.pregen.enabled flag routing")
class BeneficiaryDownsyncControllerTest {

    private static final long ONE_HOUR_MS = 3600L * 1000;

    @Mock
    private DownsyncService downsyncService;

    @Mock
    private DownsyncPregenService pregenService;

    @Mock
    private ReferralManagementConfiguration config;

    @InjectMocks
    private BeneficiaryDownsyncController controller;

    private DownsyncRequest request(Long lastSyncedTime) {
        DownsyncCriteria criteria = DownsyncCriteria.builder()
                .locality("LOC-1")
                .tenantId("default")
                .projectId("PRJ-1")
                .lastSyncedTime(lastSyncedTime)
                .build();
        return DownsyncRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .downsyncCriteria(criteria)
                .build();
    }

    private DownsyncFileLink someLink() {
        return DownsyncFileLink.builder()
                .fileType("HH_MEMBERS").url("http://s3/file").recordCount(1L).expiresAt(1L).build();
    }

    @Test
    @DisplayName("flag OFF + first sync (lastSyncedTime null) → live scan, pregen never touched")
    void flagOff_firstSync_usesLiveScan() throws Exception {
        when(config.isPregenEnabled()).thenReturn(false);
        when(downsyncService.prepareDownsyncData(any())).thenReturn(Downsync.builder().build());

        ResponseEntity<?> response = controller.getBeneficiaryData(request(null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(downsyncService, times(1)).prepareDownsyncData(any());
        verify(pregenService, never()).getPregenLinks(any());
    }

    @Test
    @DisplayName("flag OFF + stale sync → live scan, pregen never touched")
    void flagOff_staleSync_usesLiveScan() throws Exception {
        when(config.isPregenEnabled()).thenReturn(false);
        when(config.getDownsyncStaleThresholdHours()).thenReturn(8);
        when(downsyncService.prepareDownsyncData(any())).thenReturn(Downsync.builder().build());

        long staleTime = System.currentTimeMillis() - (9 * ONE_HOUR_MS); // older than 8h threshold
        ResponseEntity<?> response = controller.getBeneficiaryData(request(staleTime));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(downsyncService, times(1)).prepareDownsyncData(any());
        verify(pregenService, never()).getPregenLinks(any());
    }

    @Test
    @DisplayName("flag ON + first sync (lastSyncedTime null) → pregen path, live scan never touched")
    void flagOn_firstSync_usesPregen() throws Exception {
        when(config.isPregenEnabled()).thenReturn(true);
        when(pregenService.getPregenLinks(any())).thenReturn(List.of(someLink()));

        ResponseEntity<?> response = controller.getBeneficiaryData(request(null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pregenService, times(1)).getPregenLinks(any());
        verify(downsyncService, never()).prepareDownsyncData(any());
    }

    @Test
    @DisplayName("flag ON + recent sync → still live scan, pregen never touched")
    void flagOn_recentSync_usesLiveScan() throws Exception {
        when(config.isPregenEnabled()).thenReturn(true);
        when(config.getDownsyncStaleThresholdHours()).thenReturn(8);
        when(downsyncService.prepareDownsyncData(any())).thenReturn(Downsync.builder().build());

        long recentTime = System.currentTimeMillis() - ONE_HOUR_MS; // 1h ago, within threshold
        ResponseEntity<?> response = controller.getBeneficiaryData(request(recentTime));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(downsyncService, times(1)).prepareDownsyncData(any());
        verify(pregenService, never()).getPregenLinks(any());
    }
}
