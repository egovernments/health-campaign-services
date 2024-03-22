import React, { useMemo, useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Button, Header, InboxSearchComposer, Loader } from "@egovernments/digit-ui-react-components";
import { useLocation } from "react-router-dom";
import { myCampaignConfig } from "../../configs/myCampaignConfig";

const MyCampaign = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const moduleName = Digit?.Utils?.getConfigModuleName() || "commonCampaignUiConfig";
  const tenant = Digit.ULBService.getStateId();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [config, setConfig] = useState(myCampaignConfig?.myCampaignConfig?.[0]);
  const [tabData, setTabData] = useState(myCampaignConfig?.myCampaignConfig?.map((i, n) => ({ key: n, label: i.label, active: n === 0 ? true : false })));

  const onTabChange = (n) => {
    setTabData((prev) => prev.map((i, c) => ({ ...i, active: c === n ? true : false })));
    setConfig(myCampaignConfig?.myCampaignConfig?.[n]);
  };

  return (
    <React.Fragment>
      <Header styles={{ fontSize: "32px" }}>
        {t(config?.label)}
        {<span className="inbox-count">{location?.state?.count ? location?.state?.count : 0}</span>}
      </Header>
      <div className="inbox-search-wrapper">
        <InboxSearchComposer configs={config} showTab={true} tabData={tabData} onTabChange={onTabChange}></InboxSearchComposer>
      </div>
    </React.Fragment>
  );
};

export default MyCampaign;
