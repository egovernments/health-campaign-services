import React, { useState, useEffect, useCallback, Fragment, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Trash } from "@egovernments/digit-ui-svg-components";
import { CloseButton, ModalHeading } from "./CommonComponents";
import { Dropdown, TextInput, Toast } from "@egovernments/digit-ui-components";
import { useMyContext } from "../utils/context";
import { tourSteps } from "../configs/tourSteps";
import { v4 as uuidv4 } from "uuid";
import { PlusWithSurroundingCircle } from "../icons/Svg";
import { PRIMARY_THEME_COLOR } from "../configs/constants";
import { Button, Modal } from "@egovernments/digit-ui-react-components";
const page = "hypothesis";

const Hypothesis = ({
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
  const [assumptions, setAssumptions] = useState([]);
  const [hypothesisAssumptionsList, setHypothesisAssumptionsList] = useState([]);
  const [itemForDeletion, setItemForDeletion] = useState();
  const [exampleOption, setExampleOption] = useState("");
  // const [toast, setToast] = useState();
  const [autofillHypothesis, setAutofillHypothesis] = useState([]);
  const { state, dispatch } = useMyContext();
  const [orignalHypothesisCount, setOrignalHypothesisCount] = useState(0);

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
    if (microplanData?.hypothesis) {
      const temp = microplanData?.hypothesis;
      setAssumptions(temp);
    }

    fetchDataAndUpdateState();
  }, []);

  const fetchDataAndUpdateState = useCallback(() => {
    const hypothesisAssumptions = state?.HypothesisAssumptions || [];
    const temp = hypothesisAssumptions.find((item) => item.campaignType === campaignType);
    if (!temp?.assumptions) return;

    const hypothesisAssumptionsList = Array.isArray(temp.assumptions) ? temp.assumptions : [];
    setOrignalHypothesisCount(hypothesisAssumptionsList.length);
    setExampleOption(hypothesisAssumptionsList.length !== 0 ? hypothesisAssumptionsList[0] : "");

    const currentHypothesis = microplanData?.hypothesis || assumptions;
    const newAssumptions = setAutofillHypothesisData(hypothesisAssumptionsList, currentHypothesis, setAssumptions);

    const newHypothesislist = filterHypothesisList(
      newAssumptions.length !== 0 ? newAssumptions : microplanData.hypothesis,
      hypothesisAssumptionsList
    );
    setHypothesisAssumptionsList(newHypothesislist);
  }, [campaignType, microplanData, state, assumptions, setAssumptions]);

  // UseEffect for checking completeness of data before moveing to next section
  useEffect(() => {
    if (!assumptions || checkDataCompletion !== "true" || !setCheckDataCompletion) return;
    // uncomment to activate data change save check
    // if (!microplanData?.hypothesis || !_.isEqual(assumptions, microplanData.hypothesis)) setModal("data-change-check");
    // else
    updateData(true);
  }, [checkDataCompletion]);

  // UseEffect to store current data
  useEffect(() => {
    if (!assumptions || !setMicroplanData) return;
    setMicroplanData((previous) => ({ ...previous, hypothesis: assumptions }));
  }, [assumptions]);

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
      if (!assumptions || !setMicroplanData) return;
      if (check) {
        if (assumptions.some((item) => item.active && parseFloat(item.value) === 0)) {
          setToast({ state: "error", message: t("ERROR_HYPOTHESIS_VALUE_SHOULD_NOT_BE_ZERO") });
          setCheckDataCompletion("false");
          return;
        }
        let newAssumptions = assumptions.map((item) => {
          if (parseFloat(item.value) === 0) {
            return { ...item, value: 0.01 };
          }
          return item;
        });
        setMicroplanData((previous) => ({ ...previous, hypothesis: newAssumptions }));
        setAssumptions(newAssumptions);
        let checkValid = validateAssumptions(assumptions);
        checkValid = checkValid && assumptions.filter((subItem) => subItem?.active).length !== 0;
        if (checkValid) setCheckDataCompletion("valid");
        else setCheckDataCompletion("invalid");
      } else {
        let checkValid = microplanData?.hypothesis?.every((item) => Object.values(item).every((data) => data !== ""));
        checkValid = checkValid && assumptions.length !== 0;
        if (checkValid) setCheckDataCompletion("valid");
        else setCheckDataCompletion("invalid");
      }
    },
    [assumptions, setMicroplanData, microplanData, setCheckDataCompletion]
  );

  const validateAssumptions = useCallback((assumptions) => {
    return assumptions.filter((item) => item?.active).every((item) => Object.values(item).every((data) => data !== "")) && assumptions.length !== 0;
  }, []);

  const cancelUpdateData = useCallback(() => {
    setCheckDataCompletion("false");
    setModal("none");
  }, [setCheckDataCompletion, setModal]);

  const closeModal = useCallback(() => {
    setModal("none");
  }, []);

  // Function to Delete an assumption
  const deleteAssumptionHandlerCallback = useCallback(() => {
    deleteAssumptionHandler(itemForDeletion, setItemForDeletion, setAssumptions, setHypothesisAssumptionsList, setToast, t);
    closeModal();
  }, [itemForDeletion, deleteAssumptionHandler, setItemForDeletion, setAssumptions, setHypothesisAssumptionsList, closeModal, setToast, t]);

  const sectionClass = `jk-header-btn-wrapper hypothesis-section ${editable ? "" : "non-editable-component"} popup-wrap-rest-unfocus `;

  return (
    <>
      <div className={sectionClass}>
        <div className="hypothesis-help" style={{ position: "absolute", top: "20rem", left: "40%", zIndex: "-100" }} />
        {/* NonInterractable Section */}
        <NonInterractableSection t={t} />
        {/* Interractable Section that includes the example as well as the assumptions */}
        <InterractableSection
          assumptions={assumptions}
          setAssumptions={setAssumptions}
          hypothesisAssumptionsList={hypothesisAssumptionsList}
          setHypothesisAssumptionsList={setHypothesisAssumptionsList}
          setModal={setModal}
          setItemForDeletion={setItemForDeletion}
          exampleOption={exampleOption}
          t={t}
        />
        <div className="add-button-help" />
        <Button
          variation={"secondary"}
          icon={<PlusWithSurroundingCircle fill={PRIMARY_THEME_COLOR} width="1.05rem" height="1.05rem" style={{ margin: 0 }} />}
          className="add-button"
          onButtonClick={() => addAssumptionsHandler(setAssumptions)}
          label={t("ADD_ROW")}
          isDisabled={assumptions?.filter((item) => item.active)?.length === orignalHypothesisCount}
        />
      </div>
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
              <p className="modal-main-body-p">{t("HYPOTHESIS_INSTRUCTIONS_DELETE_ENTRY_CONFIRMATION")}</p>
            </div>
          </Modal>
        )}
      </div>
    </>
  );
};

// Function to add a new assumption
const addAssumptionsHandler = (setAssumptions) => {
  const uuid = uuidv4();
  setAssumptions((previous) => [
    ...previous,
    {
      id: uuid,
      // previous.length ? previous[previous.length - 1].id + 1 : 0,
      key: "",
      value: "",
      active: true,
    },
  ]);
};

// Defination for NonInterractable Section
const NonInterractableSection = React.memo(({ t }) => {
  return (
    <div>
      <h2 className="heading">{t("HEADING_HYPOTHESIS")}</h2>
      <p className="instruction">{t("INSTRUCTION_HYPOTHESIS")}</p>
    </div>
  );
});

// Defination for NonInterractable Section
const InterractableSection = React.memo(
  ({ assumptions, setAssumptions, hypothesisAssumptionsList, setHypothesisAssumptionsList, setModal, setItemForDeletion, exampleOption, t }) => {
    const itemRefs = useRef([]);
    const [expandedIndex, setExpandedIndex] = useState(null);
    const scrollContainerRef = useRef(null);
    const [renderCycle, setRenderCycle] = useState(0);

    useEffect(() => {
      if (expandedIndex !== null) {
        setRenderCycle(0); // Reset render cycle count when expandedIndex changes
      }
    }, [expandedIndex]);

    useEffect(() => {
      // Scroll to the expanded item after the state has updated and the DOM has re-rendered
      if (renderCycle < 2) {
        setRenderCycle((prev) => prev + 1); // Increment render cycle count
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

            // Scroll the container
            scrollContainer.scrollTo({
              top: scrollContainer.scrollTop + offset - 10,
              behavior: "smooth",
            });
          }

          if (childElement) {
            childElement.focus();
          }
        } catch (error) {
          console.error("Error scrolling to element:", error);
        }
      }
    }, [renderCycle, expandedIndex]);

    useEffect(() => {
      if (expandedIndex !== null) {
        const observer = new MutationObserver(() => {
          setRenderCycle((prev) => prev + 1); // Trigger render cycle when the DOM changes
        });

        if (itemRefs.current[expandedIndex]) {
          observer.observe(itemRefs.current[expandedIndex], { childList: true, subtree: true });
        }

        return () => observer.disconnect();
      }
    }, [expandedIndex]);

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
            <div className="key">
              <p className="heading">{t("KEY")}</p>
            </div>
            <div className="value">
              <p className="heading">{t("VALUE")}</p>
            </div>
            <div className="invisible">
              <button type="button" className="delete-button invisible" onClick={() => deleteHandler(item)}>
                <div> {Trash && <Trash width={"0.8rem"} height={"1rem"} fill={PRIMARY_THEME_COLOR} />}</div>
                <p>{t("DELETE")}</p>
              </button>
            </div>
          </div>
          {assumptions
            ?.filter((item) => item.active)
            ?.map((item, index) => (
              <div
                key={item?.id || index}
                className={`${index === 0 ? "select-and-input-wrapper-first" : "select-and-input-wrapper"} ${
                  index === assumptions?.filter((item) => item.active)?.length - 1 ? "last-container" : ""
                } `}
              >
                <div
                  className="key"
                  ref={(el) => {
                    itemRefs.current[index] = el;
                  }}
                  onClick={() => {
                    toggleExpand(index);
                  }}
                >
                  <Select
                    key={item.id}
                    item={item}
                    assumptions={assumptions}
                    setAssumptions={setAssumptions}
                    options={hypothesisAssumptionsList}
                    setOptions={setHypothesisAssumptionsList}
                    t={t}
                  />
                </div>
                <div className="value">
                  <Input key={item.id} item={item} t={t} assumptions={assumptions} setAssumptions={setAssumptions} />
                </div>
                <div>
                  <button
                    type="button"
                    className="delete-button delete-button-help-locator"
                    onClick={() => deleteHandler(item)}
                    onKeyDown={(e) => e.key === "Enter" && deleteHandler(item)}
                    aria-label={t("DELETE")}
                    role="button"
                  >
                    <div> {Trash && <Trash width={"0.8rem"} height={"1rem"} fill={PRIMARY_THEME_COLOR} />}</div>
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
    <div className="example">
      <p className="heading">{t("EXAMPLE")}</p>
      <div className="example-body">
        <div className="key">
          <p className="heading">{t("KEY")}</p>
          <Dropdown
            variant="select-dropdown"
            t={t}
            isMandatory={false}
            option={[]}
            selected={null}
            optionKey="code"
            placeholder={t(exampleOption)}
            showToolTip={true}
          />
          <p className="heading">{t("HYPOTHESIS_KEY_HELP_TEXT")}</p>
        </div>
        <div className="value">
          <p className="heading">{t("VALUE")}</p>
          <TextInput
            name={"input"}
            type={"number"}
            value={t(10)}
            t={t}
            config={{}}
            disabled={true}
            onChange={null}
            inputClassName="input"
            style={{ backgroundColor: "rgb(0,0,0,0)", border: "1px solid red" }}
          />
          <p className="heading">{t("HYPOTHESIS_VALUE_HELP_TEXT")}</p>
        </div>
      </div>
    </div>
  );
};

const deleteAssumptionHandler = (item, setItemForDeletion, setAssumptions, setHypothesisAssumptionsList, setToast, t) => {
  let add = true;
  setAssumptions((previous) => {
    if (!previous.length) return [];
    if (previous.filter((item) => item.active)?.length <= 1) {
      setToast({ state: "error", message: t("ERROR_CANNOT_DELETE_LAST_HYPOTHESIS") });
      add = false;
      return previous;
    }
    // const filteredData = previous.filter((data) => data.id !== item.id);
    const deletionElementIndex = previous.findIndex((data) => data.id === item.id);
    const filteredData = previous.map((data, index) => (index === deletionElementIndex ? { ...data, active: false } : data));
    return filteredData || [];
  });
  if (add && item && item.key)
    setHypothesisAssumptionsList((previous) => {
      if (!previous.includes(item.key)) return [...previous, item.key];
      return previous; // Return previous array if key already exists
    });
  setItemForDeletion();
};

const Select = React.memo(({ item, assumptions, setAssumptions, disabled = false, options, setOptions, t }) => {
  const [selected, setSelected] = useState();
  const [filteredOptions, setFilteredOptions] = useState([]);

  useEffect(() => {
    if (item?.key) setSelected({ code: item.key });
  }, [item]);

  useEffect(() => {
    if (!options) return;
    const filteredOptions = options.length ? options : [];
    if (item?.key && !filteredOptions.includes(item.key)) {
      setFilteredOptions([item.key, ...filteredOptions]);
    } else setFilteredOptions(filteredOptions);
  }, [options]);

  const selectChangeHandler = useCallback(
    (e) => {
      const existingEntry = assumptions.find((item) => item?.active && item?.key === e?.code);
      if (existingEntry) return;
      const newDataSegment = {
        ...item,
        id: item.id,
        key: e?.code,
        value: item.value,
      };
      setAssumptions((previous) => {
        const filteredAssumptionsList = previous.map((data) => {
          if (data.id === item.id) return newDataSegment;
          return data;
        });
        return filteredAssumptionsList;
      });

      setOptions((previous) => {
        let newOptions = previous.filter((item) => item !== e?.code);
        if (selected && !newOptions.includes(selected?.code)) newOptions.unshift(selected?.code);
        return newOptions;
      });
    },
    [assumptions, item, selected, setAssumptions, setOptions]
  );

  return (
    <Dropdown
      variant="select-dropdown"
      t={t}
      isMandatory={false}
      option={filteredOptions?.map((item) => ({ code: item }))}
      selected={selected}
      optionKey="code"
      select={selectChangeHandler}
      // style={{ width: "100%", backgroundColor: "rgb(0,0,0,0)", position:"sticky" }}
      optionCardStyles={{ position: "absolute" }}
      placeholder={t("SELECT_OPTION")}
      showToolTip={true}
    />
  );
});

const Input = React.memo(({ item, setAssumptions, t, disabled = false }) => {
  const [inputValue, setInputValue] = useState("");

  useEffect(() => {
    if (item) setInputValue(item.value);
  }, [item]);

  const inputChangeHandler = useCallback(
    (e) => {
      if (e.target.value.includes("+") || e.target.value.includes("e")) return;
      if ((e.target.value < 0 || e.target.value > 10000000000) && e.target.value !== "") return;
      let value;
      const decimalIndex = e.target.value.indexOf(".");
      if (decimalIndex !== -1) {
        const numDecimals = e.target.value.length - decimalIndex - 1;
        value = e.target.value;
        if (numDecimals <= 2) {
          value = e.target.value;
        } else if (numDecimals > 2) {
          value = value.substring(0, decimalIndex + 3);
        }
      } else value = Number.parseFloat(e.target.value);

      setInputValue(!Number.isNaN(value) ? value : "");
      const newDataSegment = {
        ...item,
        id: item.id,
        key: item.key,
        value: !Number.isNaN(value) ? value : "",
      };
      setAssumptions((previous) => {
        const filteredAssumptionsList = previous.map((data) => {
          if (data.id === item.id) {
            return newDataSegment;
          }
          return data;
        });
        return filteredAssumptionsList;
      });
    },
    [item, setAssumptions]
  );

  return (
    <TextInput
      name={"input"}
      type={"text"}
      value={inputValue}
      t={t}
      config={{}}
      onChange={
        // valueChangeHandler({ item, newValue: value?.target?.value }, setTempHypothesisList, boundarySelections, setToast, t)
        inputChangeHandler
      }
      style={{ paddingRight: "0.7rem" }}
      disable={false}
    />
  );
});

const setAutofillHypothesisData = (autofillHypothesis, assumptions, setAssumptions) => {
  if (assumptions?.length !== 0) return [];
  let newAssumptions = [];
  for (let i in autofillHypothesis) {
    const uuid = uuidv4();
    newAssumptions.push({
      id: uuid,
      key: autofillHypothesis[Number(i)],
      value: "",
      active: true,
    });
  }
  setAssumptions(newAssumptions);
  return newAssumptions;
};

const filterHypothesisList = (assumptions, hypothesisList) => {
  let alreadySelectedHypothesis = assumptions.filter((item) => item?.active).map((item) => item?.key) || [];
  return hypothesisList.filter((item) => !alreadySelectedHypothesis.includes(item));
};

export default Hypothesis;
