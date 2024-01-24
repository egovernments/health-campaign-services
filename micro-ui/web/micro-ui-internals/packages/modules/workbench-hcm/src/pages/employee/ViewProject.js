import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { Header, Card, Loader, ViewComposer, ActionBar, SubmitBar, Toast, Menu } from "@egovernments/digit-ui-react-components";
import { data } from "../../configs/ViewProjectConfig";
import AssignCampaign from "../../components/AssignCampaign";
import AssignTarget from "../../components/AssignTarget";

const ViewProject = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const [showEditDateModal, setShowEditDateModal] = useState(false);

  const [showTargetModal, setShowTargetModal] = useState(false);

  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [showToast, setShowToast] = useState(false);
  const [selectedAction, setSelectedAction] = useState(null);
  const [displayMenu, setDisplayMenu] = useState(false);

  const { tenantId, projectNumber, projectId } = Digit.Hooks.useQueryParams();

  const [formData, setFormData] = useState({
    beneficiaryType: "",
    totalNo: 0,
    targetNo: 0,
  });
  function onActionSelect(action) {
    setSelectedAction(action);
    if (action === "DATE") {
      setShowEditDateModal(true);
    } else if (action === "TARGET") {
      setShowTargetModal(true);
    } else {
      setShowEditDateModal(false);
      setShowTargetModal(false);

      setSelectedAction(null);
    }
    setDisplayMenu(false);
  }

  let ACTIONS = ["DATE", "TARGET"];
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
      // includeAncestors: true,
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
      enabled: projectId || projectNumber ? true : false,
    },
  };

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 5000);
  };

  //fetching the project data
  const { data: project, refetch } = Digit.Hooks.useCustomAPIHook(requestCriteria);

  // Render the data once it's available
  let config = null;

  const reqProjectCreate = {
    url: "/project/v1/_create",
    params: {},
    body: {},
    config: {
      enabled: true,
    },
  };

  const mutation = Digit.Hooks.useCustomAPIMutationHook(reqProjectCreate);

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
              refetch();
              closeToast();
            },
          }
        );
      }
    } catch (error) {
      setShowToast({ label: "WBH_DATES_UPDATED_FAILED", isError: true });
      // setShowTargetModal(false);
    }
  };

  config = data(project);

  const handleOnCancel = () => {
    setShowEditDateModal(false);
    setShowTargetModal(false);
  };

  const reqCriteria = {
    url: "/project/v1/_create",
    config: false,
  };

  const mutationTarget = Digit.Hooks.useCustomAPIMutationHook(reqCriteria);

  const onSuccess = () => {
    closeToast();
    refetch();
    setShowToast({ key: "success", label: "WBH_PROJECT_TARGET_ADDED_SUCESSFULLY" });
  };
  const onError = (resp) => {
    const label = resp?.response?.data?.Errors?.[0]?.code;
    setShowToast({ isError: true, label });
    refetch();
  };

  const handleProjectTargetSubmit = async () => {
    const targets = {
      beneficiaryType: formData?.beneficiaryType,
      totalNo: Number(formData?.totalNo),
      targetNo: Number(formData?.targetNo),
      isDeleted: false,
    };

    const projectTarget = project?.Project?.[0];

    await mutation.mutate(
      {
        body: {
          Projects: [
            {
              ...projectTarget,
              targets: [...projectTarget?.targets, targets],
            },
          ],
        },
      },
      {
        onError,
        onSuccess,
      }
    );
    setShowTargetModal(false);
  };

  const handleOnChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
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
      {showTargetModal && (
        <AssignTarget
          t={t}
          isEdit={false}
          onClose={() => setShowTargetModal(false)}
          heading={"WBH_CAMPAIGN_ASSIGNMENT_TARGET"}
          onCancel={handleOnCancel}
          onSubmitTarget={handleProjectTargetSubmit}
          onChange={handleOnChange}
        />
      )}

      <ActionBar>
        {displayMenu ? <Menu localeKeyPrefix={"WBH_ASSIGN_CAMPAIGN"} options={ACTIONS} t={t} onSelect={onActionSelect} /> : null}

        <SubmitBar label={t("ES_COMMON_TAKE_ACTION")} onSubmit={() => setDisplayMenu(!displayMenu)} />
      </ActionBar>
      {showToast && <Toast label={showToast.label} error={showToast?.isError} isDleteBtn={true} onClose={() => setShowToast(null)}></Toast>}
    </React.Fragment>
  );
};
export default ViewProject;
