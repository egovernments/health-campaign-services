import { InboxSearchComposer } from "@egovernments/digit-ui-react-components";
import { Header} from "@egovernments/digit-ui-react-components";
import React, { useTransition } from "react";
import IngestionInboxConfig from "../../configs/IngestionInboxConfig";

const IngestionInbox = () => {
    const {t} = useTransition;
    const config = IngestionInboxConfig();

    return (
        <React.Fragment>
        <div>
            <Header>Ingestion Inbox</Header>
        </div>
        <div className="inbox-search-wrapper">
            <InboxSearchComposer configs = {config}></InboxSearchComposer>
        </div>
        </React.Fragment>
    )
}

export default IngestionInbox;