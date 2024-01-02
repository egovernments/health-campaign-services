import React from "react";
import { useTranslation } from "react-i18next";
import { useState, useEffect } from "react";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { data } from "../configs/ViewProjectConfig";

const ProjectBeneficiaryComponent = (props) => {
    const { t } = useTranslation();

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
        }
    };

    const { data: variantDetails } = Digit.Hooks.useCustomAPIHook(productVariantRequest);

    const userMap = {};
    variantDetails?.ProductVariant?.forEach(productVariant => {
        userMap[productVariant.id] = productVariant;
    });

    // Map productVariantId to productVariantInfo
    const mappedProjectVariant = projectResource?.ProjectResources.map(resource => {
        const productVariantInfo = userMap[resource.resource?.productVariantId];
        if (productVariantInfo) {
            return {
                ...resource,
                productVariant: productVariantInfo
            };
        } else {
            // Handle the case where productVariant info is not found for a productVariantId
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
        { label: t("IS_DELETED"), key: "isDeleted" },
        { label: t("START_DATE"), key: "formattedStartDate" },
        { label: t("END_DATE"), key: "formattedEndDate" },
        { label: t("PRODUCT_TYPE"), key: "resource.type" },
        { label: t("IS_BASE_UNIT_VARIANT"), key: "resource.isBaseUnitVariant" },
    ];

    const getDetailFromProductVariant = (row, key) => {
        const productVariantId = row.resource?.productVariantId;
        const productVariantDetail = row.productVariant || {};

        // Check if the key is nested within "resource."
        if (key.includes("resource.")) {
            const resourceValue = row.resource[key.split("resource.")[1]];
            return resourceValue !== undefined ? resourceValue.toString() : "NA";
        }

        // Check if the key is nested within "productVariant."
        if (key.includes("productVariant.")) {
            const detailValue = productVariantDetail[key.split("productVariant.")[1]];
            return detailValue !== undefined ? detailValue.toString() : "NA";
        }

        // If not nested, try to get the value from the row or productVariantDetails
        const rowValue = row[key];

        // Handle boolean values
        if (typeof rowValue === 'boolean') {
            return rowValue.toString();
        }

        return rowValue !== undefined ? rowValue : productVariantDetail[key] || "NA";
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
                            {columns.map((column, index) => (
                                <th key={index}>{column.label}</th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {mappedProjectVariant.map((row, rowIndex) => (
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