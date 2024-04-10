import React, { useReducer, Fragment, useEffect, useState } from "react";
import { CardText, LabelFieldPair, Card, CardLabel, CardSubHeader, Paragraph, Header } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { TextInput } from "@egovernments/digit-ui-components";

const initialState = (saved) => {
  return {
    cycleConfgureDate: {
      cycle: saved?.cycleConfgureDate?.cycle ? saved?.cycleConfgureDate?.cycle : 1,
      deliveries: saved?.cycleConfgureDate?.deliveries ? saved?.cycleConfgureDate?.deliveries : 1,
    },
    cycleData: saved?.cycleData ? [...saved?.cycleData] : [],
  };
};

const reducer = (state, action) => {
  switch (action.type) {
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
  const saved = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA")?.HCM_CAMPAIGN_CYCLE_CONFIGURE?.cycleConfigure;
  const [state, dispatch] = useReducer(reducer, initialState(saved));
  const { cycleConfgureDate, cycleData } = state;
  const { t } = useTranslation();
  const tempSession = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA");
  const [dateRange, setDateRange] = useState({
    startDate: tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate,
    endDate: tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate,
  });

  useEffect(() => {
    onSelect("cycleConfigure", state);
  }, [state]);

  const updateCycle = (d) => {
    if (d === 0) return;
    if (d?.target?.value.trim() === "") return;
    dispatch({ type: "UPDATE_CYCLE", payload: d?.target?.value ? Number(d?.target?.value) : d });
  };

  const updateDelivery = (d) => {
    if (d === 0) return;
    dispatch({ type: "UPDATE_DELIVERY", payload: d });
  };

  const selectToDate = (index, d) => {
    dispatch({ type: "SELECT_TO_DATE", index, payload: d });
  };

  const selectFromDate = (index, d) => {
    dispatch({ type: "SELECT_FROM_DATE", index, payload: d });
  };

  return (
    <>
      <Header>{t(`CAMPAIGN_PROJECT_${tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code?.toUpperCase()}`)}</Header>
      <Paragraph
        customClassName="cycle-paragraph"
        value={`(${tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
          .split("-")
          .reverse()
          .join("/")} - ${tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate.split("-").reverse().join("/")})`}
      />
      <Card className="campaign-counter-container">
        <CardText>{t(`CAMPAIGN_CYCLE_CONFIGURE_HEADING`)}</CardText>
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
                value={cycleData?.find((j) => j.key === index + 1)?.fromDate}
                min={dateRange?.startDate}
                max={dateRange?.endDate}
                onChange={(d) => selectFromDate(index + 1, d)}
              />
              <TextInput
                type="date"
                value={cycleData?.find((j) => j.key === index + 1)?.toDate}
                min={cycleData?.find((j) => j.key === index + 1)?.fromDate}
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
