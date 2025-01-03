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
    // throw new Error(error?.response?.data?.Errors[0].message);
    if (!error?.response?.data?.Errors[0].description) {
      throw new Error(error?.response?.data?.Errors[0].code);
    } else {
      throw new Error(error?.response?.data?.Errors[0].description);
    }
  }
};

export default updateCampaignService;
