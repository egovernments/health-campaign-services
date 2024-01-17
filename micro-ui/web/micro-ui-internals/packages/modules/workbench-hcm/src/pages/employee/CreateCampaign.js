import { Loader, FormComposerV2, Header, Toast, MultiUploadWrapper, Button, Close } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory } from "react-router-dom";

const CreateCampaign = () => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();
  const history = useHistory();
  const [showToast, setShowToast] = useState(false);

  const requestCriteria = {
    url: "/hcm-bff/hcm/_processmicroplan",
    params: {},
    body: {
      HCMConfig: {},
    },
  };

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 9000);
  };

  const mutation = Digit.Hooks.useCustomAPIMutationHook(requestCriteria);

  const campaignConfig = [
    {
      body: [
        {
          isMandatory: false,
          key: "campaignName",
          type: "text",
          label: "CAMPAIGN_NAME",
          disable: false,
          populators: {
            name: "campaignName",
            error: "ES_TQM_REQUIRED",
            required: true,
          },
        },
        {
          isMandatory: true,
          key: "campaignType",
          type: "radioordropdown",
          label: "CAMPAIGN_TYPE",
          disable: false,
          populators: {
            name: "campaignType",
            optionsKey: "campaignType",
            // error: "ES__REQUIRED",
            required: true,
            mdmsConfig: {
              moduleName: "HCM",
              masterName: "HCMTemplate",
            },
          },
        },
        {
          isMandatory: false,
          key: "projectType",
          type: "radioordropdown",
          label: "PROJECT_TYPE",
          disable: false,
          populators: {
            name: "projectType",
            optionsKey: "code",
            error: "ES__REQUIRED",
            required: true,
            mdmsConfig: {
              moduleName: "HCM-PROJECT-TYPES",
              masterName: "projectTypes",
            },
          },
        },
        {
          isMandatory: true,
          key: "rowDetails",
          type: "component",
          component: "RowDetails",
          label: "ROW_DETAILS",
          disable: false,
          "customProps": {
          "module": "HCM"
        },
          populators: {
            name: "rowDetails",
            // optionsKey: "code",
            error: "ES__REQUIRED",
            required: true,
          },
        },
        {
          isMandatory: true,
          key: "upload",
          type: "multiupload",
          label: "BULK_UPLOAD",
          disable: false,
          populators: {
            name: "upload",
            error: "ES_TQM_REQUIRED",
            required: true,
          },
        },
      ],
    },
  ];

  const [formConfigs, setFormConfigs] = useState([campaignConfig]);

  const onSubmit = async (data) => {
    await mutation.mutate(
      {
        params: {},
        body: {
          HCMConfig: {
            tenantId: Digit.ULBService.getCurrentTenantId(),
            fileStoreId: data?.upload?.[0]?.[1]?.fileStoreId?.fileStoreId,
            campaignType: data?.campaignType?.campaignType,
            selectedRows: (data?.rowDetails || []).map((row) => ({
              startRow: row.startRow,
              endRow: row.endRow,
            })),
          },
        },
      },
      {
        onSuccess: () => {
          setShowToast({ label: `${t("WBH_CAMPAIGN_CREATED")}` });
          closeToast();
        },
      }
      // {
      //   onSuccess: () => {

      //   }
      // }
    );
  };
  const handleMutation = async (params, campaignType) => {
    params.body.HCMConfig.campaignType=campaignType;
    await mutation.mutate(
      {
        ...params,
        body: {
          ...params.body
        },
      },
      {
        onSuccess: () => {
          setShowToast({ label: `${t("WBH_CAMPAIGN_CREATED")}` });
          closeToast();
        },
      }
    );
    await new Promise(resolve => setTimeout(resolve, 3000));
  };
  const onSubmit2 = async (data) => {
    const commonParams = {
      params: {},
      body: {
        HCMConfig: {
          tenantId: Digit.ULBService.getCurrentTenantId(),
          fileStoreId: data?.upload?.[0]?.[1]?.fileStoreId?.fileStoreId,
          selectedRows: (data?.rowDetails || []).map((row) => ({
            startRow: row.startRow,
            endRow: row.endRow,
          })),
        },
      },
    };
  
    if (data?.campaignType?.campaignType === "MicroPlanBoundaryProvincia") {
      await handleMutation(commonParams, data?.campaignType?.campaignType);
      await handleMutation(commonParams, "MicroPlanBoundaryPostoAdministrativo");
      await handleMutation(commonParams, "MicroPlanBoundaryVillage");
    } else {
      await handleMutation(commonParams, data?.campaignType?.campaignType);
    }
  };

  const handleDeleteForm = (index) => {
    const updatedConfigs = [...formConfigs];
    updatedConfigs.splice(index, 1);
    setFormConfigs(updatedConfigs);
  };

  return (
    <div>
      <Header>{t("WORKBENCH_CREATE_CAMPAIGN")}</Header>
      {formConfigs.map((formConfig, index) => (
        <div key = {index}>
          {formConfigs.length > 1 && (
              <div className="deleteConfig" onClick={() => handleDeleteForm(index)}>
                <Close />
              </div>
            )}
        <FormComposerV2
          key={index}
          label="WORKBENCH_CREATE_CAMPAIGN"
          config={formConfig.map((config) => ({ ...config }))}
          defaultValues={{}}
          // onSubmit={onSubmit}
          onSubmit={onSubmit2}

          // onSubmit={(data) => onSubmit(data, createMutation())}
          fieldStyle={{ marginRight: 0 }}
          noBreakLine={true}
          cardClassName={"page-padding-fix"}
        />
        </div>
      ))}
      {showToast && <Toast error={showToast?.isError} label={showToast?.label} isDleteBtn={"true"} onClose={() => setShowToast(false)} />}
      {/* <Button
          variation="secondary"
          label={`${t("ADD_CAMPAIGN")}`}
          type="button"
          className="workbench-add-row-detail-btn"
          onButtonClick={() => setFormConfigs([...formConfigs, campaignConfig])}
          // onButtonClick={handleCreateNewRowDetails}
          style={{ fontSize: "1rem" }}
        /> */}
    </div>
  );
};

export default CreateCampaign;
