import React from "react";
import { Link } from "react-router-dom/cjs/react-router-dom.min";
import { useTranslation } from "react-i18next";

export const statusBasedNavigation = ( value ) => {
    const { t } = useTranslation();

    let linkTo = `/${window?.contextPath}/employee/hcmworkbench/inbox`;

    if (value !== "Started") {
        linkTo = `/${window?.contextPath}/employee/hcmworkbench/view?ingestionId=${value}`;
    }

    return (
        <Link to={linkTo}>
            {value ? value : t("ES_COMMON_NA")}
        </Link>
    );
};