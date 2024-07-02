const SearchCampaignConfig = async (body) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/project-factory/v1/project-type/search",
      useCache: false,
      method: "POST",
      userService: false,
      body,
    });
    if (response?.CampaignDetails?.length === 0) {
      throw new Error("Campaign not found with the given id");
    }
    return response?.CampaignDetails?.[0];
  } catch (error) {
    if (error?.response?.data?.Errors) {
      throw new Error(error.response.data.Errors[0].message);
    }
    throw new Error("An unknown error occurred");
  }
};

export default SearchCampaignConfig;
