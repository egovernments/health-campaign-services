import React, { useState } from "react";

import { Modal, CardLabel, LabelFieldPair, CloseSvg, Close, TextInput } from "@egovernments/digit-ui-react-components";

const AssignTarget = ({ t, onClose, heading, beneficiaryType, totalNo, targetNo, onChange, onCancel, onSubmit, data,isEdit,onSubmitTarget }) => {
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
      headerBarEnd={<CloseBtn onClick={onClose} />}
      actionSaveLabel={t("CORE_COMMON_SUBMIT")}
      formId="modal-action"
      actionCancelLabel={t("CORE_COMMON_CANCEL")}
      actionCancelOnSubmit={onCancel}
      actionSaveOnSubmit={isEdit ? onSubmit : onSubmitTarget}
    >
      <LabelFieldPair>
        <CardLabel className={"card-label-smaller"}> {`${t("WBH_BENEFICIARY_TYPE_LABEL")}`} </CardLabel>
        <TextInput type="text" name="beneficiaryType" value={beneficiaryType} onChange={onChange} />
      </LabelFieldPair>
      <LabelFieldPair>
        <CardLabel className={"card-label-smaller"}> {`${t("WBH_TOTAL_NO_LABEL")}`} </CardLabel>
        <TextInput type="number" name="totalNo" value={totalNo} onChange={onChange} />
      </LabelFieldPair>
      <LabelFieldPair>
        <CardLabel className={"card-label-smaller"}> {`${t("WBH_TARGET_NO_LABEL")}`} </CardLabel>
        <TextInput type="number" name="targetNo" value={targetNo} onChange={onChange} />
      </LabelFieldPair>
    </Modal>
  );
};

export default AssignTarget;
