import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, Button, TextInput, SVG, LabelFieldPair, Close, Toast } from "@egovernments/digit-ui-react-components";

const RowDetails = ({ onSelect, formData, props }) => {
  const { t } = useTranslation();
  const [showToast, setShowToast] = useState(false);
  const [cardDetails, setCardDetails] = useState([{ startRow: "", endRow: "" }]);

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(false);
    }, 5000);
  };

  const handleCreateNewRowDetails = () => {
    setCardDetails((prevDetails) => [...prevDetails, { startRow: "", endRow: "" }]);
  };

  useEffect(() => {
    onSelect("rowDetails", cardDetails);
  }, [cardDetails]);

  const handleDeleteRowDetails = (index) => {
    if (cardDetails.length > 1) {
      setCardDetails((prevDetails) => {
        const updatedDetails = [...prevDetails];
        updatedDetails.splice(index, 1);
        return updatedDetails;
      });
    }
  };

  const handleStartNumberChange = (index, value) => {
    setCardDetails((prevDetails) => {
      const updatedDetails = [...prevDetails];
      updatedDetails[index].startRow = Number(value);
      return updatedDetails;
    });
  };

  const handleEndNumberChange = (index, value) => {
    setCardDetails((prevDetails) => {
      const updatedDetails = [...prevDetails];
      const endNew = Number(value);
      if (endNew >= updatedDetails[index].startRow) {
        updatedDetails[index].endRow = endNew;
      } else {
        setShowToast({
          label: t('HCM_END_ROW_GREATER_THAN_START_ROW'),
          isWarning: true,
        });
        closeToast();
        updatedDetails[index].endRow = endNew;
        
      }
      // updatedDetails[index].endRow = Number(value);
      return updatedDetails;
    });
  };

  return (
    <React.Fragment>
      {cardDetails.map((details, index) => (
        <LabelFieldPair card key={index}>
          <Card className="card-details">
            {cardDetails.length > 1 && (
              <div className="CloseButton" onClick={() => handleDeleteRowDetails(index)}>
                <Close />
              </div>
            )}
            <div className="startNumber">
              <span className="start">{`${t("HCM_START_ROW")}`}</span>  
              <TextInput name={"startRow"} value={details.startRow} onChange={(e) => handleStartNumberChange(index, e.target.value)} />
            </div>
            <div className="endNumber">
              <span className="end">{`${t("HCM_END_ROW")}`}</span>
              <TextInput name={"endRow"} value={details.endRow} onChange={(e) => handleEndNumberChange(index, e.target.value)} />
            </div>
          </Card>
        </LabelFieldPair>
      ))}
      <LabelFieldPair>
        <Button
          variation="secondary"
          label={`${t("ADD_ROW_DETAILS")}`}
          type="button"
          className="workbench-add-row-detail-btn"
          onButtonClick={handleCreateNewRowDetails}
          style={{ fontSize: "1rem" }}
        />
      </LabelFieldPair>
      {showToast && <Toast warning={showToast.isWarning} label={showToast.label} isDleteBtn={"true"} onClose={() => setShowToast(false)} style={{ bottom: "8%" }} />}
    </React.Fragment>
    
  );
};

export default RowDetails;
