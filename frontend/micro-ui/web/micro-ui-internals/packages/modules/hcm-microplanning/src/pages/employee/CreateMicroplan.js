import React, { useState, useEffect, useCallback, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { timeLineOptions } from "../../configs/timeLineOptions.json";
import Upload from "../../components/Upload";
import Hypothesis from "../../components/Hypothesis";
import RuleEngine from "../../components/RuleEngine";
import Mapping from "../../components/Mapping";
import Navigator from "../../components/Nagivator";
import { Toast } from "@egovernments/digit-ui-components";
import MicroplanPreview from "../../components/MicroplanPreview";
import MicroplanDetails from "../../components/MicroplanDetails";

export const components = {
  MicroplanDetails,
  Upload,
  Hypothesis,
  RuleEngine,
  Mapping,
  MicroplanPreview,
};

import MicroplanCreatedScreen from "../../components/MicroplanCreatedScreen";
import { LoaderWithGap, Tutorial } from "@egovernments/digit-ui-react-components";
import { useMyContext } from "../../utils/context";
import { updateSessionUtils } from "../../utils/updateSessionUtils";
import { render } from "react-dom";

// Main component for creating a microplan
const CreateMicroplan = () => {
  // Fetching data using custom MDMS hook
  const { id: campaignId = "" } = Digit.Hooks.useQueryParams();
  const { mutate: CreateMutate } = Digit.Hooks.microplan.useCreatePlanConfig();
  const { mutate: UpdateMutate } = Digit.Hooks.microplan.useUpdatePlanConfig();
  const [toRender, setToRender] = useState("navigator");
  const { t } = useTranslation();

  // States
  const [microplanData, setMicroplanData] = useState();
  const [operatorsObject, setOperatorsObject] = useState([]);
  const [toast, setToast] = useState();
  const [checkForCompleteness, setCheckForCompletion] = useState([]);
  const [loaderActivation, setLoaderActivation] = useState(false);
  const { state } = useMyContext();

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
    }
  );
  // to save microplan helper data to ssn
  useEffect(() => {
    if (campaignData) Digit.SessionStorage.set("microplanHelperData", { ...Digit.SessionStorage.get("microplanHelperData"), campaignData });
  }, [campaignData]);

  const campaignType = campaignData?.projectType;

  // request body for boundary hierarchy api
  const reqCriteria = {
    url: `/boundary-service/boundary-hierarchy-definition/_search`,
    params: {},
    body: {
      BoundaryTypeHierarchySearchCriteria: {
        tenantId: Digit.ULBService.getStateId(),
        hierarchyType: campaignData?.hierarchyType,
      },
    },
    config: {
      enabled: !!campaignData?.hierarchyType,
      select: (data) => {
        return data?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.map((item) => item?.boundaryType) || {};
      },
    },
  };
  const { isLoading: ishierarchyLoading, data: hierarchyData } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  // useEffect to initialise the data from MDMS
  useEffect(() => {
    let temp;
    if (!state || !state.UIConfiguration) return;
    const UIConfiguration = state?.UIConfiguration || {};
    if (UIConfiguration) temp = UIConfiguration.find((item) => item.name === "ruleConfigure");
    if (!temp?.ruleConfigureOperators) return;
    setOperatorsObject(temp.ruleConfigureOperators);
  }, []);

  // useEffect to store data in session storage
  useEffect(() => {
    if (!microplanData) return;
    Digit.SessionStorage.set("microplanData", microplanData);
  }, [microplanData]);

  // useEffect to store data in session storage
  useEffect(() => {
    const data = Digit.SessionStorage.get("microplanData");
    if (data?.microplanStatus === "GENERATED") setToRender("success-screen");
    let statusData = {};
    let toCheckCompletenesData = [];
    timeLineOptions.forEach((item) => {
      statusData[item.name] = false;
      if (item?.checkForCompleteness) toCheckCompletenesData.push(item.name);
    });
    if (data && data?.status) {
      if (Object.keys(data?.status) === 0) setMicroplanData({ ...data, status: statusData });
      else setMicroplanData({ ...data });
    }
    setCheckForCompletion(toCheckCompletenesData);
  }, []);

  // An addon function to pass to Navigator
  const nextEventAddon = useCallback(
    async (currentPage, checkDataCompletion, setCheckDataCompletion) => {
      if (!microplanData) {
        setCheckDataCompletion("perform-action");
        return;
      }
      setMicroplanData((previous) => ({
        ...previous,
        status: { ...previous?.status, [currentPage?.name]: checkDataCompletion === "valid" },
      }));

      setCheckDataCompletion("false");
      let body = Digit.Utils.microplan.mapDataForApi(
        microplanData,
        operatorsObject,
        microplanData?.microplanDetails?.name,
        campaignId,
        "DRAFT",
        microplanData?.planConfigurationId ? "update" : "create"
      );
      if (!Digit.Utils.microplan.planConfigRequestBodyValidator(body, state, campaignType)) {
        setCheckDataCompletion("perform-action");
        return;
      }
      setLoaderActivation(true);
      try {
        if (!microplanData?.planConfigurationId) {
          await createPlanConfiguration(body, setCheckDataCompletion, setLoaderActivation, state);
        } else if (microplanData?.planConfigurationId) {
          await updatePlanConfiguration(body, setCheckDataCompletion, setLoaderActivation, state);
        }
      } catch (error) {
        console.error("Failed to create/update plan configuration:", error);
      }
    },
    [microplanData, UpdateMutate, CreateMutate]
  );

  const createPlanConfiguration = async (body, setCheckDataCompletion, setLoaderActivation, state) => {
    await CreateMutate(body, {
      onSuccess: async (data) => {
        const readMeConstant = state?.CommonConstants?.find((item) => item?.name === "readMeSheetName");
        const additionalProps = {
          hierarchyData: hierarchyData,
          t,
          campaignType,
          campaignData,
          readMeSheetName: readMeConstant ? readMeConstant.value : undefined,
        };
        const computedSession = await updateSessionUtils.computeSessionObject(data?.PlanConfiguration[0], state, additionalProps);
        if (computedSession) {
          computedSession.microplanStatus = "DRAFT";
          setMicroplanData(computedSession);
        } else {
          console.error("Failed to compute session data.");
        }
        setLoaderActivation(false);
        setCheckDataCompletion("perform-action");
      },
      onError: (error, variables) => {
        setToast({
          message: t("ERROR_DATA_NOT_SAVED"),
          state: "error",
          transitionTime: 10000,
        });
        setTimeout(() => {
          setLoaderActivation(false);
          setCheckDataCompletion("false");
        }, 2000);
      },
    });
  };

  const updatePlanConfiguration = async (body, setCheckDataCompletion, setLoaderActivation, state) => {
    body.PlanConfiguration["id"] = microplanData?.planConfigurationId;
    body.PlanConfiguration["auditDetails"] = microplanData?.auditDetails;
    await UpdateMutate(body, {
      onSuccess: async (data) => {
        const readMeConstant = state?.CommonConstants?.find((item) => item?.name === "readMeSheetName");
        const additionalProps = {
          hierarchyData: hierarchyData,
          t,
          campaignType,
          campaignData,
          readMeSheetName: readMeConstant ? readMeConstant.value : undefined,
        };
        const computedSession = await updateSessionUtils.computeSessionObject(data?.PlanConfiguration[0], state, additionalProps);
        if (computedSession) {
          computedSession.microplanStatus = "DRAFT";
          setMicroplanData(computedSession);
        } else {
          console.error("Failed to compute session data.");
        }
        setLoaderActivation(false);
        setCheckDataCompletion("perform-action");
      },
      onError: (error, variables) => {
        setToast({
          message: t("ERROR_DATA_NOT_SAVED"),
          state: "error",
          transitionTime: 10000,
        });
        setTimeout(() => {
          setLoaderActivation(false);
          setCheckDataCompletion("false");
        }, 2000);
      },
    });
  };

  const setCurrentPageExternally = useCallback(
    (props) => {
      switch (props.method) {
        case "set": {
          let currentPage;
          const data = Digit.SessionStorage.get("microplanData");
          if (data?.currentPage) currentPage = data.currentPage;
          if (currentPage && props?.setCurrentPage && timeLineOptions.find((item) => item.id === currentPage?.id)) {
            props.setCurrentPage(currentPage);
            return true;
          }
          break;
        }
        case "save": {
          if (props.currentPage) {
            setMicroplanData((previous) => ({ ...previous, currentPage: props.currentPage }));
          }
          break;
        }
      }
    },
    [microplanData, setMicroplanData, Navigator]
  );

  const completeNavigation = useCallback(() => {
    setToRender("success-screen");
  }, [setToRender]);

  return (
    <>
      <div className="create-microplan">
        {toRender === "navigator" && (
          <Navigator
            config={timeLineOptions}
            checkDataCompleteness={true}
            stepNavigationActive={true}
            components={components}
            childProps={{ microplanData, setMicroplanData, campaignType, MicroplanName: microplanData?.microplanDetails?.name, setToast }}
            nextEventAddon={nextEventAddon}
            setCurrentPageExternally={setCurrentPageExternally}
            completeNavigation={completeNavigation}
            setToast={setToast}
          />
        )}
        {toRender === "success-screen" && <MicroplanCreatedScreen microplanData={microplanData} />}
      </div>
      {toast && (
        <Toast
          style={{ zIndex: "999991" }}
          label={toast.message}
          type={toast.state}
          transitionTime={toast?.transitionTime}
          onClose={() => setToast(undefined)}
        />
      )}
      {loaderActivation && <LoaderWithGap text={"LOADING"} />}
    </>
  );
};

export default CreateMicroplan;
