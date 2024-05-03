import { Loader, FormComposerV2, Header, Toast } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { addProductConfig } from "../../configs/addProductConfig";

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
    target?.forEach((item) => {
      if (item?.name && item?.name?.trim()?.length !== 0 && item?.type && item?.variant && item?.variant?.trim()?.length !== 0) {
        isValid = true;
      } else {
        isValid = false;
      }
    });
    return isValid;
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
    if (!isValid) {
      setShowToast({ key: "error", label: "CAMPAIGN_ADD_PRODUCT_MANDATORY_ERROR", isError: true });
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
        setShowToast({ key: "error", label: error });
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
            };
          }
          return;
        });
        await createProductVariant(variantPayload, {
          onError: (error, variables) => {
            console.log(error);
            setShowToast({ key: "error", label: error });
          },
          onSuccess: async (data) => {
            history.push(`/${window.contextPath}/employee/campaign/response?isSuccess=${true}`, {
              message: "ES_PRODUCT_CREATE_SUCCESS_RESPONSE",
              text: "ES_PRODUCT_CREATE_SUCCESS_RESPONSE_TEXT",
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
    return;
  };
  return (
    <div>
      <FormComposerV2
        showMultipleCardsWithoutNavs={true}
        label="ES_CAMPAIGN_ADD_PRODUCT_BUTTON"
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
        actionClassName={"actionBarClass"}
        showSecondaryLabel={true}
        secondaryLabel={t("HCM_BACK")}
        onSecondayActionClick={onSecondayActionClick}
      />

      {showToast && <Toast error={showToast?.isError} label={showToast?.label} isDleteBtn={"true"} onClose={() => setShowToast(false)} />}
    </div>
  );
}

export default AddProduct;
