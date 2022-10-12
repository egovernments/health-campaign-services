package org.digit.health.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.helper.SyncUpRequestTestBuilder;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.SyncRepository;
import org.digit.health.sync.service.checksum.Md5ChecksumValidator;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.digit.health.sync.web.models.SyncLog;
import org.digit.health.sync.web.models.dao.SyncData;
import org.digit.health.sync.web.models.request.*;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private SyncRepository syncRepository;

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
                syncRepository
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
        when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(),any())).thenReturn(true);
        when(compressor.decompress(any())).thenReturn(fileData);

        fileSyncService.syncUp(syncUpDto);

        verify(fileStoreService,times(1)).getFile(syncUpDto.getFileDetails()
                .getFileStoreId(),syncUpDto.getRequestInfo().getUserInfo().getTenantId());
        verify(checksumValidator,times(1)).validate(fileData,syncUpDto
                .getFileDetails().getChecksum());
        verify(compressor,times(1)).decompress(any());
        verify(producer,times(1)).send(any(String.class),any(SyncLog.class));
    }

    @Test
    @DisplayName("should throw custom exception when checksum validation fails")
    void shouldThrowCustomExceptionWhenChecksumValidationFails() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/test.json");
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);
        when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(),any())).thenThrow(new CustomException("INVALID_CHECKSUM",
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
        when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(),any())).thenReturn(true);

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
        when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        when(checksumValidator.validate(any(),any())).thenReturn(true);
        when(compressor.decompress(any())).thenReturn(fileData);

        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("should successfully get results from sync repository")
    void shouldSuccessfullyGetResultsFromSyncRepository()  {
        SyncSearchRequest syncSearchRequest = SyncSearchRequestTestBuilder.builder().build();
        List<SyncData> searchedData = new ArrayList<>();
        searchedData.add(SyncData.builder().build());
        SyncSearchDto syncSearchDto = SyncSearchMapper.INSTANCE.toDTO(syncSearchRequest);

        when(syncRepository.findByCriteria(any(SyncSearchDto.class))).thenReturn(searchedData);

        fileSyncService.findByCriteria(syncSearchDto);

        verify(syncRepository,times(1)).findByCriteria(syncSearchDto);
    }

    private byte[] getFileData(String file) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(Objects.requireNonNull(classLoader
                .getResource(file)).getFile());
        return Files.readAllBytes(gzipFile.toPath());
    }
}