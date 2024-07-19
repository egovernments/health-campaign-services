import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";
import { LabelFieldPair, Header } from "@egovernments/digit-ui-react-components";
import { Button, Card, Dropdown, FieldV1, MultiSelectDropdown } from "@egovernments/digit-ui-components";

const BoundaryWithDate = ({ project, props, onSelect, dateReducerDispatch }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  // const { t } = useTranslation();
  const ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;
  const today = Digit.Utils.date.getDate(Date.now());
  const [startDate, setStartDate] = useState(Digit.Utils.date.getDate(project?.startDate)); // Set default start date to today
  const [endDate, setEndDate] = useState(Digit.Utils.date.getDate(project?.endDate)); // Default end date
  const [cycleDates, setCycleDates] = useState(null);

  useEffect(() => {
    setStartDate(Digit.Utils.date.getDate(project?.startDate));
    setEndDate(Digit.Utils.date.getDate(project?.endDate));
    if (project?.additionalDetails?.projectType?.cycles?.length > 0) {
      const cycleDateData = project?.additionalDetails?.projectType?.cycles?.map((cycle) => ({
        cycleIndex: cycle.id,
        startDate: Digit.Utils.date.getDate(cycle.startDate),
        endDate: Digit.Utils.date.getDate(cycle.endDate),
      }));
      setCycleDates(cycleDateData);
    }
  }, [project]);

  const handleDateChange = ({ date, endDate = false, cycleDate = false, cycleIndex }) => {
    if (!endDate) {
      dateReducerDispatch({
        type: "START_DATE",
        date: date,
        item: project,
      });
    } else {
      dateReducerDispatch({
        type: "END_DATE",
        date: date,
        item: project,
      });
    }
  };

  const handleCycleDateChange = ({ date, endDate = false, cycleIndex }) => {
    if (!endDate) {
      dateReducerDispatch({
        type: "CYCLE_START_DATE",
        date: date,
        item: project,
        cycleIndex: cycleIndex,
        cycles: project?.additionalDetails?.projectType?.cycles,
      });
    } else {
      dateReducerDispatch({
        type: "CYCYLE_END_DATE",
        date: date,
        item: project,
        cycleIndex: cycleIndex,
        cycles: project?.additionalDetails?.projectType?.cycles,
      });
    }
  };

  return (
    <Card className={"boundary-with-container"}>
      <Header className="header">{t(`${project?.address?.boundary}`)}</Header>
      <LabelFieldPair style={{ display: "grid", gridTemplateColumns: "13rem 2fr", alignItems: "start" }}>
        <div className="campaign-dates">
          <p>{t(`HCM_CAMPAIGN_DATES`)}</p>
          <span className="mandatory-date">*</span>
        </div>
        <div className="date-field-container">
          <FieldV1
            withoutLabel={true}
            type="date"
            value={startDate}
            nonEditable={today >= startDate ? true : false}
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
                date: d,
              });
            }}
          />
          <FieldV1
            withoutLabel={true}
            type="date"
            value={endDate}
            nonEditable={today >= endDate ? true : false}
            placeholder={t("HCM_END_DATE")}
            populators={
              today >= endDate
                ? {}
                : {
                    validation: {
                      min: startDate
                        ? Digit.Utils.date.getDate(new Date(startDate).getTime() + 2 * ONE_DAY_IN_MS)
                        : Digit.Utils.date.getDate(Date.now() + 2 * ONE_DAY_IN_MS),
                    },
                  }
            }
            onChange={(d) => {
              handleDateChange({
                date: d,
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
                  withoutLabel={true}
                  type="date"
                  nonEditable={today >= item?.startDate ? true : false}
                  value={item?.startDate}
                  placeholder={t("HCM_START_DATE")}
                  populators={
                    today >= item?.startDate
                      ? {}
                      : {
                          validation: {
                            min:
                              index > 0
                                ? new Date(new Date(cycleDates?.find((j) => j.cycleIndex == index)?.endDate)?.getTime() + 86400000)
                                    ?.toISOString()
                                    ?.split("T")?.[0]
                                : startDate,
                            max: endDate,
                          },
                        }
                  }
                  onChange={(d) => {
                    // setStartValidation(true);
                    handleCycleDateChange({
                      date: d,
                      cycleIndex: item?.cycleIndex,
                    });
                  }}
                />
                <FieldV1
                  withoutLabel={true}
                  type="date"
                  value={item?.endDate}
                  nonEditable={today >= item?.endDate ? true : false}
                  placeholder={t("HCM_END_DATE")}
                  populators={
                    today >= item?.endDate
                      ? {}
                      : {
                          validation: {
                            min: cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate
                              ? new Date(new Date(cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate)?.getTime() + 86400000)
                                  ?.toISOString()
                                  ?.split("T")?.[0]
                              : null,
                            max: endDate,
                          },
                        }
                  }
                  onChange={(d) => {
                    handleCycleDateChange({
                      date: d,
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

export default BoundaryWithDate;
