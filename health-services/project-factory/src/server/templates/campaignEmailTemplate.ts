interface EmailTemplateParams {
    logoLabel: string;
    headerContent: string;
    header: string;
    greeting: string;
    campaignNameLabel: string;
    campaignName: string;
    userCredentialLabel: string;
    accessLink: string;
    mobileApp: string;
    appLink: string;
    instructionHeader: string;
    instruction1: string;
    instruction2: string;
    instruction3: string;
    regards: string;
    footerLink1: string;
    footerLink2: string;
    footerContent: string;
    regardsTeam:String;
    supportEmail: string;
}

export function generateCampaignEmailTemplate(params: EmailTemplateParams): string {
    const {
        logoLabel,
        headerContent,
        header,
        greeting,
        campaignNameLabel,
        campaignName,
        userCredentialLabel,
        accessLink,
        mobileApp,
        appLink,
        instructionHeader,
        instruction1,
        instruction2,
        instruction3,
        regards,
        footerLink1,
        footerLink2,
        footerContent,
        regardsTeam,
        supportEmail
    } = params;

    return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${logoLabel} ${headerContent}</title>
</head>
<body style="margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f5f5f5;">

<!-- Main Container -->
<table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f5f5f5;">
  <tr>
    <td align="center" style="padding: 0;">

      <!-- Header -->
      <table width="600" border="0" cellspacing="0" cellpadding="0" style="background-color: #3c4a5c; max-width: 600px;">
        <tr>
          <td align="center" style="padding: 15px 20px;">
            <table border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td valign="middle">
                  <img src="https://egov-dev-assets.s3.ap-south-1.amazonaws.com/digit.png" alt="DIGIT" height="26" border="0" style="height: 26px; vertical-align: middle; display: inline-block; border: 0;" />
                  <span style="color: #8a9ba8; font-size: 24px; font-weight: 300; margin: 0 8px; line-height: 1; vertical-align: middle;">|</span>
                  <span style="font-size: 24px; color: white; font-weight: 300; line-height: 1; vertical-align: middle;">${headerContent}</span>
                </td>
              </tr>
            </table>
          </td>
        </tr>
      </table>

      <!-- Content Container -->
      <table width="600" border="0" cellspacing="0" cellpadding="0" style="background-color: white; max-width: 600px;">
        <tr>
          <td style="padding: 40px 30px;" align="center">

            <!-- Main Title -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td align="center" style="color: #0066cc; font-size: 32px; font-weight: 700; padding-bottom: 15px; font-family: Arial, Helvetica, sans-serif;">
                  ${header}
                </td>
              </tr>
              <tr>
                <td align="center" style="color: #0066cc; font-size: 18px; font-weight: 400; padding-bottom: 25px; font-family: Arial, Helvetica, sans-serif;">
                  ${campaignName}
                </td>
              </tr>
            </table>

            <!-- Horizontal Line -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td align="center" style="padding: 0 0 25px 0;">
                  <table border="0" cellspacing="0" cellpadding="0" width="75%">
                    <tr>
                      <td style="background-color: #8a9ba8; height: 1px; line-height: 1px; font-size: 1px;">&nbsp;</td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>

            <!-- Intro Text -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td align="center" style="color: #666; font-size: 15px; line-height: 1.6; padding-bottom: 40px; font-family: Arial, Helvetica, sans-serif;">
                  ${greeting}
                </td>
              </tr>
            </table>

            <!-- Campaign Details Section -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f8f9fa; border-radius: 8px;">
              <tr>
                <td style="padding: 30px;">

                  <!-- Section Title -->
                  <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                      <td align="center" style="color: #2c3e50; font-size: 20px; font-weight: 600; padding-bottom: 25px; font-family: Arial, Helvetica, sans-serif;">
                        Campaign Details
                      </td>
                    </tr>
                  </table>

                  <!-- Campaign Name Row -->
                  <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                      <td align="center" style="padding-bottom: 25px;">
                        <span style="color: #2c5282; font-size: 15px; font-weight: 600; font-family: Arial, Helvetica, sans-serif;">${campaignNameLabel}</span>
                        <span style="color: #0066cc; font-size: 15px; font-weight: 400; margin-left: 40px; font-family: Arial, Helvetica, sans-serif;">${campaignName}</span>
                      </td>
                    </tr>
                  </table>

                  <!-- Buttons -->
                  <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                      <td align="center">
                        <table border="0" cellspacing="0" cellpadding="0">
                          <tr>
                            <!-- Access Button -->
                            <td style="padding: 0 10px;">
                              <a href="${accessLink}" style="display: inline-block; padding: 12px 30px; background-color: white; color: #16a34a; border: 2px solid #16a34a; border-radius: 6px; font-size: 15px; font-weight: 600; text-decoration: none; font-family: Arial, Helvetica, sans-serif;">
                                <span style="font-size: 18px;">&#128274;</span> ${userCredentialLabel}
                              </a>
                            </td>
                            <!-- Download Button -->
                            <td style="padding: 0 10px;">
                              <a href="${appLink}" style="display: inline-block; padding: 12px 30px; background-color: white; color: #ff6b35; border: 2px solid #ff6b35; border-radius: 6px; font-size: 15px; font-weight: 600; text-decoration: none; font-family: Arial, Helvetica, sans-serif;">
                                <span style="font-size: 18px;">&#128229;</span> ${mobileApp}
                              </a>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>

                </td>
              </tr>
            </table>

            <!-- Important Box -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0" style="margin-top: 30px;">
              <tr>
                <td style="background-color: #fffbeb; border: 1px solid #fbbf24; border-radius: 8px; padding: 20px 25px;">

                  <!-- Important Header -->
                  <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                      <td style="padding-bottom: 15px;">
                        <span style="color: #f59e0b;"font-size: 20px; vertical-align: middle;">&#9888;</span>
                        <span style="color: #f59e0b; font-size: 16px; font-weight: 600; margin-left: 8px; font-family: Arial, Helvetica, sans-serif; vertical-align: middle;">${instructionHeader}!</span>
                      </td>
                    </tr>
                  </table>

                  <!-- Important List -->
                  <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                      <td style="color: #666; font-size: 14px; line-height: 1.6; padding-bottom: 8px; font-family: Arial, Helvetica, sans-serif;">
                        &bull; ${instruction1}
                      </td>
                    </tr>
                    <tr>
                      <td style="color: #666; font-size: 14px; line-height: 1.6; padding-bottom: 8px; font-family: Arial, Helvetica, sans-serif;">
                        &bull; ${instruction2}
                      </td>
                    </tr>
                    <tr>
                      <td style="color: #666; font-size: 14px; line-height: 1.6; font-family: Arial, Helvetica, sans-serif;">
                        &bull; ${instruction3}
                      </td>
                    </tr>
                  </table>

                </td>
              </tr>
            </table>

            <!-- Contact Info -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0" style="margin-top: 30px;">
              <tr>
                <td style="color: #666; font-size: 14px; line-height: 1.6; padding-bottom: 15px; font-family: Arial, Helvetica, sans-serif;">
                  ${regards} <a href="mailto:${supportEmail}" style="color: #007bff; text-decoration: none; font-family: Arial, Helvetica, sans-serif;">${supportEmail}</a>
                </td>
              </tr>
              <tr>
                <td style="color: #666; font-size: 14px; line-height: 1.6; font-family: Arial, Helvetica, sans-serif;">
                  Regards,
                </td>
              </tr>
              <tr>
                <td style="color: #666; font-size: 14px; line-height: 1.6; font-family: Arial, Helvetica, sans-serif;">
                  ${regardsTeam}
                </td>
              </tr>
            </table>

          </td>
        </tr>
      </table>

      <!-- Footer -->
      <table width="600" border="0" cellspacing="0" cellpadding="0" style="background-color: #3c4a5c; max-width: 600px;">
        <tr>
          <td align="center" style="padding: 20px;">

            <!-- Logo -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td align="center" style="padding-bottom: 15px;">
                  <img src="https://egov-qa-assets.s3.ap-south-1.amazonaws.com/hcm/eGov-logo.png" alt="eGov" width="80" style="display: block; border: 0;"/>
                </td>
              </tr>
            </table>

            <!-- Footer Links -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td align="center" style="padding-bottom: 15px; font-family: Arial, Helvetica, sans-serif;">
                  <a href="https://egov.org.in/" style="color: white; text-decoration: none; font-size: 12px; font-weight: 300;">${footerLink1}</a>
                  <span style="color: #8a9ba8; font-size: 12px; margin: 0 8px;">|</span>
                  <a href="https://www.linkedin.com/company/egovfoundation" style="color: white; text-decoration: none; font-size: 12px; font-weight: 300;">${footerLink2}</a>
                </td>
              </tr>
            </table>

            <!-- Footer Text -->
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td align="center" style="color: white; font-size: 12px; font-weight: 300; font-family: Arial, Helvetica, sans-serif;">
                  ${footerContent}
                </td>
              </tr>
            </table>

          </td>
        </tr>
      </table>

    </td>
  </tr>
</table>

</body>
</html>
    `.trim();
}
