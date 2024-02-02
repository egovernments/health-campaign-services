import { Button, Header, SVG, Toast } from "@egovernments/digit-ui-react-components";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import AssignTarget from "./AssignTarget";

const TargetComponent = (props) => {
  const { t } = useTranslation();

  const [showModal, setShowModal] = useState(false);
  const [showToast, setShowToast] = useState(false);

  const [formData, setFormData] = useState({
    beneficiaryType: "",
    totalNo: 0,
    targetNo: 0,
  });

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 5000);
  };

  const handleOnCancel = () => {
    setShowModal(false);
  };

  const targetIndex = props?.project?.targets?.findIndex((target) => target?.beneficiaryType === formData?.beneficiaryType);

  const handleOnChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };
  const columns = [
    { label: t("WBH_BENEFICIARY_TYPE"), key: "beneficiaryType" },
    { label: t("WBH_TOTAL_NUMBER"), key: "totalNo" },
    { label: t("WBH_TARGET_NUMBER"), key: "targetNo" },
    // { label: t("WBH_ACTIONS"), key: "actions" },
  ];

  // const handleEditButtonClick = (index) => {
  //   // const updatedTargets = [...props.targets[index]];
  //   console.log(props.targets[index].isDeleted);
  //   setFormData(props.targets[index]);
  //   setShowModal(true);
  // };

  // const handleEditButtonClick = (index) => {
  //   const updatedTarget = { ...props.targets[index], isDeleted: true };
  
  //   console.log(updatedTarget); // It should print true
  
  //   setFormData(updatedTarget);
  //   setShowModal(true);
  // };
  


  const reqCriteria = {
    url: "/project/v1/_update",
    config: false
  };


  const mutation = Digit.Hooks.useCustomAPIMutationHook(reqCriteria);
  // const { refetch } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  const onSuccess = () => {
    closeToast();
    // refetch();
    setShowToast({ key: "success", label: "WBH_PROJECT_TARGET_EDITED_SUCESSFULLY" });
  };
  const onError = (resp) => {
    const label = resp?.response?.data?.Errors?.[0]?.code;
    setShowToast({ isError: true, label });
  };

  const handleSubmitTarget = async () => {
    const targets = {
      beneficiaryType: formData?.beneficiaryType,
      totalNo: Number(formData?.totalNo),
      targetNo: Number(formData?.targetNo),
      isDeleted: false,
    };

    // Find the index of the target in the existing targets array
    const targetIndex = props.project.targets.findIndex((target) => target.beneficiaryType === formData.beneficiaryType);

    if (targetIndex !== -1) {
      // If the target exists, update it
      const updatedTargets = [...props.project.targets.slice(0, targetIndex), targets, ...props.project.targets.slice(targetIndex + 1)];
      // const updatedTargets = [...props.project.targets];
      updatedTargets[targetIndex] = targets;

      const updatedProject = {
        ...props?.project,
        targets: updatedTargets,
      };

      await mutation.mutate(
        {
          body: {
            Projects: [updatedProject],
          },
        },
        {
          onError,
          onSuccess,
        }
      );

      setShowModal(false);

    } else {
      // Handle case when the target doesn't exist (optional)
      console.log("Target not found for update");
    }
  };

  return (
    <div className="override-card">
      <Header className="works-header-view">{t("WBH_TARGET")}</Header>

      {showModal && (
        <AssignTarget
          t={t}
          isEdit={true}
          heading={"WBH_CAMPAIGN_ASSIGNMENT_EDIT_TARGET"}
          onClose={() => {
            setShowModal(false);
          }}
          onChange={handleOnChange}
          beneficiaryType={formData?.beneficiaryType}
          totalNo={formData?.totalNo}
          targetNo={formData?.targetNo}
          onSubmit={handleSubmitTarget}
          onCancel={handleOnCancel}
        />
      )}

      {showToast && <Toast label={showToast.label} error={showToast?.isError} isDleteBtn={true} onClose={() => setShowToast(null)}></Toast>}
      {props?.targets?.length === 0 ? (
        <h1>{t("WBH_NO_TARGETS_FOUND")}</h1>
      ) : (
        <table className="table reports-table sub-work-table">
          <thead>
            <tr>
              {columns.map((column, index) => (
                <th key={index}>{column.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {props?.targets?.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {columns.map((column, columnIndex) => (
                  <td key={columnIndex}>
                    {column.key !== "actions" ? (
                      row[column.key]
                    ) : (
                      <Button
                        label={`${t("WBH_EDIT_ACTION")}`}
                        type="button"
                        variation="secondary"
                        icon={<SVG.Delete width={"28"} height={"28"} />}
                        onButtonClick={() => handleEditButtonClick(rowIndex)}
                      />
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default TargetComponent;
