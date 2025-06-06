import { produceModifiedMessages } from "../kafka/Producer";
import { searchMDMSDataViaV2Api } from "../api/coreApis";
import config from "../config";
import { MDMSModels } from "../models";
import { getLocalizedName } from "./campaignUtils";
import { getLocalizedMessagesHandlerViaLocale } from "./genericUtils";
import { getFileUrl } from "./onGoingCampaignUpdateUtils";

export async function sendNotificationEmail(
    fileStoreIdMap: Record<string, string>, requestInfo: any
): Promise<void> {

    const localizationMap = await getLocalizedMessagesHandlerViaLocale((requestInfo?.msgId?.split("|")?.[1] || "en-IN"), requestInfo?.userInfo?.tenantId);

    const tenantId = requestInfo?.userInfo?.tenantId;
    const MdmsCriteria: MDMSModels.MDMSv2RequestCriteria = {
        MdmsCriteria: {
            tenantId: tenantId,
            schemaCode: "HCM-ADMIN-CONSOLE.emailTemplate"
        }
    };

    const mdmsResponse = await searchMDMSDataViaV2Api(MdmsCriteria);
    const emailTemplate = mdmsResponse?.mdms?.[0];

    if (!emailTemplate) throw new Error("Email template not found in MDMS");

    const subject = getLocalizedName(emailTemplate?.data?.subjectCode, localizationMap);
    const bodyLines = emailTemplate?.data?.bodyCodes.map((code: string) =>
        getLocalizedName(code, localizationMap)
    );

    const fileUrls = await Promise.all(
        Object.entries(fileStoreIdMap).map(async ([fileId, fileName]) => {
            const url = await getFileUrl(fileId, tenantId);
            return `<a href="${url}">${fileName}</a>`;
        })
    );

    const allFileUrls = fileUrls.join("<br/>");
    const fullBody = `${bodyLines.join("<br/>")}<br/><br/>${allFileUrls}`;

    const message = {
        requestInfo: requestInfo,
        email: {
            emailTo: [requestInfo?.userInfo?.emailId],
            subject,
            body: fullBody,
            fileStoreId: fileStoreIdMap,
            tenantId,
            isHTML: true,
        },
    };


    await produceModifiedMessages(message, config?.kafka?.KAFKA_NOTIFICATION_EMAIL_TOPIC);
}