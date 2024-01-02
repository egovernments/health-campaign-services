import React from "react";
import { useTranslation } from "react-i18next";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { data } from "../configs/ViewProjectConfig";

const FacilityComponent = (props) => {
    const { t } = useTranslation();
    
    const requestCriteria = {
        url: "/facility/v1/_search",
        changeQueryName:props.projectId,
        params: {
            tenantId : "mz",
            offset: 0,
            limit: 10,
        },
        
        body: {
            Facility: {
                
            },
        }
    };

    const {isLoading, data: facility } = Digit.Hooks.useCustomAPIHook(requestCriteria);

    const columns = [
        { label: t("FACILITY_ID"), key: "id" },
        { label: t("CLIENT_REFERENCE_ID"), key: "clientReferenceId" },
        { label: t("IS_DELETED"), key: "isDeleted" },
        { label: t("NAME"), key: "name" },
        { label: t("STORAGE_CAPACITY"), key: "storageCapacity" },
        { label: t("USAGE"), key: "usage"}
    ];


    if (isLoading) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("FACILITY")}</Header>
            {facility?.Facilities.length === 0 ? (
                <h1>{t("NO_FACILITY")}</h1>
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
                        {facility?.Facilities.map((row, rowIndex) => (
                            <tr key={rowIndex}>
                                {columns.map((column, columnIndex) => (
                                   <td key={columnIndex}>
                                   {column.key === "isDeleted" ? String(row[column.key]) :
                                       (column.key === "storageCapacity" ? (row[column.key] === 0 ? 0 : row[column.key]) : row[column.key] || "NA")}
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

export default FacilityComponent