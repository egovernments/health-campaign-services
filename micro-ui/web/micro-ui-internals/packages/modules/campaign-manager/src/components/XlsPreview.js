import { PopUp, SVG, DownloadIcon, Button } from "@egovernments/digit-ui-react-components";
import React from "react";
import DocViewer, { DocViewerRenderers } from "@cyntler/react-doc-viewer";
import { useTranslation } from "react-i18next";

function XlsPreview({ file, ...props }) {
  const { t } = useTranslation();
  const documents = file
    ? [
        {
          fileType: "xlsx",
          fileName: file?.fileName,
          uri: file?.url,
        },
      ]
    : null;

  return (
    <PopUp className="campaign-data-preview" style={{ flexDirection: "column" }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginLeft: "2.5rem", marginRight: "2.5rem", marginTop: "2.5rem" }}>
        <Button
          label={t("BACK")}
          variation="secondary"
          icon={<SVG.ArrowBackIos styles={{ height: "1.25rem", width: "1.25rem" }} fill="#F47738" />}
          type="button"
          className="workbench-download-template-btn"
          onButtonClick={() => props?.onBack()}
        />
        <Button
          label={t("WBH_DOWNLOAD")}
          variation="secondary"
          icon={<DownloadIcon styles={{ height: "1.25rem", width: "1.25rem" }} fill="#F47738" />}
          type="button"
          className="workbench-download-template-btn"
          onButtonClick={() => props?.onDownload()}
        />
      </div>
      <div className="campaign-popup-module" style={{ marginTop: "1.5rem" }}>
        <DocViewer
          style={{ height: "80vh", overflowY: "hidden" }}
          theme={{
            primary: "#F47738",
            secondary: "#feefe7",
            tertiary: "#feefe7",
            textPrimary: "#0B0C0C",
            textSecondary: "#505A5F",
            textTertiary: "#00000099",
            disableThemeScrollbar: true,
          }}
          documents={documents}
          pluginRenderers={DocViewerRenderers}
        />
      </div>
    </PopUp>
  );
}

export default XlsPreview;
