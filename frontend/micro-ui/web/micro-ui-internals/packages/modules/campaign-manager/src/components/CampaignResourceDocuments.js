import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import CampaignDocumentsPreview from "./CampaignDocumentsPreview";
import { CardText } from "@egovernments/digit-ui-components";

function CampaignResourceDocuments({ resources = [], svgStyles = {}, isUserGenerate = false }) {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [processData, setProcessData] = useState([]);
  const reqCriteriaResource = {
    url: `/project-factory/v1/data/_search`,
    body: {
      SearchCriteria: {
        tenantId: tenantId,
        id: resources,
      },
    },
    config: {
      enabled: true,
      select: (data) => {
        return data?.ResourceDetails;
      },
    },
  };

  const { isLoading, data: resourceData, isFetching } = Digit.Hooks.useCustomAPIHook(reqCriteriaResource);

  useEffect(() => {
    if (!isLoading) {
      const temp = resourceData.map((i) => {
        return {
          id: i?.processedFilestoreId,
          type: "User Credential",
        };
      });
      setProcessData(temp);
    }
  }, [isLoading, resourceData]);

  if (!processData?.[0]?.id) {
    return <CardText>{t("NO_DOCUMENTS_AVAILABLE")}</CardText>;
  }
  return (
    <div>
      <CampaignDocumentsPreview documents={processData} isUserGenerate={true} />
    </div>
  );
}

export default CampaignResourceDocuments;
