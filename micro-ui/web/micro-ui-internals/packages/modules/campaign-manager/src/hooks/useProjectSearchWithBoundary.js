const useProjectSearchWithBoundary = async ({ name, tenantId, boundaries }) => {
  const requests = boundaries.map(({ code }) => {
    return Digit.CustomService.getResponse({
      url: "/health-project/v1/_search",
      params: {
        tenantId: tenantId,
        limit: 10,
        offset: 0,
      },
      body: {
        Projects: [
          {
            name: name,
            tenantId: tenantId,
            address: {
              boundary: code,
            },
          },
        ],
      },
    }).then((res) => {
      return res?.Project?.[0];
    });
  });
  const newData = await Promise.all(requests);
  return newData;
};

export default useProjectSearchWithBoundary;
