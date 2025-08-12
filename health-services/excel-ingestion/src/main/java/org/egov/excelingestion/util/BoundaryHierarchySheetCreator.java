package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.service.BoundaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class BoundaryHierarchySheetCreator {

    @Autowired
    private BoundaryService boundaryService;

    public Workbook createBoundaryHierarchySheet(XSSFWorkbook workbook, 
                                                 String hierarchyType,
                                                 String tenantId,
                                                 RequestInfo requestInfo,
                                                 Map<String, String> localizationMap,
                                                 String localizedSheetName) {
        
        // Fetch boundary hierarchy data to get level types
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        
        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            throw new RuntimeException("Boundary Hierarchy Search API returned no data.");
        }

        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        
        // Get original levels from hierarchy (just the boundary types for column headers)
        List<String> originalLevels = new ArrayList<>();
        if (hierarchyRelations != null && !hierarchyRelations.isEmpty()) {
            for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
                originalLevels.add(hierarchyRelation.getBoundaryType());
            }
        }

        // Create the sheet
        Sheet hierarchySheet = workbook.createSheet(localizedSheetName);
        
        // Create headers
        Row hiddenRow = hierarchySheet.createRow(0);
        Row visibleRow = hierarchySheet.createRow(1);
        
        // Add columns for each level
        for (int i = 0; i < originalLevels.size(); i++) {
            String levelType = originalLevels.get(i);
            
            // Hidden row: unlocalized level names
            String unlocalizedCode = hierarchyType.toUpperCase() + "_" + levelType.toUpperCase();
            hiddenRow.createCell(i).setCellValue(unlocalizedCode);
            
            // Visible row: localized level names
            String localizedLevelName = localizationMap.getOrDefault(unlocalizedCode, unlocalizedCode);
            visibleRow.createCell(i).setCellValue(localizedLevelName);
            
            // Set column width
            hierarchySheet.setColumnWidth(i, 40 * 256);
        }
        
        // Hide the first row (technical names)
        hiddenRow.setZeroHeight(true);
        
        // Freeze panes after headers
        hierarchySheet.createFreezePane(0, 2);
        
        // Only unlock the hierarchy columns for data entry (rows 3-5000)
        // Excel will default lock all other cells when sheet protection is applied
        CellStyle unlockedStyle = workbook.createCellStyle();
        unlockedStyle.setLocked(false);
        
        // Apply unlocked style only to hierarchy columns in data rows
        for (int r = 2; r <= 5000; r++) {
            Row row = hierarchySheet.getRow(r);
            if (row == null) row = hierarchySheet.createRow(r);
            
            // Only create and unlock cells for hierarchy columns
            for (int c = 0; c < originalLevels.size(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) cell = row.createCell(c);
                cell.setCellStyle(unlockedStyle);
            }
        }
        
        // Protect the sheet to enforce locking (headers locked by default, only hierarchy columns unlocked)
        hierarchySheet.protectSheet("passwordhere");
        
        return workbook;
    }


}