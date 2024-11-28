import config from '../config'
import { getConfigurableColumnHeadersBasedOnCampaignType, getLocalizedName } from './campaignUtils';
import _ from 'lodash';
import { replicateRequest } from './genericUtils';
import { callGenerate } from './generateUtils';
import { isMicroplanRequest } from './microplanUtils';


async function generateDynamicTargetHeaders(request: any, campaignObject: any, localizationMap?: any) {
    const isSourceMicroplan = await isMicroplanRequest(request);
    let headerColumnsAfterHierarchy: any;
    if (isDynamicTargetTemplateForProjectType(campaignObject?.projectType) && campaignObject.deliveryRules && campaignObject.deliveryRules.length > 0 && !isSourceMicroplan) {
        const modifiedUniqueDeliveryConditions = modifyDeliveryConditions(campaignObject.deliveryRules);
        headerColumnsAfterHierarchy = generateTargetColumnsBasedOnDeliveryConditions(modifiedUniqueDeliveryConditions, localizationMap);

    }
    else {
        headerColumnsAfterHierarchy = await getConfigurableColumnHeadersBasedOnCampaignType(request);
        headerColumnsAfterHierarchy.shift();
    }
    return headerColumnsAfterHierarchy;
}


function modifyDeliveryConditions(dataa: any[]): any {
    let resultSet = new Set<string>();
    dataa.forEach((delivery) => {
        const conditions = delivery.conditions;
        let newArray: any[] = [];

        conditions.forEach((item: any) => {
            const existingIndex = newArray.findIndex(
                (element) => element.attribute.code === item.attribute
            );

            if (existingIndex !== -1) {
                const existingItem = newArray[existingIndex];
                // Combine conditions if necessary
                if (existingItem.operator.code !== item.operator.code) {
                    newArray[existingIndex] = {
                        attribute: existingItem.attribute,
                        operator: { code: "IN_BETWEEN" },
                        toValue:
                            existingItem.value && item.value ? Math.max(existingItem.value, item.value) : null,
                        fromValue:
                            existingItem.value && item.value ? Math.min(existingItem.value, item.value) : null
                    };
                }
            } else {
                // If attribute does not exist in newArray, add the item
                newArray.push({
                    attribute: { code: item.attribute },
                    operator: { code: item.operator },
                    value: item.value
                });
            }
        });
        newArray.map((element: any) => {
            const stringifiedElement = JSON.stringify(element); // Convert object to string
            resultSet.add(stringifiedElement);
        })
    });
    return resultSet;
}


function generateTargetColumnsBasedOnDeliveryConditions(uniqueDeliveryConditions: any, localizationMap?: any) {
    const targetColumnsBasedOnDeliveryConditions: string[] = [];
    uniqueDeliveryConditions.forEach((str: any, index: number) => {
        const uniqueDeliveryConditionsObject = JSON.parse(str); // Parse JSON string into object
        const targetColumnString = createTargetString(uniqueDeliveryConditionsObject, localizationMap);
        targetColumnsBasedOnDeliveryConditions.push(targetColumnString);
    });
    if (targetColumnsBasedOnDeliveryConditions.length > 18) {
        targetColumnsBasedOnDeliveryConditions.splice(18);
        targetColumnsBasedOnDeliveryConditions.push(getLocalizedName("OTHER_TARGETS", localizationMap));
    }
    return targetColumnsBasedOnDeliveryConditions;
}

function createTargetString(uniqueDeliveryConditionsObject: any, localizationMap?: any) {
    let targetString: any;
    const prefix = getLocalizedName("HCM_ADMIN_CONSOLE_TARGET_SMC", localizationMap);
    const attributeCode = getLocalizedName(uniqueDeliveryConditionsObject.attribute.code.toUpperCase(), localizationMap);
    const operatorMessage = getLocalizedName(uniqueDeliveryConditionsObject.operator.code, localizationMap);
    const localizedFROM = getLocalizedName("FROM", localizationMap);
    const localizedTO = getLocalizedName("TO", localizationMap);
    if (uniqueDeliveryConditionsObject.operator.code === 'IN_BETWEEN') {
        targetString = `${prefix} ${attributeCode} ${localizedFROM} ${uniqueDeliveryConditionsObject.fromValue} ${localizedTO} ${uniqueDeliveryConditionsObject.toValue}`;
    } else {
        targetString = `${prefix} ${attributeCode} ${operatorMessage} ${uniqueDeliveryConditionsObject.value}`;
    }
    return targetString;
}

async function updateTargetColumnsIfDeliveryConditionsDifferForSMC(request: any) {
    const existingCampaignDetails = request?.body?.ExistingCampaignDetails;
    if (existingCampaignDetails) {
        if (isDynamicTargetTemplateForProjectType(request?.body?.CampaignDetails?.projectType) && config?.isCallGenerateWhenDeliveryConditionsDiffer && !_.isEqual(existingCampaignDetails?.deliveryRules, request?.body?.CampaignDetails?.deliveryRules)) {
            const newRequestBody = {
                RequestInfo: request?.body?.RequestInfo,
                Filters: {
                    boundaries: request?.body?.boundariesCombined
                }
            };

            const { query } = request;
            const params = {
                tenantId: request?.body?.CampaignDetails?.tenantId,
                forceUpdate: 'true',
                hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
                campaignId: request?.body?.CampaignDetails?.id
            };

            const newParamsBoundary = { ...query, ...params, type: "boundary" };
            const newRequestBoundary = replicateRequest(request, newRequestBody, newParamsBoundary);
            await callGenerate(newRequestBoundary, "boundary", true);
        }
    }
}

function isDynamicTargetTemplateForProjectType(projectType: string) {
    const projectTypesFromConfig = config?.enableDynamicTemplateFor;
    const projectTypesArray = projectTypesFromConfig ? projectTypesFromConfig.split(',') : [];
    return projectTypesArray.includes(projectType);
}





export {
    modifyDeliveryConditions,
    generateTargetColumnsBasedOnDeliveryConditions,
    generateDynamicTargetHeaders,
    updateTargetColumnsIfDeliveryConditionsDifferForSMC,
    isDynamicTargetTemplateForProjectType
};
