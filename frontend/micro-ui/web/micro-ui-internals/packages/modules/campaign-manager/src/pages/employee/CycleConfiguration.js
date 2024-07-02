import React, { useReducer, Fragment, useEffect, useState } from "react";
import { CardText, LabelFieldPair, Card, CardLabel, CardSubHeader, Paragraph, Header } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { TextInput, InfoCard } from "@egovernments/digit-ui-components";
// import { deliveryConfig } from "../../configs/deliveryConfig";

const initialState = (saved, filteredDeliveryConfig, refetch) => {
  const data = {
    cycleConfgureDate: {
      cycle:
        saved?.cycleConfgureDate?.cycle && !refetch
          ? saved?.cycleConfgureDate?.cycle
          : filteredDeliveryConfig?.cycleConfig
          ? filteredDeliveryConfig?.cycleConfig?.cycle
          : 1,
      deliveries:
        saved?.cycleConfgureDate?.deliveries && !refetch
          ? saved?.cycleConfgureDate?.deliveries
          : filteredDeliveryConfig?.cycleConfig
          ? filteredDeliveryConfig?.cycleConfig?.deliveries
          : 1,
    },
    cycleData: saved?.cycleData ? [...saved?.cycleData] : [],
  };
  // onSelect("cycleConfigure", state);
  return data;
};

const reducer = (state, action) => {
  switch (action.type) {
    case "RELOAD":
      return initialState(action.saved, action.filteredDeliveryConfig, action.refetch);
    case "UPDATE_CYCLE":
      return { ...state, cycleConfgureDate: { ...state.cycleConfgureDate, cycle: action.payload } };
    case "UPDATE_DELIVERY":
      return { ...state, cycleConfgureDate: { ...state.cycleConfgureDate, deliveries: action.payload } };
    case "SELECT_TO_DATE":
      return {
        ...state,
        cycleData: updateCycleData(state.cycleData, action.index, { toDate: action.payload }),
        // cycleData: state.cycleData.map((item) => (item.key === action.index ? { ...item, toDate: action.payload } : item)),
      };
    case "SELECT_FROM_DATE":
      return {
        ...state,
        cycleData: updateCycleData(state.cycleData, action.index, { fromDate: action.payload }),
        // cycleData: state.cycleData.map((item) => (item.key === action.index ? { ...item, fromDate: action.payload } : item)),
      };
    default:
      return state;
  }
};

const updateCycleData = (cycleData, index, update) => {
  const existingItem = cycleData.find((item) => item.key === index);

  if (!existingItem) {
    // If the item with the specified key doesn't exist, add a new item
    return [...cycleData, { key: index, ...update }];
  }

  // If the item exists, update it
  return cycleData.map((item) => (item.key === index ? { ...item, ...update } : item));
};

function CycleConfiguration({ onSelect, formData, control, ...props }) {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const selectedProjectType = window.Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA")?.HCM_CAMPAIGN_TYPE?.projectType?.code;
  const { isLoading: deliveryConfigLoading, data: filteredDeliveryConfig } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "deliveryConfig" }],
    {
      select: (data) => {
        const temp = data?.["HCM-ADMIN-CONSOLE"]?.deliveryConfig;
        return temp?.find((i) => i?.projectType === selectedProjectType);
        // return deliveryConfig?.find((i) => i?.projectType === selectedProjectType);
      },
    }
  );
  const saved = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA")?.HCM_CAMPAIGN_CYCLE_CONFIGURE?.cycleConfigure;
  const refetch = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA")?.HCM_CAMPAIGN_CYCLE_CONFIGURE?.cycleConfigure?.cycleConfgureDate
    ?.refetch;
  const tempSession = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA");
  const [state, dispatch] = useReducer(reducer, initialState(saved, filteredDeliveryConfig, refetch));
  const { cycleConfgureDate, cycleData } = state;
  const { t } = useTranslation();
  const [dateRange, setDateRange] = useState({
    startDate: tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate,
    endDate: tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate,
  });
  const [executionCount, setExecutionCount] = useState(0);

  useEffect(() => {
    if (!deliveryConfigLoading) {
      dispatch({
        type: "RELOAD",
        saved: saved,
        filteredDeliveryConfig: filteredDeliveryConfig,
        refetch: refetch,
      });
    }
  }, [filteredDeliveryConfig, deliveryConfigLoading]);
  useEffect(() => {
    onSelect("cycleConfigure", state);
  }, [state]);

  useEffect(() => {
    if (executionCount < 5) {
      onSelect("cycleConfigure", state);
      setExecutionCount((prevCount) => prevCount + 1);
    }
  });

  const updateCycle = (d) => {
    if (d === 0 || d > 5) return;
    if (Number(d?.target?.value) === 0 || Number(d?.target?.value) > 5) return;
    // if (d?.target?.value.trim() === "") return;
    dispatch({ type: "UPDATE_CYCLE", payload: d?.target?.value ? Number(d?.target?.value) : d?.target?.value === "" ? d.target.value : d });
  };

  const updateDelivery = (d) => {
    if (d === 0 || d > 5) return;
    if (Number(d?.target?.value) === 0 || Number(d?.target?.value) > 5) return;
    // if (d?.target?.value.trim() === "") return;
    dispatch({ type: "UPDATE_DELIVERY", payload: d?.target?.value ? Number(d?.target?.value) : d });
  };

  const selectToDate = (index, d) => {
    dispatch({ type: "SELECT_TO_DATE", index, payload: d });
  };

  const selectFromDate = (index, d) => {
    dispatch({ type: "SELECT_FROM_DATE", index, payload: d });
  };

  return (
    <>
      <Header>
        {t(
          `CAMPAIGN_PROJECT_${
            tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code
              ? tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code?.toUpperCase()
              : tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.toUpperCase()
          }`
        )}
      </Header>
      <Paragraph
        customClassName="cycle-paragraph"
        value={`(${tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
          ?.split("-")
          ?.reverse()
          ?.join("/")} - ${tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate?.split("-")?.reverse()?.join("/")})`}
      />
      <InfoCard
        className={"infoClass"}
        populators={{
          name: "infocard",
        }}
        variant="default"
        style={{ marginBottom : "1.5rem", marginLeft : "0rem", maxWidth: "100%" }}
        additionalElements={[
          <img className="whoLogo"
            // style="display: block;-webkit-user-select: none;margin: auto;cursor: zoom-in;background-color: hsl(0, 0%, 90%);transition: background-color 300ms;"
            src="https://cdn.worldvectorlogo.com/logos/world-health-organization-logo-1.svg"
            alt="WHO Logo"
            width="164"
            height="90"
          ></img>,
          <span style={{ color: "#505A5F" }}>{t(
            `CAMPAIGN_CYCLE_INFO_${
              tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code
                ? tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code?.toUpperCase()
                : tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.toUpperCase()
            }`
          )}</span>,
        ]}
        label={"Info"}
        headerClassName={"headerClassName"}
      />
      <Card className="campaign-counter-container">
        <CardText>
          {t(
            `CAMPAIGN_CYCLE_CONFIGURE_HEADING_${
              tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code
                ? tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code?.toUpperCase()
                : tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.toUpperCase()
            }`
          )}
        </CardText>
        <LabelFieldPair>
          <CardLabel>
            {t(`CAMPAIGN_NO_OF_CYCLE`)}
            <span className="mandatory-span">*</span>
          </CardLabel>
          <TextInput type="numeric" value={cycleConfgureDate?.cycle} onChange={(d) => updateCycle(d)} />
          {/* <PlusMinusInput defaultValues={cycleConfgureDate?.cycle} onSelect={(d) => updateCycle(d)} /> */}
        </LabelFieldPair>
        <LabelFieldPair>
          <CardLabel>
            {t(`CAMPAIGN_NO_OF_DELIVERY`)}
            <span className="mandatory-span">*</span>
          </CardLabel>
          <TextInput type="numeric" value={cycleConfgureDate?.deliveries} onChange={(d) => updateDelivery(d)} />
          {/* <PlusMinusInput defaultValues={cycleConfgureDate?.deliveries} onSelect={(d) => updateDelivery(d)} /> */}
        </LabelFieldPair>
      </Card>
      <Card className="campaign-counter-container">
        <CardSubHeader>{t(`CAMPAIGN_ADD_START_END_DATE_TEXT`)}</CardSubHeader>
        {[...Array(cycleConfgureDate.cycle)].map((_, index) => (
          <LabelFieldPair key={index}>
            <CardLabel>
              {t(`CAMPAIGN_CYCLE`)} {index + 1}
            </CardLabel>
            <div className="date-field-container">
              <TextInput
                type="date"
                placeholder={t("FROM_DATE")}
                value={cycleData?.find((j) => j.key === index + 1)?.fromDate}
                min={
                  index > 0 && cycleData?.find((j) => j.key === index)?.toDate
                    ? new Date(new Date(cycleData?.find((j) => j.key === index)?.toDate)?.getTime() + 86400000)?.toISOString()?.split("T")?.[0]
                    : dateRange?.startDate
                }
                max={dateRange?.endDate}
                onChange={(d) => selectFromDate(index + 1, d)}
              />
              <TextInput
                type="date"
                placeholder={t("TO_DATE")}
                value={cycleData?.find((j) => j.key === index + 1)?.toDate}
                min={
                  cycleData?.find((j) => j.key === index + 1)?.fromDate
                    ? new Date(new Date(cycleData?.find((j) => j.key === index + 1)?.fromDate)?.getTime() + 86400000)?.toISOString()?.split("T")?.[0]
                    : null
                }
                max={dateRange?.endDate}
                onChange={(d) => selectToDate(index + 1, d)}
              />
            </div>
          </LabelFieldPair>
        ))}
      </Card>
    </>
  );
}

export default CycleConfiguration;
