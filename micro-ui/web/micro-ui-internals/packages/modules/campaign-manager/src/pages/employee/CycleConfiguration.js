import React, { useReducer, Fragment } from "react";
import { CardText, DatePicker, LabelFieldPair, Card, CardHeader, CardLabel, CardSubHeader } from "@egovernments/digit-ui-react-components";
import PlusMinusInput from "../../components/PlusMinusInput";
import { useTranslation } from "react-i18next";

const initialState = {
  cycleConfgureDate: {
    cycle: 1,
    deliveries: 1,
  },
  cycleData: [],
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

function CycleConfiguration() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const { cycleConfgureDate, cycleData } = state;
  const { t } = useTranslation();
  const updateCycle = (d) => {
    dispatch({ type: "UPDATE_CYCLE", payload: d });
  };

  const updateDelivery = (d) => {
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
      <Card className="campaign-counter-container">
        <CardText>{t(`CAMPAIGN_CYCLE_CONFIGURE_HEADING`)}</CardText>
        <LabelFieldPair>
          <CardLabel>{t(`CAMPAIGN_NO_OF_CYCLE`)}</CardLabel>
          <PlusMinusInput defaultValues={cycleConfgureDate?.cycle} onSelect={(d) => updateCycle(d)} />
        </LabelFieldPair>
        <LabelFieldPair>
          <CardLabel>{t(`CAMPAIGN_NO_OF_DELIVERY`)}</CardLabel>
          <PlusMinusInput defaultValues={cycleConfgureDate?.deliveries} onSelect={(d) => updateDelivery(d)} />
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
              <DatePicker
                // min={Digit.Utils.date.getDate(Date.now() + 1 * 24 * 60 * 60 * 1000)}
                // max={Digit.Utils.date.getDate(Date.now() + 2 * 24 * 60 * 60 * 1000)}
                date={cycleData?.find((j) => j.key === index + 1)?.fromDate}
                onChange={(d) => selectFromDate(index + 1, d)}
              />
              <DatePicker
                // min={Digit.Utils.date.getDate()}
                // max={Digit.Utils.date.getDate(Date.now() + 10 * 24 * 60 * 60 * 1000)}
                date={cycleData?.find((j) => j.key === index + 1)?.toDate}
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
