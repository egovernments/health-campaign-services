package org.egov.processor.util;

import org.apache.poi.ss.usermodel.*;
import org.egov.processor.web.models.Locale;
import org.egov.processor.web.models.LocaleResponse;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.processor.web.models.census.Census;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.egov.tracer.model.CustomException;

import static org.egov.processor.config.ServiceConstants.*;

@Component
public class OutputEstimationGenerationUtil {

    private LocaleUtil localeUtil;

    private ParsingUtil parsingUtil;

    private ExcelStylingUtil excelStylingUtil;

    private EnrichmentUtil enrichmentUtil;

    public OutputEstimationGenerationUtil(LocaleUtil localeUtil, ParsingUtil parsingUtil, EnrichmentUtil enrichmentUtil, ExcelStylingUtil excelStylingUtil) {
        this.localeUtil = localeUtil;
        this.parsingUtil = parsingUtil;
        this.excelStylingUtil = excelStylingUtil;
        this.enrichmentUtil = enrichmentUtil;
    }

    public void processOutputFile(Workbook workbook, PlanConfigurationRequest request) {
        LocaleResponse localeResponse = localeUtil.searchLocale(request);
        //removing readme sheet
        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
            Sheet sheet = workbook.getSheetAt(i);
            if (!parsingUtil.isSheetAllowedToProcess(request, sheet.getSheetName(), localeResponse)) {
                workbook.removeSheetAt(i);
            }
        }

        Map<String, String> localizationCodeAndMessageMap = localeResponse.getMessages().stream()
                .collect(Collectors.toMap(
                        Locale::getCode,
                        Locale::getMessage,
                        (existingValue, newValue) -> existingValue // Keep the existing value in case of duplicates
                ));

        for(Sheet sheet: workbook) {
            processSheetForHeaderLocalization(sheet, localizationCodeAndMessageMap);
        }
    }

    public void processSheetForHeaderLocalization(Sheet sheet, Map<String, String> localizationCodeAndMessageMap) {
        // Fetch the header row from sheet
        Row row = sheet.getRow(0);
        if (parsingUtil.isRowEmpty(row))
            throw new CustomException(EMPTY_HEADER_ROW_CODE, EMPTY_HEADER_ROW_MESSAGE);


        //Iterate from the end, for every cell localize the header value
        for (int i = row.getLastCellNum() - 1; i >= 0; i--) {
            Cell headerColumn = row.getCell(i);

            if (headerColumn == null || headerColumn.getCellType() != CellType.STRING) {
                continue;
            }
            String headerColumnValue = headerColumn.getStringCellValue();

            // Exit the loop if the header column value is not in the localization map
            if (!localizationCodeAndMessageMap.containsKey(headerColumnValue)) {
                break;
            }

            // Update the cell value with the localized message
            excelStylingUtil.styleCell(headerColumn);
            headerColumn.setCellValue(localizationCodeAndMessageMap.get(headerColumnValue));
        }

    }

    /**
     * This is the main method responsible for adding an assigned facility name column to each sheet in the workbook.
     * It iterates through all the sheets, verifies if they are eligible for processing, retrieves required mappings
     * and boundary codes, and populates the new column with facility names based on these mappings.
     *
     * @param workbook    the workbook containing the sheets to be processed.
     * @param request     the plan configuration request containing the resource mapping and other configurations.
     * @param fileStoreId the associated file store ID used to filter resource mappings.
     */
    public void addAssignedFacility(Workbook workbook, PlanConfigurationRequest request, String fileStoreId) {
        LocaleResponse localeResponse = localeUtil.searchLocale(request);

        String assignedFacilityColHeader = localeUtil.localeSearch(localeResponse.getMessages(), HCM_MICROPLAN_SERVING_FACILITY);
        assignedFacilityColHeader = assignedFacilityColHeader != null ? assignedFacilityColHeader : HCM_MICROPLAN_SERVING_FACILITY;

        // Creating a map of MappedTo and MappedFrom values from resource mapping
        Map<String, String> mappedValues = request.getPlanConfiguration().getResourceMapping().stream()
                .filter(f -> f.getFilestoreId().equals(fileStoreId))
                .collect(Collectors.toMap(
                        ResourceMapping::getMappedTo,
                        ResourceMapping::getMappedFrom,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        // Get the map of boundary code to the facility assigned to that boundary.
        Map<String, String> boundaryCodeToFacility = getBoundaryCodeToFacilityMap(workbook, request, fileStoreId);

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (parsingUtil.isSheetAllowedToProcess(request, sheet.getSheetName(), localeResponse)) {
                addFacilityNameToSheet(sheet, assignedFacilityColHeader, boundaryCodeToFacility, mappedValues);
            }
        }
    }

    /**
     * Collects boundary codes from all eligible sheets in the workbook, fetches census records for these boundaries,
     * and maps each boundary code to its assigned facility name obtained from the census data.
     *
     * @param workbook    the workbook containing the sheets.
     * @param request     the plan configuration request with boundary code details.
     * @param fileStoreId the associated file store ID for filtering.
     * @return a map of boundary codes to their assigned facility names.
     */
    private Map<String, String> getBoundaryCodeToFacilityMap(Workbook workbook, PlanConfigurationRequest request, String fileStoreId) {
        List<String> boundaryCodes = new ArrayList<>();

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (parsingUtil.isSheetAllowedToProcess(request, sheet.getSheetName(), localeUtil.searchLocale(request))) {
                boundaryCodes.addAll(enrichmentUtil.getBoundaryCodesFromTheSheet(sheet, request, fileStoreId));
            }
        }

        List<Census> censusList = enrichmentUtil.getCensusRecordsForEnrichment(request, boundaryCodes);
        return censusList.stream()
                .collect(Collectors.toMap(
                        Census::getBoundaryCode,
                        census -> (String) parsingUtil.extractFieldsFromJsonObject(census.getAdditionalDetails(), FACILITY_NAME)));
    }

    /**
     * Processes a given sheet by adding a new column for assigned facilities and populating
     * each row with the corresponding facility name based on the boundary code.
     *
     * @param sheet                     the sheet being processed.
     * @param assignedFacilityColHeader the header for the new assigned facility column.
     * @param boundaryCodeToFacility    the mapping of boundary codes to assigned facilities.
     * @param mappedValues              a map of 'MappedTo' to 'MappedFrom' values.
     */
    private void addFacilityNameToSheet(Sheet sheet, String assignedFacilityColHeader, Map<String, String> boundaryCodeToFacility, Map<String, String> mappedValues) {
        int indexOfFacility = createAssignedFacilityColumn(sheet, assignedFacilityColHeader);
        Map<String, Integer> columnNameIndexMap = parsingUtil.getAttributeNameIndexFromExcel(sheet);
        int indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0, parsingUtil.sortColumnByIndex(columnNameIndexMap), mappedValues);

        for (Row row : sheet) {
            if (row.getRowNum() == 0 || parsingUtil.isRowEmpty(row)) {
                continue;
            }

            String boundaryCode = row.getCell(indexOfBoundaryCode).getStringCellValue();

            Cell facilityCell = row.getCell(indexOfFacility);
            if (facilityCell == null) {
                facilityCell = row.createCell(indexOfFacility, CellType.STRING);
            }

            facilityCell.setCellValue(boundaryCodeToFacility.getOrDefault(boundaryCode, ""));
        }
    }

    /**
     * Adds a new column for the assigned facility name in the provided sheet, styles the header cell,
     * and returns the index of the newly created column.
     *
     * @param sheet                     the sheet where the column is to be added.
     * @param assignedFacilityColHeader the header for the new column.
     * @return the index of the newly created column.
     */
    private int createAssignedFacilityColumn(Sheet sheet, String assignedFacilityColHeader) {
        int indexOfFacility = (int) sheet.getRow(0).getLastCellNum();
        Cell facilityColHeader = sheet.getRow(0).createCell(indexOfFacility, CellType.STRING);
        excelStylingUtil.styleCell(facilityColHeader);
        facilityColHeader.setCellValue(assignedFacilityColHeader);
        return indexOfFacility;
    }
}

