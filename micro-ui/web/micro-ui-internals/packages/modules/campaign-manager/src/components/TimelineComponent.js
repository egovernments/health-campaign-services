import { Timeline, TimelineMolecule } from "@egovernments/digit-ui-components";
import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@egovernments/digit-ui-components";
import { LabelFieldPair } from "@egovernments/digit-ui-components";
import { downloadExcelWithCustomName } from "../utils";

function epochToDateTime(epoch) {
  // Create a new Date object using the epoch time
  const date = new Date(epoch ); 
  // Extract the date components
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0'); // Months are 0-based, so add 1
  const day = String(date.getDate()).padStart(2, '0');
  
  // Extract the time components
  let hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  
  // Determine AM/PM and convert to 12-hour format
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  hours = hours ? hours : 12; // the hour '0' should be '12'
  const formattedHours = String(hours).padStart(2, '0');
  
  // Format the date and time
  const formattedDate = `${day}/${month}/${year}`;
  const formattedTime = `${formattedHours}:${minutes}:${seconds} ${ampm}`;
  
  // Return the formatted date and time
  return `${formattedDate} ${formattedTime}`;
}

const TimelineComponent = ({campaignId, resourceId}) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const searchParams = new URLSearchParams(location.search);
  const [userCredential, setUserCredential] = useState(null);
  const [newResourceId, setNewResourceId] = useState(resourceId);
  const [searchDATA, setSearchDATA] = useState(null);
  const [dataFetched, setDataFetched] = useState(false); 
  const { data: baseTimeOut } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "baseTimeOut" }]);

  const formatLabel = (label) => {
    if (!label) return null;
    return `HCM_${label.replace(/-/g, "_").toUpperCase()}`;
  };

  const fetchUser = async () => {
    const responseTemp = await Digit.CustomService.getResponse({
      url: `/project-factory/v1/data/_search`,
      body: {
        SearchCriteria: {
          tenantId: tenantId,
          id: newResourceId,
        },
      },
    });

    const response = responseTemp?.ResourceDetails?.map((i) => i?.processedFilestoreId);

    if (response?.[0]) {
      setUserCredential({ fileStoreId: response?.[0], customName: "userCredential" });
    }
  };

  useEffect(() => {
    if (newResourceId?.length > 0) {
      fetchUser();
    }
  }, [newResourceId]);

  const downloadUserCred = async () => {
    downloadExcelWithCustomName(userCredential);
  };

  const reqCriteria = {
    url: `/project-factory/v1/project-type/getProcessTrack`,
    params: {
      campaignId: campaignId,
    },
  };
  // use refetch interval in this
  const { data: progessTrack , refetch} = Digit.Hooks.useCustomAPIHook(reqCriteria);

  const lastCompletedProcess = progessTrack?.processTrack
    .filter((process) => process.status === "completed" && process.showInUi === true)
    .reduce((latestProcess, currentProcess) => {
      if (!latestProcess || currentProcess.lastModifiedTime > latestProcess.lastModifiedTime) {
        return currentProcess;
      }
      return latestProcess;
    }, null);


  const searchAPIData = async (campaignId, resourceId) => {
    try {
      const response = await Digit.CustomService.getResponse({
        url: "/project-factory/v1/project-type/search",
        body: {
          CampaignDetails: {
            tenantId: tenantId,
            ids: [campaignId],
          },
        },
      });
      return response;
    } catch (error) {
      throw new Error(error?.response?.data?.Errors?.[0].description);
    }
  };

  useEffect(() => {
    if (resourceId.length === 0 && lastCompletedProcess?.type === "campaign-creation" && lastCompletedProcess?.status === "completed" && !dataFetched ) {
      const fetchData = async () => {
        try {
          const data = await searchAPIData(campaignId);
          setSearchDATA(data);
          setDataFetched(true)
        } catch (error) {
          console.error("Error fetching data:", error);
        }
      };
  
      fetchData();
    }
  }, [campaignId, newResourceId, lastCompletedProcess , dataFetched]);
  
  useEffect(() => {
    if (searchDATA) {
      const userResource = searchDATA?.CampaignDetails?.[0]?.resources?.find((resource) => resource.type === "user");
      if (userResource) {
        setNewResourceId([userResource?.createResourceId]);
      }
    }
  }, [searchDATA]);
  
  useEffect(() => {
    let intervalId;

    if (lastCompletedProcess?.type !== "campaign-creation") {
      intervalId = setInterval(() => {
        refetch();
      }, baseTimeOut?.["HCM-ADMIN-CONSOLE"]?.baseTimeOut?.[0]?.timelineRefetch);
    }

    return () => {
      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [lastCompletedProcess]);

  const completedProcesses = progessTrack?.processTrack
    .filter((process) => process.status === "completed" && process.showInUi === true)
    .sort((a, b) => b.lastModifiedTime - a.lastModifiedTime)
    .map((process) => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  const completedTimelines = completedProcesses?.map((process) => ({
    label: t(formatLabel(process?.type)),
    subElements: [epochToDateTime(process.lastModifiedTime)],
  }));

  const inprogressProcesses = progessTrack?.processTrack
    .filter((process) => process.status === "inprogress" && process.showInUi === true)
    .map((process) => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  const subElements =
    inprogressProcesses?.length > 0
      ? inprogressProcesses.map((process) => `${t(formatLabel(process?.type))} , ${epochToDateTime(process.lastModifiedTime)}`)
      : [];

  const upcomingProcesses = progessTrack?.processTrack
    .filter((process) => process.status === "toBeCompleted" && process.showInUi === true)
    .map((process) => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  const subElements2 =
    upcomingProcesses?.length > 0
      ? upcomingProcesses.map((process) => `${t(formatLabel(process?.type))} , ${epochToDateTime(process.lastModifiedTime)}`)
      : [];

  // useEffect(()=>{
  //   const lastCompletedProcess = progessTrack?.processTrack
  //   .filter((process) => process.status === "completed")
  //   .reduce((latestProcess, currentProcess) => {
  //     if (!latestProcess || currentProcess.lastModifiedTime > latestProcess.lastModifiedTime) {
  //       return currentProcess;
  //     }
  //     return latestProcess;
  //   }, null);
  //   setLastCompletedProgress(lastCompletedProcess);

  //   const completedProcesses = progessTrack?.processTrack
  //   .filter(process => process.status === 'completed')
  //   .sort((a, b) => b.lastModifiedTime - a.lastModifiedTime)
  //   .map(process => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));
  //   setCompletedProgress(completedProcesses);

  //   const completedTimelines = completedProcesses?.map(process => ({
  //     label:  t(formatLabel(process.type)),
  //     subElements: [epochToDateTime(process.lastModifiedTime)],
  //   }));
  //   setCompletedTimeline(completedTimelines);

  //   const inprogressProcesses = progessTrack?.processTrack
  //   .filter(process => process.status === 'inprogress')
  //   .map(process => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  //   setinprogressProcesses(inprogressProcesses);

  //   const upcomingProcesses = progessTrack?.processTrack
  //   .filter(process => process.status === "toBeCompleted")
  //   .map(process => ({ type: process.type, lastModifiedTime: process.lastModifiedTime }));

  //   setupcomingProcesses(upcomingProcesses);

  // }, [progessTrack])

  return (
    <React.Fragment>
      <div className="timeline-user">
        {subElements.length > 0 || subElements2.length > 0 ? (
          <TimelineMolecule>
            <Timeline label={t("HCM_UPCOMING")} variant="upcoming" subElements={subElements2} className={"upcoming-timeline"} showConnector={true} />

            {/* <Timeline
              label={t("HCM_CURRENT")}
              subElements={subElements}
              variant="inprogress"
              showConnector={true}
            /> */}
            {subElements.length > 0 && <Timeline label={t("HCM_CURRENT")} subElements={subElements} variant="inprogress" showConnector={true} />}

            {completedTimelines?.map((timeline, index) => (
              <Timeline key={index} label={timeline?.label} subElements={timeline?.subElements} variant="completed" showConnector={true} />
            ))}
          </TimelineMolecule>
        ) : (
          <TimelineMolecule initialVisibleCount={1} hideFutureLabel={true}>
            {completedTimelines?.map((timeline, index) => (
              <Timeline key={index} label={timeline?.label} subElements={timeline?.subElements} variant="completed" showConnector={true} />
            ))}
          </TimelineMolecule>
        )}
        {userCredential && lastCompletedProcess?.type === "campaign-creation" && (
          <Button
            label={t("CAMPAIGN_DOWNLOAD_USER_CRED")}
            variation="primary"
            icon={"DownloadIcon"}
            type="button"
            className="campaign-download-template-btn hover"
            onClick={downloadUserCred}
          />
        )}
      </div>
    </React.Fragment>
  );
};

export default TimelineComponent;
