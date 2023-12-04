import React, { useState, useEffect } from "react";

export const data = (data) => {
  const generateCards = () => {
    const cards = [];
   const eventHistory=data?.eventHistory;

    // Loop through each object in EventHistory
    eventHistory.forEach((event) => {
      const dataSection = {
        type: "DATA",
        values: [
          { key: "JobId", value:event?.jobId },
          { key: "eventId", value:event?.eventId },
          { key: "Url", value:event?.url },
          { key: "Status", value:event?.status },
          { key: "Message", value:event?.message },

         
        ],
      };

      cards.push({ sections: [dataSection] });
    });
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
