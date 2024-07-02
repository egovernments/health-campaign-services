import ExcelJS from "exceljs";

// input is a xlsx blob
// options {header}
// header: true -> have seperate header so data will be in key: value pair
export const parseXlsxToJsonMultipleSheets = async (file, options = {}) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();

    reader.onload = async (event) => {
      try {
        const arrayBuffer = event.target.result;
        const workbook = await loadWorkbook(arrayBuffer);
        const jsonData = processWorkbook(workbook, options);
        resolve(jsonData);
      } catch (error) {
        console.error(error);
        resolve({ error: true });
      }
    };

    reader.onerror = (error) => {
      console.error(error);
      resolve({ error: true, details: error });
    };

    reader.readAsArrayBuffer(file);
  });
};

const loadWorkbook = async (arrayBuffer) => {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(arrayBuffer);
  return workbook;
};

const processWorkbook = (workbook, options) => {
  const jsonData = {};
  workbook.eachSheet((worksheet) => {
    const jsonSheetData = processSheet(worksheet, options);
    if (jsonSheetData.length !== 0 && jsonSheetData?.[0].length !== 0) {
      jsonData[worksheet.name] = jsonSheetData;
    }
  });
  return jsonData;
};

const processSheet = (worksheet, options) => {
  const jsonSheetData = [];
  let headers = [];

  worksheet.eachRow({ includeEmpty: true }, (row, rowNumber) => {
    const rowData = cleanRowData(row.values);
    if (options.header && rowNumber === 1) {
      headers = rowData;
    } else if (options.header && headers.length > 0) {
      jsonSheetData.push(mapRowToHeaders(rowData, headers));
    } else {
      jsonSheetData.push(rowData);
    }
  });

  removeTrailingEmptyRows(jsonSheetData);
  return jsonSheetData;
};

const cleanRowData = (rowData) => {
  return rowData.slice(1).map((cell) => (typeof cell === "string" ? cell.trim() : cell));
};

const mapRowToHeaders = (rowData, headers) => {
  const rowObject = {};
  headers.forEach((header, index) => {
    rowObject[header] = rowData[index];
  });
  return rowObject;
};

const removeTrailingEmptyRows = (data) => {
  while (data.length > 0) {
    const lastRow = data[data.length - 1];
    const isEmptyRow = checkIfRowIsEmpty(lastRow);
    if (isEmptyRow) {
      data.pop();
    } else {
      break;
    }
  }
};

const checkIfRowIsEmpty = (row) => {
  if (Array.isArray(row)) {
    return row.filter((item) => item !== "").length === 0;
  }
  if (typeof row === "object" && row !== null) {
    return Object.values(row).filter((item) => item !== "").length === 0;
  }
  return false;
};
