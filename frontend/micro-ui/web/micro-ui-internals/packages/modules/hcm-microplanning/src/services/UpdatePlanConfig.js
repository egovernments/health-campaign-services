const UpdatePlanConfig = async (body) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/plan-service/config/_update",
      useCache: false,
      method: "POST",
      userService: true,
      body,
    });
    return response
  } catch (error) {
    throw new Error(error?.response?.data?.Errors[0].message);
  }
};
export default UpdatePlanConfig;
