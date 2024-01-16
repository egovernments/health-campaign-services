import React from "react";
import { useTranslation } from "react-i18next";
import { Banner , Card, ActionBar, SubmitBar, ArrowRightInbox } from "@egovernments/digit-ui-react-components"; // Import the Banner component you provided
import { withRouter , Link , useHistory} from "react-router-dom";
const IngestionResponse = ({location}) => {

    const { t } = useTranslation();
    const history = useHistory();

   
     const responseObj= location?.state?.responseData;

 

    const isIngestionSubmitted = responseObj?.ResponseInfo?.status === "Success";

    const ingestionNumber =isIngestionSubmitted? location?.state?.responseData?.ingestionNumber: "";
    const goToHome = () => {
        history.push({
            pathname: `/${window?.contextPath}/employee`

        });
    };

    const message = isIngestionSubmitted

        ? t("HCM_WORKBENCH_SUCCESS_MESSAGE")
        : t("HCM_WORKBENCH_ERROR_MESSAGE");
    const applicationNumber = isIngestionSubmitted
    ? t("HCM_WORKBENCH_INGESTION_NUMBER")+ " :" + ingestionNumber
    : "";

    return (
        <React.Fragment>
        <Card>
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
        <div className="link " >
                        <ArrowRightInbox />
                        <Link to={`/${window.contextPath}/employee/hcmworkbench/inbox`}>
                            {t(" HCM_WORKBENCH_INBOX")}
                        </Link>
                    </div>
        </Card>
        <ActionBar>
                <SubmitBar label={t("HCM_WORKBENCH_HOME")} onSubmit={goToHome} />
            </ActionBar>
        </React.Fragment>
    );
};
export default withRouter(IngestionResponse);