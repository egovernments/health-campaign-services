import React from "react";
import { Card, Header, Button, Loader } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { useState, useEffect } from "react";
import { data } from "../configs/ViewProjectConfig";

const ProjectStaffComponent = (props) => {
    const { t } = useTranslation();
    const [userIds, setUserIds] = useState([]);
    const [userInfoMap, setUserInfoMap] = useState({});

    const requestCriteria = {
        url: "/project/staff/v1/_search",
        changeQueryName: props.projectId,
        params: {
            tenantId: "mz",
            offset: 0,
            limit: 10,
        },
        body: {
            ProjectStaff: {
                projectId: props.projectId
            },
            // apiOperation: "SEARCH"
        }
    };

    const { isLoading, data: projectStaff } = Digit.Hooks.useCustomAPIHook(requestCriteria);

    const isValidTimestamp = (timestamp) => timestamp !== 0 && !isNaN(timestamp);

    //to convert epoch to date and to convert isDeleted boolean to string
    projectStaff?.ProjectStaff.forEach(row => {
        row.formattedStartDate = isValidTimestamp(row.startDate)
            ? Digit.DateUtils.ConvertEpochToDate(row.startDate)
            : "NA";
        row.formattedEndDate = isValidTimestamp(row.endDate)
            ? Digit.DateUtils.ConvertEpochToDate(row.endDate)
            : "NA";
        row.isDeleted = row.isDeleted.toString();
    });


    useEffect(() => {
        // Extract user IDs and save them in the state
        if (projectStaff && projectStaff.ProjectStaff.length > 0) {
            const userIdArray = projectStaff.ProjectStaff.map(row => row.userId);
            setUserIds(userIdArray);
        }
    }, [projectStaff]);

    const requestCriteria1 = {
        url: "/user/_search",
        body: {
            "tenantId": "mz",
            "uuid": userIds
        }
    };

    const { isLoading1,data: userInfo } = Digit.Hooks.useCustomAPIHook(requestCriteria1);


    const userMap = {};
    userInfo?.user?.forEach(user => {
        userMap[user.uuid] = user;
    });

    // Map userId to userInfo
    const mappedProjectStaff = projectStaff?.ProjectStaff.map(staff => {
        const user = userMap[staff.userId];
        if (user) {
            return {
                ...staff,
                userInfo: user
            };
        } else {
            // Handle the case where user info is not found for a userId
            return {
                ...staff,
                userInfo: null
            };
        }
    });

    const columns = [
        { label: t("PROJECT_STAFF_ID"), key: "id" },
        { label: t("USERNAME"), key: "userInfo.userName" },
        { label: t("ROLES"), key: "userInfo.roles" },
        { label: t("IS_DELETED"), key: "isDeleted" },
        { label: t("START_DATE"), key: "formattedStartDate" },
        { label: t("END_DATE"), key: "formattedEndDate" },
        
    ];

    function getNestedPropertyValue(obj, path) {
        return path.split('.').reduce((acc, key) => (acc && acc[key]) ? acc[key] : "NA", obj);
    }

    if (isLoading) {
        return <Loader></Loader>;
    }

    if (isLoading1) {
        return <Loader></Loader>;
    }

    return (
        <div className="override-card">
            <Header className="works-header-view">{t("PROJECT_STAFF")}</Header>
            {mappedProjectStaff.length === 0 ? (
                <h1>{t("NO_PROJECT_STAFF")}</h1>
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
                        {mappedProjectStaff.map((row, rowIndex) => (
                            <tr key={rowIndex}>
                                {columns.map((column, columnIndex) => (
                                    <td key={columnIndex}>
                                        {column.render
                                            ? column.render(row)
                                            : column.key === "userInfo.roles"
                                                ? row?.userInfo?.roles.slice(0, 2).map(role => role.name).join(', ') // to show 2 roles
                                                : column.key.includes('.')
                                                    ? getNestedPropertyValue(row, column.key)
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

export default ProjectStaffComponent;