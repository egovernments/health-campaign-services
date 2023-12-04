import { Link, useHistory } from "react-router-dom";
import _ from "lodash";
import React from "react";
import { statusBasedNavigation } from "../utils/statusBasedNavigation";

//create functions here based on module name set in mdms(eg->SearchProjectConfig)
//how to call these -> Digit?.Customizations?.[masterName]?.[moduleName]
// these functions will act as middlewares
var Digit = window.Digit || {};

export const UICustomizations = {
  IngestionConfig: {
    preProcess: (data) => {
      let ingestionSearchCriteria = data.body.IngestionSearchCriteria;

      const { ingestionType, ingestionStatus } = ingestionSearchCriteria;
      if (ingestionType?.code) ingestionSearchCriteria.ingestionType = ingestionType.code;
      if (ingestionStatus?.code) ingestionSearchCriteria.ingestionStatus = ingestionStatus.code;

      return data;
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      switch (key) {
        case "Ingestion Id":
          return (
            <span className="link">
              {/* <Link to={`/${window.contextPath}/employee/hcmworkbench/ingestion-view?ingestionId=${value}`}>
                {String(value ? (column.translate ? t(column.prefix ? `${column.prefix}${value}` : value) : value) : t("ES_COMMON_NA"))}
              </Link> */}
              {statusBasedNavigation(value)}
            </span>
          );

        case "Created Time":
          return <span>{Digit.DateUtils.ConvertEpochToDate(value)}</span>;

        default:
          return t("ES_COMMON_NA");
      }
    }
  },
};
