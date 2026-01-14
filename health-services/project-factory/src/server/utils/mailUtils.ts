import { downloadTemplate } from "../utils/pollUtils";
import { produceModifiedMessages } from "../kafka/Producer";
import { searchMDMSDataViaV2Api } from "../api/coreApis";
import config from "../config";
import { MDMSModels } from "../models";
import { getLocalizedName } from "./campaignUtils";
import { getLocalizedMessagesHandlerViaLocale } from "./genericUtils";
import { getFileUrl } from "./onGoingCampaignUpdateUtils";
import { logger } from "./logger";
import { generateCampaignEmailTemplate } from "../templates/campaignEmailTemplate";

export async function sendNotificationEmail(
    fileStoreIdMap: Record<string, string>, requestBody: any , createdByEmail?: string
): Promise<void> {

    try {
        const requestInfo = requestBody?.RequestInfo;
        logger.info("Step 1: Starting sendNotificationEmail");

        const locale = config?.localisation?.defaultLocale || "en-IN";
        const tenantId = requestInfo?.userInfo?.tenantId;

        logger.info(`Step 2: Extracted locale: ${locale}, tenantId: ${tenantId}`);

        const localizationMap = await getLocalizedMessagesHandlerViaLocale(locale, tenantId,"hcm-admin-notification");
        logger.info("Step 3: Fetched localization map");
        
        const MdmsCriteria: MDMSModels.MDMSv2RequestCriteria = {
            MdmsCriteria: {
                tenantId: tenantId,
                schemaCode: "HCM-ADMIN-CONSOLE.emailTemplateV2"
            }
        };

        logger.info("Step 4: Calling MDMS API with criteria: " + JSON.stringify(MdmsCriteria));
        const mdmsResponse = await searchMDMSDataViaV2Api(MdmsCriteria);
        const bednetCampaign = false;

        const emailTemplate = bednetCampaign ? mdmsResponse?.mdms?.[1] : mdmsResponse?.mdms?.[2];

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

        const headerCode = emailTemplate?.data?.body?.header;
        const header = replacePlaceholders(getLocalizedName(headerCode, localizationMap), replacements);

        const greetingCode = emailTemplate?.data?.body?.greeting;
        const greeting = replacePlaceholders(getLocalizedName(greetingCode, localizationMap), replacements);

        const mobileAppCode = emailTemplate?.data?.body?.mobileApp;
        const mobileApp = replacePlaceholders(getLocalizedName(mobileAppCode, localizationMap), replacements);

        const campaignNameLabelCode = emailTemplate?.data?.body?.campaignName;
        const campaignNameLabel = replacePlaceholders(getLocalizedName(campaignNameLabelCode, localizationMap), replacements);

        const userCredentialLabelCode = emailTemplate?.data?.body?.userCredentialLabel;
        const userCredentialLabel = replacePlaceholders(getLocalizedName(userCredentialLabelCode, localizationMap), replacements);

        const headerContentCode = emailTemplate?.data?.header?.content;
        const headerContent = replacePlaceholders(getLocalizedName(headerContentCode, localizationMap), replacements);

        const logoLabelCode = emailTemplate?.data?.header?.logoLabel;
        const logoLabel = replacePlaceholders(getLocalizedName(logoLabelCode, localizationMap), replacements);

        const footerLink1Code = emailTemplate?.data?.footer?.links?.[0];
        const footerLink1 = replacePlaceholders(getLocalizedName(footerLink1Code, localizationMap), replacements);

        const footerLink2Code = emailTemplate?.data?.footer?.links?.[1];
        const footerLink2 = replacePlaceholders(getLocalizedName(footerLink2Code, localizationMap), replacements);

        const footerContentCode = emailTemplate?.data?.footer?.content;
        const footerContent = replacePlaceholders(getLocalizedName(footerContentCode, localizationMap), replacements);

        const regardsCode = emailTemplate?.data?.regards?.[0];
        const regards = replacePlaceholders(getLocalizedName(regardsCode, localizationMap), replacements);

        const regardsTeamCode = emailTemplate?.data?.regards?.[1];
        const regardsTeam = replacePlaceholders(getLocalizedName(regardsTeamCode, localizationMap), replacements);

        const instructionHeaderCode = emailTemplate?.data?.instructions?.[0];
        const instructionHeader = replacePlaceholders(getLocalizedName(instructionHeaderCode, localizationMap), replacements);

        const instruction1Code = emailTemplate?.data?.instructions?.[1];
        const instruction1 = replacePlaceholders(getLocalizedName(instruction1Code, localizationMap), replacements);

        const instruction2Code = emailTemplate?.data?.instructions?.[2];
        const instruction2 = replacePlaceholders(getLocalizedName(instruction2Code, localizationMap), replacements);

        const instruction3Code = emailTemplate?.data?.instructions?.[3];
        const instruction3 = replacePlaceholders(getLocalizedName(instruction3Code, localizationMap), replacements);

        const subjectCode = emailTemplate?.data?.subject;
        const subject = replacements.campaignName +" - " +replacePlaceholders(getLocalizedName(subjectCode, localizationMap), replacements);

        logger.info("Step 6: Constructed localized subject and all email fields");

        // Generate the complete HTML email template
        const fullBody = generateCampaignEmailTemplate({
            logoLabel,
            headerContent,
            header,
            greeting,
            campaignNameLabel,
            campaignName: replacements.campaignName,
            userCredentialLabel,
            accessLink: replacements.accessLink,
            mobileApp,
            appLink: replacements.appLink,
            instructionHeader,
            instruction1,
            instruction2,
            instruction3,
            regards,
            footerLink1,
            footerLink2,
            footerContent,
            regardsTeam,
            supportEmail: config.values.emailNotificationId,
            egovLogoLink: config.values.egovLogoLink
        });

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
                emailTo: requestInfo?.userInfo?.emailId ? [requestInfo.userInfo.emailId] : createdByEmail 
                        ? [createdByEmail] : null,
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

export async function triggerUserCredentialEmailFlow(requestBody: any , createdByEmail?: string): Promise<void> {
  logger.info("triggerUserCredentialEmailFlow: Email flow started...");
  // waiting for 3 seconds to ensure that user credentials are ready
  logger.info("triggerUserCredentialEmailFlow: Waiting for 3 seconds before proceeding with email flow...");
  await new Promise(resolve => setTimeout(resolve, 3000));
  try {
    const userCredentialFileMap = await getUserCredentialFileMap(requestBody);
    await sendNotificationEmail(userCredentialFileMap, requestBody , createdByEmail);
    logger.info("triggerUserCredentialEmailFlow: Email flow completed successfully.");
  } catch (emailError) {
    logger.error("triggerUserCredentialEmailFlow: Email flow failed — continuing main flow", emailError);
  }
}


