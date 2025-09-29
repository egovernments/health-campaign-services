export const generatedResourceTransformer = (dbRows: any[] = []) => {
  return dbRows.map((item: any) => {
    // Extract and structure audit details
    item.auditDetails = {
      lastModifiedTime: item.lastmodifiedtime,
      createdTime: item.createdtime,
      lastModifiedBy: item.lastmodifiedby,
      createdBy: item.createdby,
    };

    // Rename and restructure properties
    item.tenantId = item.tenantid;
    item.additionalDetails = item.additionaldetails ? item.additionaldetails : {};
    item.additionalDetails.Filters = item?.additionaldetails?.filters ? {} : item?.additionaldetails?.filters;
    item.fileStoreid = item.filestoreid;
    item.locale = item.locale;

    // Remove unnecessary properties
    delete item.additionaldetails;
    delete item.lastmodifiedtime;
    delete item.createdtime;
    delete item.lastmodifiedby;
    delete item.createdby;
    delete item.filestoreid;
    delete item.tenantid;

    return { ...item }; // Return the transformed object
  });
};