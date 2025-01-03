import React, { memo } from "react";
import { ActionBar, ArrowForward, Banner } from "@egovernments/digit-ui-components";
import { useTranslation } from "react-i18next";
import { ArrowBack, FileDownload } from "@egovernments/digit-ui-svg-components";
import { convertJsonToXlsx, writeWorkbookToBuffer } from "../utils/jsonToExcelBlob";
import { Button } from "@egovernments/digit-ui-react-components";
import { useHistory } from "react-router-dom";
import { Link } from "react-router-dom/cjs/react-router-dom.min";
import { PRIMARY_THEME_COLOR, commonColumn } from "../configs/constants";
import { colorHeaders } from "../utils/uploadUtils";

const MicroplanCreatedScreen = memo(({ microplanData, ...props }) => {
  const { t } = useTranslation();
  const history = useHistory();

  const downloadMicroplan = async () => {
    try {
      if (!microplanData?.microplanPreview) return;
      const data = _.cloneDeep(microplanData?.microplanPreview?.previewData);
      const commonColumnIndex = data[0]?.findIndex((item) => item === commonColumn);
      data[0] = data[0].map((item) => t(item));

      for (const i in data) {
        data[i] = data[i].map((item, index) =>
          item ? (typeof item === "number" ? item : index === commonColumnIndex ? item : t(item)) : t("NO_DATA")
        );
      }

      const headers = data?.[0] || [];
      const workbook = await convertJsonToXlsx({ [microplanData?.microplanDetails?.name]: data }, {}, true);
      colorHeaders(workbook, headers, [], []);
      const blob = await writeWorkbookToBuffer(workbook);

      if (!blob) {
        return;
      }

      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;

      const fileNameParts = microplanData?.microplanDetails?.name;
      if (!fileNameParts) {
        return;
      }

      link.download = fileNameParts;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error(`Failed to download microplan: ${error.message}`, error);
    }
  };

  const clickGoHome = () => {
    history.push("/microplan-ui/employee");
  };

  return (
    <div className="">
      <div className="microplan-success-screen">
        <div>
          <Banner
            message={t("MICROPLAN_GENERATED_SUCCESSFULLY")}
            whichSvg="tick"
            applicationNumber={microplanData?.microplanDetails?.name ? microplanData?.microplanDetails?.name : ""}
            info={t("MICROPLAN_GENERATED_SUCCESSFULLY_NAME_LABEL")}
            successful={true}
            headerStyles={{ fontWeight: "700", fontFamily: " Roboto Condensed", fontSize: "2.5rem" }}
          />
        </div>
        <p>{t("MICROPLAN_GENERATED_SUCCESSFULLY_DESCRIPTIION")}</p>
        <div className="button-container">
          <Button
            label={t("DOWNLOAD_MICROPLAN")}
            variation="secondary"
            onButtonClick={downloadMicroplan}
            icon={<FileDownload width={"1.5rem"} height={"1.5rem"} fill={PRIMARY_THEME_COLOR} />}
            isSuffix={false}
          />
        </div>
      </div>

      <ActionBar className={"custom-action-bar-success-screen"}>
        {/* Back button */}
        <Button
          type="button"
          className="custom-button custom-button-left-icon"
          label={t("GO_BACK_HOME")}
          onButtonClick={clickGoHome}
          isSuffix={false}
          variation={"secondary"}
          icon={<ArrowBack className={"icon"} width={"1.5rem"} height={"1.5rem"} fill={PRIMARY_THEME_COLOR} />}
        />
        {/* Next/Submit button */}
        <a style={{ textDecoration: "none" }} href={"/workbench-ui/employee/"}>
          <Button
            type="button"
            className="custom-button"
            label={t("GO_TO_HCM")}
            isSuffix={true}
            variation={"primary"}
            textStyles={{ padding: 0, margin: 0 }}
          />
        </a>
      </ActionBar>
    </div>
  );
});

export default MicroplanCreatedScreen;
