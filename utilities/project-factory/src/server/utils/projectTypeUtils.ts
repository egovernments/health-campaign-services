
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
    mandatoryWaitSinceLastDeliveryInDays:{
        default: null,
        other: null,
    }
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
const deliveriesConv = (deliveryObj:any = {}) => {
    return Object.keys(deliveryObj).map((key, ind) => {
        return {
            id: key,
            deliveryStrategy:
                configObject.deliveryStrategy?.[ind == 0 ? "default" : "other"],

            mandatoryWaitSinceLastDeliveryInDays:
                configObject.mandatoryWaitSinceLastDeliveryInDays?.["default"],
            doseCriteria: deliveryObj?.[key]?.map((e: any) => {
                return {
                    ProductVariants: deliveryObj?.[key].flatMap(
                        (elem: { products: any }) =>
                            [...elem.products].map((ele, index) => ({
                                isBaseUnitVariant: index == 0,
                                productVariantId: ele.value,
                            }))
                    ),
                    // cylce conditions hardcoded TODO update logic
                    conditions: "3<=age<11",
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
    var newProjectType = {
        ...projectType,
        name: campaignObject.campaignName,
        resources,
    };
    /*Handled the logics for the SMC Project Type  */
    if (projectType.code == "MR-DN") {
        const cyclesObj = deliveryRulesToCyles(deliveryRules);
        const cycles = transformDeliveryConditions(cyclesObj);
        newProjectType["cycles"] = cycles;
    }
    return newProjectType;
};
/* 
Enrich project details from campaign details.
*/
export const enrichProjectDetailsFromCampaignDetails = (
    CampaignDetails: any = {}
) => {
    var { tenantId, projectType, startDate, endDate, campaignName } =
        CampaignDetails;
    const defaultProject =
        defaultProjectType?.[projectType] || defaultProjectType?.["MR-DN"];
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
