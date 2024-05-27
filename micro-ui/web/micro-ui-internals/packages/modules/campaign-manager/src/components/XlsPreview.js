import { PopUp, SVG, DownloadIcon, Button } from "@egovernments/digit-ui-react-components";
import React from "react";
import DocViewer, { DocViewerRenderers } from "@cyntler/react-doc-viewer";
import { useTranslation } from "react-i18next";
import { PRIMARY_COLOR } from "../utils";

const ArrowBack = ({ className = "", height = "15", width = "15", styles = {} }) => {
  return (
    <svg className={className} style={styles} width={width} height={height} viewBox="0 0 15 14" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M14.1663 6.16658H4.02467L8.68301 1.50825L7.49967 0.333252L0.833008 6.99992L7.49967 13.6666L8.67467 12.4916L4.02467 7.83325H14.1663V6.16658Z"
        fill={PRIMARY_COLOR}
      />
    </svg>
  );
};
function XlsPreview({ file, ...props }) {
  const { t } = useTranslation();
  const documents = file
    ? [
        {
          fileType: "xlsx",
          fileName: file?.filename,
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
          icon={<ArrowBack styles={{ height: "1.25rem", width: "1.25rem" }} fill={PRIMARY_COLOR} />}
          type="button"
          className="workbench-download-template-btn"
          onButtonClick={() => props?.onBack()}
        />
        <Button
          label={t("WBH_DOWNLOAD")}
          variation="secondary"
          icon={<DownloadIcon styles={{ height: "1.25rem", width: "1.25rem" }} fill={PRIMARY_COLOR} />}
          type="button"
          className="workbench-download-template-btn"
          onButtonClick={() => props?.onDownload()}
        />
      </div>
      <div className="campaign-popup-module" style={{ marginTop: "1.5rem" }}>
        <DocViewer
          style={{ height: "80vh", overflowY: "hidden" }}
          theme={{
            primary: PRIMARY_COLOR,
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
