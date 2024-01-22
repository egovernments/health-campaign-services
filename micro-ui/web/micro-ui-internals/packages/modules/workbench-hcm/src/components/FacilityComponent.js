import React from "react";
import { useTranslation } from "react-i18next";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { data } from "../configs/ViewProjectConfig";

const FacilityComponent = (props) => {
    const { t } = useTranslation();

    const requestCriteria = {
        url: "/project/facility/v1/_search",
        changeQueryName: props.projectId,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },

        body: {
            ProjectFacility: {
                "projectId": [props.projectId]
            },
        },
        config:{
            enabled: props.projectId? true: false
        }
    };

    const { isLoading, data: projectFacility } = Digit.Hooks.useCustomAPIHook(requestCriteria);


    const facilityRequestCriteria = {
        url: "/facility/v1/_search",
        changeQueryName: projectFacility?.ProjectFacilities?.[0]?.facilityId,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },
        body: {
            Facility: {
                "id": [projectFacility?.ProjectFacilities?.[0]?.facilityId]
            },
        },
        config:{
            enabled:projectFacility?.ProjectFacilities?.[0]?.facilityId? true: false
        }
    };

    const { isLoadingFacilty, data: Facility } = Digit.Hooks.useCustomAPIHook(facilityRequestCriteria);

    const updatedProjectFacility = projectFacility?.ProjectFacilities.map(row => {
        const facilityData = Facility?.Facilities?.find(facility => facility.id === row.facilityId);
        return {
            ...row,
            storageCapacity: facilityData?.storageCapacity || "NA",
            name: facilityData?.name || "NA",
            usage: facilityData?.usage || "NA",
            address: facilityData?.address || "NA",
        };
    });

    const columns = [
        { label: t("FACILITY_ID"), key: "facilityId" },
        { label: t("PROJECT_FACILITY_ID"), key: "id" },
        { label: t("STORAGE_CAPACITY"), key: "storageCapacity" },
        { label: t("FACILITY_NAME"), key: "name" },
        { label: t("FACILITY_USAGE"), key: "usage" }
    ];


    if (isLoading) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("FACILITY")}</Header>
            {updatedProjectFacility?.length === 0 ? (
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
                        {updatedProjectFacility?.map((row, rowIndex) => (
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

export default FacilityComponent