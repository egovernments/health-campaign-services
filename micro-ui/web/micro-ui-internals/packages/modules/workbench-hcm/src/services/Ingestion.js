import { Request } from "@egovernments/digit-ui-libraries";

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
    }),

    eventSearch: async (searchParams) =>
    await Request({
      url: "/hcm-moz-impl/v1/eventHistory/_search",
      useCache: false,
      method: "POST",
      auth: true,
      userService: true,
      params: searchParams,
    }),

  
    project: async (formData) => 
    await Request({
        url: "/hcm-moz-impl/v1/dhis2/project/ingest",
        useCache: false,
        method: "POST",
        auth: true,
        multipartFormData: true,
        multipartData: {
          data: formData,
        },
        params:{
          fileStoreId : "hjfrbjdbfh",
          source: "EXCEL"
          },
      }),
  
      boundary: async (formData) => 
      await Request({
          url: "/hcm-moz-impl/v1/dhis2/boundary/ingest",
          useCache: false,
          method: "POST",
          auth: true,
          multipartFormData: true,
          multipartData: {
            data: formData,
          },
          params:{
            fileStoreId : "hjfrbjdbfh",
            source: "EXCEL"
            },
        }),
        ingest: async (searchParams,tenantId) =>
        await Request({
          data:{tenantId:tenantId},
          url: "/hcm-moz-impl/v1/ingest",
          useCache: false,
          method: "POST",
          auth: true,
          userService: true,
          params: searchParams,
        }),
        fileStore: async (formData) => 
        await Request({
            url: "/filestore/v1/files",
            useCache: false,
            method: "POST",
            auth: true,
            multipartFormData: true,
            multipartData: {
              data: formData,
            },
          }),
          fileStoreSearch: async (searchParams) => 
          await Request({
              url: "/filestore/v1/files/url",
              useCache: false,
              method: "GET",
              auth: true,
              userService: true,
              params: searchParams,
            }),

        


  
};

export default IngestionService;
