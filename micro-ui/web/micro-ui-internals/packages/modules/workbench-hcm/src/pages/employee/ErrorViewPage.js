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
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    useEffect(() => {
      // Create a variable to track whether the component is mounted
      let isMounted = true;
    
      const fetchData = async () => {
        setLoading(true);
        try {
          const searchParams = {
            jobId: jobid,
          };
    
          const fetchedData = await Digit.IngestionService.eventSearch(searchParams);
    
          // Check if the component is still mounted before updating state
          if (isMounted) {
            setData(fetchedData);
          }
    
        } catch (error) {
          // Check if the component is still mounted before updating state
          if (isMounted) {
            setError(error);
          }
        } finally {
          // Check if the component is still mounted before updating state
          if (isMounted) {
            setLoading(false);
          }
        }
      };
    
      // Fetch data when the component mounts
      fetchData();
    
      // Cleanup function to set isMounted to false when the component is unmounted
      return () => {
        isMounted = false;
      };
    }, [jobid]); // Add jobid as a dependency if it's being used in fetchData
    

    const downloadExcel = async ()=>{
      const searchParams = {
        tenantId: Digit.ULBService.getCurrentTenantId(),
        fileStoreIds: allData?.EventHistory[0]?.fileStoreId
      }
      const fileStoreData = await Digit.IngestionService.fileStoreSearch(searchParams);

      downloadFile(fileStoreData?.fileStoreIds[0]?.url, 'Download.xlsx');


    }
    const downloadFile = (url, fileName) => {
      // Create a hidden link element
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName||'download.xlsx';
      link.target = '_blank';

    
      // Append the link to the document
      document.body.appendChild(link);
    
      // Trigger a click event to start the download
      link.click();
    
      // Remove the link from the document
      document.body.removeChild(link);
    };

   

    // Render the data once it's available
    if(loading){
      <Loader />
    }
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