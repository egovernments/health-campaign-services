import React, { useEffect } from "react";

import PopUp from "../atoms/PopUp";
import HeaderBar from "../atoms/HeaderBar";
import ButtonSelector from "../atoms/ButtonSelector";
import Toast from "../atoms/Toast";

const Modal = ({
  headerBarMain,
  headerBarEnd,
  popupStyles,
  children,
  actionCancelLabel,
  actionCancelOnSubmit,
  actionSaveLabel,
  actionSaveOnSubmit,
  error,
  setError,
  formId,
  isDisabled,
  hideSubmit,
  style={},
  footerLeftButtonstyle={},
  footerRightButtonstyle={},
  footerLeftButtonBody,
  footerRightButtonBody,
  popupModuleMianStyles,
  headerBarMainStyle,
  isOBPSFlow = false,
  popupModuleActionBarStyles={}
}) => {
  /**
   * TODO: It needs to be done from the desgin changes
   */
   const mobileView = Digit.Utils.browser.isMobile() ? true : false;
  useEffect(() => {
    document.body.style.overflowY = 'hidden';
    return () =>{
      document.body.style.overflowY = 'auto';
    }
  }, [])

  return (
    <PopUp>
      <div className="popup-module" style={popupStyles}>
        <HeaderBar main={headerBarMain} end={headerBarEnd} style={headerBarMainStyle ? headerBarMainStyle : {}}/>
        <div className="popup-module-main" style={popupModuleMianStyles ? popupModuleMianStyles : {}}>
          {children}
          <div className="popup-module-action-bar" style={isOBPSFlow?!mobileView?{marginRight:"18px"}:{position:"absolute",bottom:"5%",right:"10%",left:window.location.href.includes("employee")?"0%":"7%"}:popupModuleActionBarStyles}>
            {actionCancelLabel || footerLeftButtonBody ? <ButtonSelector textStyles={{margin:"0px"}} ButtonBody={footerLeftButtonBody} theme="border" label={actionCancelLabel} onSubmit={actionCancelOnSubmit} style={Object.keys(style).length>0?style:footerLeftButtonstyle}/> : null}
            {!hideSubmit ? <ButtonSelector textStyles={{margin:"0px"}} ButtonBody={footerRightButtonBody} label={actionSaveLabel} onSubmit={actionSaveOnSubmit} formId={formId} isDisabled={isDisabled} style={Object.keys(style).length>0?style:footerRightButtonstyle}/> : null}
          </div>
        </div>
      </div>
      {error && <Toast label={error} onClose={() => setError(null)} error />}
    </PopUp>
  );
};

export default Modal;
