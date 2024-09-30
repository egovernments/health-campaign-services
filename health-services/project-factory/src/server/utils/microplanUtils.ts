export function lockSheet(workbook: any) {
  workbook.worksheets.forEach((sheet: any) => {
    const statusCell = findStatusColumn(sheet); // Find the status column

    if (!statusCell) {
      // Lock the entire sheet if no "#status#" found
      sheet.protect('passwordhere', {
        selectLockedCells: true,
        selectUnlockedCells: false
      });
    } else {
      // Lock the entire sheet but allow selecting unlocked cells
      sheet.protect('passwordhere', {
        selectLockedCells: true,
        selectUnlockedCells: true // Allow selecting unlocked cells
      });

      // Lock the first row
      sheet.getRow(1).eachCell({ includeEmpty: true }, (cell: any) => {
        cell.protection = { locked: true };
      });

      // Lock every column starting from the "#status#" column
      const statusColIndex = statusCell.col;
      for (let col = statusColIndex; col <= sheet.columnCount; col++) {
        sheet.getColumn(col).eachCell({ includeEmpty: true }, (cell: any) => {
          cell.protection = { locked: true };
        });
      }

      // Unlock the rest of the sheet (if needed)
      sheet.eachRow((row: any, rowIndex: any) => {
        if (rowIndex > 1) {  // Skip first row
          row.eachCell({ includeEmpty: true }, (cell: any) => {
            if (cell.col < statusColIndex) {  // Unlock cells before the status column
              cell.protection = { locked: false };
            }
          });
        }
      });
    }
  });
}

// Helper function to find the column containing "#status#"
function findStatusColumn(sheet: any) {
  let statusCell: any = null;

  // Loop through each row
  sheet.eachRow((row: any, rowIndex: any): any => {
    // Loop through each cell in the row
    row.eachCell((cell: any, colIndex: any) => {
      // If "#status#" is found, capture the row and column
      if (cell.value === "#status#") {
        statusCell = { row: rowIndex, col: colIndex };
      }
    });

    // If the status cell is found, stop further iteration
    if (statusCell) {
      return false; // This will only break the row loop, not the function
    }
  });

  // Return the found statusCell, or null if not found
  return statusCell;
}


