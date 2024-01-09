import React from "react";
import { useTranslation } from "react-i18next";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { data } from "../configs/ViewProjectConfig";

const ProductDeliveryComponent = (props) => {
    const { t } = useTranslation();
    
    const requestCriteria = {
        url: "/product/v1/_search",
        changeQueryName:props.projectId,
        params: {
            tenantId : "mz",
            offset: 0,
            limit: 10,
        },
        
        body: {
            Product: {
                
            },
            // apiOperation: "SEARCH"
        }
    };

    const {isLoading, data: product } = Digit.Hooks.useCustomAPIHook(requestCriteria);


    const columns = [
        { label: t("PRODUCT_ID"), key: "id" },
        { label: t("MANUFACTURER"), key: "manufacturer" },
        { label: t("NAME"), key: "name" },
        { label: t("TYPE"), key: "type" }
    ];


    if (isLoading) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("PRODUCT")}</Header>
            {product?.Product.length === 0 ? (
                <h1>{t("NO_PRODUCT")}</h1>
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
                        {product?.Product.map((row, rowIndex) => (
                            <tr key={rowIndex}>
                                {columns.map((column, columnIndex) => (
                                    <td key={columnIndex}>
                                        {row[column.key] || "NA"}
                                    </td>
                                ))}
                            </tr>
                        ))}
                    </tbody>
                </table>
            )
            }

        </div>
        
    )
}

export default ProductDeliveryComponent;