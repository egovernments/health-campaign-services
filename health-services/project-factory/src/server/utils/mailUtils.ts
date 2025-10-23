import { downloadTemplate } from "../utils/pollUtils";
import { produceModifiedMessages } from "../kafka/Producer";
import { searchMDMSDataViaV2Api } from "../api/coreApis";
import config from "../config";
import { MDMSModels } from "../models";
import { getLocalizedName } from "./campaignUtils";
import { getLocalizedMessagesHandlerViaLocale } from "./genericUtils";
import { getFileUrl } from "./onGoingCampaignUpdateUtils";
import { getEmployeeByUuid } from '../api/campaignApis';
import { logger } from "./logger"; // if you use a custom logger

export async function sendNotificationEmail(
    fileStoreIdMap: Record<string, string>, requestBody: any
): Promise<void> {

    try {
        const requestUserInfo = await getEmployeeByUuid(requestBody);
        const requestInfo = requestBody?.RequestInfo;
        logger.info("Step 1: Starting sendNotificationEmail");

        const locale = requestInfo?.msgId?.split("|")?.[1] || "en-IN";
        const tenantId = requestInfo?.userInfo?.tenantId;

        logger.info(`Step 2: Extracted locale: ${locale}, tenantId: ${tenantId}`);

        const localizationMap = await getLocalizedMessagesHandlerViaLocale(locale, tenantId);
        logger.info("Step 3: Fetched localization map");
        
        const MdmsCriteria: MDMSModels.MDMSv2RequestCriteria = {
            MdmsCriteria: {
                tenantId: tenantId,
                schemaCode: "HCM-ADMIN-CONSOLE.emailTemplate"
            }
        };

        logger.info("Step 4: Calling MDMS API with criteria: " + JSON.stringify(MdmsCriteria));
        const mdmsResponse = await searchMDMSDataViaV2Api(MdmsCriteria);

        const emailTemplate = mdmsResponse?.mdms?.[0];
        if (!emailTemplate) {
            logger.error("Step 5: Email template not found in MDMS response");
            throw new Error("Email template not found in MDMS");
        }
        logger.info("Step 5: Fetched email template from MDMS");


        // Step 4.1: Fetch appLink from MDMS
        const AppLinkCriteria: MDMSModels.MDMSv2RequestCriteria = {
            MdmsCriteria: {
                tenantId: tenantId,
                schemaCode: "HCM-ADMIN-CONSOLE.AppLink"
            }
        };

        logger.info("Step 4.1: Calling MDMS API for appLink with criteria: " + JSON.stringify(AppLinkCriteria));
        const appLinkResponse = await searchMDMSDataViaV2Api(AppLinkCriteria);
        const appLink = appLinkResponse?.mdms?.[0]?.data?.appLink || "https://default-app-link.com";
        logger.info("Step 4.2: Fetched appLink from MDMS: " + appLink);



        // Step 3: Prepare replacements
        const campaignName = requestBody?.CampaignDetails?.campaignName || "";
        const campaignManagerName = requestInfo?.userInfo?.userName || "Campaign Manager";

        // Extracting download link (use only the first file ID)
        const [firstFileId] = Object.keys(fileStoreIdMap || {});
        const downloadLink = firstFileId ? await getFileUrl(firstFileId, tenantId) : "#";

        const replacements: Record<string, string> = {
            campaignName,
            campaignManagerName,
            accessLink: downloadLink,
            appLink
        };

        const subjectCode = emailTemplate?.data?.subjectCode;
        const subject = replacePlaceholders(getLocalizedName(subjectCode, localizationMap), replacements);

        const bodyCodes = emailTemplate?.data?.bodyCodes || [];
        const bodyLines = bodyCodes.map((code: string) =>
            replacePlaceholders(getLocalizedName(code, localizationMap), replacements)
        );
        logger.info("Step 6: Constructed localized subject and body lines");
        const fullBody = bodyLines.join("<br/><br/>");

        // const fileUrls = await Promise.all(
        //     Object.entries(fileStoreIdMap).map(async ([fileId, fileName]) => {
        //         const url = await getFileUrl(fileId, tenantId);
        //         logger.info(`Step 7: Fetched file URL for fileStoreId: ${fileId}`);
        //         return `<a href="${url}">${fileName}</a>`;
        //     })
        // );

        // const allFileUrls = fileUrls.join("<br/>");
        // const fullBody = `${bodyLines.join("<br/>")}<br/><br/>${allFileUrls}`;
        logger.info("Step 8: Constructed full email body");

        const message = {
            requestInfo: requestInfo,
            email: {
                emailTo: [requestUserInfo.user.emailId],
                subject,
                body: fullBody,
                fileStoreId: fileStoreIdMap,
                tenantId,
                isHTML: true,
            },
        };

        logger.info("Step 9: Prepared email message object");
        logger.info("Step 10: Producing message to Kafka topic: " + config?.kafka?.KAFKA_NOTIFICATION_EMAIL_TOPIC);
        await produceModifiedMessages(message, config?.kafka?.KAFKA_NOTIFICATION_EMAIL_TOPIC, tenantId);

        logger.info("Step 11: Email message successfully produced to Kafka");

    } catch (error) {
        logger.error("Error occurred in sendNotificationEmail: ", error);
        throw error;
    }
}

function replacePlaceholders(template: string, replacements: Record<string, string>): string {
  return template.replace(/\{(.*?)\}/g, (_, key) => replacements[key.trim()] ?? '');
}

export async function getUserCredentialFileMap(requestBody: any): Promise<Record<string, string>> {
  try {
    const campaignId = requestBody?.CampaignDetails?.id;
    const tenantId = requestBody?.CampaignDetails?.tenantId;
    const hierarchy = requestBody?.CampaignDetails?.hierarchyType;
    const type = "userCredential";

    logger.info(`getUserCredentialFileMap: Initiating downloadTemplate for campaign ID: ${campaignId}`);

    const downloadResponse = await downloadTemplate(campaignId, tenantId, type, hierarchy, requestBody);

    const fileStoreId = downloadResponse?.[0]?.fileStoreid;
    if (!fileStoreId) {
      logger.error("getUserCredentialFileMap: fileStoreId missing in downloadTemplate response.");
      throw new Error("File store ID not found in download template response");
    }

    logger.info(`getUserCredentialFileMap: Received fileStoreId: ${fileStoreId}`);

    const userCredentialFileMap = { [fileStoreId]: "userCredentials.xlsx" };
    logger.info("getUserCredentialFileMap: Constructed fileStoreIdMap: " + JSON.stringify(userCredentialFileMap));

    return userCredentialFileMap;

  } catch (error) {
    logger.error("getUserCredentialFileMap: Error occurred while fetching user credential file map", error);
    throw error;
  }
}

export async function triggerUserCredentialEmailFlow(requestBody: any): Promise<void> {
  logger.info("triggerUserCredentialEmailFlow: Email flow started...");
  // waiting for 3 seconds to ensure that user credentials are ready
  logger.info("triggerUserCredentialEmailFlow: Waiting for 3 seconds before proceeding with email flow...");
  await new Promise(resolve => setTimeout(resolve, 3000));
  try {
    const userCredentialFileMap = await getUserCredentialFileMap(requestBody);
    await sendNotificationEmail(userCredentialFileMap, requestBody);
    logger.info("triggerUserCredentialEmailFlow: Email flow completed successfully.");
  } catch (emailError) {
    logger.error("triggerUserCredentialEmailFlow: Email flow failed — continuing main flow", emailError);
  }
}


