import { ActionBar, Stepper, Toast } from "@egovernments/digit-ui-components";
import PropTypes from "prop-types";
import React, { useState, useEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@egovernments/digit-ui-react-components";
import { ArrowBack, ArrowForward } from "@egovernments/digit-ui-svg-components";
import { PRIMARY_THEME_COLOR } from "../configs/constants";
import { memo } from "react";

/**
 *
 * @param { config: Object, checkDataCompleteness: boolean, components: Object, childProps: Object, stepNavigationActive: boolean, nextEventAddon: function, setCurrentPageExternally: function, completeNavigation } props
 * @returns
 *
 */
// Main component for creating a microplan
const Navigator = memo((props) => {
  // States
  const [currentPage, setCurrentPage] = useState();
  // const [toast, setToast] = useState();
  const [navigationEvent, setNavigationEvent] = useState();
  const [activeSteps, setActiveSteps] = useState(Digit.SessionStorage.get("microplanHelperData")?.activeSteps || -1);
  /**
   * checkDataCompletion
   * "true": check for data completeness
   * "false": do nothing
   * "valid": data is present
   * "invalid": whole or a part of the data is missing
   * "perform-action": move to the respective step ( had to add this as mutate addons need some buffer time)
   */
  const [checkDataCompletion, setCheckDataCompletion] = useState("false");

  const { t } = useTranslation();

  // Effect to set initial current page when timeline options change
  useEffect(() => {
    if (!props.config || props.config.length === 0) return;
    let response;
    if (props.setCurrentPageExternally) {
      response = props.setCurrentPageExternally({ setCurrentPage, method: "set" });
    }
    if (!response) setCurrentPage(props.config[0]);
  }, [props.config]);

  // Might need it later
  // Effect to handle data completion validation and show toast
  useEffect(() => {
    if (checkDataCompletion === "invalid") {
      if (navigationEvent && navigationEvent.name === "next") {
        props?.setToast({ state: "error", message: t("MICROPLAN_PLEASE_FILL_ALL_THE_FIELDS_AND_RESOLVE_ALL_THE_ERRORS") });
      } else if (navigationEvent && navigationEvent.name === "step" && navigationEvent.step !== undefined) {
        if (navigationEvent.step > currentPage.id)
          props?.setToast({ state: "error", message: t("MICROPLAN_PLEASE_FILL_ALL_THE_FIELDS_AND_RESOLVE_ALL_THE_ERRORS") });
        else onStepClick(navigationEvent.step);
      } else if (navigationEvent && navigationEvent.name === "previousStep") previousStep();
      setCheckDataCompletion("false");
    }
  }, [checkDataCompletion]);

  const checkStatusTillPageToNavigate = (status) => {
    let check = true;
    if (navigationEvent?.step) {
      const navigateTo = props.config?.[navigationEvent?.step]?.name;
      for (const item of props.config) {
        if (item.name === navigateTo) break;
        check = check && status[item.name];
      }
    }
    return check;
  };

  // Effect to handle navigation events and transition between steps
  useEffect(() => {
    // if (checkDataCompletion !== "valid" || navigationEvent === undefined) return;
    if (
      checkDataCompletion === "valid" &&
      ((navigationEvent.step && navigationEvent.step <= activeSteps + 1) || !navigationEvent.step) &&
      (!props?.status || checkStatusTillPageToNavigate(props?.status) || navigationEvent.step === currentPage.id + 1)
    ) {
      if (typeof props.nextEventAddon === "function") {
        if (LoadCustomComponent({ component: props.components[currentPage?.component] }) !== null)
          props.nextEventAddon(currentPage, checkDataCompletion, setCheckDataCompletion);
        else props.nextEventAddon(currentPage, true, setCheckDataCompletion);
      } else {
        setCheckDataCompletion("perform-action");
      }
    }
  }, [navigationEvent, checkDataCompletion, props.nextEventAddon]);

  useEffect(() => {
    handleNavigationEvent(
      checkDataCompletion,
      navigationEvent,
      currentPage,
      setCheckDataCompletion,
      setNavigationEvent,
      onStepClick,
      nextStep,
      previousStep,
      props
    );
  }, [checkDataCompletion, navigationEvent]);

  // Function to navigate to the next step
  const nextStep = useCallback(() => {
    if (!currentPage) return;
    changeCurrentPage(props.config[currentPage?.id + 1]);
    if (currentPage?.id + 1 > props.config.length - 1) return;
    setCurrentPage((previous) => props.config[previous?.id + 1]);
  }, [currentPage]);

  // Function to navigate to the previous step
  const previousStep = useCallback(() => {
    changeCurrentPage(props.config[currentPage?.id - 1]);
    setCurrentPage((previous) => props.config[previous?.id - 1]);
  }, [currentPage]);

  // Function to handle step click and navigate to the selected step
  const onStepClick = useCallback((index) => {
    const newCurrentPage = props.config.find((item) => item.id === index);
    changeCurrentPage(newCurrentPage);
    setCurrentPage(newCurrentPage);
  });

  // Function to handle next button click
  const previousbuttonClickHandler = useCallback(() => {
    if (
      (props.checkDataCompleteness &&
        props?.config[currentPage?.id]?.checkForCompleteness &&
        LoadCustomComponent({ component: props.components[currentPage?.component] }) !== null) ||
      currentPage?.id === props.config[props.config.length - 1].id
    ) {
      setNavigationEvent({ name: "previousStep" });
      setCheckDataCompletion("true");
    } else {
      if (typeof props?.setMicroplanData === "function") {
        props?.setMicroplanData((previous) => ({
          ...previous,
          status: { ...previous?.status, [currentPage?.name]: true },
        }));
      }
      previousStep();
    }
  }, [props.checkDataCompleteness, previousStep, setNavigationEvent]);

  // Function to handle next button click
  const nextbuttonClickHandler = useCallback(() => {
    if (
      props.checkDataCompleteness &&
      props?.config[currentPage?.id]?.checkForCompleteness &&
      LoadCustomComponent({ component: props.components[currentPage?.component] }) !== null
    ) {
      setCheckDataCompletion("true");
      setNavigationEvent({ name: "next" });
    } else {
      if (typeof props?.setMicroplanData === "function") {
        props?.setMicroplanData((previous) => ({
          ...previous,
          status: { ...previous?.status, [currentPage?.name]: true },
        }));
      }
      nextStep();
    }
  }, [props.checkDataCompleteness, nextStep, setNavigationEvent]);

  // Function to handle step click
  const stepClickHandler = useCallback(
    (index) => {
      if (index === currentPage?.id) return;
      if (!props.stepNavigationActive) return;
      if (
        (props.checkDataCompleteness &&
          props?.config[currentPage?.id]?.checkForCompleteness &&
          LoadCustomComponent({ component: props.components[currentPage?.component] }) !== null) ||
        currentPage?.id === props.config[props.config.length - 1].id
      ) {
        setCheckDataCompletion("true");
        setNavigationEvent({ name: "step", step: index });
      } else {
        if (typeof props?.setMicroplanData === "function") {
          props?.setMicroplanData((previous) => ({
            ...previous,
            status: { ...previous?.status, [currentPage?.name]: true },
          }));
        }
        onStepClick(index);
      }
    },
    [props.checkDataCompleteness, props.stepNavigationActive, onStepClick]
  );

  // Function to set current page
  const changeCurrentPage = (newPage) => {
    if (props.setCurrentPageExternally) {
      props.setCurrentPageExternally({ currentPage: newPage, method: "save" });
    }
  };

  const completeNavigation = () => {
    setNavigationEvent({ name: "next" });
    setCheckDataCompletion("true");
  };

  // changing active state
  useEffect(() => {
    if (currentPage?.id > activeSteps) {
      setActiveSteps(currentPage?.id);
      Digit.SessionStorage.set("microplanHelperData", { ...(Digit.SessionStorage.get("microplanHelperData") || {}), activeSteps: currentPage?.id });
    }
  }, [currentPage]);

  return (
    <div className="create-microplan">
      {/* Stepper component */}
      <Stepper
        type="stepper"
        currentStep={currentPage?.id + 1}
        customSteps={props.config.map((item) => t(item.name))}
        direction="horizontal"
        activeSteps={activeSteps >= 0 ? activeSteps + 1 : null}
        onStepClick={stepClickHandler}
      />

      {/* Load custom component based on current page */}
      {props?.components[currentPage?.component] ? (
        LoadCustomComponent({ component: props.components[currentPage?.component] }) !== null ? (
          <LoadCustomComponent
            component={props.components[currentPage?.component]}
            secondaryProps={
              checkDataCompletion
                ? { checkDataCompletion, setCheckDataCompletion, currentPage, pages: props.config, navigationEvent, ...props.childProps }
                : {}
            }
          />
        ) : (
          <div className="navigator-component-not-found">{t("COMMON_DATA_NOT_PRESENT")}</div>
        )
      ) : (
        ""
      )}

      {/* Action bar */}
      <ActionBar className={`${currentPage?.id === 0 ? "custom-action-bar-no-first-button" : "custom-action-bar"} popup-wrap-rest-unfocus`}>
        {/* Back button */}
        {currentPage?.id > 0 && (
          <Button
            type="button"
            className="custom-button custom-button-left-icon"
            label={t("BACK")}
            onButtonClick={previousbuttonClickHandler}
            isSuffix={false}
            variation={"secondary"}
            icon={<ArrowBack className={"icon"} width={"1.5rem"} height={"1.5rem"} fill={PRIMARY_THEME_COLOR} />}
          />
        )}
        {/* Next/Submit button */}
        <Button
          type="button"
          className="custom-button custom-button-right-icon"
          label={currentPage?.id < props.config.length - 1 ? t("NEXT") : t("GENERATE_MICROPLAN")}
          onButtonClick={currentPage?.id < props.config.length - 1 ? nextbuttonClickHandler : completeNavigation}
          variation={"primary"}
          textStyles={{ padding: 0, margin: 0 }}
        >
          <ArrowForward className={"icon"} width={"1.5rem"} height={"1.5rem"} fill={"rgb(255,255,255)"} />
        </Button>
      </ActionBar>
    </div>
  );
});

// Component to load custom component based on current page
const LoadCustomComponent = (props) => {
  if (props && !props.component) return null;
  const secondaryProps = props.secondaryProps;
  return <props.component {...secondaryProps} />;
};
LoadCustomComponent.propTypes = {
  component: PropTypes.elementType.isRequired,
  secondaryProps: PropTypes.object,
};

const handleNavigationEvent = (
  checkDataCompletion,
  navigationEvent,
  currentPage,
  setCheckDataCompletion,
  setNavigationEvent,
  onStepClick,
  nextStep,
  previousStep,
  props
) => {
  if (checkDataCompletion === "perform-action") {
    if (navigationEvent && navigationEvent.name === "next") {
      if (currentPage?.id === props.config.length - 1 && typeof props?.completeNavigation === "function") {
        return props?.completeNavigation();
      }
      nextStep();
    } else if (navigationEvent && navigationEvent.name === "step" && navigationEvent.step !== undefined) onStepClick(navigationEvent.step);
    else if (navigationEvent && navigationEvent.name === "previousStep") previousStep();
    setCheckDataCompletion("false");
    setNavigationEvent(undefined);
  }
};

export default Navigator;
