import React, { useState } from "react";

import { DatePicker, Modal, CardLabel, LabelFieldPair, CloseSvg, Close } from "@egovernments/digit-ui-react-components";

const AssignCampaign = ({ t, onClose, heading, startDate, endDate, onChange, onCancel, onSubmit }) => {
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
      actionSaveOnSubmit={onSubmit}
    >
      <LabelFieldPair>
        <CardLabel className={"card-label-smaller"}> {`${t("WBH_CAMPAIGN_FROM_DATE_LABEL")}`} </CardLabel>

        <DatePicker type="date" name="startDate" date={startDate} onChange={(date) => onChange(date, "startDate")} />
      </LabelFieldPair>

      <LabelFieldPair>
        <CardLabel className={"card-label-smaller"}> {`${t("WBH_CAMPAIGN_TO_DATE_LABEL")}`} </CardLabel>
        <div className="field">
          <DatePicker type="date" name="endDate" date={endDate} onChange={(date) => onChange(date, "endDate")} />
        </div>
      </LabelFieldPair>
    </Modal>
  );
};

export default AssignCampaign;
