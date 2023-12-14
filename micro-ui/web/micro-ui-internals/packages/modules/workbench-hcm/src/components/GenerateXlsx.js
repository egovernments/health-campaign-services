import React from 'react';
import XLSX from 'xlsx';

const GenerateXlsx = ({inputRef, jsonData}) =>  {
  const handleExport = () => {
    // Sample JSON data
    // const jsonData = [
    //     {
    //         "code": 123456789,
    //         "name_in_english": "Test_HCM1",
    //         "name": "TEST_HCM1",
    //         "parent_code": "mz",
    //         "boundary_type": "Provincia",
    //         "boundary_level": 2,
    //         "campaign_start_date": "29/10/2023",
    //         "campaign_end_date": "04/11/2023",
    //         "total_households": 0,
    //         "targeted_households": 0,
    //         "total_individuals": 0,
    //         "targeted_individuals": 0,
    //         "estimated_bednets": 0,
    //         "checklist_target": {
    //           "targets": [
    //             {"beneficiaryType": "DISTRICT_SUPERVISOR.DISTRICT_MONITOR_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "NATIONAL_SUPERVISOR.DISTRICT_MONITOR_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "PROVINCIAL_SUPERVISOR.DISTRICT_MONITOR_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "DISTRICT_SUPERVISOR.AS_MONITORING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "NATIONAL_SUPERVISOR.AS_MONITORING", "totalNo": 2, "targetNo": 2},
    //             {"beneficiaryType": "PROVINCIAL_SUPERVISOR.AS_MONITORING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "DISTRICT_SUPERVISOR.LOCAL_MONITOR_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "NATIONAL_SUPERVISOR.LOCAL_MONITOR_TRAINING", "totalNo": 7, "targetNo": 7},
    //             {"beneficiaryType": "PROVINCIAL_SUPERVISOR.LOCAL_MONITOR_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "DISTRICT_SUPERVISOR.REGISTRATION_TEAM_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "NATIONAL_SUPERVISOR.REGISTRATION_TEAM_TRAINING", "totalNo": 2, "targetNo": 2},
    //             {"beneficiaryType": "PROVINCIAL_SUPERVISOR.REGISTRATION_TEAM_TRAINING", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "DISTRICT_SUPERVISOR.REGISTRATION_BASIC", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "NATIONAL_SUPERVISOR.REGISTRATION_BASIC", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "PROVINCIAL_SUPERVISOR.REGISTRATION_BASIC", "totalNo": 0, "targetNo": 0},
    //             {"beneficiaryType": "NATIONAL_SUPERVISOR.SOCIAL_MOBILIZATION", "totalNo": 0, "targetNo": 0}
    //           ]
    //         },
    //         "insert_time": "14/11/2023",
    //         "update_time": "14/11/2023",
    //         "filename": ""
    //       }
    // ];

    // Create a new worksheet
    const ws = XLSX.utils.json_to_sheet(jsonData);

    // Create a new workbook
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Sheet1');

    // Save the workbook as an XLSX file
    XLSX.writeFile(wb, 'template.xlsx');
  };

    return (
      <div style={{display:"none"}}>
        <h1>JSON to XLSX Converter</h1>
        <button ref={inputRef} onClick={handleExport}>Export to XLSX</button>
      </div>
    );
}

export default GenerateXlsx;