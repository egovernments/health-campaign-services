import React, { useState, useEffect, useCallback, Fragment, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Info, Trash } from "@egovernments/digit-ui-svg-components";
import { ModalHeading } from "./CommonComponents";
import { Button, Modal } from "@egovernments/digit-ui-react-components";
import { Dropdown, InfoCard, Toast } from "@egovernments/digit-ui-components";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import { v4 as uuidv4 } from "uuid";
import { PlusWithSurroundingCircle } from "../icons/Svg";
import { PRIMARY_THEME_COLOR } from "../configs/constants";

const page = "ruleEngine";

const RuleEngine = ({
  campaignType = Digit.SessionStorage.get("microplanHelperData")?.campaignData?.projectType,
  microplanData,
  setMicroplanData,
  checkDataCompletion,
  setCheckDataCompletion,
  currentPage,
  pages,
  setToast,
}) => {
  const { t } = useTranslation();

  // States
  const [editable, setEditable] = useState(true);
  const [modal, setModalState] = useState("none");
  const [rules, setRules] = useState([]);
  const [hypothesisAssumptionsList, setHypothesisAssumptionsList] = useState([]);
  const [itemForDeletion, setItemForDeletion] = useState();
  const [exampleOption, setExampleOption] = useState("");
  const [inputs, setInputs] = useState([]);
  const [outputs, setOutputs] = useState([]);
  const [operators, setOperators] = useState([]);
  const [validationSchemas, setValidationSchemas] = useState([]);
  const [autofillData, setAutoFillData] = useState([]);
  const { state, dispatch } = useMyContext();
  const [originalRuleOutputCount, setOriginalRuleOutputCount] = useState(0);
  // const [toast, setToast] = useState();
  const [pureInputList, setPureInputList] = useState([]);
  // Set TourSteps
  useEffect(() => {
    const tourData = tourSteps(t)?.[page] || {};
    if (state?.tourStateData?.name === page) return;
    dispatch({
      type: "SETINITDATA",
      state: { tourStateData: tourData },
    });
  }, []);

  const setModal = (modalString) => {
    const elements = document.querySelectorAll(".popup-wrap-rest-unfocus");
    elements.forEach((element) => {
      element.classList.toggle("popup-wrap-rest-unfocus-active");
    });
    setModalState(modalString);
  };

  // UseEffect to extract data on first render
  useEffect(() => {
    if (pages) {
      const previouspage = pages[currentPage?.id - 1];
      if (previouspage?.checkForCompleteness && !microplanData?.status?.[previouspage?.name]) setEditable(false);
      else setEditable(true);
    }
  }, []);

  // UseEffect for checking completeness of data before moveing to next section
  useEffect(() => {
    if (!rules || checkDataCompletion !== "true" || !setCheckDataCompletion) return;
    // uncomment to activate data change save check
    // if (!microplanData?.ruleEngine || !_.isEqual(rules, microplanData.ruleEngine)) setModal("data-change-check");
    // else
    updateData(true);
  }, [checkDataCompletion]);

  // UseEffect to store current data
  useEffect(() => {
    if (!rules || !setMicroplanData) return;
    setMicroplanData((previous) => ({ ...previous, ruleEngine: rules }));
  }, [rules]);

  // UseEffect to add a event listener for keyboard
  useEffect(() => {
    window.addEventListener("keydown", handleKeyPress);

    return () => window.removeEventListener("keydown", handleKeyPress);
  }, [modal]);

  const handleKeyPress = (event) => {
    // if (modal !== "upload-guidelines") return;
    if (["x", "Escape"].includes(event.key)) {
      // Perform the desired action when "x" or "esc" is pressed
      // if (modal === "upload-guidelines")
      setCheckDataCompletion("false");
      setModal("none");
    }
  };

  // check if data has changed or not
  const updateData = useCallback(
    (check) => {
      if (!rules || !setMicroplanData) return;
      if (check) {
        setMicroplanData((previous) => ({ ...previous, ruleEngine: rules }));
        const activeRules = rules.filter((item) => item.active);
        const isValid = activeRules.every((item) => Object.values(item).every((data) => data !== "")) && activeRules.length !== 0;
        if (isValid) setCheckDataCompletion("valid");
        else setCheckDataCompletion("invalid");
      } else {
        let isValid = microplanData?.ruleEngine?.every((item) => Object.values(item).every((data) => data !== ""));
        isValid = isValid && rules.length !== 0;
        if (isValid) setCheckDataCompletion("valid");
        else setCheckDataCompletion("invalid");
      }
    },
    [rules, setMicroplanData, microplanData, setCheckDataCompletion]
  );

  const cancelUpdateData = useCallback(() => {
    setCheckDataCompletion(false);
    setModal("none");
  }, [setCheckDataCompletion, setModal]);

  // useEffect to initialise the data from MDMS
  useEffect(() => {
    if (!state) return;
    const schemas = state?.Schemas;
    const hypothesisAssumptions = [];
    microplanData?.hypothesis?.filter((item) => item.active).forEach((item) => (item.key !== "" ? hypothesisAssumptions.push(item.key) : null));
    const ruleConfigureOutput = state?.RuleConfigureOutput;
    const UIConfiguration = state?.UIConfiguration;
    const ruleConfigureInputs = getRuleConfigInputsFromSchema(campaignType, microplanData, schemas) || [];
    let AutoFilledRuleConfigurationsList = state?.AutoFilledRuleConfigurations;
    AutoFilledRuleConfigurationsList = AutoFilledRuleConfigurationsList.find((item) => item.campaignType === campaignType)?.data;
    microplanData?.ruleEngine?.forEach((item) => {
      if (Object.values(item).every((e) => e !== "")) ruleConfigureInputs.push(item?.output);
    });
    if (schemas) setValidationSchemas(schemas);

    let temp;
    setHypothesisAssumptionsList(hypothesisAssumptions);
    let outputs;
    if (ruleConfigureOutput) temp = ruleConfigureOutput?.find((item) => item.campaignType === campaignType);
    if (temp?.data) {
      let data = temp.data;
      setOriginalRuleOutputCount(data.length);
      microplanData?.ruleEngine?.forEach((item) => {
        if (item.active) {
          const filteredData = data.filter((e) => e !== item?.output);
          data = filteredData;
        }
      });
      outputs = data;
      setOutputs(data);
    }

    if (ruleConfigureInputs) setInputs(ruleConfigureInputs);
    let operator;
    if (UIConfiguration) temp = UIConfiguration.find((item) => item.name === "ruleConfigure");
    if (temp?.ruleConfigureOperators) {
      temp = temp.ruleConfigureOperators.map((item) => item.name);
      operator = temp;
      setOperators(temp);
    }
    // if (AutoFilledRuleConfigurationsList) setAutoFillData(AutoFilledRuleConfigurationsList);
    // Pure inputs - output not there
    const pureInputs = getRuleConfigInputsFromSchema(campaignType, microplanData, schemas);
    setPureInputList(pureInputs);

    const ssnRuleOutputs = microplanData?.ruleEngine?.reduce((acc, item) => {
      if (item?.active && item?.output) acc.push(item?.output);
      return acc;
    }, []);
    const tempOutput = [...outputs, ...(ssnRuleOutputs ? ssnRuleOutputs : [])];
    setExampleOption({
      output: tempOutput.length ? tempOutput[0] : "",
      input: pureInputs.length ? pureInputs[0] : "",
      operator: operator.length ? operator[0] : "",
      assumptionValue: hypothesisAssumptions.length ? hypothesisAssumptions[0] : "",
    });

    let filteredRules = [];
    let response;
    if (microplanData?.ruleEngine && microplanData?.hypothesis) {
      const hypothesisAssumptions = microplanData?.hypothesis?.filter((item) => item.active && item.key !== "").map((item) => item.key) || [];
      if (hypothesisAssumptions.length !== 0) {
        setHypothesisAssumptionsList(hypothesisAssumptions);
        response = filterRulesAsPerConstrains(
          microplanData.ruleEngine,
          [],
          hypothesisAssumptions,
          tempOutput,
          operator,
          pureInputs,
          setInputs,
          setOutputs,
          false
        );
        filteredRules = response?.rules;

        // setRuleEngineDataFromSsn(microplanData.ruleEngine, hypothesisAssumptions, setRules);
      }
    }
    if (response?.rulesDeleted)
      setToast({
        state: "warning",
        message: t("WARNING_RULES_DELETED_DUE_TO_PRIOR_SECTION_DATA_CHANGES"),
      });
    if (!AutoFilledRuleConfigurationsList || !outputs || !hypothesisAssumptions || !schemas) return;

    response = filterRulesAsPerConstrains(
      AutoFilledRuleConfigurationsList,
      filteredRules,
      hypothesisAssumptions,
      outputs,
      operator,
      pureInputs,
      setInputs,
      setOutputs,
      true
    );

    if (response?.rules) setRules(response?.rules);
  }, []);

  const closeModal = useCallback(() => {
    setModal("none");
  }, [setModal]);

  // Function to Delete an assumption
  const deleteAssumptionHandlerCallback = useCallback(() => {
    deleteAssumptionHandler(itemForDeletion, setItemForDeletion, setRules, setOutputs, setInputs, pureInputList);
    closeModal();
  }, [itemForDeletion, deleteAssumptionHandler, setItemForDeletion, setRules, setOutputs, setInputs, closeModal, pureInputList]);

  const sectionClass = `jk-header-btn-wrapper rule-engine-section ${editable ? "" : "non-editable-component"} popup-wrap-rest-unfocus`;
  return (
    <>
      <div className={sectionClass}>
        <div className="rule-engine-body">
          <div className="rule-engine-help" style={{ position: "absolute", top: "20rem", left: "40%", zIndex: "-100" }} />
          {/* NonInterractable Section */}
          <NonInterractableSection t={t} />
          {/* Interractable Section that includes the example as well as the rules */}
          <InterractableSection
            rules={rules}
            setRules={setRules}
            hypothesisAssumptionsList={hypothesisAssumptionsList}
            setHypothesisAssumptionsList={setHypothesisAssumptionsList}
            setModal={setModal}
            setItemForDeletion={setItemForDeletion}
            exampleOption={exampleOption}
            inputs={inputs}
            setInputs={setInputs}
            outputs={outputs}
            setOutputs={setOutputs}
            operators={operators}
            setOperators={setOperators}
            pureInputList={pureInputList}
            t={t}
          />
          <div className="add-button-help" />

          <Button
            variation={"secondary"}
            icon={<PlusWithSurroundingCircle fill={PRIMARY_THEME_COLOR} width="1.05rem" height="1.05rem" style={{ margin: 0 }} />}
            className="add-button"
            onButtonClick={() => addRulesHandler(setRules)}
            label={t("ADD_ROW")}
            isDisabled={rules?.filter((item) => item.active)?.length === originalRuleOutputCount}
          />
        </div>
      </div>
      {/* delete conformation */}
      <div className="popup-wrap-focus">
        {modal === "delete-conformation" && (
          <Modal
            popupStyles={{ borderRadius: "0.25rem", width: "31.188rem" }}
            popupModuleActionBarStyles={{
              display: "flex",
              flex: 1,
              justifyContent: "flex-start",
              width: "100%",
              padding: "1rem",
            }}
            popupModuleMianStyles={{ padding: 0, margin: 0 }}
            style={{
              flex: 1,
              backgroundColor: "white",
              height: "2.5rem",
              border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
            }}
            headerBarMainStyle={{ padding: 0, margin: 0 }}
            headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_DELETE_FILE_CONFIRMATION")} />}
            actionCancelLabel={t("YES")}
            actionCancelOnSubmit={deleteAssumptionHandlerCallback}
            actionSaveLabel={t("NO")}
            actionSaveOnSubmit={closeModal}
          >
            <div className="modal-body">
              <p className="modal-main-body-p">{t("RULE_ENGINE_INSTRUCTIONS_DELETE_ENTRY_CONFIRMATION")}</p>
            </div>
          </Modal>
        )}
      </div>
    </>
  );
};

// Function to add a new assumption
const addRulesHandler = (setRules) => {
  const uuid = uuidv4();
  setRules((previous) => [
    ...previous,
    {
      id: uuid,
      // previous.length ? previous[previous.length - 1].id + 1 : 0,
      output: "",
      input: "",
      operator: "",
      assumptionValue: "",
      active: true,
    },
  ]);
};

// Defination for NonInterractable Section
const NonInterractableSection = React.memo(({ t }) => {
  return (
    <div>
      <h2 className="heading">{t("HEADING_RULE_ENGINE")}</h2>
      <p className="instruction">{t("INSTRUCTION_RULE_ENGINE")}</p>
    </div>
  );
});

// Defination for NonInterractable Section
const InterractableSection = React.memo(
  ({
    rules,
    setRules,
    hypothesisAssumptionsList,
    setHypothesisAssumptionsList,
    setModal,
    setItemForDeletion,
    exampleOption,
    inputs,
    outputs,
    operators,
    setInputs,
    setOutputs,
    setOperators,
    pureInputList,
    t,
  }) => {
    // References to the items in the list
    const itemRefs = useRef([]);
    // State to keep track of the currently expanded item index
    const [expandedIndex, setExpandedIndex] = useState(null);
    // Reference to the scroll container
    const scrollContainerRef = useRef(null);
    // State to track the render cycle count
    const [renderCycle, setRenderCycle] = useState(0);

    // Effect to reset the render cycle count whenever the expandedIndex changes
    useEffect(() => {
      if (expandedIndex !== null) {
        setRenderCycle(0);
      }
    }, [expandedIndex]);

    // Effect to handle scrolling to the expanded item after the DOM has updated
    useEffect(() => {
      if (renderCycle < 3) {
        // Increment render cycle count to ensure multiple render checks
        setRenderCycle((prev) => prev + 1);
      } else if (expandedIndex !== null && itemRefs.current[expandedIndex]) {
        try {
          const parentElement = itemRefs.current[expandedIndex];
          const childElement = itemRefs.current[expandedIndex].children[1];

          if (parentElement) {
            const scrollContainer = scrollContainerRef.current;
            const parentRect = parentElement.getBoundingClientRect();
            const containerRect = scrollContainer.getBoundingClientRect();

            // Calculate the offset from the top of the container
            const offset = parentRect.top - containerRect.top;

            // Scroll the container to the target position
            scrollContainer.scrollTo({
              top: scrollContainer.scrollTop + offset - 100,
              behavior: "smooth",
            });
          }

          if (childElement) {
            // Focus the child element if it exists
            childElement.focus();
          }
        } catch (error) {
          console.error("Error scrolling to element:", error);
        }
      }
    }, [renderCycle, expandedIndex]);

    // Effect to observe DOM changes in the expanded item and trigger render cycle
    useEffect(() => {
      if (expandedIndex !== null) {
        const observer = new MutationObserver(() => {
          setRenderCycle((prev) => prev + 1);
        });

        if (itemRefs.current[expandedIndex]) {
          observer.observe(itemRefs.current[expandedIndex], { childList: true, subtree: true });
        }

        return () => observer.disconnect();
      }
    }, [expandedIndex]);

    // Function to toggle the expanded state of an item
    const toggleExpand = (index) => {
      setExpandedIndex(index === expandedIndex ? null : index);
    };

    // Handler for deleting an assumption on conformation
    const deleteHandler = useCallback(
      (item) => {
        setModal("delete-conformation");
        setItemForDeletion(item);
      },
      [setModal, setItemForDeletion]
    );

    return (
      <div className="user-input-section" ref={scrollContainerRef}>
        <Example exampleOption={exampleOption} t={t} />
        <div className="interactable-section">
          <div className="headerbar">
            <div className="value-input-key">
              <p className="heading">{t("VALUE")}</p>
            </div>
            <div className="equal-to-icon invisible">=</div>
            <div className="value-input-key">
              <p className="heading">{t("RULE_ENGINE_INPUT")}</p>
            </div>
            <div className="operator">
              <p className="heading">{t("RULE_ENGINE_OPERATOR")}</p>
            </div>
            <div className="value-input-key">
              <p className="heading">{t("KEY")}</p>
            </div>
            <div className="invisible">
              <button className="delete-button invisible" onClick={() => deleteHandler(item)} aria-label={t("DELETE")} role="button" type="button">
                <div>{Trash && <Trash width={"0.8rem"} height={"1rem"} fill={PRIMARY_THEME_COLOR} />}</div>
                <p>{t("DELETE")}</p>
              </button>
            </div>
          </div>
          {rules
            .filter((item) => item.active)
            .map((item, index) => (
              <div
                key={index}
                className={`${index === 0 ? "select-and-input-wrapper-first" : "select-and-input-wrapper"}`}
                ref={(el) => {
                  itemRefs.current[index] = el;
                }}
                onClick={() => toggleExpand(index)}
              >
                <div key={item.id} className="value-input-key">
                  <Select
                    key={item.id}
                    item={item}
                    rules={rules}
                    setRules={setRules}
                    options={outputs}
                    setOptions={setOutputs}
                    toChange={"output"}
                    unique={true}
                    setInputs={setInputs}
                    pureInputList={pureInputList}
                    t={t}
                  />
                </div>

                <div className="equal-to-icon">=</div>

                <div className="value-input-key input">
                  <Select
                    key={item.id}
                    item={item}
                    rules={rules}
                    setRules={setRules}
                    options={inputs}
                    setOptions={setInputs}
                    toChange={"input"}
                    unique={false}
                    setInputs={setInputs}
                    outputs={outputs}
                    pureInputList={pureInputList}
                    t={t}
                  />
                </div>
                <div className="operator">
                  <Select
                    key={item.id}
                    item={item}
                    rules={rules}
                    setRules={setRules}
                    options={operators}
                    setOptions={setOperators}
                    toChange={"operator"}
                    unique={false}
                    setInputs={setInputs}
                    pureInputList={pureInputList}
                    t={t}
                  />
                </div>
                <div className="value-input-key">
                  <Select
                    key={item.id}
                    item={item}
                    rules={rules}
                    setRules={setRules}
                    options={hypothesisAssumptionsList}
                    setOptions={setHypothesisAssumptionsList}
                    toChange={"assumptionValue"}
                    unique={false}
                    setInputs={setInputs}
                    pureInputList={pureInputList}
                    t={t}
                  />
                </div>
                <div>
                  <button
                    className="delete-button"
                    onClick={() => deleteHandler(item)}
                    onKeyDown={(e) => e.key === "Enter" && deleteHandler(item)}
                    aria-label={t("DELETE")}
                    role="button"
                    type="button"
                  >
                    <div>{Trash && <Trash width={"0.8rem"} height={"1rem"} fill={PRIMARY_THEME_COLOR} />}</div>
                    <p>{t("DELETE")}</p>
                  </button>
                </div>
              </div>
            ))}
        </div>
      </div>
    );
  }
);

const Example = ({ exampleOption, t }) => {
  return (
    <div className="example-wrapper">
      <div className="example">
        <p className="heading">{t("EXAMPLE")}</p>
        <div className="example-body">
          <div className="value-input-key value">
            <p className="heading">{t("VALUE")}</p>
            <Dropdown
              variant="select-dropdown"
              t={t}
              isMandatory={false}
              option={[]}
              selected={null}
              optionKey="code"
              placeholder={t(exampleOption?.output ? exampleOption?.output : "SELECT_OPTION")}
              showToolTip={true}
            />
            <p className="heading">{t("RULE_ENGINE_VALUE_HELP_TEXT")}</p>
          </div>

          <div className="equal-to-icon">
            <p className="heading invisible">{"="}</p>

            <div className="equal-to-icon">=</div>
            <p className="heading invisible">{"="}</p>
          </div>

          <div className="value-input-key">
            <p className="heading">{t("RULE_ENGINE_INPUT")}</p>
            <Dropdown
              variant="select-dropdown"
              t={t}
              isMandatory={false}
              option={[]}
              selected={null}
              optionKey="code"
              placeholder={t(exampleOption?.input ? exampleOption?.input : "SELECT_OPTION")}
              showToolTip={true}
            />
            <p className="heading">{t("RULE_ENGINE_INPUT_HELP_TEXT")}</p>
          </div>
          <div className="operator">
            <p className="heading">{t("RULE_ENGINE_OPERATOR")}</p>
            <Dropdown
              variant="select-dropdown"
              t={t}
              isMandatory={false}
              option={[]}
              selected={null}
              optionKey="code"
              placeholder={t(exampleOption?.operator ? exampleOption?.operator : "SELECT_OPTION")}
              showToolTip={true}
            />
            <p className="heading">{t("RULE_ENGINE_OPERATOR_HELP_TEXT")}</p>
          </div>
          <div className="value-input-key">
            <p className="heading">{t("KEY")}</p>
            <Dropdown
              variant="select-dropdown"
              t={t}
              isMandatory={false}
              option={[]}
              selected={null}
              optionKey="code"
              placeholder={t(exampleOption?.assumptionValue ? exampleOption?.assumptionValue : "SELECT_OPTION")}
              showToolTip={true}
            />
            <p className="heading">{t("RULE_ENGINE_KEY_HELP_TEXT")}</p>
          </div>
        </div>
      </div>
      <div>
        <button className="delete-button invisible" aria-label={t("DELETE")} role="button" type="button">
          <div>{Trash && <Trash width={"0.8rem"} height={"1rem"} fill={PRIMARY_THEME_COLOR} />}</div>
          <p>{t("DELETE")}</p>
        </button>
      </div>
    </div>
  );
};

const deleteAssumptionHandler = (item, setItemForDeletion, setRules, setOutputs, setInputs, pureInputList) => {
  try {
    const outputToRemove = [];
    setRules((previous) => {
      if (!previous.length) return [];
      const deletionElementIndex = previous.findIndex((data) => data.id === item.id);
      const filteredData = previous.map((data, index) => (index === deletionElementIndex ? { ...data, active: false } : data));
      const newRules = filteredData.reduce((acc, dataItem, index) => {
        if (dataItem.active) {
          const possibleOutputs = acc.reduce((reducedData, element, index) => {
            if (element.active && !Object.values(element).some((e) => e === "")) reducedData.push(element?.output);
            return reducedData;
          }, []);
          possibleOutputs.push(...pureInputList);
          if (!possibleOutputs.includes(dataItem?.input)) {
            if (dataItem?.output !== "") outputToRemove.push(dataItem.output);
            acc.push({ ...dataItem, input: "", oldInput: dataItem?.input ? dataItem?.input : dataItem?.oldInput });
          } else {
            acc.push(dataItem);
          }
        } else {
          acc.push(dataItem);
        }
        return acc;
      }, []);

      return newRules || [];
    });
    if (item?.output) {
      setOutputs((previous) => {
        if (!previous?.includes(item.output)) return previous ? [...previous, item.output] : [item.output];
      });
      setInputs((previous) => {
        return previous?.filter((e) => e !== item.output && !outputToRemove.includes(e));
      });
    }
    setItemForDeletion();
  } catch (error) {
    console.error("Error while deleting a rule: ", error.message);
  }
};

const Select = React.memo(
  ({ item, rules, setRules, disabled = false, options, setOptions, toChange, unique, setInputs, outputs, pureInputList, t }) => {
    const [selected, setSelected] = useState("");
    const [filteredOptions, setFilteredOptions] = useState([]);

    useEffect(() => {
      if (item) {
        if (outputs?.some((e) => e === item.input)) {
          if (rules.filter((item) => item.active).some((e) => e?.output === item?.input)) setSelected({ code: item?.[toChange] });
        } else setSelected({ code: item[toChange] });
      }
    }, [item]);

    useEffect(() => {
      if (!options) return;
      const filteredOptions = options.length ? options : [];
      let filteredOptionPlaceHolder = [];
      if (item?.[toChange] && !filteredOptions.includes(item[toChange])) {
        filteredOptionPlaceHolder = [item[toChange], ...filteredOptions];
      } else filteredOptionPlaceHolder = filteredOptions;

      if (toChange === "input") {
        const currentRuleIndex = rules.findIndex((e) => e?.id === item?.id);
        filteredOptionPlaceHolder = filteredOptionPlaceHolder.filter((data) => {
          let priorOutputs = [];
          if (currentRuleIndex !== -1) {
            priorOutputs = rules.reduce((acc, item, index) => {
              if (item.active && index < currentRuleIndex) acc.push(item?.output);
              return acc;
            }, []);
          }
          priorOutputs.push(...pureInputList);
          return data !== item.output && priorOutputs.includes(data);
        });
      }
      setFilteredOptions(filteredOptionPlaceHolder);
    }, [options]);

    const selectChangeHandler = useCallback(
      (e) => {
        if (e.code === "SELECT_OPTION") return;
        const existingEntry = rules.find((item) => item.active && item[toChange] === e.code);
        if (existingEntry && unique) {
          console.error("Attempted to add a duplicate entry where uniqueness is required.");
          return;
        }
        const newDataSegment = { ...item };
        newDataSegment[toChange] = e.code;
        setRules((previous) => {
          const filteredAssumptionsList = previous.map((data) => {
            if (data.id === item.id) return newDataSegment;
            return data;
          });
          return filteredAssumptionsList;
        });
        if (typeof setInputs === "function") {
          setInputs((previous) => {
            let temp = _.cloneDeep(previous);
            if (toChange === "output") {
              temp = temp.filter((item) => item !== selected?.code);
            }
            if (!temp.includes(newDataSegment.output) && Object.values(newDataSegment).every((item) => item !== ""))
              temp = [...temp, newDataSegment.output];

            const currentRuleIndex = rules.findIndex((e) => e?.id === item?.id);
            temp = temp.filter((data) => {
              let priorOutputs = [];
              if (currentRuleIndex !== -1) {
                priorOutputs = rules.reduce((acc, item, index) => {
                  if (index < currentRuleIndex) acc.push(item?.output);
                  return acc;
                }, []);
              }
              priorOutputs.push(...pureInputList);
              return data !== item.output && priorOutputs.includes(data);
            });
            return temp;
          });
        }
        if (unique)
          setOptions((previous) => {
            const newOptions = previous.filter((item) => item !== e.code);
            if (selected?.code && !newOptions.includes(selected?.code)) newOptions.unshift(selected?.code);
            return newOptions;
          });
      },
      [rules, item, selected, setRules, setOptions, setInputs]
    );

    return (
      <Dropdown
        variant="select-dropdown"
        t={t}
        isMandatory={false}
        option={filteredOptions.map((item) => ({ code: item }))}
        selected={selected}
        select={selectChangeHandler}
        optionKey="code"
        placeholder={t("SELECT_OPTION")}
        showToolTip={true}
      />
    );
  }
);

// get schema for validation
const getRuleConfigInputsFromSchema = (campaignType, microplanData, schemas) => {
  if (!schemas || !microplanData || !microplanData?.upload || !campaignType) return [];
  const sortData = [];
  if (!schemas) return;
  for (const value of microplanData?.upload?.filter((value) => value?.active && value?.error === null) || []) {
    sortData.push({ section: value?.section, fileType: value?.fileType });
  }
  const filteredSchemas =
    schemas?.filter((schema) => {
      if (schema.campaignType) {
        return schema.campaignType === campaignType && sortData.some((entry) => entry.section === schema.section && entry.fileType === schema.type);
      }
      return sortData.some((entry) => entry.section === schema.section && entry.fileType === schema.type);
    }) || [];
  const finalData = filteredSchemas
    ?.flatMap((item) =>
      Object.entries(item?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.isRuleConfigureInputs) {
          acc.push(key);
        }
        return acc;
      }, [])
    )
    .filter((item) => !!item);
  return [...new Set(finalData)];
};

// This function adding the rules configures in MDMS with respect to the canpaign when rule section is empty
const filterRulesAsPerConstrains = (autofillData, rules, hypothesisAssumptionsList, outputs, operators, inputs, setInputs, setOutputs, autofill) => {
  if (rules && rules.filter((item) => item.active).length !== 0) return { rules };

  let wereRulesNotDeleted = true;
  const newRules = [];
  const ruleOuputList = rules ? rules.filter((item) => item.active).map((item) => item?.output) : [];
  let rulePlusInputs;
  if (ruleOuputList) rulePlusInputs = [...inputs, ...ruleOuputList];
  else rulePlusInputs = inputs;
  for (const item of autofillData) {
    let active = !(item && item.active === false);
    const ruleNotCompleteCheck = (!autofill && item && Object.values(item).filter((e) => e === "").length === 0) || autofill;
    if (
      (ruleOuputList?.includes(item?.output) ||
        (outputs && !outputs.includes(item?.output)) ||
        (rulePlusInputs && !rulePlusInputs.includes(item?.input)) ||
        (operators && !operators.includes(item?.operator)) ||
        (hypothesisAssumptionsList && !hypothesisAssumptionsList.includes(item?.assumptionValue)) ||
        !outputs ||
        !rulePlusInputs ||
        !operators ||
        !hypothesisAssumptionsList) &&
      ruleNotCompleteCheck
    ) {
      if (autofill) {
        continue;
      }
      if (active) {
        wereRulesNotDeleted = false;
        active = false;
      }
    }
    if (!item["id"]) {
      const uuid = uuidv4();
      item["id"] = uuid;
    }
    item.active = active;
    newRules.push(item);
    if (active && ruleNotCompleteCheck) {
      rulePlusInputs?.push(item?.output);
      ruleOuputList?.push(item?.output);
    }
  }
  if (newRules.length !== 0) {
    let newOutputs = [];
    outputs.forEach((e) => {
      if (!ruleOuputList.includes(e)) {
        newOutputs.push(e);
      }
    });
    setOutputs(newOutputs);
    setInputs(rulePlusInputs);
    // setRules((previous) => [...previous, ...newRules]);
  }

  return { rules: [...(rules ? rules : []), ...newRules], rulesDeleted: !autofill && !wereRulesNotDeleted };
};

export default RuleEngine;
