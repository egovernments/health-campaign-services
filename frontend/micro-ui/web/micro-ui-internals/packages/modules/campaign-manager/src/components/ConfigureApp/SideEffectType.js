import React, { useState, useMemo, useRef, useEffect } from "react";
import { Header ,LabelFieldPair } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { Dropdown, ErrorMessage } from "@egovernments/digit-ui-components";

const SideEffectType = ({ onSelect, formData, formState, ...props }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getStateId();
  const { isLoading, data: projectType } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-PROJECT-TYPES", [{ name: "projectTypes" }]);
  const [type, setType] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType || {});
  const [beneficiaryType, setBeneficiaryType] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType || "");
  const [showBeneficiary, setShowBeneficiaryType] = useState(Boolean(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType));
  const [executionCount, setExecutionCount] = useState(0);
  const [error, setError] = useState(null);
  const [startValidation, setStartValidation] = useState(null);

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
    if (!type && startValidation) {
      setError({ message: "CAMPAIGN_FIELD_MANDATORY" });
    } else {
      setError(null);
      onSelect("projectType", type);
    }
  }, [type]);

  useEffect(() => {
    if (executionCount < 5) {
      onSelect("projectType", type);
      setExecutionCount((prevCount) => prevCount + 1);
    }
  });
  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_SIDE_EFFECT_TYPE_HEADER`)}</Header>
      <p className="description-type">{t(`HCM_CAMPAIGN_SIDE_EFFECTTYPE_DESCRIPTION`)}</p>
      <LabelFieldPair>
        <div className="campaign-type">
          <span>{`${t("HCM_CAMPAIGN_SIDE_EFFECT_TYPE")}`}</span>
          <span className="mandatory-span">*</span>
        </div>
        <div>
          <Dropdown
            style={!showBeneficiary ? { width: "40rem", paddingBottom: 0, marginBottom: 0 } : { width: "40rem", paddingBottom: "1rem" }}
            variant={error ? "error" : ""}
            t={t}
            option={projectType?.["HCM-PROJECT-TYPES"]?.projectTypes}
            optionKey={"code"}
            selected={type}
            select={(value) => {
              setStartValidation(true);
              handleChange(value);
            }}
          />
          {error?.message && <ErrorMessage message={t(error?.message)} showIcon={true} />}
        </div>
      </LabelFieldPair>
    </React.Fragment>
  );
};

export default SideEffectType;
