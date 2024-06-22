import { Button, DownloadIcon, SVG } from "@egovernments/digit-ui-react-components";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { PRIMARY_THEME_COLOR } from "../configs/constants";

export const JsonPreviewInExcelForm = (props) => {
  const { t } = useTranslation();
  const sheetsData = props?.sheetsData;
  const [currentSheetName, setCurrentSheetName] = useState(Object.keys(sheetsData).length > 0 ? Object.keys(sheetsData)[0] : undefined);
  return (
    <div className="preview-data ">
      <div
        className="operational-buttons"
        style={{
          ...props?.btnStyle,
        }}
      >
        <Button
          label={t("BUTTON_BACK")}
          variation="secondary"
          icon={<SVG.ArrowBack height={"2rem"} width={"2rem"} fill={PRIMARY_THEME_COLOR} />}
          type="button"
          onButtonClick={() => props?.onBack()}
        />
        <Button
          label={t("BUTTON_DOWNLOAD")}
          variation="secondary"
          icon={<DownloadIcon height={"1.25rem"} width={"1.25rem"} fill={PRIMARY_THEME_COLOR} />}
          type="button"
          onButtonClick={() => props?.onDownload()}
        />
      </div>
      <div className="excel-wrapper">
        {props?.errorLocationObject?.[currentSheetName] && <p className="error-user-directions">{t("USER_DIRECTIONS_FOR_ERROR_MESSAGE")}</p>}
        {/* {Object.entries(sheetsData).map(([sheetName, sheetData], index) => ( */}
        <div
          key={sheetsData?.[currentSheetName]}
          className="sheet-wrapper"
          style={props?.errorLocationObject?.[currentSheetName] ? { height: "72.5vh" } : {}}
        >
          <table className="excel-table">
            <thead>
              <tr>
                {sheetsData?.[currentSheetName]?.[0]
                  ?.filter((header) => header)
                  .map((header) => (
                    <th key={header}>{t(header)}</th>
                  ))}
              </tr>
            </thead>
            <tbody>
              {sheetsData?.[currentSheetName]?.slice(1).map((rowData, rowIndex) => (
                <tr key={rowIndex}>
                  {Object.values(sheetsData?.[currentSheetName]?.[0])?.map((_, cellIndex) => {
                    const headerName = sheetsData?.[currentSheetName]?.[0]?.[cellIndex];
                    const error = headerName ? props?.errorLocationObject?.[currentSheetName]?.[rowIndex]?.[headerName] : undefined;
                    let convertedError;
                    if (typeof error?.[0] === "object") {
                      let { error: actualError, ...otherProperties } = error[0];
                      convertedError = t(actualError, otherProperties?.values);
                    } else {
                      convertedError = t(error);
                    }
                    const rowHasError =
                      typeof props?.errorLocationObject?.[currentSheetName]?.[rowIndex] === "object"
                        ? Object.keys(props?.errorLocationObject?.[currentSheetName]?.[rowIndex]).length !== 0
                        : undefined;
                    return (
                      <td
                        key={cellIndex}
                        style={{
                          ...(rowData[cellIndex] || rowData[cellIndex] === 0
                            ? !isNaN(rowData[cellIndex]) && isFinite(rowData[cellIndex])
                              ? { textAlign: "end" }
                              : {}
                            : {}),
                          ...(convertedError ? { backgroundColor: "rgb(250,148,148)" } : {}),
                        }}
                        title={convertedError ? convertedError : undefined}
                      >
                        {cellIndex === 0 && rowHasError && <div className="edited-row-marker" />}

                        {rowData[cellIndex] || rowData[cellIndex] === 0 ? rowData[cellIndex] : ""}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="excel-tab-list">
          {Object.entries(sheetsData).map(([sheetName, sheetData], index) => (
            <button
              type="button"
              key={sheetName}
              className={`tab ${sheetName === currentSheetName ? "active" : ""}`}
              onClick={() => {
                setCurrentSheetName(sheetName);
              }}
              style={{
                ...(props?.errorLocationObject?.[sheetName] ? { backgroundColor: "rgb(250,148,148)", color: "black" } : {}),
              }}
            >
              {sheetName}
            </button>
          ))}
        </div>
        {/* ))} */}
      </div>
    </div>
  );
};
