import React, { useState, useEffect ,Fragment} from "react";
import { Header , LabelFieldPair } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { RadioButtons } from "@egovernments/digit-ui-components";

const SideEffects = ({ onSelect, formData, control, formState, ...props }) => {
  const { t } = useTranslation();
  const [name, setName] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_NAME?.campaignName || "");
  const [executionCount, setExecutionCount] = useState(0);
  const [startValidation, setStartValidation] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    setName(props?.props?.sessionData?.HCM_CAMPAIGN_NAME?.campaignName);
  }, [props?.props?.sessionData?.HCM_CAMPAIGN_NAME]);

  useEffect(() => {
    if (props?.props?.isSubmitting && !name) {
      setError({ message: "CAMPAIGN_FIELD_ERROR_MANDATORY" });
    } else {
      setError(null);
    }
  }, [props?.props?.isSubmitting]);
  useEffect(() => {
    if (startValidation && !name) {
      setError({ message: "CAMPAIGN_NAME_FIELD_ERROR" });
    } else if (name) {
      setError(null);
      onSelect("campaignName", name);
    }
  }, [name, props?.props?.sessionData?.HCM_CAMPAIGN_NAME?.campaignName]);

  useEffect(() => {
    if (executionCount < 5) {
      onSelect("campaignName", name);
      setExecutionCount((prevCount) => prevCount + 1);
    }
  });

  const options = [
    { code: "Y", label: "HCM_YES" },
    { code: "N", label: "HCM_NO" }
  ];
  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_SIDE_EFFECTS`)}</Header>
      <LabelFieldPair style={{ alignItems: "baseline" }}>
        <RadioButtons
        options={options}
        optionsKey="label"
        name="gender"
        style={{ display: "flex", flexDirection: "column" }}
      />
      </LabelFieldPair>
    </React.Fragment>
  );
};

export default SideEffects;
