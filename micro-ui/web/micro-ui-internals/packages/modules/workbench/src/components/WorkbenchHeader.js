import {
  BackButton,
  Tutorial,
  useTourState,
  Help,
} from '@egovernments/digit-ui-react-components';
import React, { useEffect, useContext, Fragment } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';

import { TourSteps } from '../utils/TourSteps';


const WorkbenchHeader = () => {
  const { tourState, setTourState } = useTourState();
  // const { tutorial, updateTutorial } = useContext(TutorialContext);
  const { t } = useTranslation();
  //using location.pathname we can update the stepIndex accordingly when help is clicked from any other screen(other than home screen)
  const { pathname } = useLocation();
  
  const startTour = () => {
    setTourState({
      run: TourSteps[pathname]?.length > 0 ? true : false,
      steps: TourSteps[pathname] || [],
      tourActive: TourSteps[pathname]?.length > 0 ? true : false,
    });
  };

  return (
    <>
      <Tutorial tutorial={tourState} updateTutorial={setTourState} />
      <div className="wbh-header">
        <Help startTour={startTour} />
      </div>
    </>
  );
};

export default WorkbenchHeader;
