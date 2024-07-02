import { useMutation } from "react-query";

const createProductVariantService = async (req, tenantId) => {
  try {
    const response = await Digit.CustomService.getResponse({
      url: "/product/variant/v1/_create",
      body: {
        ProductVariant: req,
        apiOperation: "CREATE",
      },
    });
    return response;
  } catch (error) {
    throw new Error(error?.response?.data?.Errors?.[0].description);
  }
};

const useCreateProductVariant = (tenantId) => {
  return useMutation((reqData) => {
    return createProductVariantService(reqData, tenantId);
  });
};

export default useCreateProductVariant;
