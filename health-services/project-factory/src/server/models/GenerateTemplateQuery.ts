interface GenerateTemplateQuery {
    type: string;
    tenantId: string;
    hierarchyType: string;
    campaignId: string;
    additionalDetails: any
}

export default GenerateTemplateQuery;