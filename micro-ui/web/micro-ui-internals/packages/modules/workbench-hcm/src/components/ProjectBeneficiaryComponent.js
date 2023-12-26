import React from "react";
import { useTranslation } from "react-i18next";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { data } from "../configs/ViewProjectConfig";

const ProjectBeneficiaryComponent = (props) => {
    const { t } = useTranslation();
    
    const requestCriteria = {
        url: "/project/resource/v1/_search",
        changeQueryName:props.projectId,
        params: {
            tenantId : "mz",
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

    const {isLoading, data: projectResource } = Digit.Hooks.useCustomAPIHook(requestCriteria);


    const columns = [
        { label: t("PROJECT_RESOURCE_ID"), key: "id" },
        { label: t("PROJECT_ID"), key: "projectId" },
        { label: t("IS_DELETED"), key: "isDeleted" },
        { label: t("START_DATE"), key: "startDate" },
        { label: t("END_DATE"), key: "endDate" },
        { label: t("PRODUCT_VARIANT_ID"), key: "resource.productVariantId"},
        { label: t("PRODUCT_TYPE"), key: "resource.type" },
        { label: t("IS_BASE_UNIT_VARIANT"), key: "resource.isBaseUnitVariant" },
    ];


    if (isLoading) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("PROJECT_RESOURCE")}</Header>
            {projectResource?.ProjectResources.length ===0 ? (
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
                    {projectResource?.ProjectResources.map((row, rowIndex) => (
                        <tr key={rowIndex}>
                            {columns.map((column, columnIndex) => (
                                <td key={columnIndex}>
                                    {column.key.includes("resource.")
                                        ? row.resource[column.key.split("resource.")[1]]
                                        : row[column.key] || "NA"}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
            
            )}

        </div>
    )
}

export default ProjectBeneficiaryComponent