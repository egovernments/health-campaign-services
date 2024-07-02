import React, { Fragment, useState, useEffect, useCallback } from "react";
import {
  Card,
  CardSubHeader,
  CardSectionHeader,
  StatusTable,
  Row,
  Loader,
  LabelFieldPair,
  CardLabel,
  TextInput,
  LoaderWithGap,
} from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import { InfoCard, Modal, Toast } from "@egovernments/digit-ui-components";
import { CloseButton, ModalHeading } from "./CommonComponents";
import { PRIMARY_THEME_COLOR } from "../configs/constants";
import SearchPlanConfig from "../services/SearchPlanConfig";

const page = "microplanDetails";

const MicroplanDetails = ({
  MicroplanName = "default",
  campaignType = Digit.SessionStorage.get("microplanHelperData")?.campaignData?.projectType,
  microplanData,
  setMicroplanData,
  checkDataCompletion,
  setCheckDataCompletion,
  currentPage,
  pages,
  setToast,
  ...props
}) => {
  const { t } = useTranslation();
  const [microplan, setMicroplan] = useState(Digit.SessionStorage.get("microplanData")?.microplanDetails?.name);
  const { state, dispatch } = useMyContext();
  const [modal, setModal] = useState("none");
  // const [toast, setToast] = useState();
  const [showNamingConventions, setShowNamingConventions] = useState(false);
  const [loader, setLoader] = useState(false);

  //fetch campaign data
  const { id = "" } = Digit.Hooks.useQueryParams();
  const { isLoading: isCampaignLoading, data: campaignData } = Digit.Hooks.microplan.useSearchCampaign(
    {
      CampaignDetails: {
        tenantId: Digit.ULBService.getCurrentTenantId(),
        ids: [id],
      },
    },
    {
      enabled: !!id,
      select: (data) => {
        const campaignCard = [
          {
            label: t("CAMPAIGN_NAME"),
            value: data?.campaignName ? data?.campaignName : t("ES_COMMON_NA"),
          },
          {
            label: t(`CAMPAIGN_TYPE`),
            value: data?.projectType ? t(`CAMPAIGN_TYPE_${data?.projectType}`) : t("ES_COMMON_NA"),
          },
          {
            label: t(`CAMPAIGN_BENEFICIARY_TYPE`),
            value: data?.additionalDetails?.beneficiaryType
              ? t(`CAMPAIGN_BENEFICIARY_TYPE${data?.additionalDetails?.beneficiaryType}`)
              : t("ES_COMMON_NA"),
          },
          {
            label: t("CAMPAIGN_DATE"),
            value: data.startDate
              ? data.endDate
                ? `${Digit.DateUtils.ConvertEpochToDate(data.startDate)} - ${Digit.DateUtils.ConvertEpochToDate(data.endDate)}`
                : Digit.DateUtils.ConvertEpochToDate(data.startDate)
              : t("ES_COMMON_NA"),
          },
        ];
        return campaignCard;
      },
    }
  );

  // Set TourSteps
  useEffect(() => {
    const tourData = tourSteps(t)?.[page] || {};
    if (state?.tourStateData?.name === page) return;
    dispatch({
      type: "SETINITDATA",
      state: { tourStateData: tourData },
    });
  }, []);

  // Save data to ssn of data change
  useEffect(() => {
    setMicroplanData((previous) => ({
      ...previous,
      microplanDetails: {
        name: microplan,
      },
    }));
  }, [microplan]);

  useEffect(() => {
    if (checkDataCompletion !== "true" || !setCheckDataCompletion) return;

    updateData(true);
  }, [checkDataCompletion]);

  // UseEffect to add a event listener for keyboard
  useEffect(() => {
    window.addEventListener("keydown", handleKeyPress);

    return () => window.removeEventListener("keydown", handleKeyPress);
  }, [modal]);

  const handleKeyPress = (event) => {
    // if (modal !== "upload-guidelines") return;
    if (["x", "Escape"].includes(event.key)) {
      // Perform the desired action when "x" or "esc" is pressed
      // if (modal === "upload-guidelines")
      setCheckDataCompletion("false");
      setModal("none");
    }
  };
  const validateMicroplanName = async () => {
    try {
      setLoader("LOADING");
      const body = {
        PlanConfigurationSearchCriteria: {
          name: microplan,
          tenantId: Digit.ULBService.getCurrentTenantId(),
        },
      };
      const response = await SearchPlanConfig(body);
      if (response?.PlanConfiguration?.length === 0) {
        return true;
      }
      if (response?.PlanConfiguration?.length === 1) {
        if (response?.PlanConfiguration[0].id === microplanData?.planConfigurationId) {
          setLoader();
          return true;
        }
      }
      setLoader();
      return false;
    } catch (error) {
      console.error("Error while checking microplan name duplication: ", error.message);
      setLoader();
      return false;
    }
  };
  // check if data has changed or not
  const updateData = useCallback(
    async (check) => {
      if (checkDataCompletion !== "true" || !setCheckDataCompletion) return;
      if (!microplan || !validateName(microplan)) {
        setCheckDataCompletion("false");
        setShowNamingConventions(true);
        return setToast({ state: "error", message: t("ERROR_MICROPLAN_NAME_CRITERIA") });
      }
      const valid = await validateMicroplanName();
      if (!valid) {
        setToast({ state: "error", message: t("ERROR_DUPLICATE_MICROPLAN_NAME") });
        setCheckDataCompletion("false");
        return;
      }
      if (check) {
        setMicroplanData((previous) => ({
          ...previous,
          microplanDetails: {
            name: microplan,
          },
        }));
        if (!["", null, undefined].includes(microplan)) {
          setCheckDataCompletion("valid");
        } else {
          setCheckDataCompletion("invalid");
        }
      } else {
        if (!["", null, undefined].includes(microplanData?.microplanDetails?.name)) {
          setCheckDataCompletion("valid");
        } else {
          setCheckDataCompletion("invalid");
        }
      }
    },
    [checkDataCompletion, microplan, microplanData, setCheckDataCompletion, setMicroplanData, validateMicroplanName]
  );

  // const cancelUpdateData = useCallback(() => {
  //   setCheckDataCompletion(false);
  //   setModal('none');
  // }, [setCheckDataCompletion, setModal]);
  function validateName(name) {
    const microplanNamingRegxString = state?.UIConfiguration?.find((item) => item.name === "microplanNamingRegx")?.microplanNamingRegx;
    const namePattern = new RegExp(microplanNamingRegxString);
    return namePattern.test(name);
  }
  const onChangeMicroplanName = (e) => {
    setMicroplan(e.target.value);
  };

  if (isCampaignLoading) {
    return <Loader />;
  }

  return (
    <>
      {loader && <LoaderWithGap text={t(loader)} />}
      <Card
        style={{
          margin: "1rem 0 1rem 0",
          padding: "1.5rem 1.5rem 1.5rem 1.5rem",
        }}
        className="microplan-campaign-detials"
      >
        <CardSectionHeader
          style={{
            margin: "0",
            paddingLeft: "0",
          }}
        >
          {t("CAMPAIGN_DETAILS")}
        </CardSectionHeader>

        <StatusTable style={{ paddingLeft: "0" }}>
          {campaignData?.length > 0 &&
            campaignData?.map((row, idx) => {
              return (
                <Row
                  key={idx}
                  label={row?.label}
                  text={row?.value}
                  rowContainerStyle={{ margin: "0", padding: "0", height: "2.4rem", justifyContent: "flex-start" }}
                  className="border-none"
                  last={idx === campaignData?.length - 1}
                />
              );
            })}
        </StatusTable>
      </Card>
      <Card
        style={{
          margin: "1.5rem 0 1rem 0",
          padding: "1.5rem 1.5rem 1.5rem 1.5rem",
        }}
        className="microplan-name"
      >
        <CardSubHeader style={{ marginBottom: "1.5rem" }}>{t("NAME_YOUR_MP")}</CardSubHeader>
        <p style={{ marginBottom: "1.5rem" }}>{t("MP_FOOTER")}</p>
        <LabelFieldPair>
          <CardLabel style={{ fontWeight: "500", display: "flex", alignItems: "center", margin: 0 }}>
            {`${t("NAME_OF_MP")}  `} <p style={{ color: "red", margin: 0, paddingLeft: "0.15rem" }}> *</p>
          </CardLabel>
          <div style={{ width: "100%", maxWidth: "960px", height: "fit-content" }}>
            <TextInput
              t={t}
              style={{ width: "100%", margin: 0 }}
              type={"text"}
              isMandatory={false}
              name="name"
              value={microplan}
              onChange={onChangeMicroplanName}
              placeholder={t("MICROPLAN_NAME_INPUT_PLACEHOLDER")}
              disable={false}
            />
          </div>
        </LabelFieldPair>
      </Card>
      <InfoCard
        label={t("MICROPLAN_NAMING_CONVENTION")}
        style={{ margin: "1.5rem 0 0 0", width: "100%", maxWidth: "unset" }}
        additionalElements={[
          <div className="microplan-naming-conventions">
            {state?.UIConfiguration?.find((item) => item.name === "microplanNamingConvention")?.microplanNamingConvention?.map((item, index) => (
              <div key={`container-${index}`} className="microplan-naming-convention-instruction-list-container">
                <p key={`number-${index}`} className="microplan-naming-convention-instruction-list number">
                  {t(index + 1)}.
                </p>
                <p key={`text-${index}`} className="microplan-naming-convention-instruction-list text">
                  {t(item)}
                </p>
              </div>
            ))}
          </div>,
        ]}
      />
    </>
  );
};

export default MicroplanDetails;
