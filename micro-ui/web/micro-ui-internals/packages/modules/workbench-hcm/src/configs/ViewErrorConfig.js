import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";


export const data = (data) => {
  const { t } = useTranslation();

  const generateCards = () => {
    const cards = [];
   const eventHistory=data?.EventHistory;

    // Loop through each object in EventHistory
   if(eventHistory!=undefined){
    eventHistory.forEach((event) => {
      const dataSection = {
        type: "DATA",
        values: [
          { key: t("WORKBENCH_JOBID"), value:event?.jobId },
          { key: t("WORKBENCH_EVENTID"), value:event?.eventId },
          { key: t("WORKBENCH_URL"), value:event?.url },
          { key: t("WORKBENCH_STATUS"), value:event?.status },
          { key: t("WORKBENCH_MESSAGE"), value:event?.message },
          { key: t("WORKBENCH_INGESTION_NUMBER"), value:event?.ingestionNumber },
          { key: t("WOKRBENCH_FILESTOREID"), value:event?.fileStoreId },
        ],
      };

      cards.push({ sections: [dataSection] });
    });
  }
    return cards;
  }

  return {
    cards: generateCards() ,
    apiResponse: {},
    additionalDetails: {},
    horizontalNav: {
      showNav: false,
      configNavItems: [],
      activeByDefault: "",
    },
  };
};
