import { Link, useHistory } from "react-router-dom";
import _ from "lodash";
import React from "react";
import { statusBasedNavigation } from "../utils/statusBasedNavigation";

//create functions here based on module name set in mdms(eg->SearchProjectConfig)
//how to call these -> Digit?.Customizations?.[masterName]?.[moduleName]
// these functions will act as middlewares
// var Digit = window.Digit || {};

export const UICustomizations = {
  IngestionConfig: {
    preProcess: (data) => {
      let ingestionSearchCriteria = data.body.IngestionSearchCriteria;
      data.params = {
        recordCount: data.state.tableForm.limit,
      };

      const { ingestionType, ingestionStatus } = ingestionSearchCriteria;
      if (ingestionType?.code) ingestionSearchCriteria.ingestionType = ingestionType.code;
      if (ingestionStatus?.code) ingestionSearchCriteria.ingestionStatus = ingestionStatus.code;

      return data;
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      switch (key) {
        case "WORKBENCH_INGESTION_ID":
          return (
            <span className="link">
              {statusBasedNavigation(row?.executionStatus, row?.jobID, value)}
            </span>
          );

        case "WORKBENCH_CREATED_TIME":
          return <span>{Digit.DateUtils.ConvertEpochToDate(value)}</span>;

        default:
          return t("ES_COMMON_NA");
      }
    }
  },
};
