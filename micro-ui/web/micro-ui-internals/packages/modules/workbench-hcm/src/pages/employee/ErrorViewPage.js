import React, { useEffect , useState } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { Header, Card, Loader , ViewComposer} from "@egovernments/digit-ui-react-components";

import { data } from "../../configs/ViewErrorConfig"

const ErrorViewPage = () => {
    const { t } = useTranslation();
    const location = useLocation();
    const { jobId } = Digit.Hooks.useQueryParams();
     // State to store the data from the API call
    const [allData, setData] = useState(null);
    useEffect(async () => {
      const searchParams = {
        jobId: jobId,
      };
      const allData = await Digit.IngestionService.eventSearch(searchParams);
      setData(allData);
    }, []);

   

    // Render the data once it's available
    let config = null;

    config = data(allData);
    return (
       <React.Fragment>
      <Header className="works-header-view">{t("WORKBENCH_ERROR_VIEW_PAGE")}</Header>
     <ViewComposer data={config} isLoading={false}/>
    </React.Fragment>
    
      );
  };
  export default ErrorViewPage;