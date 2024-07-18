import config from '../config/index'
import { getLocalizedName } from './campaignUtils';
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
    const targetColumnsBasedOnDeliveryConditions: string[] = [config?.boundary?.boundaryCode];

    uniqueDeliveryConditions.forEach((str: any) => {
        const uniqueDeliveryConditionsObject = JSON.parse(str); // Parse JSON string into object
        const targetColumnString = createTargetString(uniqueDeliveryConditionsObject, localizationMap);
        targetColumnsBasedOnDeliveryConditions.push(targetColumnString);
    });

    return targetColumnsBasedOnDeliveryConditions;
}

function createTargetString(uniqueDeliveryConditionsObject: any, localizationMap?: any) {
    let targetString: any;
    const prefix = getLocalizedName("HCM_ADMIN_CONSOLE_TARGET", localizationMap);
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

export {
    modifyDeliveryConditions,
    generateTargetColumnsBasedOnDeliveryConditions
};
