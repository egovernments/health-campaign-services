import ExcelJS from "exceljs";

export const convertJsonToXlsx = async (jsonData, columnWithStyle) => {
  // Create a new workbook
  const workbook = new ExcelJS.Workbook();

  // Iterate over each sheet in jsonData
  for (const [sheetName, data] of Object.entries(jsonData)) {
    // Create a new worksheet
    const worksheet = workbook.addWorksheet(sheetName);

    // Convert data to worksheet
    for (const row of data) {
      const newRow = worksheet.addRow(row);
      // Apply red font color to the errorColumn if it exists
      let errorColumnIndex = data[0].indexOf(columnWithStyle?.errorColumn);
      if (columnWithStyle?.errorColumn && errorColumnIndex !== -1) {
        const columnIndex = errorColumnIndex + 1; 
        if (columnIndex > 0) {
          const newCell = newRow.getCell(columnIndex);
          if (columnWithStyle.style && newCell)
            for (const key in columnWithStyle.style) newCell[key] = columnWithStyle.style[key];
        }
      }
    }

    // Make the first row bold
    if (worksheet.getRow(1)) {
      worksheet.getRow(1).font = { bold: true };
    }

    // Set column widths
    const columnCount = data?.[0]?.length || 0;
    const wscols = Array(columnCount).fill({ width: 30 });
    wscols.forEach((col, colIndex) => {
      worksheet.getColumn(colIndex + 1).width = col.width;
    });
  }

  // Write the workbook to a buffer
  return await workbook.xlsx.writeBuffer({ compression: true }).then((buffer) => {
    // Create a Blob from the buffer
    return new Blob([buffer], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  });
};
