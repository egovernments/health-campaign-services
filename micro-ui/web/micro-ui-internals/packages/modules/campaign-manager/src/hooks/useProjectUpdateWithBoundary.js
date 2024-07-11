const useProjectUpdateWithBoundary = async ({ formData }) => {
  const requests = formData.map((item) => {
    return Digit.CustomService.getResponse({
      url: "/health-project/v1/_update",
      body: {
        Projects: [item],
      },
    }).then((res) => {
      return res;
    });
  });
  const newData = await Promise.all(requests);
  return newData;
};

export default useProjectUpdateWithBoundary;
