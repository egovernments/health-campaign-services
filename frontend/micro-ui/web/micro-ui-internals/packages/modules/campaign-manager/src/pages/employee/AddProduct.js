import { Loader, FormComposerV2, Header } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { addProductConfig } from "../../configs/addProductConfig";
import { Toast } from "@egovernments/digit-ui-components";

function AddProduct() {
  const { t } = useTranslation();
  const history = useHistory();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [showToast, setShowToast] = useState(null);
  const { state } = useLocation();
  const { mutate: createProduct } = Digit.Hooks.campaign.useCreateProduct(tenantId);
  const { mutate: createProductVariant } = Digit.Hooks.campaign.useCreateProductVariant(tenantId);

  const checkValid = (formData) => {
    const target = formData?.["addProduct"];
    let isValid = false;
    if (target) {
      isValid = target?.some((i) => !i.name || !i.type || !i.variant);
      return !isValid;
    } else {
      return isValid;
    }
  };
  const closeToast = () => {
    setShowToast(null);
  };

  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);

  const onSubmit = async (formData) => {
    const isValid = checkValid(formData);
    const invalidName = formData?.addProduct
      ?.map((i) => {
        if (i?.name?.length > 2 && i?.name?.length < 101) {
          return true;
        } else {
          return false;
        }
      })
      ?.includes(false);

    const invalidVariant = formData?.addProduct
      ?.map((i) => {
        if (i?.variant?.length > 2 && i?.variant?.length < 101) {
          return true;
        } else {
          return false;
        }
      })
      ?.includes(false);

    if (!isValid) {
      setShowToast({ key: "error", label: "CAMPAIGN_ADD_PRODUCT_MANDATORY_ERROR", isError: true });
      return;
    }

    if (invalidName) {
      setShowToast({ key: "error", label: "CAMPAIGN_PRODUCT_NAME_ERROR", isError: true });
      return;
    }

    if (invalidVariant) {
      setShowToast({ key: "error", label: "CAMPAIGN_PRODUCT_VARIANT_ERROR", isError: true });
      return;
    }

    const payloadData = formData?.["addProduct"]?.map((i) => ({
      tenantId: tenantId,
      type: i?.type?.code,
      name: i?.name,
    }));

    await createProduct(payloadData, {
      onError: (error, variables) => {
        console.log(error);
        setShowToast({ key: "error", label: error, isError: true });
      },
      onSuccess: async (data) => {
        const resData = data?.Product;
        const variantPayload = resData.map((i) => {
          const target = formData?.["addProduct"]?.find((f) => f.name === i.name);
          if (target) {
            return {
              tenantId: tenantId,
              productId: i?.id,
              variation: target?.variant,
              sku: `${target?.name} - ${target?.variant}`,
            };
          }
          return;
        });
        await createProductVariant(variantPayload, {
          onError: (error, variables) => {
            console.log(error);
            setShowToast({ key: "error", label: error, isError: true });
          },
          onSuccess: async (data) => {
            history.push(`/${window.contextPath}/employee/campaign/response?isSuccess=${true}`, {
              message: "ES_PRODUCT_CREATE_SUCCESS_RESPONSE",
              preText: "ES_PRODUCT_CREATE_SUCCESS_RESPONSE_PRE_TEXT",
              boldText: "ES_PRODUCT_CREATE_SUCCESS_RESPONSE_BOLD_TEXT",
              postText: "ES_PRODUCT_CREATE_SUCCESS_RESPONSE_POST_TEXT",
              actionLabel: "ES_PRODUCT_RESPONSE_ACTION",
              actionLink: `/${window.contextPath}/employee/campaign/setup-campaign${state?.urlParams}`,
            });
          },
        });
      },
    });
    return;
  };
  const onFormValueChange = (setValue, formData, formState, reset, setError, clearErrors, trigger, getValues) => {
    return;
  };

  const onSecondayActionClick = () => {
    history.push(`/${window.contextPath}/employee/campaign/setup-campaign${state?.urlParams}`);
  };

  return (
    <div>
      <FormComposerV2
        showMultipleCardsWithoutNavs={true}
        label={t("ES_CAMPAIGN_ADD_PRODUCT_BUTTON")}
        config={addProductConfig?.map((config) => {
          return {
            ...config,
          };
        })}
        onSubmit={onSubmit}
        fieldStyle={{ marginRight: 0 }}
        noBreakLine={true}
        cardClassName={"page-padding-fix"}
        onFormValueChange={onFormValueChange}
        actionClassName={"addProductActionClass"}
        showSecondaryLabel={true}
        secondaryLabel={t("HCM_BACK")}
        onSecondayActionClick={onSecondayActionClick}
      />

      {showToast && (
        <Toast
          type={showToast?.isError ? "error" : "success"}
          // error={showToast?.isError}
          label={t(showToast?.label)}
          isDleteBtn={"true"}
          onClose={() => setShowToast(false)}
        />
      )}
    </div>
  );
}

export default AddProduct;
