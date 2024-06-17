import ExcelJS from "exceljs";

// input is a xlsx blob
// options {header}
// header: true -> have seperate header so data will be in key: value pair
export const parseXlsxToJsonMultipleSheets = async (file, options = {}) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();

    reader.onload = async function (event) {
      try {
        const arrayBuffer = event.target.result;
        const workbook = new ExcelJS.Workbook();
        await workbook.xlsx.load(arrayBuffer);

        const jsonData = {};
        workbook.eachSheet((worksheet, sheetId) => {
          const jsonSheetData = [];
          let headers = [];

          worksheet.eachRow((row, rowNumber) => {
            const rowData = row.values.slice(1); // Remove the first element (it's always undefined due to ExcelJS indexing from 1)
            console.log(rowData);
            for (let i = 0; i < rowData.length; i++) {
              if (typeof rowData[i] === "string") {
                rowData[i] = rowData[i].trim();
              }
            }
            console.log(rowData);

            if (options.header && rowNumber === 1) {
              headers = rowData;
            } else if (options.header && headers.length > 0) {
              const rowObject = {};
              headers.forEach((header, index) => {
                rowObject[header] = rowData[index];
              });
              jsonSheetData.push(rowObject);
            } else {
              jsonSheetData.push(rowData);
            }
          });

          if (jsonSheetData.length !== 0 && jsonSheetData?.[0].length !== 0) jsonData[worksheet.name] = jsonSheetData;
        });
        console.log("Json Data: ", jsonData);
        resolve(jsonData);
      } catch (error) {
        console.error(error);
        resolve({ error: true });
      }
    };

    reader.onerror = function (error) {
      console.error(error);
      resolve({ error: true, details: error });
    };

    reader.readAsArrayBuffer(file);
  });
};

// export const parseXlsxToJsonMultipleSheetsForSessionUtil = (file, options, fileData) => {
//   return new Promise((resolve, reject) => {
//     const reader = new FileReader();

//     reader.onload = function (event) {
//       try {
//         const arrayBuffer = event.target.result;
//         const workbook = XLSX.read(arrayBuffer, { type: "arraybuffer" });
//         const jsonData = {};

//         workbook.SheetNames.forEach((sheetName) => {
//           const worksheet = workbook.Sheets[sheetName];
//           // const options = { header: 1 };
//           const jsonSheetData = XLSX.utils.sheet_to_json(worksheet, options);
//           for (let i = 0; i < jsonSheetData.length; i++) {
//             for (let j = 0; j < jsonSheetData[i].length; j++) {
//               const cell = jsonSheetData[i][j];
//               if (typeof cell === "string") {
//                 jsonSheetData[i][j] = cell.trim();
//               }
//             }
//           }
//           if (jsonSheetData.length !== 0 && jsonSheetData?.[0].length !== 0) jsonData[sheetName] = jsonSheetData;
//         });

//         resolve({ jsonData, file: fileData });
//       } catch (error) {
//         resolve({ error: true });
//       }
//     };

//     reader.onerror = function (error) {
//       resolve({ error: true, details: error });
//     };

//     reader.readAsArrayBuffer(file);
//   });
// };
