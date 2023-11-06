import { Header } from "@egovernments/digit-ui-react-components";
import React from "react";
import FileDropArea from "./FileDropArea";


function DataIngestionComponent({ingestionType}) {
    return (
        <div className="ingestion-container">
            {/* <Header>{facility}</Header> */}
            <FileDropArea ingestionType={ingestionType}/>
        </div>
    )
}

export default DataIngestionComponent;