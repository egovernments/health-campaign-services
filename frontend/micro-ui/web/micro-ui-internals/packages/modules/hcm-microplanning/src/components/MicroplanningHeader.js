import { Help, Tutorial, useTourState } from "@egovernments/digit-ui-react-components";
import React, { Fragment } from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";
import { useMyContext } from "../utils/context";
import { PRIMARY_THEME_COLOR } from "../configs/constants";

const MicroplanningHeader = () => {
  const { tourState, setTourState } = useTourState();
  const { state } = useMyContext();
  const { t } = useTranslation();
  //using location.pathname we can update the stepIndex accordingly when help is clicked from any other screen(other than home screen)
  const { pathname } = useLocation();

  const startTour = () => {
    if (state?.tourStateData) setTourState(state.tourStateData);
  };

  return (
    <>
      <Tutorial tutorial={tourState} updateTutorial={setTourState} theme={{ zIndex: 500, primaryColor: PRIMARY_THEME_COLOR }} />
      <div className="wbh-header">
        <Help startTour={startTour} labelClassName="help-label" />
      </div>
    </>
  );
};

export default MicroplanningHeader;
