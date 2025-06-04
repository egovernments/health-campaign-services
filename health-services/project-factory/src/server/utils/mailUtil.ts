import { produceModifiedMessages } from "../kafka/Producer";
import { defaultRequestInfo } from "../api/coreApis";
import config from "../config";

export async function sendNotificationEmail() {
    const message = {
        requestInfo: defaultRequestInfo.RequestInfo,
        email: {
            emailTo: ["nitishsingh776580@gmail.com"],   // set your email here
            subject: "Welcome to eGov",
            body: "Your complaint has been registered by local.",
            fileStoreId: { "4ffb941f-8306-48f7-a655-6fa8ae7e02bb": "doc1" },
            tenantId: "dev",
            isHTML: true,
        },
    };

    await produceModifiedMessages(message, config?.kafka?.KAFKA_NOTIFICATION_EMAIL_TOPIC);
}

