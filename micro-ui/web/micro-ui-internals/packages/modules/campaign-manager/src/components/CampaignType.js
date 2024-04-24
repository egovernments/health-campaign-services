import React, { useState, useMemo, useRef, useEffect } from "react";
import { UploadIcon, FileIcon, DeleteIconv2, Toast, Card, Header, Dropdown } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { LabelFieldPair } from "@egovernments/digit-ui-react-components";

const CampaignSelection = ({ onSelect, formData, ...props }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getStateId();
  const { isLoading, data: projectType } = Digit.Hooks.useCustomMDMS("mz", "HCM-PROJECT-TYPES", [{ name: "projectTypes" }]);
  const [type, setType] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType || {});
  const [beneficiaryType, setBeneficiaryType] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType || "");
  const [showBeneficiary, setShowBeneficiaryType] = useState(Boolean(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType));
  const [executionCount, setExecutionCount] = useState(0);

  useEffect(() => {
    setType(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType);
    setBeneficiaryType(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType);
    setShowBeneficiaryType(Boolean(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType));
  }, [props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType]);

  const handleChange = (data) => {
    setType(data);
    setBeneficiaryType(data?.beneficiaryType);
    setShowBeneficiaryType(true);
  };

  useEffect(() => {
    onSelect("projectType", type);
  }, [type]);
  
  useEffect(() => {
    if (executionCount < 5) {
      onSelect("projectType", type);
      setExecutionCount(prevCount => prevCount + 1);
    }
  });
  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_TYPE_HEADER`)}</Header>
      <p className="description-type">{t(`HCM_CAMPAIGN_TYPE_DESCRIPTION`)}</p>
      <LabelFieldPair>
        <div className="campaign-type">
          <span>{`${t("HCM_CAMPAIGN_TYPE")}`}</span>
          <span className="mandatory-span">*</span>
        </div>
        <Dropdown
          style={{ width: "40rem" }}
          t={t}
          option={projectType?.["HCM-PROJECT-TYPES"]?.projectTypes}
          optionKey={"code"}
          selected={type}
          select={(value) => {
            handleChange(value);
          }}
        />
      </LabelFieldPair>
      {showBeneficiary && (
        <LabelFieldPair>
          <div className="beneficiary-type">{`${t("HCM_BENEFICIARY_TYPE")}`}</div>
          <div>{t(`CAMPAIGN_TYPE_${beneficiaryType}`)}</div>
        </LabelFieldPair>
      )}
    </React.Fragment>
  );
};

export default CampaignSelection;
