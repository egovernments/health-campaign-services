const updateCampaignService = async (req, tenantId) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/project-factory/v1/project-type/update",
      body: {
        CampaignDetails: req,
      },
    });
    return response;
  } catch (error) {
    throw new Error(error?.response?.data?.Errors[0].message);
  }
};

export default updateCampaignService;
