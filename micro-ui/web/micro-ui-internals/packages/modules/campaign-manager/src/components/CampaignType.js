import React, { useState, useMemo, useRef, useEffect } from "react";
import { UploadIcon, FileIcon, DeleteIconv2, Toast, Card, Header, Dropdown } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { LabelFieldPair } from "@egovernments/digit-ui-react-components";

const CampaignType = ({ onSelect, formData, ...props }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getStateId();
  const { isLoading, data: projectType } = Digit.Hooks.useCustomMDMS("mz", "HCM-PROJECT-TYPES", [{ name: "projectTypes" }]);
  const [type, setType] = useState(props?.props?.sessionData?.[2]?.projectType || {});
  const [ beneficiaryType , setBeneficiaryType] = useState(props?.props?.sessionData?.[2]?.projectType?.beneficiaryType || "");
  const [ showBeneficiary , setShowBeneficiaryType] = useState(Boolean(props?.props?.sessionData?.[2]?.projectType?.beneficiaryType));
  const handleChange = (data) => {
    setType(data);
    setBeneficiaryType(data?.beneficiaryType);
    setShowBeneficiaryType(true);
  };

  useEffect(() => {
    onSelect("projectType", type);
  }, [type]);

  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_TYPE_HEADER`)}</Header>
      <p>{t(`HCM_CAMPAIGN_TYPE_DESCRIPTION`)}</p>
      <LabelFieldPair>
        <span className="campaign-type">{`${t("HCM_CAMPAIGN_TYPE")}`}</span>
        <Dropdown
          style={{ width: "50%" }}
          t={t}
          option={projectType?.["HCM-PROJECT-TYPES"]?.projectTypes}
          optionKey={"code"}
          selected={type}
          select={(value) => {
            handleChange(value);
          }}
        />
      </LabelFieldPair>
      {showBeneficiary &&
      <LabelFieldPair>
        <div style={{marginRight: "4rem"}}>{`${t("HCM_BENEFICIARY_TYPE")}`}</div>
        <div>{beneficiaryType}</div>
      </LabelFieldPair>
      }
    </React.Fragment>
  );
};

export default CampaignType;
