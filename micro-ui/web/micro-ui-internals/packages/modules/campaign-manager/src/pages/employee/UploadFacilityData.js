import { Button, Header } from "@egovernments/digit-ui-react-components";
import React, { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { DownloadIcon } from "@egovernments/digit-ui-react-components";
import BulkUpload from "../../components/BulkUpload";

const UploadFacilityData = () => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [uploadedFile, setUploadedFile] = useState([]);

  const onBulkUploadSubmit = async (file) => {
    const module = "HCM";
    const { data: { files: fileStoreIds } = {} } = await Digit.UploadServices.MultipleFilesStorage(module, file, tenantId);
    const filesArray = [fileStoreIds?.[0]?.fileStoreId];
    const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
    const fileData = fileUrl.map((i) => {
      const t = i?.url?.split("/");
      const dd = t[t?.length - 1]?.split("?")?.[0];
      return {
        ...i,
        fileName: dd,
      };
    });
    setUploadedFile(fileData);
  };

  const onFileDelete = (file, index) => {
    setUploadedFile((prev) => prev.filter((i) => i.id !== file.id));
  };

  const onFileDownload = (file) => {
    window.open(file.url, "_blank", `name=${file.fileName}`);
  };

  return (
    <React.Fragment>
      <div className="campaign-bulk-upload">
        <Header className="digit-form-composer-sub-header">{t("WBH_UPLOAD_Facility")}</Header>
        <Button
          label={t("WBH_DOWNLOAD_TEMPLATE")}
          variation="secondary"
          icon={<DownloadIcon styles={{ height: "1.25rem", width: "1.25rem" }} fill="#F47738" />}
          type="button"
          className="campaign-download-template-btn"
        />
      </div>
      <div className="info-text">{t(`HCM_FACILITY_MESSAGE`)}</div>
      <BulkUpload onSubmit={onBulkUploadSubmit} fileData={uploadedFile} onFileDelete={onFileDelete} onFileDownload={onFileDownload} />
    </React.Fragment>
  );
};

export default UploadFacilityData;
