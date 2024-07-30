import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";
import { LabelFieldPair, Header } from "@egovernments/digit-ui-react-components";
import { Button, Card, Dropdown, DustbinIcon, FieldV1, MultiSelectDropdown } from "@egovernments/digit-ui-components";

const BoundaryWithDate = ({ project, props, onSelect, dateReducerDispatch, canDelete, onDeleteCard }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  // const { t } = useTranslation();
  const ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;
  const today = Digit.Utils.date.getDate(Date.now());
  const [startDate, setStartDate] = useState(project?.startDate ? Digit.Utils.date.getDate(project?.startDate) : ""); // Set default start date to today
  const [endDate, setEndDate] = useState(project?.endDate ? Digit.Utils.date.getDate(project?.endDate) : ""); // Default end date
  const [cycleDates, setCycleDates] = useState(null);

  useEffect(() => {
    setStartDate(project?.startDate ? Digit.Utils.date.getDate(project?.startDate) : "");
    setEndDate(project?.endDate ? Digit.Utils.date.getDate(project?.endDate) : "");
    if (project?.additionalDetails?.projectType?.cycles?.length > 0) {
      const cycleDateData = project?.additionalDetails?.projectType?.cycles?.map((cycle) => ({
        cycleIndex: cycle.id,
        startDate: cycle.startDate ? Digit.Utils.date.getDate(cycle.startDate) : "",
        endDate: cycle?.endDate ? Digit.Utils.date.getDate(cycle.endDate) : "",
      }));
      setCycleDates(cycleDateData);
    }
  }, [project]);

  const handleDateChange = ({ date, endDate = false, cycleDate = false, cycleIndex }) => {
    if (typeof date === "undefined") {
      return null;
    }
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
    if (typeof date === "undefined") {
      return null;
    }
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
        type: "CYCLE_END_DATE",
        date: date,
        item: project,
        cycleIndex: cycleIndex,
        cycles: project?.additionalDetails?.projectType?.cycles,
      });
    }
  };

  return (
    <Card className={"boundary-with-container"}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Header className="header">{t(`${project?.address?.boundary}`)}</Header>
        {canDelete && (
          <div className="delete-resource-icon" onClick={onDeleteCard}>
            <DustbinIcon />
          </div>
        )}
      </div>
      <LabelFieldPair style={{ display: "grid", gridTemplateColumns: "13rem 2fr", alignItems: "start", gap: "1rem" }}>
        <div className="campaign-dates">
          <p>{t(`HCM_CAMPAIGN_DATES`)}</p>
          <span className="mandatory-date">*</span>
        </div>
        <div className="date-field-container">
          <FieldV1
            withoutLabel={true}
            type="date"
            value={startDate}
            nonEditable={startDate?.length > 0 && today > startDate ? true : false}
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
            withoutLabel={true}
            type="date"
            value={endDate}
            nonEditable={endDate?.length > 0 && today > endDate ? true : false}
            placeholder={t("HCM_END_DATE")}
            populators={{
              validation: {
                min:
                  startDate >= today
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
                  withoutLabel={true}
                  type="date"
                  nonEditable={item?.startDate?.length > 0 && today > item?.startDate ? true : false}
                  value={item?.startDate}
                  placeholder={t("HCM_START_DATE")}
                  populators={{
                    validation: {
                      min:
                        index > 0 && !isNaN(new Date(cycleDates?.find((j) => j.cycleIndex == index)?.endDate)?.getTime())
                          ? new Date(new Date(cycleDates?.find((j) => j.cycleIndex == index)?.endDate)?.getTime() + ONE_DAY_IN_MS)
                              ?.toISOString()
                              ?.split("T")?.[0]
                          : today >= startDate
                          ? today
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
                  withoutLabel={true}
                  type="date"
                  value={item?.endDate}
                  nonEditable={item?.endDate?.length > 0 && today > item?.endDate && today > cycleDates?.[index + 1]?.startDate ? true : false}
                  placeholder={t("HCM_END_DATE")}
                  populators={{
                    validation: {
                      min: !isNaN(new Date(cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate)?.getTime())
                        ? new Date(new Date(cycleDates?.find((j) => j.cycleIndex == index + 1)?.startDate)?.getTime() + ONE_DAY_IN_MS)
                            ?.toISOString()
                            ?.split("T")?.[0]
                        : today >= startDate
                        ? today
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

export default BoundaryWithDate;
