const createCampaignService = async (req, tenantId) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/project-factory/v1/project-type/create",
      body: {
        CampaignDetails: req,
      },
    });
    return response;
  } catch (error) {
    if (!error?.response?.data?.Errors[0].description) {
      throw new Error(error?.response?.data?.Errors[0].code);
    } else {
      throw new Error(error?.response?.data?.Errors[0].description);
    }
  }
};

export default createCampaignService;
