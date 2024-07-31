import React, { useState, useEffect, Fragment, useReducer } from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";
import { LabelFieldPair, Header } from "@egovernments/digit-ui-react-components";
import { Card, FieldV1 } from "@egovernments/digit-ui-components";

const initialState = (projectData) => {
  return projectData;
};

const reducer = (state, action) => {
  switch (action.type) {
    case "RELOAD":
      return initialState(action?.projectData);
      break;
    case "START_DATE":
      return {
        ...state,
        startDate: Digit.Utils.pt.convertDateToEpoch(action?.date, "dayStart"),
      };
      break;
    case "END_DATE":
      return {
        ...state,
        endDate: Digit.Utils.pt.convertDateToEpoch(action?.date),
      };
      break;
    case "CYCLE_START_DATE":
      const cycleStartRemap = action?.cycles?.map((item, index) => {
        if (item?.id === action?.cycleIndex) {
          return {
            ...item,
            startDate: Digit.Utils.pt.convertDateToEpoch(typeof action?.date === "string" ? action?.date : null, "dayStart"),
          };
        }
        return item;
      });
      return {
        ...state,
        additionalDetails: {
          ...state?.additionalDetails,
          projectType: {
            ...state?.additionalDetails?.projectType,
            cycles: cycleStartRemap,
          },
        },
      };
      break;
    case "CYCLE_END_DATE":
      const cycleEndRemap = action?.cycles?.map((item, index) => {
        if (item?.id === action?.cycleIndex) {
          return {
            ...item,
            endDate: Digit.Utils.pt.convertDateToEpoch(action?.date),
          };
        }
        return item;
      });
      return {
        ...state,
        additionalDetails: {
          ...state?.additionalDetails,
          projectType: {
            ...state?.additionalDetails?.projectType,
            cycles: cycleEndRemap,
          },
        },
      };
      break;
    default:
      return state;
      break;
  }
};

const DateAndCycleUpdate = ({ onSelect, formData, ...props }) => {
  const { t } = useTranslation();
  const ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;
  const today = Digit.Utils.date.getDate(Date.now());
  const tomorrow = Digit.Utils.date.getDate(new Date(today).getTime() + ONE_DAY_IN_MS);
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { state } = useLocation();
  const historyState = window.history.state;

  const reqCriteria = {
    url: "/health-project/v1/_search",
    params: {
      tenantId: tenantId,
      limit: 10,
      offset: 0,
    },
    body: {
      Projects: [
        {
          tenantId: tenantId,
          id: state?.projectId ? state.projectId : historyState?.projectId,
        },
      ],
    },
    config: {
      enabled: true,
      select: (data) => {
        return data?.Project?.[0];
      },
    },
  };

  const { isLoading, data: projectData } = Digit.Hooks.useCustomAPIHook(reqCriteria);
  const [dateReducer, dateReducerDispatch] = useReducer(reducer, initialState(projectData));
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [cycleDates, setCycleDates] = useState(null);

  useEffect(() => {
    onSelect("dateAndCycle", dateReducer);
  }, [dateReducer]);

  useEffect(() => {
    if (dateReducer) {
      setStartDate(dateReducer?.startDate ? Digit.Utils.date.getDate(dateReducer?.startDate) : "");
      setEndDate(dateReducer?.endDate ? Digit.Utils.date.getDate(dateReducer?.endDate) : "");
      if (dateReducer?.additionalDetails?.projectType?.cycles?.length > 0) {
        const cycleDateData = dateReducer?.additionalDetails?.projectType?.cycles?.map((cycle) => {
          return {
            cycleIndex: cycle.id,
            startDate: cycle.startDate ? Digit.Utils.date.getDate(cycle.startDate) : "",
            endDate: cycle.endDate ? Digit.Utils.date.getDate(cycle.endDate) : "",
          };
        });
        setCycleDates(cycleDateData);
      }
    }
  }, [dateReducer]);

  useEffect(() => {
    if (!isLoading && projectData) {
      dateReducerDispatch({
        type: "RELOAD",
        projectData: projectData,
      });
    }
  }, [isLoading, projectData]);

  const handleDateChange = ({ date, endDate = false, cycleDate = false, cycleIndex }) => {
    if (typeof date === "undefined") {
      return null;
    }
    if (!endDate) {
      dateReducerDispatch({
        type: "START_DATE",
        date: date,
        item: dateReducer,
      });
    } else {
      dateReducerDispatch({
        type: "END_DATE",
        date: date,
        item: dateReducer,
      });
    }
  };

  const handleCycleDateChange = ({ date, endDate = false, cycleIndex }) => {
    if (typeof date === "undefined") {
      return null;
    }
    if (!endDate) {
      dateReducerDispatch({
        type: "CYCLE_START_DATE",
        date: date,
        item: dateReducer,
        cycleIndex: cycleIndex,
        cycles: dateReducer?.additionalDetails?.projectType?.cycles,
      });
    } else {
      dateReducerDispatch({
        type: "CYCLE_END_DATE",
        date: date,
        item: dateReducer,
        cycleIndex: cycleIndex,
        cycles: dateReducer?.additionalDetails?.projectType?.cycles,
      });
    }
  };
  return (
    <Card className={"boundary-with-container"}>
      <Header className="header">{t(`UPDATE_DATE_AND_CYCLE_HEADER`)}</Header>
      <LabelFieldPair style={{ display: "grid", gridTemplateColumns: "13rem 2fr", alignItems: "start", gap: "1rem" }}>
        <div className="campaign-dates">
          <p>{t(`HCM_CAMPAIGN_DATES`)}</p>
          <span className="mandatory-date">*</span>
        </div>
        <div className="date-field-container">
          <FieldV1
            required={true}
            withoutLabel={true}
            type="date"
            value={startDate}
            nonEditable={startDate && startDate?.length > 0 && today >= startDate ? true : false}
            placeholder={t("HCM_START_DATE")}
            populators={
              today >= startDate
                ? {}
                : {
                    validation: {
                      min: Digit.Utils.date.getDate(Date.now() + ONE_DAY_IN_MS),
                    },
                  }
            }
            onChange={(d) => {
              handleDateChange({
                date: d?.target?.value,
              });
            }}
          />
          <FieldV1
            required={true}
            withoutLabel={true}
            type="date"
            value={endDate}
            nonEditable={endDate && endDate?.length > 0 && today >= endDate ? true : false}
            placeholder={t("HCM_END_DATE")}
            populators={{
              validation: {
                min:
                  startDate && startDate > today
                    ? Digit.Utils.date.getDate(new Date(startDate).getTime() + 2 * ONE_DAY_IN_MS)
                    : Digit.Utils.date.getDate(Date.now() + 2 * ONE_DAY_IN_MS),
              },
            }}
            onChange={(d) => {
              handleDateChange({
                date: d?.target?.value,
                endDate: true,
              });
            }}
          />
        </div>
      </LabelFieldPair>
      {cycleDates?.length > 0 && (
        <Card className={"cycle-date-container"}>
          {cycleDates?.map((item, index) => (
            <LabelFieldPair style={{ display: "grid", gridTemplateColumns: "13rem 2fr", alignItems: "start" }}>
              <div className="campaign-dates">
                <p>{`${t(`CYCLE`)} ${item?.cycleIndex}`}</p>
                <span className="mandatory-date">*</span>
              </div>
              <div className="date-field-container">
                <FieldV1
                  required={true}
                  withoutLabel={true}
                  type="date"
                  value={item?.startDate}
                  nonEditable={item?.startDate && item?.startDate?.length > 0 && today >= item?.startDate ? true : false}
                  placeholder={t("HCM_START_DATE")}
                  populators={{
                    validation: {
                      min:
                        index > 0 && !isNaN(new Date(cycleDates?.find((j) => j.cycleIndex == index)?.endDate)?.getTime())
                          ? new Date(new Date(cycleDates?.find((j) => j.cycleIndex == index)?.endDate)?.getTime() + ONE_DAY_IN_MS)
                              ?.toISOString()
                              ?.split("T")?.[0]
                          : today >= startDate
                          ? tomorrow
                          : startDate,
                      max: endDate,
                    },
                  }}
                  onChange={(d) => {
                    // setStartValidation(true);
                    handleCycleDateChange({
                      date: d?.target?.value,
                      cycleIndex: item?.cycleIndex,
                    });
                  }}
                />
                <FieldV1
                  required={true}
                  withoutLabel={true}
                  type="date"
                  value={item?.endDate}
                  nonEditable={
                    item?.endDate &&
                    item?.endDate?.length > 0 &&
                    today >= item?.endDate &&
                    (cycleDates?.[index + 1] ? today >= cycleDates?.[index + 1]?.startDate : true)
                      ? true
                      : false
                  }
                  placeholder={t("HCM_END_DATE")}
                  populators={{
                    validation: {
                      min:
                        !isNaN(new Date(cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate)?.getTime()) &&
                        Digit.Utils.date.getDate(new Date(cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate)?.getTime() + ONE_DAY_IN_MS) >
                          today
                          ? new Date(new Date(cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate)?.getTime() + ONE_DAY_IN_MS)
                              ?.toISOString()
                              ?.split("T")?.[0]
                          : today >= startDate
                          ? tomorrow
                          : startDate,
                      max: endDate,
                    },
                  }}
                  onChange={(d) => {
                    handleCycleDateChange({
                      date: d?.target?.value,
                      endDate: true,
                      cycleIndex: item?.cycleIndex,
                    });
                  }}
                />
              </div>
            </LabelFieldPair>
          ))}
        </Card>
      )}
    </Card>
  );
};

export default DateAndCycleUpdate;
