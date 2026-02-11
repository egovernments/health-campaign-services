/**
 * Transforms generic resource data fetched from the database into a standardized format.
 *
 * @param {any[]} dbRows - The array of database rows to transform.
 * @returns {Object[]} - An array of transformed resource objects.
 */
export const genericResourceTransformer = (dbRows: any[] = []) => {
  return dbRows?.map((row: any) => ({
    id: row?.id,
    tenantId: row?.tenantid,
    status: row?.status,
    action: row?.action,
    fileStoreId: row?.filestoreid,
    processedFilestoreId: row?.processedfilestoreid,
    campaignId: row?.campaignid,
    type: row?.type,
    auditDetails: {
      createdBy: row?.createdby,
      lastModifiedBy: row?.lastmodifiedby,
      createdTime: Number(row?.createdtime),
      lastModifiedTime: row?.lastmodifiedtime
        ? Number(row?.lastmodifiedtime)
        : null,
    },
    additionalDetails: row?.additionaldetails,
  }));
};

/**
 * Transforms campaign details fetched from the database into a standardized format.
 *
 * @param {any[]} dbRows - The array of database rows to transform.
 * @returns {Object[]} - An array of transformed campaign detail objects.
 */
export const campaignDetailsTransformer = (dbRows: any[] = []) => {
  return dbRows.map((row: any) => ({
    id: row?.id,
    tenantId: row?.tenantid,
    status: row?.status,
    action: row?.action,
    campaignNumber: row?.campaignnumber,
    isActive: row?.isactive,
    parentId: row?.parentid,
    campaignName: row?.campaignname,
    projectType: row?.projecttype,
    hierarchyType: row?.hierarchytype,
    boundaryCode: row?.boundarycode,
    projectId: row?.projectid,
    startDate: Number(row?.startdate),
    endDate: Number(row?.enddate),
    createdBy: row?.createdby,
    lastModifiedBy: row?.lastmodifiedby,
    createdTime: Number(row?.createdtime),
    lastModifiedTime: row?.lastmodifiedtime
      ? Number(row?.lastmodifiedtime)
      : null,
    campaignDetails: row?.campaigndetails,
    additionalDetails: row?.additionaldetails,
  }));
};

/**
 * Transforms generated resource data fetched from the database into a standardized format.
 *
 * @param {any[]} dbRows - The array of database rows to transform.
 * @returns {Object[]} - An array of transformed resource objects.
 */
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
