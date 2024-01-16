import { Loader, FormComposerV2, Header, Toast, MultiUploadWrapper } from "@egovernments/digit-ui-react-components";
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
    }, 5000);
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
    );
  };

  return (
    <div>
      <Header>{t("WORKBENCH_CREATE_CAMPAIGN")}</Header>
      <FormComposerV2
        label="WORKBENCH_CREATE_CAMPAIGN"
        config={campaignConfig.map((config) => {
          return {
            ...config,
          };
        })}
        defaultValues={{}}
        onSubmit={onSubmit}
        fieldStyle={{ marginRight: 0 }}
        noBreakLine={true}
        cardClassName={"page-padding-fix"}
      />
      {showToast && <Toast error={showToast?.isError} label={showToast?.label} isDleteBtn={"true"} onClose={() => setShowToast(false)} />}
    </div>
  );
};

export default CreateCampaign;
