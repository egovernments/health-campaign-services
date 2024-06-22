import ExcelJS from "exceljs";

export const convertJsonToXlsx = async (jsonData, columnWithStyle) => {
  const workbook = new ExcelJS.Workbook();

  for (const [sheetName, data] of Object.entries(jsonData)) {
    const worksheet = workbook.addWorksheet(sheetName);
    populateWorksheet(worksheet, data, columnWithStyle);
  }

  return await writeWorkbookToBuffer(workbook);
};

const populateWorksheet = (worksheet, data, columnWithStyle) => {
  data.forEach((row, rowIndex) => {
    const newRow = worksheet.addRow(row);
    if (columnWithStyle?.errorColumn && rowIndex > 0) {
      applyStyleToColumn(newRow, data[0], columnWithStyle);
    }
  });

  styleHeaderRow(worksheet);
  setColumnWidths(worksheet, data[0].length);
};

/**
 * Applies a specified style to a column in a given row of a spreadsheet.
 *
 * @param {Object} newRow - The row object where the style will be applied.
 * @param {Array} headerRow - The header row array containing column names.
 * @param {Object} columnWithStyle - An object containing the column name and the style to be applied.
 * @param {string} columnWithStyle.errorColumn - The name of the column where the style should be applied.
 * @param {Object} columnWithStyle.style - The style properties to be applied to the cell.
 */
const applyStyleToColumn = (newRow, headerRow, columnWithStyle) => {
  const errorColumnIndex = headerRow.indexOf(columnWithStyle.errorColumn);
  if (errorColumnIndex !== -1) {
    const columnIndex = errorColumnIndex + 1;
    const newCell = newRow.getCell(columnIndex);
    if (columnWithStyle.style && newCell) {
      for (const key in columnWithStyle.style) {
        newCell[key] = columnWithStyle.style[key];
      }
    }
  }
};

const styleHeaderRow = (worksheet) => {
  const headerRow = worksheet.getRow(1);
  if (headerRow) {
    headerRow.font = { bold: true };
  }
};

const setColumnWidths = (worksheet, columnCount) => {
  const columnWidth = 30;
  for (let colIndex = 1; colIndex <= columnCount; colIndex++) {
    worksheet.getColumn(colIndex).width = columnWidth;
  }
};

const writeWorkbookToBuffer = async (workbook) => {
  const buffer = await workbook.xlsx.writeBuffer({ compression: true });
  return new Blob([buffer], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
};
