import { Button, Modal, TextInput, Close, CloseSvg, Card, BreakLine, LabelFieldPair, CardLabel } from "@egovernments/digit-ui-react-components";
import React, { useState } from "react";

const ProjectStaffModal = ({ t, onClose, heading, onCancel, onSubmit, userName, onSearch, searchResult, onChange, isDisabled, showDepartment }) => {
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
      formId="modal-action"
      headerBarMain={<Heading t={t} heading={t(heading)} />}
      headerBarEnd={<CloseBtn onClick={onClose} />}
      actionSaveLabel={t("CORE_COMMON_SUBMIT")}
      actionCancelLabel={t("CORE_COMMON_CANCEL")}
      actionCancelOnSubmit={onClose}
      actionSaveOnSubmit={onSubmit}
      isDisabled={isDisabled}
    >
      <Card style={{ boxShadow: "none" }}>
        <LabelFieldPair>
          <TextInput name={"name"} placeholder={`${t("WBH_SEARCH_BY_NAME")}`} value={userName} onChange={onChange} />
        </LabelFieldPair>
        <Button label={`${t("WBH_ACTION_SEARCH")}`} type="button" onButtonClick={onSearch} />
        <BreakLine />
        <LabelFieldPair>
          <CardLabel>{`${t("WBH_BOUNDARY")}`}</CardLabel>
          <TextInput name={"name"} value={searchResult?.boundary} disabled={true} />
        </LabelFieldPair>
        <LabelFieldPair>
          <CardLabel>{`${t("WBH_BOUNDARY_TYPE")}`}</CardLabel>
          <TextInput name={"name"} value={searchResult?.boundaryType} disabled={true} />
        </LabelFieldPair>
        <LabelFieldPair>
          <CardLabel>{`${t("WBH_DEPARTMENT")}`}</CardLabel>
          <TextInput name={"name"} value={showDepartment} disabled={true} />
        </LabelFieldPair>
      </Card>
    </Modal>
  );
};

export default ProjectStaffModal;
