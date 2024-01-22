import React from "react";
import { useTranslation } from "react-i18next";
import { useState, useEffect } from "react";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { data } from "../configs/ViewProjectConfig";

const ProjectBeneficiaryComponent = (props) => {
    const { t } = useTranslation();
    const [productIds, setProductIds] = useState([]);

    const requestCriteria = {
        url: "/project/resource/v1/_search",
        changeQueryName: props.projectId,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },

        body: {
            ProjectResource: {
                projectId: props.projectId
            },
            // apiOperation: "SEARCH"
        },
        config:{
            enabled: props.projectId ? true: false
        }
    };

    const { isLoading, data: projectResource } = Digit.Hooks.useCustomAPIHook(requestCriteria);

    const [userIds, setUserIds] = useState([]);

    useEffect(() => {
        // Extract productVariantIds and save them in the state
        if (projectResource && projectResource?.ProjectResources?.length > 0) {
            const productVariantIdsArray = projectResource?.ProjectResources.map(row => row.resource?.productVariantId);
            setUserIds(productVariantIdsArray);
        }
    }, [projectResource]);

    const productVariantRequest = {
        url: "/product/variant/v1/_search",
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },
        body: {
            ProductVariant: {
                "id": userIds
            },
        },
        config:{
            enabled: userIds.length>0 ? true: false
        }
    };

    const { isLoading: VariantLoading, data: variantDetails } = Digit.Hooks.useCustomAPIHook(productVariantRequest);

    useEffect(() => {
        // Extract product IDs from variantDetails and save them in the state
        if (variantDetails && variantDetails?.ProductVariant.length > 0) {
            const ProductIdArray = variantDetails.ProductVariant.map(row => row.productId);
            setProductIds(ProductIdArray);
        }
    }, [variantDetails]);

    const productRequest = {
        url: "/product/v1/_search",
        changeQueryName: productIds,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },
        body: {
            Product: {
                "id": productIds
            },
        },
        config:{
            enabled: productIds.length>0 ? true: false
        }
    };

    const { data: product } = Digit.Hooks.useCustomAPIHook(productRequest);


    const userMap = {};
    variantDetails?.ProductVariant?.forEach(productVariant => {
        userMap[productVariant.id] = productVariant;
    });


    const mappedProjectVariant = projectResource?.ProjectResources.map(resource => {
        const productVariantInfo = userMap[resource.resource?.productVariantId];
        const productInfo = product?.Product?.find(p => p.id === productVariantInfo?.productId);

        if (productVariantInfo && productInfo) {
            return {
                ...resource,
                productVariant: {
                    ...productVariantInfo,
                    product: productInfo
                }
            };
        } else {
            // Handle the case where productVariant or product info is not found for a productVariantId
            return {
                ...resource,
                productVariant: null
            };
        }
    });


    const isValidTimestamp = (timestamp) => timestamp !== 0 && !isNaN(timestamp);

    //to convert epoch to date
    projectResource?.ProjectResources.forEach(row => {
        row.formattedStartDate = isValidTimestamp(row.startDate)
            ? Digit.DateUtils.ConvertEpochToDate(row.startDate)
            : "NA";
        row.formattedEndDate = isValidTimestamp(row.endDate)
            ? Digit.DateUtils.ConvertEpochToDate(row.endDate)
            : "NA";
    });


    const columns = [
        { label: t("PROJECT_RESOURCE_ID"), key: "id" },
        { label: t("PRODUCT_VARIANT_ID"), key: "resource.productVariantId" },
        { label: t("PRODUCT_ID"), key: "productVariant.productId" },
        { label: t("SKU"), key: "productVariant.sku" },
        { label: t("PRODUCT_VARIATION"), key: "productVariant.variation" },
        { label: t("START_DATE"), key: "formattedStartDate" },
        { label: t("END_DATE"), key: "formattedEndDate" },
        { label: t("RESOURCE_TYPE"), key: "resource.type" },
        // { label: t("IS_BASE_UNIT_VARIANT"), key: "resource.isBaseUnitVariant" },
        { label: t("NAME"), key: "productVariant.product.name" },
        { label: t("MANUFACTURER"), key: "productVariant.product.manufacturer" },
        { label: t("PRODUCT_TYPE"), key: "productVariant.product.type" }
    ];

    const getDetailFromProductVariant = (row, key) => {
        const productVariantDetail = row.productVariant || {};
    
        // Helper function to traverse nested keys
        const getValue = (object, nestedKey) => {
            const keys = nestedKey.split('.');
            return keys.reduce((acc, curr) => acc?.[curr], object);
        };
    
        // Check if the key is nested
        const value = getValue(row, key);
    
        // Handle boolean values
        if (typeof value === 'boolean') {
            return value.toString();
        }
    
        // Check if the value exists, otherwise return 'NA'
        return value !== undefined ? value.toString() : "NA";
    };

    if (isLoading) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("PROJECT_RESOURCE")}</Header>
            {projectResource?.ProjectResources.length === 0 ? (
                <h1>{t("NO_PROJECT_RESOURCE")}</h1>
            ) : (
                <table className="table reports-table sub-work-table">
                    <thead>
                        <tr>
                            {columns?.map((column, index) => (
                                <th key={index}>{column?.label}</th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {mappedProjectVariant?.map((row, rowIndex) => (
                            <tr key={rowIndex}>
                                {columns.map((column, columnIndex) => (
                                    <td key={columnIndex}>
                                        {getDetailFromProductVariant(row, column.key)}
                                    </td>
                                ))}
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
        </div>
    );

}

export default ProjectBeneficiaryComponent