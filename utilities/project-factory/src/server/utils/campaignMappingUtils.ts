import createAndSearch from "../config/createAndSearch";
import config from "../config";
import { getDataFromSheet, throwError } from "./genericUtils";
import { logger } from "./logger";
import { httpRequest } from "./request";


async function fetchAndMap(resources: any[], messageObject: any) {
    const sheetName: any = {
        "user": createAndSearch?.user?.parseArrayConfig?.sheetName,
        "facility": createAndSearch?.facility?.parseArrayConfig?.sheetName,
    }
    for (const resource of resources) {
        const processedFilestoreId = resource?.processedFilestoreId;
        console.log(resource, " rrrrrrrrrrrrrrrrrr")
        console.log(processedFilestoreId, " ppppppppppppppppppppp")
        if (processedFilestoreId) {
            const dataFromSheet: any = await getDataFromSheet(messageObject, processedFilestoreId, messageObject?.Campaign?.tenantId, undefined, sheetName[resource?.type]);
            console.log(dataFromSheet, " dddddddddddddddddddddddddddddddddddd")
            for (const data of dataFromSheet) {
                const uniqueCodeColumn = createAndSearch?.[resource?.type]?.uniqueIdentifierColumnName
                const code = data[uniqueCodeColumn];
                console.log(code, resource?.type, data[createAndSearch?.[resource?.type]?.boundaryValidation?.column], " crrrrrrrrrrrrrrrrrrrrrrrrrr")
            }
        }
    }
}

async function searchResourceDetailsById(resourceDetailId: string, messageObject: any) {
    var searchBody = {
        RequestInfo: messageObject?.RequestInfo,
        SearchCriteria: {
            id: [resourceDetailId],
            tenantId: messageObject?.Campaign?.tenantId
        }
    }
    console.log(resourceDetailId, " rrrrrrrrrrrrrrrriiiiiiiiiiiiiiiiiiiiiiiiiii")
    logger.info("searchBody : " + JSON.stringify(searchBody));
    const response = await httpRequest(config.host.projectFactoryBff + "project-factory/v1/data/_search", searchBody);
    return response?.ResourceDetails?.[0];
}

async function processCampaignMapping(messageObject: any) {
    const resourceDetailsIds = messageObject?.Campaign?.resourceDetailsIds
    var completedResources: any = []
    var resources = [];
    for (const resourceDetailId of resourceDetailsIds) {
        var retry = 5;
        while (retry--) {
            const response = await searchResourceDetailsById(resourceDetailId, messageObject);
            console.log(response, " rressssssssssssspppppppppppppppppppppppppp");
            if (response?.status == "invalid") {
                throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "resource with id " + resourceDetailId + " is invalid");
                break;
            }
            else if (response?.status == "failed") {
                throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "resource with id " + resourceDetailId + " is failed");
                break;
            }
            else if (response?.status == "completed") {
                completedResources.push(resourceDetailId);
                resources.push(response);
                break;
            }
            else {
                await new Promise(resolve => setTimeout(resolve, 20000));
            }
        }
    }
    var uncompletedResourceIds = resourceDetailsIds?.filter((x: any) => !completedResources.includes(x));
    logger.info("uncompletedResourceIds " + JSON.stringify(uncompletedResourceIds));
    logger.info("completedResources " + JSON.stringify(completedResources));
    if (uncompletedResourceIds?.length > 0) {
        throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "resource with id " + JSON.stringify(uncompletedResourceIds) + " is not validated after long wait. Check file");
    }
    await fetchAndMap(resources, messageObject);
}


export {
    processCampaignMapping
}
