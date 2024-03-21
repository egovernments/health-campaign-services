import React, { useState, useEffect } from "react";
import { Header, TextInput } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { LabelFieldPair } from "@egovernments/digit-ui-react-components";

const CampaignName = ({ onSelect, formData, control, ...props }) => {
  const { t } = useTranslation();
  const [name, setName] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_NAME?.campaignName || null);
  

  // useEffect(() => {
  //   if (name === null) {
  //     onSelect("campaignName", "name");
  //   }
  // }, []);

  useEffect(() => {
    onSelect("campaignName", name);
  }, [name]);

  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_NAME_HEADER`)}</Header>
      <p>{t(`HCM_CAMPAIGN_NAME_DESCRIPTION`)}</p>
      <LabelFieldPair>
        <span className="name-container">{`${t("HCM_CAMPAIGN_NAME")}`}</span>
        <TextInput style={{ width: "50%" }} name="campaignName" value={name} onChange={(event) => setName(event.target.value)} />
      </LabelFieldPair>
    </React.Fragment>
  );
};

export default CampaignName;
