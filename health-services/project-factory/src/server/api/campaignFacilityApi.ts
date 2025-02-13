import config from "../config";
import { defaultRequestInfo } from "./coreApis";
import { httpRequest } from "../utils/request";
import { produceModifiedMessages } from "../kafka/Producer";

export async function createFacilitiesAndPersistFacilityId(facilitiesToCreate: any[], tenantId : string, userUuid : string) {
    const facilityCreateRequestBody : any = {
        RequestInfo: defaultRequestInfo.RequestInfo
    }
    for(const facility of facilitiesToCreate) {
        facilityCreateRequestBody.Facility = {
           name : facility.name,
           storageCapacity : facility.storageCapacity,
           isPermanent : facility.isPermanent,
           usage : facility.facilityUsage,
           tenantId
        }
        const response = await httpRequest(config.host.facilityHost + config.paths.facilityCreate, facilityCreateRequestBody);
        if (response?.Facility?.id) {
           const produceMessage : any = {
               campaignFacilities : [
                   {
                     ...facility,
                     facilityId : response?.Facility?.id,
                     lastModifiedBy : userUuid,
                     lastModifiedTime : new Date().getTime()
                   }
               ]
           }
           await produceModifiedMessages(
               produceMessage,
               config?.kafka?.KAFKA_UPDATE_CAMPAIGN_FACILITIES_TOPIC
           )
        }
        else {
            throw new Error("Failed to create facility with name " + facility.name);
        }
    }
    return {};
}