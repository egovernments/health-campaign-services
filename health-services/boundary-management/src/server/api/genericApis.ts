import{getLocalizedName} from "../utils/boundaryUtils"
import {getExcelWorkbookFromFileURL} from "../utils/excelUtils";



// Function to retrieve data from a specific sheet in an Excel file
const getSheetData = async (
  fileUrl: string,
  sheetName: string,
  getRow = false,
  createAndSearchConfig?: any,
  localizationMap?: { [key: string]: string }
) => {
  // Retrieve workbook using the getExcelWorkbookFromFileURL function
  const localizedSheetName = getLocalizedName(sheetName, localizationMap);
  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedSheetName);

  const worksheet: any = workbook.getWorksheet(localizedSheetName);

  // If parsing array configuration is provided, validate first row of each column
  // validateFirstRowColumn(createAndSearchConfig, worksheet, localizationMap);

  // Collect sheet data by iterating through rows and cells
  const sheetData = getSheetDataFromWorksheet(worksheet);
  const jsonData = getJsonData(sheetData, getRow);
  return jsonData;
};

function getSheetDataFromWorksheet(worksheet: any) {
  var sheetData: any[][] = [];

  worksheet?.eachRow({ includeEmpty: true }, (row: any, rowNumber: any) => {
    const rowData: any[] = [];

    row.eachCell({ includeEmpty: true }, (cell: any, colNumber: any) => {
      const cellValue = getRawCellValue(cell);
      rowData[colNumber - 1] = cellValue; // Store cell value (0-based index)
    });

    // Push non-empty row only
    if (rowData.some(value => value !== null && value !== undefined)) {
      sheetData[rowNumber - 1] = rowData; // Store row data (0-based index)
    }
  });
  return sheetData;
}

// Helper function to extract raw cell value
function getRawCellValue(cell: any) {
  if (cell.value && typeof cell.value === 'object') {
    if ('richText' in cell.value) {
      // Handle rich text
      return cell.value.richText.map((rt: any) => rt.text).join('');
    }
    else if ('hyperlink' in cell.value) {
      if (cell?.value?.text?.richText?.length > 0) {
        return cell.value.text.richText.map((t: any) => t.text).join('');
      }
      else {
        return cell.value.text;
      }
    }
    else if ('formula' in cell.value) {
      // Get the result of the formula
      return cell.value.result;
    }
    else if ('sharedFormula' in cell.value) {
      // Get the result of the shared formula
      return cell.value.result;
    }
    else if ('error' in cell.value) {
      // Get the error value
      return cell.value.error;
    } else if (cell.value instanceof Date) {
      // Handle date values
      return cell.value.toISOString();
    }
    else {
      // Return as-is for other object types
      return cell.value;
    }
  }
  return cell.value; // Return raw value for plain strings, numbers, etc.
}

export function getJsonData(sheetData: any, getRow = false, getSheetName = false, sheetName = "sheet1") {
  const jsonData: any[] = [];
  const headers = sheetData[0]; // Extract the headers from the first row

  for (let i = 1; i < sheetData.length; i++) {
    const rowData: any = {};
    const row = sheetData[i];
    if (row) {
      for (let j = 0; j < headers.length; j++) {
        const key = headers[j];
        const value = row[j] === undefined || row[j] === "" ? "" : row[j];
        if (value || value === 0) {
          rowData[key] = value;
        }
      }
      if (Object.keys(rowData).length > 0) {
        if (getRow) rowData["!row#number!"] = i + 1;
        if (getSheetName) rowData["!sheet#name!"] = sheetName;
        jsonData.push(rowData);
      }
    }
  };
  return jsonData;
}



export{getSheetData};