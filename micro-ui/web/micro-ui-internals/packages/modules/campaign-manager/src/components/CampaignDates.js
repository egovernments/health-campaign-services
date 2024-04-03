import React, { useState, useEffect } from "react";
import { DatePicker,LabelFieldPair, Header } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { TextInput } from "@egovernments/digit-ui-components";

const CampaignDates = ({onSelect, formData}) => {
  const { t } = useTranslation();
  const ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;
  const today = Digit.Utils.date.getDate(Date.now() + ONE_DAY_IN_MS);
  const [dates, setDates] = useState({ startDate: today, endDate: today });
  const [startDate, setStartDate] = useState(); // Set default start date to today
  const [endDate, setEndDate] = useState(); // Default end date


  function setStart(value) {
    setStartDate(value);
  }

  function setEnd(date) {
    setEndDate(date);
  }
  useEffect(() => {
    setDates({ startDate, endDate });
  }, [startDate, endDate]);

  useEffect(() =>{
    onSelect("campaignDates", dates);
  }, [dates])

  return (
    <React.Fragment>
      <Header>{t(`HCM_CAMPAIGN_DATES_HEADER`)}</Header>
      <p className="dates-description">{t(`HCM_CAMPAIGN_DATES_DESCRIPTION`)}</p>
      <LabelFieldPair style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', alignItems: 'start' }}>
        <div className="campaign-dates">
        <p>{t(`HCM_CAMPAIGN_DATES`)}</p>
        <span className="mandatory-date">*</span>
        </div>
        <div className="date-field-container">
        {/* <DatePicker 
          date={startDate}
          key = {startDate}
          min={Digit.Utils.date.getDate(Date.now() + ONE_DAY_IN_MS)}
          onChange={(date) => setStart(date)}
        /> */}
        <TextInput 
        type="date" 
        value = {startDate}
        placeholder="start-date" 
        min={Digit.Utils.date.getDate(Date.now() + ONE_DAY_IN_MS)}
        onChange={(d) => setStart(d)}/>
        {/* <DatePicker 
         date={endDate}
        //  key = {endDate} 
        // //  min={Digit.Utils.date.getDate(Date.now() + 2 * ONE_DAY_IN_MS)}
        // //  max={Digit.Utils.date.getDate(Date.now() + 10 * ONE_DAY_IN_MS)}
         onChange={(date) => setEnd(date)}
        /> */}
        <TextInput type="date" value = {endDate} placeholder="end-date" onChange={(d) => setEnd(d)}/>
        </div>
      </LabelFieldPair>
    </React.Fragment>
  );
};

export default CampaignDates;
