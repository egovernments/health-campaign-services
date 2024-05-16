import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import CampaignDocumentsPreview from "./CampaignDocumentsPreview";

function CampaignResourceDocuments({ resources = [], svgStyles = {}, isUserGenerate = false }) {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [processData, setProcessData] = useState([]);
  // const reqCriteriaResource = {
  //   url: `/project-factory/v1/data/_search`,
  //   body: {
  //     SearchCriteria: {
  //       tenantId: tenantId,
  //       id: resources,
  //     },
  //   },
  //   config: {
  //     enabled: true,
  //     select: (data) => {
  //       return data?.ResourceDetails;
  //     },
  //   },
  // };

  // const { isLoading, data: resourceData, isFetching } = Digit.Hooks.useCustomAPIHook(reqCriteriaResource);

  useEffect(() => {
    // if (!isLoading) {
    const temp = resources.map((i) => {
      return {
        id: i?.processedFilestoreId,
        type: i?.type,
      };
    });
    setProcessData(temp);
    // }
  }, [resources]);

  return (
    <div>
      <CampaignDocumentsPreview documents={processData} isUserGenerate={true} />
    </div>
  );
}

export default CampaignResourceDocuments;
