const UpdatePlanConfig = async (body) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/plan-service/config/_update",
      useCache: false,
      method: "POST",
      userService: true,
      body,
    });
    return response;
  } catch (error) {
    if (error?.response?.data?.Errors) {
      throw new Error(error.response.data.Errors[0].message);
    }
    throw new Error("An unknown error occurred");
  }
};
export default UpdatePlanConfig;
