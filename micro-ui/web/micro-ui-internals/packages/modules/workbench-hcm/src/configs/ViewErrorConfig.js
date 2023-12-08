import React, { useState, useEffect } from "react";

export const data = (data) => {
  const generateCards = () => {
    const cards = [];
   const eventHistory=data?.EventHistory;

    // Loop through each object in EventHistory
   if(eventHistory!=undefined){
    eventHistory.forEach((event) => {
      const dataSection = {
        type: "DATA",
        values: [
          { key: "JobId", value:event?.jobId },
          { key: "eventId", value:event?.eventId },
          { key: "Url", value:event?.url },
          { key: "Status", value:event?.status },
          { key: "Message", value:event?.message },
          { key: "IngestionNumber", value:event?.ingestionNumber },
          { key: "FileStoreId", value:event?.fileStoreId },
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
