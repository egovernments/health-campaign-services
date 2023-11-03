import { Request } from "../Utils/Request";

const IngestionService = {
  facility: (file) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append(
      "DHIS2IngestionRequest",
      JSON.stringify({
        tenantId: Digit.ULBService.getCurrentTenantId(),
        dataType: "Facility",
        requestInfo: {
          userInfo: Digit.UserService.getUser(),
        },
      })
    );
    Request({
      url: "/hcm-moz-impl/v1/dhis2/facilities/ingest",
      useCache: false,
      method: "POST",
      auth: true,
      multipartFormData: true,
      multipartData: {
        data: formData,
      },
    });
  },
  user: (file) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append(
      "DHIS2IngestionRequest",
      JSON.stringify({
        tenantId: Digit.ULBService.getCurrentTenantId(),
        dataType: "Users",
        requestInfo: {
          userInfo: Digit.UserService.getUser(),
        },
      })
    );
    Request({
      url: "/hcm-moz-impl/v1/dhis2/users/ingest",
      useCache: false,
      method: "POST",
      auth: true,
      multipartFormData: true,
      multipartData: {
        data: formData,
      },
    });
  },
  ou: (file) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append(
      "DHIS2IngestionRequest",
      JSON.stringify({
        tenantId: Digit.ULBService.getCurrentTenantId(),
        dataType: "Project",
        requestInfo: {
          userInfo: Digit.UserService.getUser(),
        },
      })
    );
    Request({
      url: "/hcm-moz-impl/v1/dhis2/OU/ingest",
      useCache: false,
      method: "POST",
      auth: true,
      multipartFormData: true,
      multipartData: {
        data: formData,
      },
    });
  },
};

export default IngestionService;
