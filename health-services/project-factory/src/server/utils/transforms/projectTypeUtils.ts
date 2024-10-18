import { getFormattedStringForDebug, logger } from "../logger";

const MAX_AGE = 100;
const MAX_AGE_IN_MONTHS = MAX_AGE * 12;
/* 
TODO: Update configObject with appropriate values.
This object contains configuration settings for delivery strategies and wait times.
*/


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
Convert campaign details to project details enriched with campaign information.
*/
export const projectTypeConversion = (
  campaignObject: any = {}
) => {
  const deliveryRules = campaignObject.deliveryRules;
  // const resources = getUniqueArrayByProductVariantId(deliveryRules.flatMap((e: { products: any }) =>
  //   [...e.products].map((ele, ind) => ({
  //     isBaseUnitVariant: ind == 0,
  //     productVariantId: ele.value,
  //   }))
  // ));
// /* Temporay fix for project creation of LLIN since the structure of delivery rules is getting changed */
//   const resources = getUniqueArrayByProductVariantId(deliveryRules.flatMap((e:any) =>
//     [...e.deliveries].map((ele, ind) => ({
//       isBaseUnitVariant: ind == 0,
//       productVariantId: ele.deliveryRules?.[0]?.products?.[0]?.value,
//     }))
//   ));
//   const minAndMaxAge = getMinAndMaxAge(deliveryRules);
//   var newProjectType = {
//     ...projectType,
//     validMinAge: minAndMaxAge?.min,
//     validMaxAge: minAndMaxAge?.max,
//     name: campaignObject.campaignName,
//     resources,
//   };
//   /*Handled the logics for the SMC Project Type  */
//   if (projectType.code == "MR-DN") {
//     newProjectType["cycles"] = transformData(deliveryRules);
//   }
//   logger.debug(
//     "transformed projectType : " + getFormattedStringForDebug(newProjectType)
//   );
 /*  since the structure of delivery rules is getting changed so returning back the same delivery rules */

  return deliveryRules?.[0] || defaultProjectType?.["LLIN-mz"];
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
  logger.debug(
    "project type : " + getFormattedStringForDebug(projectTypeObject)
  );
  const defaultProject = projectTypeConversion( CampaignDetails);
  return [
    {
      tenantId,
      projectType,
      startDate,
      endDate,
      projectSubType: projectType,
      department: defaultProject?.group,
      description:`${defaultProject?.name}, disease ${defaultProject?.group} campaign created through Admin Console with Campaign Id as ${CampaignDetails?.campaignNumber}`,
      projectTypeId: defaultProject?.id,
      name: campaignName,
      additionalDetails: {
        projectType: defaultProject,
      },
    },
  ];
};


/* construct max and min age */
export const getMinAndMaxAge = (deliveries = []) => {
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



type MandatoryWaitDays = {
  default: string | null;
  other: string | null;
};

interface ConfigObject {
  mandatoryWaitSinceLastCycleInDays: MandatoryWaitDays;
  mandatoryWaitSinceLastDeliveryInDays: MandatoryWaitDays;
}

// Configuration for default values
const configObject: ConfigObject = {
  mandatoryWaitSinceLastCycleInDays: {
    default: null,
    other: "30"
  },
  mandatoryWaitSinceLastDeliveryInDays: {
    default: null,
    other: null
  }
};

// Define types for product variants and conditions
type ProductVariant = {
  value: string;
  name: string;
  count: number;
};

type ConditionOperator = 'LESS_THAN' | 'LESS_THAN_EQUAL_TO' | 'GREATER_THAN' | 'GREATER_THAN_EQUAL_TO' | 'EQUAL_TO';

type Condition = {
  attribute: string;
  operator: ConditionOperator;
  value: number | string;
};

type DeliveryItem = {
  cycleNumber: number;
  deliveryNumber: number;
  deliveryType: string;
  deliveryRuleNumber: number;
  products: ProductVariant[];
  conditions: Condition[];
  startDate:any,
  endDate:any
};

type FormattedCondition = Record<string, Record<ConditionOperator, number | string>>;

type DoseCriterion = {
  ProductVariants: {
    isBaseUnitVariant: boolean;
    productVariantId: string;
    quantity: number;
  }[];
  condition: string;
};

type Delivery = {
  id: string;
  deliveryStrategy: string;
  mandatoryWaitSinceLastDeliveryInDays: string | null;
  doseCriteria: DoseCriterion[];
};

type TransformedCycle = {
  id: string;
  mandatoryWaitSinceLastCycleInDays: string | null;
  deliveries: Delivery[];
  startDate:any,
  endDate:any,
};

// Helper functions

// Get unique product variants
const getUniqueArrayByProductVariantId = (
  array: { isBaseUnitVariant: boolean; productVariantId: string; quantity: number; }[]
): { isBaseUnitVariant: boolean; productVariantId: string; quantity: number; }[] => {
  return array.filter(
    (value, index, self) =>
      index === self.findIndex(t => t.productVariantId === value.productVariantId)
  );
};

// Construct conditions in a simplified form
const getConditionString = (condition: Record<ConditionOperator, number | string>, attribute: string): string => {
  let conditionStr = '';
  if (condition.LESS_THAN !== undefined) {
    conditionStr += `${attribute}<${condition.LESS_THAN}`;
  }
  if (condition.LESS_THAN_EQUAL_TO !== undefined) {
    conditionStr += `${attribute}<=${condition.LESS_THAN_EQUAL_TO}`;
  }
  if (condition.GREATER_THAN !== undefined) {
    conditionStr = `${condition.GREATER_THAN}<${attribute}and` + conditionStr;
  }
  if (condition.GREATER_THAN_EQUAL_TO !== undefined) {
    conditionStr = `${condition.GREATER_THAN_EQUAL_TO}<=${attribute}and` + conditionStr;
  }
  if (condition.EQUAL_TO !== undefined) {
    if (attribute === "gender") {
      conditionStr += `${attribute}==${condition.EQUAL_TO === "MALE" ? 0 : 1}`;
    } else {
      conditionStr += `${attribute}=${condition.EQUAL_TO}`;
    }
  }
  return conditionStr;
};

// Function to generate the condition string from an array of conditions
const getRequiredCondition = (conditions: Condition[]): string => {
  const formattedConditions: FormattedCondition = conditions.reduce((acc:any, { attribute, operator, value }) => {
    attribute = attribute.toLowerCase();
    if (!acc[attribute]) {
      acc[attribute] = {};
    }
    acc[attribute][operator] = value;
    return acc;
  }, {} as FormattedCondition);

  const conditionStrings = Object.keys(formattedConditions).map(attribute =>
    getConditionString(formattedConditions[attribute], attribute)
  );

  return conditionStrings.join('and');
};

// Transformation function
export const transformData = (input: DeliveryItem[]): TransformedCycle[] => {
  const groupedByCycle = input.reduce<Record<number, TransformedCycle>>((acc, item) => {
    const { cycleNumber, deliveryNumber, deliveryType, products, conditions,startDate,endDate } = item;

    if (!acc[cycleNumber]) {
      acc[cycleNumber] = {
        id: cycleNumber.toString(),
        mandatoryWaitSinceLastCycleInDays: configObject.mandatoryWaitSinceLastCycleInDays.default,
        deliveries: [],
        startDate,
        endDate
      };
    }

    const delivery: Delivery = {
      id: deliveryNumber.toString(),
      deliveryStrategy: deliveryType,
      mandatoryWaitSinceLastDeliveryInDays: configObject.mandatoryWaitSinceLastDeliveryInDays.default,
      doseCriteria: []
    };

    const doseCriteria: DoseCriterion = {
      ProductVariants: getUniqueArrayByProductVariantId(
        products.map((product, index) => ({
          isBaseUnitVariant: index === 0,
          productVariantId: product.value,
          quantity: product.count
        }))
      ),
      condition: getRequiredCondition(conditions)
    };

    const existingDelivery = acc[cycleNumber].deliveries.find(d => d.id === deliveryNumber.toString());
    if (existingDelivery) {
      existingDelivery.doseCriteria.push(doseCriteria);
    } else {
      delivery.doseCriteria.push(doseCriteria);
      acc[cycleNumber].deliveries.push(delivery);
    }

    return acc;
  }, {});

  return Object.values(groupedByCycle);
};
