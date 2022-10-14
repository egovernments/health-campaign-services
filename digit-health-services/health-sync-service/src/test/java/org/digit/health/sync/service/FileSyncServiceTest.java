package org.digit.health.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.helper.SyncUpRequestTestBuilder;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.SyncLogRepository;
import org.digit.health.sync.service.checksum.Md5ChecksumValidator;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.*;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileSyncServiceTest {

    @Mock
    private Producer producer;

    @Mock
    private FileStoreService fileStoreService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private GzipCompressor compressor;

    @Mock
    private Md5ChecksumValidator checksumValidator;

    @Mock
    @Qualifier("defaultSyncLogRepository")
    private SyncLogRepository syncLogRepository;

    @InjectMocks
    private FileSyncService fileSyncService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fileSyncService = new FileSyncService(
                producer,
                fileStoreService,
                objectMapper,
                compressor,
                checksumValidator,
                syncLogRepository
        );
    }

    @Test
    @DisplayName("should successfully call checksum validator and file store service")
    void shouldSuccessfullyCallChecksumValidatorAndFileStoreService() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/test.json");
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);
        when(fileStoreService.getFile(any(String.class), any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(), any())).thenReturn(true);
        when(compressor.decompress(any())).thenReturn(fileData);

        fileSyncService.syncUp(syncUpDto);

        verify(fileStoreService, times(1)).getFile(syncUpDto.getFileDetails()
                .getFileStoreId(), syncUpDto.getRequestInfo().getUserInfo().getTenantId());
        verify(checksumValidator, times(1)).validate(fileData, syncUpDto
                .getFileDetails().getChecksum());
        verify(compressor, times(1)).decompress(any());
        verify(syncLogRepository, times(1)).save(any(SyncLogData.class));
    }

    @Test
    @DisplayName("should throw custom exception when checksum validation fails")
    void shouldThrowCustomExceptionWhenChecksumValidationFails() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/test.json");
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);
        when(fileStoreService.getFile(any(String.class), any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(), any())).thenThrow(new CustomException("INVALID_CHECKSUM",
                "Checksum did not match"));

        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("should throw custom exception when file compression fails")
    void shouldThrowCustomExceptionWhenFileCompressionFails() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/test.json");
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);
        when(fileStoreService.getFile(any(String.class), any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);

    }

    @Test
    @DisplayName("should throw custom exception when file is not json")
    void shouldThrowCustomExceptionWhenFileIsNotJson() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/testfile.txt");
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);
        when(fileStoreService.getFile(any(String.class), any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(), any())).thenReturn(true);
        when(compressor.decompress(any())).thenReturn(fileData);

        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);
    }


    @Test
    @DisplayName("should successfully get results from sync repository")
    void shouldSuccessfullyGetResultsFromSyncRepository() {
        SyncLogSearchRequest syncLogSearchRequest = SyncSearchRequestTestBuilder.builder().build();
        List<SyncLogData> searchedData = new ArrayList<>();
        searchedData.add(SyncLogData.builder().build());
        SyncLogSearchDto syncLogSearchDto = SyncLogSearchMapper.INSTANCE.toDTO(syncLogSearchRequest);
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(syncLogSearchDto);

        when(syncLogRepository.find(any(SyncLogData.class))).thenReturn(searchedData);

        List<SyncLogData> fetechedResult = fileSyncService.find(syncLogSearchDto);

        assertTrue(
                searchedData.size() == fetechedResult.size() &&
                        searchedData.containsAll(fetechedResult) &&
                        fetechedResult.containsAll(searchedData)
        );

        verify(syncLogRepository, times(1)).find(syncLogData);
    }

    private byte[] getFileData(String file) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(Objects.requireNonNull(classLoader
                .getResource(file)).getFile());
        return Files.readAllBytes(gzipFile.toPath());
    }
}