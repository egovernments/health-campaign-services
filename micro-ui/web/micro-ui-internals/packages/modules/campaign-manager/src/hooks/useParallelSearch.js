const useParallelSearch = async ({ parentArray, tenantId, boundaryType, hierarchy, targetedData }) => {
  let cacheData = window.Digit.SessionStorage.get("HCM_CAMPAIGN_BOUNDARY_DATA") ? window.Digit.SessionStorage.get("HCM_CAMPAIGN_BOUNDARY_DATA") : {};
  let missingParent = null;
  let removeData = null;

  function findMissingCodes(d, c) {
    let missingCodes = [];

    for (let i = 0; i < d?.length; i++) {
      let found = false;
      for (let j = 0; j < c?.length; j++) {
        if (d?.[i] === c?.[j]?.parentCode) {
          found = true;
          break;
        }
      }
      if (!found) {
        missingCodes?.push(d[i]);
      }
    }

    return missingCodes;
  }

  function removeMissingParentCodes(cacheData, parentArray) {
    const validParentCodes = new Set(parentArray);

    return cacheData?.filter((obj) => validParentCodes?.has(obj?.parentCode));
  }

  function checkremoveMissingParentCodes(cacheData, parentArray) {
    const validParentCodes = new Set(parentArray);
    return cacheData?.filter((obj) => !validParentCodes?.has(obj?.parentCode));
  }

  function checkParentCodePresence(data, array) {
    return array.every((item) => data.some((entry) => entry.parentCode === item));
  }
  if (parentArray?.length > cacheData?.[targetedData]?.length) {
    missingParent = findMissingCodes(parentArray, cacheData?.[targetedData]);
    const requests = missingParent.map((parentCode) => {
      return Digit.CustomService.getResponse({
        url: "/boundary-service/boundary-relationships/_search",
        params: {
          tenantId: tenantId,
          hierarchyType: hierarchy,
          boundaryType: boundaryType,
          parent: parentCode,
        },
        body: {},
      }).then((boundaryTypeData) => ({ parentCode, boundaryTypeData }));
    });
    const newData = await Promise.all(requests);
    const setcacheData = { ...cacheData, [targetedData]: cacheData?.[targetedData] ? [...cacheData?.[targetedData], ...newData] : [...newData] };
    window.Digit.SessionStorage.set("HCM_CAMPAIGN_BOUNDARY_DATA", setcacheData);
    return setcacheData?.[targetedData];
  } else if (cacheData?.[targetedData]?.length > parentArray?.length && checkParentCodePresence(cacheData?.[targetedData], parentArray)) {
    removeData = removeMissingParentCodes(cacheData?.[targetedData], parentArray);
    const setcacheData = { ...cacheData, [targetedData]: [...removeData] };
    // window.Digit.SessionStorage.set("HCM_CAMPAIGN_BOUNDARY_DATA", setcacheData);
    return setcacheData?.[targetedData];
  } else if (
    parentArray?.length === cacheData?.[targetedData]?.length &&
    !checkremoveMissingParentCodes(cacheData?.[targetedData], parentArray)?.length &&
    findMissingCodes(parentArray, cacheData?.[targetedData])?.length === 0
  ) {
    return cacheData?.[targetedData];
  } else {
    const requests = parentArray.map((parentCode) => {
      return Digit.CustomService.getResponse({
        url: "/boundary-service/boundary-relationships/_search",
        params: {
          tenantId: tenantId,
          hierarchyType: hierarchy,
          boundaryType: boundaryType,
          parent: parentCode,
        },
        body: {},
      }).then((boundaryTypeData) => ({ parentCode, boundaryTypeData }));
    });
    const newData = await Promise.all(requests);
    const setcacheData = { ...cacheData, [targetedData]: cacheData?.targetedData ? [...cacheData?.targetedData, ...newData] : [...newData] };
    window.Digit.SessionStorage.set("HCM_CAMPAIGN_BOUNDARY_DATA", setcacheData);
    return setcacheData?.[targetedData];
  }
};

export default useParallelSearch;
