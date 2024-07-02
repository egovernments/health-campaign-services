import { AutoRenew, Close, FileDownload } from "@egovernments/digit-ui-svg-components";
import React, { useCallback } from "react";
import PropTypes from "prop-types";

export const ButtonType1 = (props) => {
  return (
    <div className="button-type-1" role="button" aria-label={props.text} tabIndex={0}>
      <p>{props.text}</p>
    </div>
  );
};

ButtonType1.propTypes = {
  text: PropTypes.string.isRequired,
};

export const ButtonType2 = (props) => {
  return (
    <div className="button-type-2" role="button" tabIndex={0} aria-label={props.text}>
      {props.showDownloadIcon && (
        <div className="icon">
          <FileDownload fill="white" height="24" width="24" />
        </div>
      )}
      <p>{props.text}</p>
    </div>
  );
};

ButtonType2.propTypes = {
  text: PropTypes.string.isRequired,
  showDownloadIcon: PropTypes.bool,
};

export const ModalHeading = (props) => {
  return (
    <p className={`modal-header ${props.className ? props.className : ""}`} style={props.style}>
      {props.label}
    </p>
  );
};

ModalHeading.propTypes = {
  label: PropTypes.string.isRequired,
  className: PropTypes.string,
  style: PropTypes.object,
};

export const CloseButton = ({ clickHandler, style = {} }) => {
  return (
    <button type="button" aria-label="Close" className="microplan-close-button" onClick={clickHandler} style={style}>
      {" "}
      <Close width={"1.5rem"} height={"1.5rem"} fill={"#000000"} />
    </button>
  );
};

CloseButton.propTypes = {
  clickHandler: PropTypes.func.isRequired,
  style: PropTypes.object,
};
