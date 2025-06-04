import { produceModifiedMessages } from "../kafka/Producer";
import { defaultRequestInfo } from "../api/coreApis";
import config from "../config";

export async function sendNotificationEmail() {
  const message = {
    requestInfo: defaultRequestInfo,
    email: {
      emailTo: ["nitishsingh776580@gmail.com"],   // set your email here
      subject: "Welcome to eGov",
      body: "<p>Your complaint has been registered by local.</p>",
      fileStoreId: { doc1: "filestore-id-abc123" },
      tenantId: "dev",
      isHTML: false,
    },
  };

  await produceModifiedMessages(message,config?.kafka?.KAFKA_NOTIFICATION_EMAIL_TOPIC);
}

