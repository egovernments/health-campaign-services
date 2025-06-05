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
            fileStoreId: { "f4687d00-e675-4932-b736-2396964103f2": "doc1" },
            tenantId: "mz",
            isHTML: true,
        },
    };

    await produceModifiedMessages(message, config?.kafka?.KAFKA_NOTIFICATION_EMAIL_TOPIC);
}

