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
        footerContent
    } = params;

    return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DIGIT HCM Console</title>
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;700&display=swap" rel="stylesheet">
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Roboto', sans-serif;
            font-weight: 300;
            background-color: #f5f5f5;
        }

        .container {
            max-width: 800px;
            margin: 0 auto;
            background: white;
            min-height: calc(100vh - 60px);
        }

        .content {
            padding: 40px 30px;
            text-align: center;
        }

        .celebration-emoji {
            font-size: 48px;
            margin-bottom: 10px;
        }

        .main-title {
            color: #0066cc;
            font-size: 32px;
            font-weight: 700;
            margin-bottom: 15px;
        }

        .subtitle {
            color: #0066cc;
            font-size: 18px;
            font-weight: 400;
            margin-bottom: 25px;
        }

        .intro-text {
            color: #666;
            line-height: 1.6;
            margin-bottom: 40px;
            font-size: 15px;
            font-weight: 300;
            max-width: 600px;
            margin-left: auto;
            margin-right: auto;
        }

        .campaign-details-section {
            background-color: #f8f9fa;
            padding: 30px 50px;
            border-radius: 8px;
            margin-bottom: 30px;
        }

        .section-title {
            color: #2c3e50;
            font-size: 20px;
            font-weight: 600;
            margin-bottom: 25px;
            text-align: center;
        }

        .campaign-name-row {
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 25px;
            gap: 40px;
        }

        .campaign-label {
            color: #2c5282;
            font-size: 15px;
            font-weight: 600;
            text-align: left;
            white-space: nowrap;
        }

        .campaign-value {
            color: #0066cc;
            font-size: 15px;
            font-weight: 400;
            text-align: left;
        }

        .button-container {
            display: flex;
            justify-content: center;
            gap: 20px;
            flex-wrap: wrap;
        }

        .action-button {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding: 12px 30px;
            border: 2px solid;
            border-radius: 6px;
            font-size: 15px;
            font-weight: 600;
            text-decoration: none;
            transition: all 0.3s ease;
            min-width: 200px;
        }

        .access-button {
            background-color: white;
            color: #16a34a;
            border-color: #16a34a;
        }

        .access-button:hover {
            background-color: #f0fdf4;
        }

        .download-button {
            background-color: white;
            color: #ff6b35;
            border-color: #ff6b35;
        }

        .download-button:hover {
            background-color: #fff5f0;
        }

        .icon {
            width: 20px;
            height: 20px;
        }

        .important-box {
            background-color: #fffbeb;
            border: 1px solid #fbbf24;
            border-radius: 8px;
            padding: 20px 25px;
            margin: 30px 0;
            text-align: left;
        }

        .important-header {
            display: flex;
            align-items: center;
            gap: 8px;
            color: #f59e0b;
            font-size: 16px;
            font-weight: 600;
            margin-bottom: 15px;
        }

        .important-list {
            list-style: none;
            padding-left: 0;
        }

        .important-list li {
            color: #666;
            font-size: 14px;
            line-height: 1.6;
            margin-bottom: 8px;
            padding-left: 20px;
            position: relative;
        }

        .important-list li:before {
            content: "•";
            position: absolute;
            left: 5px;
            color: #f59e0b;
        }

        .contact-info {
            padding: 20px 0;
            margin-bottom: 20px;
        }

        .contact-text {
            color: #666;
            font-size: 14px;
            font-weight: 300;
            line-height: 1.6;
            text-align: left;
        }

        .contact-email {
            color: #007bff;
            text-decoration: none;
            font-weight: 400;
        }

        .contact-email:hover {
            color: #0056b3;
            text-decoration: underline;
        }

        .signature {
            text-align: left;
            margin-top: 15px;
        }

        .footer {
            background-color: #3c4a5c;
            color: white;
            text-align: center;
            padding: 20px;
        }

        .footer-logo {
            margin-bottom: 15px;
            display: flex;
            justify-content: center;
            align-items: center;
        }

        .egov-logo {
            width: 80px;
            height: auto;
            display: block;
            margin: 0 auto;
        }

        .footer-text {
            font-size: 12px;
            font-weight: 300;
            color: white;
        }

        @media (max-width: 600px) {
            .content {
                padding: 20px 15px;
            }

            .main-title {
                font-size: 24px;
            }

            .subtitle {
                font-size: 16px;
            }

            .campaign-name-row {
                flex-direction: column;
                gap: 10px;
                align-items: flex-start;
            }

            .button-container {
                flex-direction: column;
                align-items: stretch;
            }

            .action-button {
                min-width: auto;
            }
        }
    </style>
</head>
<body>

<table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #3c4a5c; max-width: 800px; margin: 0 auto;">
  <tr>
    <td align="center" style="padding: 15px 20px;">
      <table border="0" cellspacing="0" cellpadding="0">
        <tr>
          <td valign="middle" style="padding-right: 8px;">
            <table border="0" cellspacing="0" cellpadding="0" style="display: inline-block; width: 24px; height: 20px; position: relative;">
              <tr>
                <td width="6" height="6" style="background-color: white; border-radius: 50%;"></td>
                <td width="2"></td>
                <td width="6" height="6" style="background-color: white; border-radius: 50%;"></td>
                <td width="2"></td>
                <td width="6" height="6" style="background-color: white; border-radius: 50%;"></td>
              </tr>
              <tr height="4"></tr>
              <tr>
                <td colspan="2"></td>
                <td width="6" height="6" style="background-color: white; border-radius: 50%;"></td>
                <td width="2"></td>
                <td width="6" height="6" style="background-color: white; border-radius: 50%;"></td>
              </tr>
              <tr height="4"></tr>
              <tr>
                <td colspan="4"></td>
                <td width="6" height="6" style="background-color: white; border-radius: 50%;"></td>
              </tr>
            </table>
          </td>

          <td valign="middle">
            <span style="font-size: 24px; font-weight: 700; color: white; display: inline-block; vertical-align: middle; line-height: 1.2;">${logoLabel}</span>
            <span style="color: #8a9ba8; font-size: 24px; font-weight: 300; margin: 0 8px; display: inline-block; vertical-align: middle; line-height: 1.2;">|</span>
            <span style="font-size: 24px; color: white; font-weight: 300; display: inline-block; vertical-align: middle; line-height: 1.2;">${headerContent}</span>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>

<div class="container">
    <div class="content">
        <div class="celebration-emoji">🎉</div>
        <h1 class="main-title">${header}</h1>
        <div class="subtitle">${campaignName}</div>

        <div class="intro-text">
            ${greeting}
        </div>

        <div class="campaign-details-section">
            <div class="section-title">Campaign Details</div>

            <div class="campaign-name-row">
                <span class="campaign-label">${campaignNameLabel}</span>
                <span class="campaign-value">${campaignName}</span>
            </div>

            <div class="button-container">
                <a href="${accessLink}" class="action-button access-button">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                        <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                    </svg>
                    ${userCredentialLabel}
                </a>
                <a href="${appLink}" class="action-button download-button">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                        <polyline points="7 10 12 15 17 10"></polyline>
                        <line x1="12" y1="15" x2="12" y2="3"></line>
                    </svg>
                    ${mobileApp}
                </a>
            </div>
        </div>

        <div class="important-box">
            <div class="important-header">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>
                ${instructionHeader}!
            </div>
            <ul class="important-list">
                <li>${instruction1}</li>
                <li>${instruction2}</li>
                <li>${instruction3}</li>
            </ul>
        </div>

        <div class="contact-info">
            <div class="contact-text">
                ${regards} <a href="mailto:contact@egovernments.org" class="contact-email">contact@egovernments.org</a>
            </div>
            <div class="signature">
                <div class="contact-text">Regards,</div>
                <div class="contact-text">HCM Console Team</div>
            </div>
        </div>
    </div>

    <div class="footer">
        <div class="footer-logo">
            <img src="https://digit-sandbox-prod-s3.s3.ap-south-1.amazonaws.com/assets/Reverse+Orange+%26+White.png" alt="eGov" class="egov-logo"/>
        </div>
        <table width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom: 15px;">
          <tr>
            <td align="center" style="font-family: 'Roboto', sans-serif;">
              <span style="color: white; text-decoration: none; font-size: 12px; font-weight: 300; display: inline-block; padding: 0 5px;">
                <a href="https://egov.org.in/" style="color: white; text-decoration: none; font-size: 12px; font-weight: 300;">${footerLink1}</a>
              </span>
              <span style="color: #8a9ba8; font-size: 12px; display: inline-block; padding: 0 5px;">|</span>

              <span style="color: white; text-decoration: none; font-size: 12px; font-weight: 300; display: inline-block; padding: 0 5px;">
                <a href="https://www.linkedin.com/company/egovernments-foundation" style="color: white; text-decoration: none; font-size: 12px; font-weight: 300;">${footerLink2}</a>
              </span>
            </td>
          </tr>
        </table>

        <div class="footer-text">${footerContent}</div>
    </div>
</div>
</body>
</html>
    `.trim();
}
