import { getFormattedStringForDebug, logger } from "../logger";

const MAX_AGE=100;
const MAX_AGE_IN_MONTHS=MAX_AGE*12;
/* 
TODO: Update configObject with appropriate values.
This object contains configuration settings for delivery strategies and wait times.
*/
const configObject: any = {
  deliveryStrategy: {
    default: "DIRECT",
    other: "INDIRECT",
  },
  mandatoryWaitSinceLastCycleInDays: {
    default: null,
    other: "30",
  },
  mandatoryWaitSinceLastDeliveryInDays: {
    default: null,
    other: null,
  },
};

/* TODO: Update the logic to fetch the projecttype master */
const defaultProjectType: any = {
  /* 
    Define default project types with their respective properties.
    Each project type represents a specific type of campaign.
    */
  "MR-DN": {
    id: "b1107f0c-7a91-4c76-afc2-a279d8a7b76a",
    name: "configuration for Multi Round Campaigns",
    code: "MR-DN",
    group: "MALARIA",
    beneficiaryType: "INDIVIDUAL",
    resources: [],
    observationStrategy: "DOT1",
    validMinAge: 3,
    validMaxAge: 60,
    cycles: [],
  },
  "LLIN-mz": {
    id: "192a20d1-0edd-4108-925a-f37bf544d6c4",
    name: "Project type configuration for IRS - Nampula Campaigns",
    code: "LLIN-mz",
    group: "IRS - Nampula",
    beneficiaryType: "HOUSEHOLD",
    eligibilityCriteria: ["All households are eligible."],
    dashboardUrls: {
      NATIONAL_SUPERVISOR:
        "/digit-ui/employee/dss/landing/national-health-dashboard",
      PROVINCIAL_SUPERVISOR:
        "/digit-ui/employee/dss/dashboard/provincial-health-dashboard",
      DISTRICT_SUPERVISOR:
        "/digit-ui/employee/dss/dashboard/district-health-dashboard",
    },
    taskProcedure: [
      "1 DDT is to be distributed per house.",
      "1 Malathion is to be distributed per house.",
      "1 Pyrethroid is to be distributed per house.",
    ],
    resources: [],
  },
};

/* 
Map delivery rules to cycles based on delivery and cycle numbers.
*/
const deliveryRulesToCyles = (delivery = []) => {
  return delivery.reduce((acc: any, curr: any) => {
    const deliveryNumber = curr.deliveryNumber;
    if (!acc?.[curr?.cycleNumber]) {
      const deliveryObj = { [deliveryNumber]: [{ ...curr }] };
      acc[curr.cycleNumber] = {
        startDate: curr.startDate,
        endDate: curr.endDate,
        delivery: deliveryObj,
      };
    } else {
      const deliveryObj = { ...acc?.[curr?.cycleNumber]?.delivery };

      if (acc?.[curr?.cycleNumber]?.delivery?.[deliveryNumber]) {
        deliveryObj[deliveryNumber] = [
          ...deliveryObj?.[deliveryNumber],
          { ...curr },
        ];
      } else {
        deliveryObj[deliveryNumber] = [{ ...curr }];
      }
      acc[curr.cycleNumber].delivery = { ...deliveryObj };
    }
    return { ...acc };
  }, {});
};
/* 
Convert delivery rules to a format suitable for processing.
*/
const deliveriesConv = (deliveryObj: any = {}) => {
  return Object.keys(deliveryObj).map((key, ind) => {
    return {
      id: key,
      deliveryStrategy:
        configObject.deliveryStrategy?.[ind == 0 ? "default" : "other"],

      mandatoryWaitSinceLastDeliveryInDays:
        configObject.mandatoryWaitSinceLastDeliveryInDays?.["default"],
      doseCriteria: deliveryObj?.[key]?.map((e: any) => {
        return {
          ProductVariants:getUniqueArrayByProductVariantId( deliveryObj?.[key].flatMap(
            (elem: { products: any }) =>
              [...elem.products].map((ele, index) => ({
                isBaseUnitVariant: index == 0,
                productVariantId: ele?.value,
                quantity: ele?.count,
              }))
          )),
          // cylce conditions hardcoded TODO update logic
          condition: getRequiredCondition(e?.conditions),
        };
      }),
    };
  });
};
/* 
Transform cycle conditions and delivery rules into a standardized format.
*/
const transformDeliveryConditions = (cyclesObj: any = {}) => {
  return Object.keys(cyclesObj).map((cycleKey, ind) => {
    var tempObj = cyclesObj[cycleKey];

    return {
      endDate: tempObj?.endDate,
      id: cycleKey,
      mandatoryWaitSinceLastCycleInDays:
        configObject.mandatoryWaitSinceLastCycleInDays?.[
          ind == 0 ? "default" : "other"
        ],
      startDate: tempObj?.startDate,
      deliveries: deliveriesConv(tempObj?.delivery),
    };
  });
};
/* 
Convert campaign details to project details enriched with campaign information.
*/
export const projectTypeConversion = (
  projectType: any = {},
  campaignObject: any = {}
) => {
  const deliveryRules = campaignObject.deliveryRules;
  const resources = deliveryRules.flatMap((e: { products: any }) =>
    [...e.products].map((ele, ind) => ({
      isBaseUnitVariant: ind == 0,
      productVariantId: ele.value,
    }))
  );
  const minAndMaxAge = getMinAndMaxAge(deliveryRules);
  var newProjectType = {
    ...projectType,
    validMinAge: minAndMaxAge?.min,
    validMaxAge: minAndMaxAge?.max,
    name: campaignObject.campaignName,
    resources,
  };
  /*Handled the logics for the SMC Project Type  */
  if (projectType.code == "MR-DN") {
    const cyclesObj = deliveryRulesToCyles(deliveryRules);
    const cycles = transformDeliveryConditions(cyclesObj);
    newProjectType["cycles"] = cycles;
  }
  logger.debug("transformed projectType : " + getFormattedStringForDebug(newProjectType));
  return newProjectType;
};
/* 
Enrich project details from campaign details.
*/
export const enrichProjectDetailsFromCampaignDetails = (
  CampaignDetails: any = {},
  projectTypeObject: any = {}
) => {
  var { tenantId, projectType, startDate, endDate, campaignName } =
    CampaignDetails;
  logger.info("campaign transformation for project type : " + projectType);
  logger.debug("project type : " + getFormattedStringForDebug(projectTypeObject));
  const defaultProject =projectTypeObject || defaultProjectType?.[projectType] || defaultProjectType?.["MR-DN"];
  return [
    {
      tenantId,
      projectType,
      startDate,
      endDate,
      projectSubType: projectType,
      department: defaultProject?.group,
      description: defaultProject?.name,
      projectTypeId: defaultProject?.id,
      name: campaignName,
      additionalDetails: {
        projectType: projectTypeConversion(defaultProject, CampaignDetails),
      },
    },
  ];
};

// Function to get the key based on condition and attribute
const getConditionsKey = (condition: any, key: string) => {
  // Get all keys of the condition object
  const keys = Object.keys(condition);

  // Check if the key is present in the condition object
  if (keys.filter((e) => e == key).length > 0) {
    return `${
      key.includes("LESS_THAN") ? "<" + condition[key] : condition[key] + "<"
    }`;
  } else if (keys.filter((e) => e.includes(key)).length > 0) {
    return `${
      key.includes("LESS_THAN") ? "<=" + condition[key] : condition[key] + "<="
    }`;
  } else if (keys.includes("EQUAL_TO")) {
    return `${condition[key]}=`;
  } else {
    return `${key.includes("LESS_THAN") ? `>${MAX_AGE_IN_MONTHS}` : "0<"}`;
  }
};

// Function to get the condition based on attribute
const getCondition = (condition: any = {}, attribute: string) => {
  if (attribute == "gender") {
    // since hcm app can understand 0 or 1 for gender
    return `${attribute}==${condition?.["EQUAL_TO"]=="MALE"?0:1}`;
  }
  // Call getConditionsKey function to get the condition for LESS_THAN and GREATER_THAN
  return `${getConditionsKey(
    condition,
    "GREATER_THAN"
  )}${attribute}and${attribute}${getConditionsKey(condition, "LESS_THAN")}`;
};

// Function to get the required condition
const getRequiredCondition = (conditions: any = []) => {
  // Format the conditions into an object with attribute keys
  const formattedCondition = conditions.reduce((acc: any, curr: any) => {
    if (acc[curr.attribute.toLowerCase()]) {
      acc[curr.attribute.toLowerCase()] = {
        [curr.operator]: curr.value,
        ...acc[curr.attribute.toLowerCase()],
      };
    } else {
      acc[curr.attribute?.toLowerCase()] = {
        [curr.operator]: curr.value,
      };
    }
    return { ...acc };
  }, {});

  // Sort keys of formattedCondition and get the first one
  const sortedKeys = Object.keys(formattedCondition).slice().sort();
  // update the below logic to support multiple conditions currently hardcoded for age or 1 st condition
  return getCondition(formattedCondition[sortedKeys[0]], sortedKeys[0]);
};

const getUniqueArrayByProductVariantId=(array:any)=> {
  return array.filter((value:any, index:any, self:any) =>
    index === self.findIndex((t:any) => (
      t.productVariantId === value.productVariantId
    ))
  );
}
/* construct max and min age */
const getMinAndMaxAge = (deliveries = []) => {
  // Flatten the conditions arrays from all delivery objects and filter to keep only 'Age' attributes
  const ageConditions = deliveries
    .flatMap((e: any) => e?.conditions)
    .filter((obj) => obj?.attribute == "Age");

  // If no age conditions are found, return the default range
  if (ageConditions.length === 0) {
    return { min: 0, max: MAX_AGE_IN_MONTHS };
  }

  // Initialize min and max values
  let min = Infinity;
  let max = -Infinity;

  // Iterate through the ageConditions to find the actual min and max values
  for (const condition of ageConditions) {
    const value = condition?.value;
    if (value !== undefined) {
      if (value < min) min = value;
      if (value > max) max = value;
    }
  }

  // Return the min and max values, with default fallbacks if no valid ages were found
  return {
    min: min !== Infinity ? min : 0,
    max: max !== -Infinity ? max : MAX_AGE_IN_MONTHS,
  };
};
