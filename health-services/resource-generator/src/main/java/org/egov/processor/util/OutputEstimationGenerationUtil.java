package org.egov.processor.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.processor.web.models.*;
import org.egov.processor.web.models.Locale;
import org.egov.processor.web.models.census.Census;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import org.egov.tracer.model.CustomException;
import org.springframework.util.ObjectUtils;

import static org.egov.processor.config.ServiceConstants.*;

@Slf4j
@Component
public class OutputEstimationGenerationUtil {

    private LocaleUtil localeUtil;

    private ParsingUtil parsingUtil;

    private ExcelStylingUtil excelStylingUtil;

    private EnrichmentUtil enrichmentUtil;

    private MdmsUtil mdmsUtil;

    public OutputEstimationGenerationUtil(LocaleUtil localeUtil, ParsingUtil parsingUtil, EnrichmentUtil enrichmentUtil, ExcelStylingUtil excelStylingUtil, MdmsUtil mdmsUtil) {
        this.localeUtil = localeUtil;
        this.parsingUtil = parsingUtil;
        this.excelStylingUtil = excelStylingUtil;
        this.enrichmentUtil = enrichmentUtil;
        this.mdmsUtil = mdmsUtil;
    }

    /**
     * Processes an output Excel workbook by removing unnecessary sheets, localizing header columns,
     * and adding facility information for each boundary code. The configuration for processing
     * is based on the provided PlanConfigurationRequest.
     *
     * @param workbook   the Excel workbook to process
     * @param request    the PlanConfigurationRequest containing processing configuration
     * @param filestoreId the identifier of the file store for additional processing requirements
     */
    public void processOutputFile(Workbook workbook, PlanConfigurationRequest request, String filestoreId) {
        LocaleResponse localeResponse = localeUtil.searchLocale(request);
        Map<String, Object> mdmsDataForCommonConstants = mdmsUtil.fetchMdmsDataForCommonConstants(
                request.getRequestInfo(),
                request.getPlanConfiguration().getTenantId());

        // 1. removing readme sheet
        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
            Sheet sheet = workbook.getSheetAt(i);
            if (!isSheetAllowedToProcess(sheet.getSheetName(), localeResponse, mdmsDataForCommonConstants)) {
                workbook.removeSheetAt(i);
            }
        }

        // 2. Stylize and localize output column headers
        for(Sheet sheet: workbook) {
            processSheetForHeaderLocalization(sheet, localeResponse);
        }

        // 3. Adding facility information for each boundary code
        for(Sheet sheet: workbook) {
            enrichsheetWithApprovedPlanAdditionalDetails(sheet, request, filestoreId);
            addAssignedFacility(sheet, request, filestoreId);
        }

    }

    private Map<String, Integer> createAdditionalDetailColumns(Sheet sheet, PlanConfigurationRequest planConfigurationRequest, List<String> additionalDetailsCOlumnNames) {

        LocaleResponse localeResponse = localeUtil.searchLocale(planConfigurationRequest);

        Map<String, Integer> columnIndexes = new HashMap<>();
        for(String columnName: additionalDetailsCOlumnNames) {

            // Get the localized column header name for assigned facilities.
            String columnHeaderName = localeUtil.localeSearch(localeResponse.getMessages(), columnName);

            if (columnHeaderName == null) {
                columnHeaderName = columnName;
            }

            int indexOfColumn = (int) sheet.getRow(0).getLastCellNum();

            // Create a new cell for the column header.
            Cell columnHeader = sheet.getRow(0).createCell(indexOfColumn, CellType.STRING);

            //stylize cell and set cell value as the localized value
            excelStylingUtil.styleCell(columnHeader);
            columnHeader.setCellValue(columnHeaderName);
            excelStylingUtil.adjustColumnWidthForCell(columnHeader);
            columnIndexes.put(columnName,indexOfColumn);
        }
        return columnIndexes;
    }
    private void enrichsheetWithApprovedPlanAdditionalDetails(Sheet sheet, PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {

        log.info("Started plan estimate export for additional details");

        List<String> boundaryCodes = enrichmentUtil.getBoundaryCodesFromTheSheet(sheet, planConfigurationRequest, fileStoreId);
        // Creating a map of MappedTo and MappedFrom values from resource mapping
        Map<String, String> mappedValues = planConfigurationRequest.getPlanConfiguration().getResourceMapping().stream()
                .filter(f -> f.getFilestoreId().equals(fileStoreId))
                .collect(Collectors.toMap(
                        ResourceMapping::getMappedTo,
                        ResourceMapping::getMappedFrom,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
        Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
                parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

        //Getting plan records for the list of boundaryCodes
        List<Plan> planList = enrichmentUtil.getPlanRecordsForEnrichment(planConfigurationRequest, boundaryCodes);

        // Create a map from boundaryCode to Plan for quick lookups
        Map<String, Plan> planMap = planList.stream()
                .collect(Collectors.toMap(Plan::getLocality, plan -> plan));

        List<String> additionalDetailsCOlumnNames = List.of("IS_COMMUNITY_DE_PRIORITIZED_FOR_ITN","NON_DE_PRIORITIZED_FOR_ITN",
                "DISTANCE_FROM_LGA_STORE_TO_DP","IS_COMMUNITY_HARD_TO_REACH_AREA","NOMADIC_SETTLEMENT","RIVERRINE","HILLY_MOUNTAINOUS_SANDY_DESERT",
                "NUMBER_OF_IDP_CAMPS","POPULATION_OF_IDP_CAMPS","SUGGESTED_MEANS_OF_TRANSPORT","NEED_FOR_SATELLITE_DP","COMMENTS","NAME_OF_SATELLITE_DP");

        Map<String, Integer> columnIndexes = createAdditionalDetailColumns(sheet,planConfigurationRequest,additionalDetailsCOlumnNames);
        log.info("plan export additional details columns created");

        for(Row row: sheet) {
            // Skip the header row and empty rows
            if (row.getRowNum() == 0 || parsingUtil.isRowEmpty(row)) {
                continue;
            }
            // Get the boundaryCode in the current row
            Cell boundaryCodeCell = row.getCell(indexOfBoundaryCode);
            String boundaryCode = boundaryCodeCell.getStringCellValue();

            Plan planEstimate = planMap.get(boundaryCode);

            log.info("plan estimate export starting setting values in additional detail column");
            if (planEstimate != null) {

                log.info("plan estimate export start writing workbook");
                // Iterate over each output column to update the row cells with resource values

                for (String additionalDetailColumn : additionalDetailsCOlumnNames) {
                    Object extractedField = parsingUtil.extractFieldsFromJsonObject(planEstimate.getAdditionalDetails(), additionalDetailColumn);
                    String estimatedValue = "";
                    if (extractedField != null) {
                        estimatedValue = (String) extractedField;


                        // Get the index of the column to update
                        Integer columnIndex = columnIndexes.get(additionalDetailColumn);
                        if (columnIndex != null) {
                            // Update the cell with the resource value
                            Cell cell = row.getCell(columnIndex);
                            if (cell == null) {
                                cell = row.createCell(columnIndex);
                            }
                            cell.setCellValue(estimatedValue);

                        } else {
                            log.info("column doesn't exist for column: {}, plan : {}",additionalDetailColumn, planEstimate.getId());
                        }
                    } else {
                        log.info("extracted field is null for column: {}, plan: {}",additionalDetailColumn,planEstimate.getId());
                    }

                }
            }

            log.info("Successfully update file with additional details columns data. for plan COnfiguration: {}",planConfigurationRequest.getPlanConfiguration().getId());
        }
    }

    /**
     * Localizes the header row in the given sheet using the provided localization map.
     * Applies styling and adjusts column widths for each localized header cell.
     *
     * @param sheet                           the Excel sheet whose header row needs localization
     * @param localeResponse                  localization search call response
     */
    public void processSheetForHeaderLocalization(Sheet sheet, LocaleResponse localeResponse) {
        // create localization code and message map
        Map<String, String> localizationCodeAndMessageMap = localeResponse.getMessages().stream()
                .collect(Collectors.toMap(
                        Locale::getCode,
                        Locale::getMessage,
                        (existingValue, newValue) -> existingValue // Keep the existing value in case of duplicates
                ));

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
            excelStylingUtil.adjustColumnWidthForCell(headerColumn);
        }

    }

    /**
     * This is the main method responsible for adding an assigned facility name column to each sheet in the workbook.
     * It iterates through all the sheets, verifies if they are eligible for processing, retrieves required mappings
     * and boundary codes, and populates the new column with facility names based on these mappings.
     *
     * @param sheet       the sheet to be processed.
     * @param request     the plan configuration request containing the resource mapping and other configurations.
     * @param fileStoreId the associated file store ID used to filter resource mappings.
     */
    public void addAssignedFacility(Sheet sheet, PlanConfigurationRequest request, String fileStoreId) {
        LocaleResponse localeResponse = localeUtil.searchLocale(request);

        // Get the localized column header name for assigned facilities.
        String assignedFacilityColHeader = localeUtil.localeSearch(localeResponse.getMessages(), HCM_MICROPLAN_SERVING_FACILITY);

        // Default to a constant value if no localized value is found.
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

        // Create the map of boundary code to the facility assigned to that boundary.
        Map<String, String> boundaryCodeToFacility = getBoundaryCodeToFacilityMap(sheet, request, fileStoreId);

        // Add facility names to the sheet.
        addFacilityNameToSheet(sheet, assignedFacilityColHeader, boundaryCodeToFacility, mappedValues);
    }

    /**
     * Collects boundary codes from all eligible sheets in the workbook, fetches census records for these boundaries,
     * and maps each boundary code to its assigned facility name obtained from the census data.
     *
     * @param sheet       the sheet to be processed.
     * @param request     the plan configuration request with boundary code details.
     * @param fileStoreId the associated file store ID for filtering.
     * @return a map of boundary codes to their assigned facility names.
     */
    public Map<String, String> getBoundaryCodeToFacilityMap(Sheet sheet, PlanConfigurationRequest request, String fileStoreId) {
        List<String> boundaryCodes = new ArrayList<>();

        // Extract boundary codes from the sheet.
        boundaryCodes.addAll(enrichmentUtil.getBoundaryCodesFromTheSheet(sheet, request, fileStoreId));

        // Fetch census records for the extracted boundary codes.
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

        // Get column index mappings from the sheet.
        Map<String, Integer> columnNameIndexMap = parsingUtil.getAttributeNameIndexFromExcel(sheet);

        // Get the index of the boundary code column based on the provided mappings.
        int indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0, parsingUtil.sortColumnByIndex(columnNameIndexMap), mappedValues);

        // Iterate over each row in the sheet and set the facility name for each row.
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || parsingUtil.isRowEmpty(row)) {
                continue;
            }

            String boundaryCode = row.getCell(indexOfBoundaryCode).getStringCellValue();

            // Get or create the facility cell in the row.
            Cell facilityCell = row.getCell(indexOfFacility);
            if (facilityCell == null) {
                facilityCell = row.createCell(indexOfFacility, CellType.STRING);
            }

            // Assign the facility name based on the boundary code.
            facilityCell.setCellValue(boundaryCodeToFacility.getOrDefault(boundaryCode, EMPTY_STRING));
            facilityCell.getCellStyle().setLocked(false); // Ensure the new cell is editable

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

        // Create a new cell for the column header.
        Cell facilityColHeader = sheet.getRow(0).createCell(indexOfFacility, CellType.STRING);

        //stylize cell and set cell value as the localized value
        excelStylingUtil.styleCell(facilityColHeader);
        facilityColHeader.setCellValue(assignedFacilityColHeader);
        excelStylingUtil.adjustColumnWidthForCell(facilityColHeader);
        return indexOfFacility;
    }

    /**
     * Checks if a sheet is allowed to be processed based on MDMS constants and locale-specific configuration.
     *
     * @param sheetName         The name of the sheet to be processed.
     * @param localeResponse    Localisation response for the given tenant id.
     * @param mdmsDataConstants Mdms data for common constants.
     * @return true if the sheet is allowed to be processed, false otherwise.
     */
    public boolean isSheetAllowedToProcess(String sheetName, LocaleResponse localeResponse, Map<String, Object> mdmsDataConstants) {

        String readMeSheetName = (String) mdmsDataConstants.get(READ_ME_SHEET_NAME);
        if(ObjectUtils.isEmpty(readMeSheetName))
            throw new CustomException(README_SHEET_NAME_LOCALISATION_NOT_FOUND_CODE, README_SHEET_NAME_LOCALISATION_NOT_FOUND_MESSAGE);

        for (Locale locale : localeResponse.getMessages()) {
            if ((locale.getCode().equalsIgnoreCase((String) mdmsDataConstants.get(READ_ME_SHEET_NAME)))
                    || locale.getCode().equalsIgnoreCase(HCM_ADMIN_CONSOLE_BOUNDARY_DATA)) {
                if (sheetName.equals(locale.getMessage()))
                    return false;
            }
        }
        return true;

    }
}

