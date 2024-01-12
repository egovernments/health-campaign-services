import React from 'react';
import XLSX from 'xlsx';

const GenerateXlsx = ({inputRef, jsonData,ingestionType}) =>  {
  const handleExport = () => {
 

    // Create a new worksheet
    const ws = XLSX.utils.json_to_sheet(jsonData);

    // Create a new workbook
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Sheet1');

    // Save the workbook as an XLSX file
    switch (ingestionType) {
      case 'facility':
        XLSX.writeFile(wb, 'facilityIngestion.xlsx');
        break;
      case 'user':
        XLSX.writeFile(wb, 'userIngestion.xlsx');
        break;
      case 'project':
        XLSX.writeFile(wb, 'projectIngestion.xlsx');
        break;
      case 'boundary':
        XLSX.writeFile(wb, 'boundaryIngestion.xlsx');
        break;
      default:
        XLSX.writeFile(wb, 'template.xlsx');
        break;
    }
    




  };

    return (
      <div style={{display:"none"}}>
        <h1>JSON to XLSX Converter</h1>
        <button ref={inputRef} onClick={handleExport}>Export to XLSX</button>
      </div>
    );
}

export default GenerateXlsx;