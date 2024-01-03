import React, { useEffect, useState } from "react";
import { Card, Header, Button, Loader, Toast, SVG, Modal } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { useState, useEffect } from "react";
import { data } from "../configs/ViewProjectConfig";
import ProjectStaffModal from "./ProjectStaffModal";
import ConfirmationDialog from "./ConfirmationDialog";

const ProjectStaffComponent = (props) => {
  const { t } = useTranslation();
  const [userIds, setUserIds] = useState([]);
  const [userInfoMap, setUserInfoMap] = useState({});

  const [showModal, setShowModal] = useState(false);
  const [userName, setUserName] = useState("");
  const [showToast, setShowToast] = useState(false);
  const [showResult, setShowResult] = useState("");
  const [deletionDetails, setDeletionDetails] = useState({
    projectId: null,
    userId: null,
    id: null,
  });

  const userId = Digit.UserService.getUser().info.uuid;

  const [showPopup, setShowPopup] = useState(false);

  const { tenantId, projectId } = Digit.Hooks.useQueryParams();

  const requestCriteria = {
    url: "/project/staff/v1/_search",
    changeQueryName: props.projectId,
    params: {
      tenantId: "mz",
      offset: 0,
      limit: 10,
    },
    config: {
      enable: data?.horizontalNav?.configNavItems[0].code === "Project Resource" ? true : false,
    },
    body: {
      ProjectStaff: {
        projectId: props.projectId,
      },
    },
  };

  const { isLoading, data: projectStaff, refetch } = Digit.Hooks.useCustomAPIHook(requestCriteria);

  const columns = [
    { label: t("PROJECT_STAFF_ID"), key: "id" },
    { label: t("PROJECT_ID"), key: "projectId" },
    { label: t("IS_DELETED"), key: "isDeleted" },
    { label: t("START_DATE"), key: "startDate" },
    { label: t("END_DATE"), key: "endDate" },
  ];

  const searchCriteria = {
    url: "/egov-hrms/employees/_search",

    config: {
      enable: true,
    },
  };

  const mutationHierarchy = Digit.Hooks.useCustomAPIMutationHook(searchCriteria);

  const handleSearch = async () => {
    try {
      await mutationHierarchy.mutate(
        {
          params: {
            codes: userName,
            tenantId,
          },
          body: {},
        },
        {
          onSuccess: async (data) => {
            if (data?.Employees && data?.Employees?.length > 0) {
              setShowResult(data?.Employees[0]?.code);
            } else {
              setShowToast({ label: "WBH_USER_NOT_FOUND", isError: true });
              setTimeout(() => setShowToast(null), 5000);
            }
          },
        }
      );
    } catch (error) {
      throw error;
    }
  };

  const handleInputChange = (event) => {
    setUserName(event.target.value);
  };

  const isValidTimestamp = (timestamp) => timestamp !== 0 && !isNaN(timestamp);

  //to convert epoch to date and to convert isDeleted boolean to string
  projectStaff?.ProjectStaff.forEach((row) => {
    row.formattedStartDate = isValidTimestamp(row.startDate) ? Digit.DateUtils.ConvertEpochToDate(row.startDate) : "NA";
    row.formattedEndDate = isValidTimestamp(row.endDate) ? Digit.DateUtils.ConvertEpochToDate(row.endDate) : "NA";
    row.isDeleted = row.isDeleted.toString();
  });

  useEffect(() => {
    // Extract user IDs and save them in the state
    if (projectStaff && projectStaff.ProjectStaff.length > 0) {
      const userIdArray = projectStaff.ProjectStaff.map((row) => row.userId);
      setUserIds(userIdArray);
    }
  }, [projectStaff]);

  const reqCriteria = {
    url: "/project/staff/v1/_create",

    config: false,
  };

  const reqDeleteCriteria = {
    url: "/project/staff/v1/_delete",

    config: false,
  };

  const mutation = Digit.Hooks.useCustomAPIMutationHook(reqCriteria);
  const mutationDelete = Digit.Hooks.useCustomAPIMutationHook(reqDeleteCriteria);
  const closeModal = () => {
    setShowModal(false);
    setShowPopup(false);
    setUserName("");
    setShowResult("");
  };

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 5000);
  };

  const onSuccess = () => {
    closeToast();
    refetch();
    setShowToast({ key: "success", label: "WBH_PROJECT_STAFF_ADDED_SUCESSFULLY" });
  };
  const onError = (resp) => {
    const label = resp?.response?.data?.Errors?.[0]?.code;
    setShowToast({ isError: true, label });
    refetch();
  };
  const handleProjectStaffSubmit = async () => {
    try {
      await mutation.mutate(
        {
          body: {
            ProjectStaff: {
              tenantId,
              userId: userId,
              projectId: projectId,
              startDate: props?.Project[0]?.startDate,
              endDate: props?.Project[0]?.endDate,
            },
          },
        },
        {
          onError,
          onSuccess,
        }
      );

      setShowModal(false);
    } catch (error) {
      setShowToast({ label: "WBH_PROJECT_STAFF_FAILED", isError: true });
      setShowModal(false);
    }
  };

  const handleProjectStaffDelete = async (projectId, staffId, id, confirmed) => {
    try {
      setShowPopup(false);
      if (confirmed) {
        await mutationDelete.mutate(
          {
            body: {
              ProjectStaff: {
                tenantId,
                id,
                userId: staffId,
                projectId: projectId,
                ...deletionDetails,
              },
            },
          },
          {
            onSuccess: () => {
              closeToast();
              refetch();
              setShowToast({ key: "success", label: "WBH_PROJECT_STAFF_DELETED_SUCESSFULLY" });
            },
            onError: (resp) => {
              const label = resp?.response?.data?.Errors?.[0]?.code;
              setShowToast({ isError: true, label });
              refetch();
            },
          }
        );
      }
    } catch (error) {
      setShowToast({ label: "WBH_PROJECT_STAFF_DELETION_FAILED", isError: true });
      setShowModal(false);
    }
  };

  if (isLoading) {
    return <Loader></Loader>;
  }

  return (
    <div className="override-card">
      <Header className="works-header-view">{t("PROJECT_STAFF")}</Header>
      <Button label={t("WBH_ADD_PROJECT_STAFF")} type="button" variation={"secondary"} onButtonClick={() => setShowModal(true)} />
      {showModal && (
        <ProjectStaffModal
          t={t}
          userName={userName}
          onSearch={handleSearch}
          onChange={handleInputChange}
          searchResult={showResult}
          onSubmit={handleProjectStaffSubmit}
          onClose={closeModal}
          heading={"WBH_ASSIGN_PROJECT_STAFF"}
        />
      )}
      {showPopup && (
        <ConfirmationDialog
          t={t}
          heading={"WBH_DELETE_POPUP_HEADER"}
          closeModal={closeModal}
          onSubmit={(confirmed) => handleProjectStaffDelete(deletionDetails.projectId, deletionDetails.userId, deletionDetails.id, confirmed)}
        />
      )}

      {showToast && <Toast label={showToast.label} error={showToast?.isError} isDleteBtn={true} onClose={() => setShowToast(null)}></Toast>}

      <table className="table reports-table sub-work-table">
        <thead>
          <tr>
            {columns.map((column, index) => (
              <th key={index}>{column.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {projectStaff?.ProjectStaff.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {columns.map((column, columnIndex) => (
                <td key={columnIndex}>{row[column.key]}</td>
              ))}
              <td>
                <Button
                  label={`${t("WBH_DELETE_ACTION")}`}
                  type="button"
                  variation="secondary"
                  icon={<SVG.Delete width={"28"} height={"28"} />}
                  onButtonClick={() => {
                    setDeletionDetails({
                      projectId: row.projectId,
                      userId: row.userId,
                      id: row.id,
                      ...row,
                    });
                    setShowPopup(true);
                  }}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default ProjectStaffComponent;
