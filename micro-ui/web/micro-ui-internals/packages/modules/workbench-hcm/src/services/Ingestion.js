import { Request } from "../utils/Request";

const IngestionService = {
  facility: async (formData) => 
    await Request({
      url: "/hcm-moz-impl/v1/dhis2/facilities/ingest",
      useCache: false,
      method: "POST",
      auth: true,
      multipartFormData: true,
      multipartData: {
        data: formData,
      },
    })
  ,
  user: async (formData)  => 
   
  await Request({
      url: "/hcm-moz-impl/v1/dhis2/users/ingest",
      useCache: false,
      method: "POST",
      auth: true,
      multipartFormData: true,
      multipartData: {
        data: formData,
      },
      params:{
      source: "EXCEL"
      },
    }),
  
  ou: async (formData) => 
  await Request({
      url: "/hcm-moz-impl/v1/dhis2/OU/ingest",
      useCache: false,
      method: "POST",
      auth: true,
      multipartFormData: true,
      multipartData: {
        data: formData,
      },
      params:{
        source: "EXCEL"
        },
    })
  
};

export default IngestionService;
