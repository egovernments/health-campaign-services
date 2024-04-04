package org.egov.processor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.service.ExcelParser;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationSearchCriteria;
import org.egov.processor.web.models.PlanConfigurationSearchRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParsingUtil {

    private PlanConfigurationUtil planConfigurationUtil;
    public ParsingUtil(PlanConfigurationUtil planConfigurationUtil) {
        this.planConfigurationUtil = planConfigurationUtil;
    }

    public List<String> getAttributeNames(SimpleFeatureCollection simpleFeatureCollection) {
        List<String> attributeNames = new ArrayList<>();
        SimpleFeatureType featureType = simpleFeatureCollection.getSchema();
        List<AttributeDescriptor> descriptors = featureType.getAttributeDescriptors();

        for (AttributeDescriptor descriptor : descriptors) {
            String attributeName = descriptor.getLocalName();
            if (!attributeName.equals("geometry")) {
                attributeNames.add(attributeName);
            }
        }

        return attributeNames;
    }

    public boolean validateAttributeMapping(List<String> attributeNames, List<ResourceMapping> resourceMappingList) {
        Set<String> mappedFromSet = resourceMappingList.stream()
                .map(ResourceMapping::getMappedFrom)
                .collect(Collectors.toSet());

        for (String attributeName : attributeNames) {
            if (!mappedFromSet.contains(attributeName)) {
                return false;
            }
        }

        return true;
    }

    public void printFeatureAttributes(SimpleFeature feature, List<String> attributeNames) {
        for (String attributeName : attributeNames) {
            if (attributeName.equals("geometry") || attributeName.equals("the_geom")) continue;
            log.info( attributeName + " - " + feature.getAttribute(attributeName));
        }
        log.info("------------------------------------------");
    }



    public List<String> getAttributeNameFromExcel(Sheet sheet) {
        List<String> columnNames = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();

        // Assuming the first row contains column headers
        Row headerRow = sheet.getRow(0);
        for (Cell cell : headerRow) {
            String columnHeader = dataFormatter.formatCellValue(cell);
            columnNames.add(columnHeader);
        }

        return columnNames;
    }
}
