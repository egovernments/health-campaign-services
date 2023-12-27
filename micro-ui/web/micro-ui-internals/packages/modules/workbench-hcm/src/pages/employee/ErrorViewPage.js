import React, { useEffect , useState } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { Header, Card, Loader , ViewComposer, ActionBar, Button} from "@egovernments/digit-ui-react-components";

import { data } from "../../configs/ViewErrorConfig"

const ErrorViewPage = () => {
    const { t } = useTranslation();
    const location = useLocation();
    const { jobid } = Digit.Hooks.useQueryParams();
     // State to store the data from the API call
    const [allData, setData] = useState(null);
    const [ingestionNumber, setIngestionNumber] = useState(null);
    useEffect(async () => {
      const searchParams = {
        jobId: jobid,
      };
      
      const allData = await Digit.IngestionService.eventSearch(searchParams);
      setData(allData);
      setIngestionNumber(allData);
    },[jobid]);
    const downloadExcel = async ()=>{
      const searchParams = {
        tenantId: Digit.ULBService.getCurrentTenantId(),
        fileStoreIds: allData?.EventHistory[0]?.fileStoreId
      }
      const fileStoreData = await Digit.IngestionService.fileStoreSearch(searchParams);
      console.log(allData?.EventHistory[0]?.fileStoreId);
      downloadFile(fileStoreData?.fileStoreIds[0]?.url, 'Download.xlsx');


    }
    const downloadFile = (url, fileName) => {
      // Create a hidden link element
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName||'download.xlsx';
      link.target = '_blank';
      console.log(link.download);
    
      // Append the link to the document
      document.body.appendChild(link);
    
      // Trigger a click event to start the download
      link.click();
    
      // Remove the link from the document
      document.body.removeChild(link);
    };

   

    // Render the data once it's available
    let config = null;

    config = data(allData);
    return (
       <React.Fragment>
      <Header className="works-header-view">{t("WORKBENCH_ERROR_VIEW_PAGE")}</Header>
      <div className="download-button">
  <Button 
    // className="download-button"
    label={"Download Excel"}
    variation="secondary"
    onButtonClick={downloadExcel}
  />
</div>
     <ViewComposer data={config} isLoading={false}/>
    </React.Fragment>
    
      );
  };
  export default ErrorViewPage;