import React from "react";
import { useTranslation } from "react-i18next";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { Link } from "react-router-dom";

const ProjectChildrenComponent = (props) => {
    const { t } = useTranslation();

    const requestCriteria = {
        url: "/project/v1/_search",
        changeQueryName: props.projectId,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 100,
            includeDescendants: true
        },
        body: {
            Projects: [
                {
                    tenantId: "mz",
                    id: props.projectId
                }
            ],
            "apiOperation": "SEARCH"
        }
    };

    const { isLoading, data: projectChildren } = Digit.Hooks.useCustomAPIHook(requestCriteria);

    const projectsArray = projectChildren?.Project || [];

    //converts the descendant array into the object
    const descendantsObject = {};

    projectsArray.forEach(project => {
        const descendantsArray = project.descendants || [];

        descendantsArray.forEach(descendant => {
            descendantsObject[descendant.id] = descendant;
        });
    });

    //converts the epoch to date
    Object.values(descendantsObject).forEach(descendant => {
        descendant.formattedStartDate = Digit.DateUtils.ConvertEpochToDate(descendant.startDate);
        descendant.formattedEndDate = Digit.DateUtils.ConvertEpochToDate(descendant.endDate);
    });


    const columns = [
        { label: t("DESCENDANTS_PROJECT_NUMBER"), key: "descendants.projectNumber" },
        { label: t("DESCENDANTS_PROJECT_NAME"), key: "descendants.name" },
        { label: t("DESCENDANTS_PROJECT_TYPE"), key: "descendants.projectType" },
        { label: t("DESCENDANTS_START_DATE"), key: "descendants.formattedStartDate" },
        { label: t("DESCENDANTS_END_DATE"), key: "descendants.formattedEndDate" }
    ];

    if (isLoading) {
        return <Loader></Loader>;
    }
    if (!projectChildren?.Project[0]?.descendants) {
        return (
            <div>
            <Header className="works-header-view">{t("PROJECT_CHILDREN")}</Header>
            <h1>{t("NO_PROJECT_CHILDREN")}</h1>
            </div>
        )
    } else {
        return (
            <div className="override-card">
                <Header className="works-header-view">{t("PROJECT_CHILDREN")}</Header>
                <table className="table reports-table sub-work-table">
                    <thead>
                        <tr>
                            {columns.map((column, index) => (
                                <th key={index}>{column.label}</th>
                            ))}
                        </tr>
                    </thead>

                    <tbody>
                        {projectsArray.map((project, rowIndex) => (
                            <tr key={rowIndex}>
                                {columns.map((column, columnIndex) => (
                                    <td key={columnIndex}>
                                        {column.key.includes("descendants.")
                                            ? project.descendants?.map((descendant, descIndex) => (
                                                <div key={descIndex}>
                                                    {column.key.split("descendants.")[1] === "projectNumber" && descendant[column.key.split("descendants.")[1]] ? (
                                                        <Link
                                                            to={{
                                                                pathname: window.location.pathname,
                                                                search: `?tenantId=${descendant.tenantId}&projectNumber=${descendant.projectNumber}`,
                                                            }}
                                                            style={{ color: "#f37f12" }}
                                                        >
                                                            {descendant[column.key.split("descendants.")[1]]}
                                                        </Link>
                                                    ) : (
                                                        descendant[column.key.split("descendants.")[1]] || "NA"
                                                    )}
                                                </div>
                                            ))
                                            : project[column.key] || "NA"}
                                    </td>
                                ))}
                            </tr>
                        ))}
                    </tbody>

                </table>

            </div>
        );
    }
}

export default ProjectChildrenComponent;