import React, { memo, useCallback } from "react";
import { useTranslation } from "react-i18next";

const ZoomControl = memo(({ map, t }) => {
  if (!map) return <div>{t("ERROR_MAP_OBJECT_MISSING")}</div>;

  const zoomIn = useCallback(() => {
    map.zoomIn();
  }, [map]);

  const zoomOut = useCallback(() => {
    map.zoomOut();
  }, [map]);

  return (
    <div className="zoom-container">
      <div className="zoom-control">
        <button className="zoom-button zoom-in" onClick={zoomIn} aria-label="Zoom in">
          +
        </button>
        <button className="zoom-button zoom-out" onClick={zoomOut} aria-label="Zoom out">
          -
        </button>
      </div>
    </div>
  );
});

export default ZoomControl;
