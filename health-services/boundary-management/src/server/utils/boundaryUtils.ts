import { getExcelWorkbookFromFileURL } from "../utils/excelUtils";
import config from "../config";
import { throwError } from "../utils/genericUtils";
import { searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { BoundaryModels } from "../models";


function getLocalizedName(
  expectedName: string,
  localizationMap?: { [key: string]: string }
) {
  if (!localizationMap || !(expectedName in localizationMap)) {
    return expectedName;
  }
  const localizedName = localizationMap[expectedName];
  return localizedName;
}
// Function to generate localisation module name based on hierarchy type
export const getBoundaryTabName = () => {
  // Construct Boundary tab name from the config
  return config?.boundary?.boundaryTab;
};

const getHeadersOfBoundarySheet = async (
  fileUrl: string,
  sheetName: string,
  getRow = false,
  localizationMap?: any
) => {
  const localizedBoundarySheetName = getLocalizedName(
    sheetName,
    localizationMap
  );
  const workbook: any = await getExcelWorkbookFromFileURL(
    fileUrl,
    localizedBoundarySheetName
  );

  const worksheet = workbook.getWorksheet(localizedBoundarySheetName);
  const columnsToValidate = worksheet
    .getRow(1)
    .values.map((header: any) =>
      header ? header.toString().trim() : undefined
    );

  // Filter out empty items and return the result
  return columnsToValidate.filter((header: any) => typeof header === "string");
};

const getHierarchy = async (
  tenantId: string,
  hierarchyType: string
) => {
  const BoundaryTypeHierarchySearchCriteria: BoundaryModels.BoundaryHierarchyDefinitionSearchCriteria =
  {
    BoundaryTypeHierarchySearchCriteria: {
      tenantId,
      hierarchyType,
    },
  };
  const response: BoundaryModels.BoundaryHierarchyDefinitionResponse =
    await searchBoundaryRelationshipDefinition(
      BoundaryTypeHierarchySearchCriteria
    );
  const boundaryList = response?.BoundaryHierarchy?.[0].boundaryHierarchy;
  return generateHierarchy(boundaryList);
};

function generateHierarchy(boundaries: any[]) {
  // Create an object to store boundary types and their parents
  const parentMap: any = {};

  // Populate the object with boundary types and their parents
  for (const boundary of boundaries) {
    parentMap[boundary.boundaryType] = boundary.parentBoundaryType;
  }

  // Traverse the hierarchy to generate the hierarchy list
  const hierarchyList = [];
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === null) {
        // This boundary type has no parent, add it to the hierarchy list
        hierarchyList.push(boundaryType);
        // Traverse its children recursively
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
  return hierarchyList;
}
function traverseChildren(parent: any, parentMap: any, hierarchyList: any[]) {
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === parent) {
        // This boundary type has the current parent, add it to the hierarchy list
        hierarchyList.push(boundaryType);
        // Traverse its children recursively
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
}

async function validateHeaders(hierarchy: any[], headersOfBoundarySheet: any, request: any, localizationMap?: any) {
    validateBoundarySheetHeaders(headersOfBoundarySheet, hierarchy, request, localizationMap);
}


function validateBoundarySheetHeaders(headersOfBoundarySheet: any[], hierarchy: any[], request: any, localizationMap?: any) {
    const localizedBoundaryCode = getLocalizedName(getBoundaryColumnName(), localizationMap)
    const boundaryCodeIndex = headersOfBoundarySheet.indexOf(localizedBoundaryCode);
    const keysBeforeBoundaryCode = boundaryCodeIndex === -1 ? headersOfBoundarySheet : headersOfBoundarySheet.slice(0, boundaryCodeIndex);
    if (keysBeforeBoundaryCode.some((key: any, index: any) => (key === undefined || key === null) || key !== hierarchy[index]) || keysBeforeBoundaryCode.length !== hierarchy.length) {
        const errorMessage = `"Boundary Sheet Headers are not the same as the hierarchy present for the given tenant and hierarchy type: ${request?.body?.ResourceDetails?.hierarchyType}"`;
        throwError("BOUNDARY", 400, "BOUNDARY_SHEET_HEADER_ERROR", errorMessage);
    }
}

export const getBoundaryColumnName = () => {
  // Construct Boundary column name from the config
  return config?.boundary?.boundaryCode;
};


export {getHeadersOfBoundarySheet ,getLocalizedName,getHierarchy,validateHeaders};