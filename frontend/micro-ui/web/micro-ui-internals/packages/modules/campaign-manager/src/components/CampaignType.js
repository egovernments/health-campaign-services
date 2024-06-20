import React, { useState, useMemo, useRef, useEffect } from "react";
import { UploadIcon, FileIcon, DeleteIconv2, Toast, Card, Header } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { LabelFieldPair } from "@egovernments/digit-ui-react-components";
import { Button, CardText, Dropdown, ErrorMessage, PopUp } from "@egovernments/digit-ui-components";

const CampaignSelection = ({ onSelect, formData, formState, ...props }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getStateId();
  const { isLoading, data: projectType } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-PROJECT-TYPES", [{ name: "projectTypes" }]);
  const [type, setType] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType || {});
  const [beneficiaryType, setBeneficiaryType] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType || "");
  const [showBeneficiary, setShowBeneficiaryType] = useState(Boolean(props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType));
  const [executionCount, setExecutionCount] = useState(0);
  const [error, setError] = useState(null);
  const [startValidation, setStartValidation] = useState(null);
  const [showPopUp, setShowPopUp] = useState(null);
  const [canUpdate, setCanUpdate] = useState(null);

  useEffect(() => {
    if (props?.props?.isSubmitting && !type) {
      setError({ message: "CAMPAIGN_FIELD_MANDATORY" });
    }
  }, [props?.props?.isSubmitting]);
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
      <Header>{t(`HCM_CAMPAIGN_TYPE_HEADER`)}</Header>
      <p className="description-type">{t(`HCM_CAMPAIGN_TYPE_DESCRIPTION`)}</p>
      <LabelFieldPair>
        <div className="campaign-type">
          <span>{`${t("HCM_CAMPAIGN_TYPE")}`}</span>
          <span className="mandatory-span">*</span>
        </div>
        <div
          className="campaign-type-wrapper"
          onClick={(e) => {
            if (props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType && !canUpdate) {
              setShowPopUp(true);
              return;
            }
            return;
          }}
          onFocus={(e) => {
            if (props?.props?.sessionData?.HCM_CAMPAIGN_TYPE?.projectType && !canUpdate) {
              setShowPopUp(true);
              return;
            }
            return;
          }}
        >
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
      {showBeneficiary && (
        <LabelFieldPair>
          <div className="beneficiary-type">{`${t("HCM_BENEFICIARY_TYPE")}`}</div>
          <div>{t(`CAMPAIGN_TYPE_${beneficiaryType}`)}</div>
        </LabelFieldPair>
      )}
      {showPopUp && (
        <PopUp
          className={"boundaries-pop-module"}
          type={"default"}
          heading={t("ES_CAMPAIGN_UPDATE_TYPE_MODAL_HEADER")}
          children={[
            <div>
              <CardText style={{ margin: 0 }}>{t("ES_CAMPAIGN_UPDATE_TYPE_MODAL_TEXT") + " "}</CardText>
            </div>,
          ]}
          onOverlayClick={() => {
            setShowPopUp(false);
          }}
          footerChildren={[
            <Button
              className={"campaign-type-alert-button"}
              type={"button"}
              size={"large"}
              variation={"secondary"}
              label={t("ES_CAMPAIGN_BOUNDARY_MODAL_BACK")}
              onClick={() => {
                setShowPopUp(false);
                setCanUpdate(true);
              }}
            />,
            <Button
              className={"campaign-type-alert-button"}
              type={"button"}
              size={"large"}
              variation={"primary"}
              label={t("ES_CAMPAIGN_BOUNDARY_MODAL_SUBMIT")}
              onClick={() => {
                setShowPopUp(false);
                setCanUpdate(false);
              }}
            />,
          ]}
          sortFooterChildren={true}
        ></PopUp>
      )}
    </React.Fragment>
  );
};

export default CampaignSelection;
