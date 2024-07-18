import { Timeline, TimelineMolecule } from "@egovernments/digit-ui-components";
import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@egovernments/digit-ui-components";
import { LabelFieldPair } from "@egovernments/digit-ui-components";

const TimelineComponent = ({}) => {
  const { t } = useTranslation();
  const searchParams = new URLSearchParams(location.search);
  const isPreview = searchParams.get("preview");
  const [showTimeLineButton, setShowTimelineButton] = useState(isPreview);
  const [showTimeLine, setShowTimeline] = useState(!isPreview);
  const campaignId = searchParams.get("id");

  const formatLabel = (label) => {
    return `HCM_${label.replace(/-/g, "_").toUpperCase()}`;
  };


  const reqCriteria = {
    url: `/project-factory/v1/project-type/getProcessTrack`,
    params: {
      campaignId: campaignId,
    },
  };
  // use refetch interval in this
  const { data: progessTrack , refetch} = Digit.Hooks.useCustomAPIHook(reqCriteria);
  useEffect(() => {
    const intervalId = setInterval(() => {
      refetch();
    }, 60000); // 60000ms = 1 minute

    return () => clearInterval(intervalId); 
  }, [refetch]);

  const lastCompletedProcess = progessTrack?.processTrack
    .filter((process) => process.status === "completed")
    .reduce((latestProcess, currentProcess) => {
      if (!latestProcess || currentProcess.lastModifiedTime > latestProcess.lastModifiedTime) {
        return currentProcess;
      }
      return latestProcess;
    }, null);

    const completedProcesses = progessTrack?.processTrack
    .filter(process => process.status === 'completed')
    .sort((a, b) => b.lastModifiedTime - a.lastModifiedTime)
    .map(process => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

    const completedTimelines = completedProcesses?.map(process => ({
      label:  t(formatLabel(process.type)),
      subElements: [Digit.Utils.date.convertEpochToDate(process.lastModifiedTime)],
    }));

  const inprogressProcesses = progessTrack?.processTrack
    .filter(process => process.status === 'inprogress')
    .map(process => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  const subElements = inprogressProcesses?.length > 0 
  ? inprogressProcesses.map(process => `${t(formatLabel(process.type))} , ${Digit.Utils.date.convertEpochToDate(process.lastModifiedTime)}`)
  : [];

  const upcomingProcesses = progessTrack?.processTrack
    .filter(process => process.status === "toBeCompleted")
    .map(process => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  const subElements2 = upcomingProcesses?.length > 0 
  ? upcomingProcesses.map(process => `${t(formatLabel(process.type))} , ${Digit.Utils.date.convertEpochToDate(process.lastModifiedTime)}`)
  : [];



  return (
    <React.Fragment>
      {showTimeLineButton && (
        <div className="timeline-div">
          <div className="timeline-button">{`${t("HCM_CAMPAIGN_CREATION_PROGRESS")}`}</div>
          <Button
            type={"button"}
            size={"large"}
            variation={"secondary"}
            label={t("HCM_VIEW_PROGRESS")}
            onClick={() => {
              setShowTimeline(true);
              setShowTimelineButton(false);
            }}
          />
        </div>
      )}
      {showTimeLine && (
        (subElements.length > 0 || subElements2.length > 0) ? (
          <TimelineMolecule >
            <Timeline label={t("HCM_UPCOMING")}
              variant="upcoming" 
              subElements={subElements2}
              showConnector={true} />
            <Timeline
              label={t("HCM_CURRENT")}
              subElements={subElements}
              variant="inprogress"
              showConnector={true}
            />
            <Timeline
              label={ t(formatLabel(lastCompletedProcess?.type))}  
              subElements={[Digit.Utils.date.convertEpochToDate(lastCompletedProcess?.lastModifiedTime)]}
              variant="completed"
              showConnector={true}
            />
          </TimelineMolecule>
        ) : (
          <TimelineMolecule initialVisibleCount={1} hideFutureLabel ={true}>
            {completedTimelines?.map((timeline, index) => (
              <Timeline
                key={index}
                label={timeline?.label}
                subElements={timeline?.subElements}
                variant="completed"
                showConnector={true}
              />
            ))}
          </TimelineMolecule>
        )
      )}
    </React.Fragment>
  );
};

export default TimelineComponent;
