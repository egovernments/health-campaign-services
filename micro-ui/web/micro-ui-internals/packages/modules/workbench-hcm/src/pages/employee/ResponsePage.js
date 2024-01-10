import React from "react";
import { useTranslation } from "react-i18next";
import { Banner } from "@egovernments/digit-ui-react-components"; // Import the Banner component you provided
import { withRouter } from "react-router-dom";
const IngestionResponse = ({location}) => {

    const { t } = useTranslation();
   
     const responseObj= location?.state?.responseData;

 

    const isIngestionSubmitted = responseObj?.ResponseInfo?.status === "Success";

    const ingestionNumber =isIngestionSubmitted? location?.state?.responseData?.ingestionNumber: "";

    const message = isIngestionSubmitted

        ? t("HCM_WORKBENCH_SUCCESS_MESSAGE")
        : t("HCM_WORKBENCH_ERROR_MESSAGE");
    const applicationNumber = isIngestionSubmitted
    ? t("HCM_WORKBENCH_INGESTION_NUMBER")+ " :" + ingestionNumber
    : "";

    return (
        <div>
            <Banner
                successful={isIngestionSubmitted}
                message={message}
                // customText={customText}
                // challanNo= {"Challan Number : " + challanNumber }
                applicationNumber={applicationNumber}
            // multipleResponseIDs={isApplicationSubmitted ? null : [responseObj?.responseInfo?.resMsgId]}
            />
        </div>
    );
};
export default withRouter(IngestionResponse);