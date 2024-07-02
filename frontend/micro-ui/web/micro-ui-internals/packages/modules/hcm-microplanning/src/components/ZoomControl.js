import React, { memo, useCallback } from "react";
import { useTranslation } from "react-i18next";

const ZoomControl = memo(({ map, t }) => {
  if (!map) return <div>{t("LOADING_MAP")}</div>;

  const zoomIn = useCallback(() => {
    map.zoomIn();
  }, [map]);

  const zoomOut = useCallback(() => {
    map.zoomOut();
  }, [map]);

  return (
    <div className="zoom-container">
      <div className="zoom-control">
        <button type="button" className="zoom-button zoom-in" onClick={zoomIn} aria-label="Zoom in">
          +
        </button>
        <button type="button" className="zoom-button zoom-out" onClick={zoomOut} aria-label="Zoom out">
          -
        </button>
      </div>
    </div>
  );
});

export default ZoomControl;
