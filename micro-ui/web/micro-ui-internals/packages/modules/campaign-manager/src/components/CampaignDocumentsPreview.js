import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { DocumentIcon } from "./DocumentIcon";

function CampaignDocumentsPreview({ documents = [], svgStyles = {} }) {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [filesArray, setFilesArray] = useState(null);
  const [pdfFiles, setPdfFiles] = useState({});

  useEffect(() => {
    let acc = documents?.map((i) => i.id);
    setFilesArray(acc);
  }, [documents]);

  useEffect(() => {
    if (filesArray?.length) {
      Digit.UploadServices.Filefetch(filesArray, Digit.ULBService.getCurrentTenantId()).then((res) => {
        setPdfFiles(res?.data);
      });
    }
  }, [filesArray]);

  return (
    <div>
      <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-start" }}>
        {documents?.length > 0 ? (
          documents?.map((document, index) => (
            <React.Fragment key={index}>
              <a
                target="_"
                href={pdfFiles[document?.id]}
                style={{ minWidth: "80px", marginRight: "10px", maxWidth: "100px", height: "auto" }}
                key={index + 1}
              >
                <div style={{ display: "flex", justifyContent: "center" }}>
                  <DocumentIcon />
                </div>
                <p className="campaign-document-title">
                  {document?.fileName ? t(document?.fileName) : t("CAMPAIGN_DOCUMENT_TITLE", { INDEX: index + 1 })}
                </p>
              </a>
            </React.Fragment>
          ))
        ) : (
          <div>
            <p>{t("ES_CAMPAIGN_NO_DOCUMENTS_AVAILABLE")}</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default CampaignDocumentsPreview;
