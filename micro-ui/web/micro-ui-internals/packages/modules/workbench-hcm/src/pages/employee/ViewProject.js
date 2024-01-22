import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { Header, Card, Loader, ViewComposer, ActionBar, SubmitBar, Toast } from "@egovernments/digit-ui-react-components";
import { data } from "../../configs/ViewProjectConfig";
import AssignCampaign from "../../components/AssignCampaign";

const ViewProject = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const [showEditDateModal, setShowEditDateModal] = useState(false);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [showToast, setShowToast] = useState(false);

  const { tenantId, projectNumber, projectId } = Digit.Hooks.useQueryParams();

  const handleDateChange = (date, type) => {
    if (type === "startDate") {
      setStartDate(date);

      if (endDate && date > endDate) {
        setEndDate(date);
      }
    } else if (type === "endDate") {
      setEndDate(date);

      if (startDate && date < startDate) {
        setStartDate(date);
      }
    }
  };
  const requestCriteria = {
    url: "/project/v1/_search",
    changeQueryName: projectId || projectNumber,
    params: {
      tenantId,
      offset: 0,
      limit: 100,
      includeAncestors: true,
    },
    body: {
      Projects: [
        {
          tenantId,
          ...(projectId ? { id: projectId } : { projectNumber }),
        },
      ],
      apiOperation: "SEARCH",
    },
    config: {
      enabled: projectId || projectNumber ? true: false
    }
  };

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 5000);
  };

  //fetching the project data
  const { data: project } = Digit.Hooks.useCustomAPIHook(requestCriteria);

  // Render the data once it's available
  let config = null;

  const reqProjectUpdate = {
    url: "/project/v1/_update",
    params: {},
    body: {},
    config: {
      enabled: true,
    },
  };

  const mutation = Digit.Hooks.useCustomAPIMutationHook(reqProjectUpdate);

  const handleAssignCampaignSubmit = async () => {
    try {
      const projectDetails = project?.Project?.[0];

      if (projectDetails) {
        const { tenantId, id, ...rest } = projectDetails;

        const updatedProject = {
          ...rest,
          tenantId,
          id,
          startDate: new Date(startDate).getTime(),
          endDate: new Date(endDate).getTime(),
        };

        await mutation.mutate(
          {
            params: {},
            body: {
              Projects: [updatedProject],
            },
          },
          {
            onSuccess: () => {
              setShowToast({ label: `${t("WBH_DATES_UPDATED_SUCCESS")}` });
              setShowEditDateModal(false);
              closeToast();
            },
          }
        );
      }
    } catch {
      throw error;
    }
  };

  config = data(project);

  const handleOnCancel = () => {
    setShowEditDateModal(false);
  };

  return (
    <React.Fragment>
      <Header className="works-header-view">{t("WORKBENCH_PROJECT")}</Header>
      <ViewComposer data={config} isLoading={false} />
      {showEditDateModal && (
        <AssignCampaign
          t={t}
          onClose={() => setShowEditDateModal(false)}
          heading={"WBH_CAMPAIGN_ASSIGNMENT"}
          startDate={startDate}
          endDate={endDate}
          onChange={handleDateChange}
          onCancel={handleOnCancel}
          onSubmit={handleAssignCampaignSubmit}
        />
      )}
      <ActionBar>
        <SubmitBar label={t("WBH_ASSIGN_CAMPAIGN")} onSubmit={() => setShowEditDateModal(true)} />
      </ActionBar>
      {showToast && <Toast label={showToast.label} error={showToast?.isError} isDleteBtn={true} onClose={() => setShowToast(null)}></Toast>}
    </React.Fragment>
  );
};
export default ViewProject;
