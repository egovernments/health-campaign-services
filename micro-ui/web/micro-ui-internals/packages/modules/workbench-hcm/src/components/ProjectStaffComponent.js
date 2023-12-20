import React from "react";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { data } from "../configs/ViewProjectConfig";

const ProjectStaffComponent = (props) => {
    const { t } = useTranslation();

    const requestCriteria = {
        url: "/project/staff/v1/_search",
        changeQueryName: props.projectId,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },
        config:{
            enable: data?.horizontalNav?.configNavItems[0].code === "Project Resource" ? true : false
        },
        body: {
            ProjectStaff: {
                projectId: props.projectId
            },
            // apiOperation: "SEARCH"
        }
    };

    const { isLoading, data: projectStaff } = Digit.Hooks.useCustomAPIHook(requestCriteria);
    console.log("staff",projectStaff);

    const columns = [
        { label: t("PROJECT_STAFF_ID"), key: "id" },
        { label: t("PROJECT_ID"), key: "projectId" },
        { label: t("IS_DELETED"), key: "isDeleted" },
        { label: t("START_DATE"), key: "startDate" },
        { label: t("END_DATE"), key: "endDate" }
    ];


    if (isLoading) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("PROJECT_STAFF")}</Header>

            
            <table className="table reports-table sub-work-table">
                <thead>
                    <tr>
                        {columns.map((column, index) => (
                            <th key={index}>{column.label}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                {projectStaff?.ProjectStaff.map((row, rowIndex) => (
                    <tr key={rowIndex}>
                        {columns.map((column, columnIndex) => (
                            <td key={columnIndex}>
                                {row[column.key]}
                            </td>
                        ))}
                    </tr>
                ))}
            </tbody>
            </table>

        </div>


    )
}

export default ProjectStaffComponent;