export const projectStaffData = (searchResult) => {
  const getRoles = searchResult?.user?.roles ? searchResult.user.roles.map((role) => `WBH_${role.name}`).join(",") : "NA";

  return {
    cards: [
      {
        sections: [
          {
            type: "DATA",
            values: [
              { key: "WBH_USERNAME", value: searchResult?.code || "NA" },
              { key: "WBH_BOUNDARY", value: searchResult?.jurisdictions?.[0]?.boundary || "NA" },
              { key: "WBH_BOUNDARY_TYPE", value: searchResult?.jurisdictions?.[0]?.boundaryType || "NA" },
              { key: "WBH_ROLES", value: getRoles },
            ],
          },
        ],
      },
    ],
  };
};
