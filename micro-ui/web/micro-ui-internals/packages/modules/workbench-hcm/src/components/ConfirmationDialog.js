import { Card, Modal, CloseSvg, Close } from "@egovernments/digit-ui-react-components";
import React, { useState } from "react";

const ConfirmationDialog = ({ t, onSubmit, closeModal, heading }) => {
  const CloseBtn = (props) => {
    return (
      <div onClick={props?.onClick} style={props?.isMobileView ? { padding: 5 } : null}>
        {props?.isMobileView ? (
          <CloseSvg />
        ) : (
          <div className={"icon-bg-secondary"} style={{ backgroundColor: "#FFFFFF" }}>
            <Close />
          </div>
        )}
      </div>
    );
  };
  const Heading = (props) => {
    return <h1 className="heading-m">{props.heading}</h1>;
  };
  return (
    <Modal
      headerBarMain={<Heading t={t} heading={t(heading)} />}
      headerBarEnd={<CloseBtn onClick={closeModal} />}
      actionCancelLabel={t("CS_COMMON_CANCEL")}
      actionCancelOnSubmit={closeModal}
      actionSaveLabel={t("WBH_EVENT_DELETE")}
      actionSaveOnSubmit={(confirmed) => onSubmit(confirmed)}
    >
      <Card style={{ boxShadow: "none" }}>{t("WBH_DELETE_TEXT")}</Card>
    </Modal>
  );
};
export default ConfirmationDialog;
