import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { DocumentIcon } from "./DocumentIcon";
import XlsPreview from "./XlsPreview";

function CampaignDocumentsPreview({ documents = [], svgStyles = {} }) {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [filesArray, setFilesArray] = useState(null);
  const [pdfFiles, setPdfFiles] = useState({});
  const [showPreview, setShowPreview] = useState(false);
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

  const handleFileDownload = (a, b) => {
    window.open(b, "_blank");
  };

  return (
    <div>
      <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-start" }}>
        {documents?.length > 0 ? (
          documents?.map((document, index) => (
            <React.Fragment key={index}>
              <div onClick={() => setShowPreview(true)}>
                <div style={{ display: "flex", justifyContent: "center" }}>
                  <DocumentIcon />
                </div>
                <p className="campaign-document-title">
                  {document?.fileName ? t(document?.fileName) : t("CAMPAIGN_DOCUMENT_TITLE", { INDEX: index + 1 })}
                </p>
              </div>
              {showPreview && (
                <XlsPreview
                  file={{ url: pdfFiles[document?.id] }}
                  onDownload={() => handleFileDownload(null, pdfFiles[document?.id])}
                  onBack={() => setShowPreview(false)}
                />
              )}
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
