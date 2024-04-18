export const useResourceData = async (data , hierarchyType) => {
    const response =  Digit.CustomService.getResponse({
        url: "/project-factory/v1/data/_create",
        body: {
            "ResourceDetails": {
                "type": "facility",
                "hierarchyType": hierarchyType,
                "tenantId": Digit.ULBService.getCurrentTenantId(),
                "fileStoreId": data?.uploadFacility?.[0]?.id,
                "action": "validate",
              }
        }
      }

    );
    const result = await response;
    return result;
  };
  