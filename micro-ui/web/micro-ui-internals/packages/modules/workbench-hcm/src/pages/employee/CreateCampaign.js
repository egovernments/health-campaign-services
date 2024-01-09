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
    url: "/hcm-bff/bulk/_transform",
    params: {
    },
    body: {
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
          isMandatory: false,
          key: "campaignType",
          type: "radioordropdown",
          label: "CAMPAIGN_TYPE",
          disable: false,
          populators: {
            name: "campaignType",
            optionsKey: "templateName",
            // error: "ES__REQUIRED",
            required: true,
            mdmsConfig: {
              moduleName:"HCM",
              masterName: "TransformTemplate",
            }
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
              moduleName:"HCM-PROJECT-TYPES",
              masterName: "projectTypes",
            }
          },
        },
        {
          isMandatory: false,
          key: "startRow",
          type: "number",
          label: "CAMPAIGN_START_NUMBER",
          disable: false,
          populators: {
            name: "startRow",
            error: "ES_TQM_REQUIRED",
            required: true
          },
        },
        {
            isMandatory: false,
            key: "endRow",
            type: "number",
            label: "CAMPAIGN_END_NUMBER",
            disable: false,
            populators: {
              name: "endRow",
              error: "ES_TQM_REQUIRED",
              required: true
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
              required: true
            },
          }
      ],
    },
    
  
  ];

  const createModifiedData = (data) => {
    
    
    const modifiedData = [
        {
            "tenantId": Digit.ULBService.getCurrentTenantId(),
            "fileStoreId": data?.upload?.[0]?.[1]?.fileStoreId?.fileStoreId  ,
            "transformTemplate": data?.campaignType?.templateName,
            "startRow": data?.startRow,
            "endRow": data?.endRow
        }
    ]
    return modifiedData;
};
  const onSubmit = async (data) => {
    const modifiedData = createModifiedData(data);
    await mutation.mutate(
      {
        params: {},
        body:{
          tenantId: Digit.ULBService.getCurrentTenantId(),
            fileStoreId: data?.upload?.[0]?.[1]?.fileStoreId?.fileStoreId  ,
            transformTemplate: data?.campaignType?.templateName,
            startRow: data?.startRow,
            endRow: data?.endRow
        }
        
      },
      {
        onSuccess: () => {
          setShowToast({ label: `${t("WBH_CAMPAIGN_CREATED")}` });
          closeToast();
        },
      }
    )
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