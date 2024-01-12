import React, { useEffect, useState } from "react";
import { Card, Header, Button, Loader, Toast, SVG, Modal } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
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

  const [showDepartment, setShowDepartment] = useState("");

  const userId = Digit.UserService.getUser().info.uuid;

  const [showPopup, setShowPopup] = useState(false);

  const { tenantId } = Digit.Hooks.useQueryParams();

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

  const isValidTimestamp = (timestamp) => timestamp !== 0 && !isNaN(timestamp);

  //to convert epoch to date and to convert isDeleted boolean to string
  const dateConversion = projectStaff?.ProjectStaff.forEach((row) => {
    row.formattedStartDate = isValidTimestamp(row.startDate) ? Digit.DateUtils.ConvertEpochToDate(row.startDate) : "NA";
    row.formattedEndDate = isValidTimestamp(row.endDate) ? Digit.DateUtils.ConvertEpochToDate(row.endDate) : "NA";
    row.isDeleted = row.isDeleted ? true : false;
  });

  useEffect(() => {
    // Extract user IDs and save them in the state
    if (projectStaff && projectStaff.ProjectStaff.length > 0) {
      const userIdArray = projectStaff.ProjectStaff.map((row) => row.userId);
      setUserIds(userIdArray);
    }
  }, [projectStaff]);

  const userRequestCriteria = {
    url: "/user/_search",
    body: {
      tenantId: "mz",
      uuid: userIds,
    },
  };

  const { isLoading: isUserSearchLoading, data: userInfo } = Digit.Hooks.useCustomAPIHook(userRequestCriteria);

  const userMap = {};
  userInfo?.user?.forEach((user) => {
    userMap[user.uuid] = user;
  });

  // Map userId to userInfo
  const mappedProjectStaff = projectStaff?.ProjectStaff.map((staff) => {
    const user = userMap[staff.userId];
    if (user) {
      return {
        ...staff,
        userInfo: user,
      };
    } else {
      // Handle the case where user info is not found for a userId
      return {
        ...staff,
        userInfo: null,
      };
    }
  });

  const columns = [
    { label: t("PROJECT_STAFF_ID"), key: "id" },
    { label: t("USERNAME"), key: "userInfo.userName" },
    { label: t("ROLES"), key: "userInfo.roles" },
    { label: t("IS_DELETED"), key: "isDeleted" },
    { label: t("START_DATE"), key: "formattedStartDate" },
    { label: t("END_DATE"), key: "formattedEndDate" },
    // { label: t("ACTIONS") },
  ];

  function getNestedPropertyValue(obj, path) {
    return path.split(".").reduce((acc, key) => (acc && acc[key] ? acc[key] : "NA"), obj);
  }

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
              setShowResult(data?.Employees[0]?.jurisdictions?.[0]);
              const employeeDepartment = data?.Employees[0]?.assignments[0]?.department || [];
              if (employeeDepartment.length > 0) {
                setShowDepartment(employeeDepartment);
              } else {
                setShowDepartment("NA");
              }
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
              projectId: props?.projectId,
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

  if (isLoading && isUserSearchLoading) {
    return <Loader></Loader>;
  }

  return (
    <div className="override-card">
      <Header className="works-header-view">{t("PROJECT_STAFF")}</Header>

      <div>
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
            showDepartment={showDepartment}
            heading={"WBH_ASSIGN_PROJECT_STAFF"}
            isDisabled={!showResult || showResult.length === 0} // Set isDisabled based on the condition
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

        {mappedProjectStaff?.length > 0 ? (
          <table className="table reports-table sub-work-table">
            <thead>
              <tr>
                {columns?.map((column, index) => (
                  <th key={index}>{column?.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {mappedProjectStaff?.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {columns?.map((column, columnIndex) => (
                    <td key={columnIndex}>
                      {column?.render
                        ? column?.render(row)
                        : column?.key === "userInfo.roles"
                        ? row?.userInfo?.roles
                            .slice(0, 2)
                            .map((role) => role.name)
                            .join(", ") // to show 2 roles
                        : column?.key.includes(".")
                        ? getNestedPropertyValue(row, column?.key)
                        : row[column.key] || "NA"}
                    </td>
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
        ) : (
          <div style={{ textAlign: "center" }}>
            <h1>{t("NO_PROJECT_STAFF")}</h1>
          </div>
        )}
      </div>
    </div>
  );
};

export default ProjectStaffComponent;
