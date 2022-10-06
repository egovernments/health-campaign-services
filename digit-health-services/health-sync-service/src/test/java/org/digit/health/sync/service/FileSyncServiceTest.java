package org.digit.health.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.helper.SyncUpRequestTestBuilder;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.service.checksum.Md5ChecksumValidator;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.digit.health.sync.web.models.SyncLog;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.digit.health.sync.web.models.request.SyncUpMapper;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.file.Files;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

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


    @InjectMocks
    private FileSyncService fileSyncService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fileSyncService = new FileSyncService(producer,fileStoreService,objectMapper,compressor,checksumValidator);
    }

    @Test
    @DisplayName("should successfully call checksum validator and file store service")
    public void shouldSuccessfullyCallChecksumValidatorAndFileStoreService() throws IOException {

        byte[] fileData = getFileData("compressionTestFiles/test.json");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader( new ByteArrayInputStream(fileData)));

        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);

        Mockito.when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        Mockito.when(checksumValidator.validate(any(),any())).thenReturn(true);
        Mockito.when(compressor.decompress(any())).thenReturn(fileData);

        fileSyncService.syncUp(syncUpDto);

        Mockito.verify(fileStoreService,times(1)).getFile(syncUpDto.getFileDetails().getFileStoreId(),syncUpDto.getRequestInfo().getUserInfo().getTenantId());
        Mockito.verify(checksumValidator,times(1)).validate(fileData,syncUpDto.getFileDetails().getChecksum());
        Mockito.verify(compressor,times(1)).decompress(any());
        Mockito.verify(producer,times(1)).send(any(String.class),any(SyncLog.class));
    }

    @Test
    @DisplayName("should throw custom exception when checksum validation fails")
    public void shouldThrowCustomExceptionWhenChecksumValidationFails() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/test.json");
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);

        Mockito.when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        Mockito.when(checksumValidator.validate(any(),any())).thenThrow(new CustomException("INVALID_CHECKSUM", "Checksum did not match"));

        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("should throw custom exception when file compression fails")
    public void shouldThrowCustomExceptionWhenFileCompressionFails() throws IOException {
        byte[] fileData = getFileData("compressionTestFiles/test.json");

        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);

        Mockito.when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        Mockito.when(checksumValidator.validate(any(),any())).thenReturn(true);
        Mockito.when(compressor.decompress(any())).thenThrow(new IOException("Invalid File"));
        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);

    }

    @Test
    @DisplayName("should throw custom exception when file is not json")
    public void shouldThrowCustomExceptionWhenFileIsNotJson() throws IOException {

        byte[] fileData = getFileData("compressionTestFiles/testfile.txt");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader( new ByteArrayInputStream(fileData)));

        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        SyncUpDto syncUpDto = SyncUpMapper.INSTANCE.toDTO(syncUpRequest);

        Mockito.when(fileStoreService.getFile(any(String.class),any(String.class))).thenReturn(fileData);
        Mockito.when(checksumValidator.validate(any(),any())).thenReturn(true);
        Mockito.when(compressor.decompress(any())).thenReturn(fileData);

        assertThatThrownBy(() -> fileSyncService.syncUp(syncUpDto)).isInstanceOf(CustomException.class);

    }

    private byte[] getFileData(String file) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File gzipFile = new File(classLoader.getResource(file).getFile());
        byte[] bytes = Files.readAllBytes(gzipFile.toPath());
        return bytes;
    }
}