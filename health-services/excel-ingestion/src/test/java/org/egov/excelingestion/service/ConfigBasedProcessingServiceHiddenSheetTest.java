package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConfigBasedProcessingServiceHiddenSheetTest {

    @Mock
    private MDMSConfigService mdmsConfigService;
    @Mock
    private CustomExceptionHandler exceptionHandler;
    @Mock
    private MDMSService mdmsService;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private Producer producer;
    @Mock
    private KafkaTopicConfig kafkaTopicConfig;
    @Mock
    private ExcelIngestionConfig config;

    private ConfigBasedProcessingService service;

    @BeforeEach
    void setUp() {
        service = new ConfigBasedProcessingService(
                mdmsConfigService,
                exceptionHandler,
                mdmsService,
                applicationContext,
                producer,
                kafkaTopicConfig,
                config
        );
    }

    @Test
    void shouldTreatLegacyPatternSheetAsHidden() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("_h_helper_h_");
            assertTrue(service.isHiddenSheet(sheet));
            assertTrue(service.isHiddenSheet("_h_helper_h_"));
        }
    }

    @Test
    void shouldTreatWorkbookHiddenStateAsHidden() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("helper");
            workbook.setSheetHidden(workbook.getSheetIndex(sheet), true);
            assertTrue(service.isHiddenSheet(sheet));
        }
    }

    @Test
    void shouldTreatTemplateMetadataSheetByNameAsHidden() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet metadataSheet = workbook.createSheet("_hcm_template_meta_");
            assertTrue(service.isHiddenSheet(metadataSheet));
            assertTrue(service.isHiddenSheet("_hcm_template_meta_"));
        }
    }

    @Test
    void shouldTreatTemplateMetadataMarkerAsHiddenEvenIfSheetNameDiffers() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet metadataSheet = workbook.createSheet("metadata");
            metadataSheet.createRow(0).createCell(0).setCellValue("__HCM_TEMPLATE_METADATA__");
            assertTrue(service.isHiddenSheet(metadataSheet));
        }
    }

    @Test
    void shouldNotTreatBusinessSheetAsHidden() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet businessSheet = workbook.createSheet("Frontline Workers");
            assertFalse(service.isHiddenSheet(businessSheet));
            assertFalse(service.isHiddenSheet("Frontline Workers"));
        }
    }
}

