import { SHEET_PASSWORD, UNPROTECT_TILL_ROW } from "../configs/constants";

export function updateFontNameToRoboto(worksheet) {
  worksheet.eachRow({ includeEmpty: true }, (row) => {
    row.eachCell({ includeEmpty: true }, (cell) => {
      // Preserve existing font properties
      const existingFont = cell.font || {};

      // Update only the font name to Roboto
      cell.font = {
        ...existingFont, // Spread existing properties
        name: "Roboto", // Update the font name
      };
    });
  });
}

export const freezeWorkbookValues = async (workbook) => {
  workbook.eachSheet((worksheet) => {
    worksheet.eachRow((row) => {
      row.eachCell((cell) => {
        // Lock each cell
        cell.protection = {
          locked: true,
        };
      });
    });
    // Protect the worksheet
    worksheet.protect(SHEET_PASSWORD, {
      selectLockedCells: true,
      selectUnlockedCells: true,
    });
  });

  return workbook;
};

export const unfreezeColumnsByHeader = async (workbook, headers) => {
  workbook.eachSheet((worksheet) => {
    const headerRow = worksheet.getRow(1); // Assuming headers are in the first row
    const columnsToUnfreeze = [];

    headerRow.eachCell((cell, colNumber) => {
      if (headers.includes(cell.value)) {
        columnsToUnfreeze.push(colNumber);
      }
    });

    worksheet.eachRow((row, rowNumber) => {
      if (rowNumber === 1) return;
      columnsToUnfreeze.forEach((colNumber) => {
        const cell = row.getCell(colNumber);
        cell.protection = {
          locked: false,
        };
      });
    });

    // Re-protect the worksheet after modifying cell protection
    worksheet.protect(SHEET_PASSWORD, {
      selectLockedCells: true,
      selectUnlockedCells: true,
    });
  });

  return workbook;
};

export const freezeSheetValues = async (workbook, sheetName) => {
  const worksheet = workbook.getWorksheet(sheetName);
  if (!worksheet) return;
  if (worksheet) {
    worksheet.eachRow((row) => {
      row.eachCell((cell) => {
        // Lock each cell
        cell.protection = {
          locked: true,
        };
      });
    });
    // Protect the worksheet
    worksheet.protect(SHEET_PASSWORD, {
      selectLockedCells: true,
      selectUnlockedCells: true,
    });
  }

  return workbook;
};

export const freezeCellsWithData = async (workbook, sheetName) => {
  const worksheet = workbook.getWorksheet(sheetName);
  if (!worksheet) return;
  if (worksheet) {
    worksheet.eachRow((row) => {
      row.eachCell((cell) => {
        if (cell.value) {
          // Check if the cell has data
          cell.protection = {
            locked: true,
          };
        } else {
          cell.protection = {
            locked: false,
          };
        }
      });
    });
    // Protect the worksheet
    worksheet.protect(SHEET_PASSWORD, {
      selectLockedCells: true,
      selectUnlockedCells: true,
    });
  }

  return workbook;
};
export const performUnfreezeCells = async (workbook, sheetName) => {
  const sheet = workbook.getWorksheet(sheetName);
  if (!sheet) return;
  let lastFilledColumn = 1;
  sheet.getRow(1).eachCell((cell, colNumber) => {
    if (cell.value !== undefined && cell.value !== null && cell.value !== "") {
      lastFilledColumn = colNumber;
    }
  });

  for (let row = 1; row <= parseInt(UNPROTECT_TILL_ROW); row++) {
    for (let col = 1; col <= lastFilledColumn; col++) {
      const cell = sheet.getCell(row, col);
      if (!cell.value && cell.value !== 0) {
        cell.protection = { locked: false };
      }
    }
  }
  sheet.protect(SHEET_PASSWORD, { selectLockedCells: true, selectUnlockedCells: true });
};

export const hideUniqueIdentifierColumn = async (workbook, sheetName, column) => {
  const sheet = workbook.getWorksheet(sheetName);
  if (!sheet) return;
  for (const item of column) {
    let colIndex;
    sheet.getRow(1).eachCell((cell, colNumber) => {
      if (cell.value === item) {
        colIndex = colNumber;
      }
    });
    if (column && sheet.getColumn(colIndex)) {
      sheet.getColumn(colIndex).hidden = true;
    }
  }
};
