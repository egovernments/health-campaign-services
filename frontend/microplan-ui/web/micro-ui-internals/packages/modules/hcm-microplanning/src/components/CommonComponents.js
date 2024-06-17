import { AutoRenew, Close, FileDownload } from "@egovernments/digit-ui-svg-components";
import React, { useCallback } from "react";

export const ButtonType1 = (props) => {
  return (
    <div className="button-type-1">
      <p>{props.text}</p>
    </div>
  );
};

export const ButtonType2 = (props) => {
  return (
    <div className="button-type-2">
      {props.showDownloadIcon && (
        <div className="icon">
          <FileDownload fill={"white"} height={"24"} width={"24"} />
        </div>
      )}
      <p>{props.text}</p>
    </div>
  );
};

export const ModalHeading = (props) => {
  return (
    <p className={`modal-header ${props.className ? props.className : ""}`} style={props.style}>
      {props.label}
    </p>
  );
};

export const CloseButton = ({ clickHandler, style = {} }) => {
  return (
    <button type="button" className="microplan-close-button" onClick={clickHandler} style={style}>
      {" "}
      <Close width={"1.5rem"} height={"1.5rem"} fill={"#000000"} />
    </button>
  );
};
