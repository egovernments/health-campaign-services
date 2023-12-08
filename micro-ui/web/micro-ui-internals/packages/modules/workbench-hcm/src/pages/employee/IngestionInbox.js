import { InboxSearchComposer } from "@egovernments/digit-ui-react-components";
import { Header} from "@egovernments/digit-ui-react-components";
import React, { useTransition } from "react";
import { useTranslation } from "react-i18next";

import IngestionInboxConfig from "../../configs/IngestionInboxConfig";

const IngestionInbox = () => {
    const { t } = useTranslation();
    const config = IngestionInboxConfig();

    return (
        <React.Fragment>
        <div>
            <Header>{t("WORKBENCH_INGESTION_INBOX")}</Header>
        </div>
        <div className="inbox-search-wrapper">
            <InboxSearchComposer configs = {config}></InboxSearchComposer>
        </div>
        </React.Fragment>
    )
}

export default IngestionInbox;