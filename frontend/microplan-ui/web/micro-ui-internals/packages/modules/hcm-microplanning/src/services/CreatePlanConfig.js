const CreatePlanConfig = async (body) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/plan-service/config/_create",
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

export default CreatePlanConfig;
