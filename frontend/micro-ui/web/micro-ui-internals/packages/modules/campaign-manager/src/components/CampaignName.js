import React, { useState, useEffect ,Fragment} from "react";
import { Header } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { LabelFieldPair } from "@egovernments/digit-ui-react-components";
import { ErrorMessage, FieldV1 } from "@egovernments/digit-ui-components";

const CampaignName = ({ onSelect, formData, control, formState, ...props }) => {
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

  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_NAME_HEADER`)}</Header>
      <p className="name-description">{t(`HCM_CAMPAIGN_NAME_DESCRIPTION`)}</p>
      <LabelFieldPair style={{ alignItems: "baseline" }}>
        <div className="name-container">
          <span>{`${t("HCM_CAMPAIGN_NAME")}`}</span>
          <span className="mandatory-span">*</span>
        </div>
        <FieldV1
          type="text"
          error={error?.message ? t(error?.message) : ""}
          style={{ width: "40rem", marginBottom: "0" }}
          populators={{ name: "campaignName" }}
          placeholder={t("HCM_CAMPAIGN_NAME_EXAMPLE")}
          value={name}
          onChange={(event) => {
            setStartValidation(true);
            setName(event.target.value);
          }}
        />
      </LabelFieldPair>
    </React.Fragment>
  );
};

export default CampaignName;
