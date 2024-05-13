import React, { useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { DocumentIcon } from "./DocumentIcon";
import XlsPreview from "./XlsPreview";
import { XlsxFile } from "./icons/XlsxFile";

function CampaignDocumentsPreview({ documents = [], svgStyles = {}, isUserGenerate = false }) {
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
            <div key={index} style={{ marginRight: "1rem" }}>
              <div onClick={() => setShowPreview(true)}>
                <div style={{ display: "flex" }}>
                  <XlsxFile />
                </div>
                <p className="campaign-document-title">
                  {isUserGenerate ? document?.type : document?.filename ? t(document?.filename) : t("CAMPAIGN_DOCUMENT_TITLE", { INDEX: index + 1 })}
                </p>
              </div>
              {showPreview && (
                <XlsPreview
                  file={{ url: pdfFiles[document?.id] }}
                  onDownload={() => handleFileDownload(null, pdfFiles[document?.id])}
                  onBack={() => setShowPreview(false)}
                />
              )}
            </div>
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
