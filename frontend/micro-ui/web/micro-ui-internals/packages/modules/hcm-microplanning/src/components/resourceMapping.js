import { Dropdown } from "@egovernments/digit-ui-components";
import { Table } from "@egovernments/digit-ui-react-components";
import { PaginationFirst, PaginationLast, PaginationNext, PaginationPrevious } from "@egovernments/digit-ui-svg-components";
import React, { useState, useEffect, useMemo, useRef, useCallback } from "react";
const SCROLL_OFFSET = 100;

export const SpatialDataPropertyMapping = ({ uploadedData, resourceMapping, setResourceMapping, schema, setToast, hierarchy, close, t }) => {
  // If no data is uploaded, display a message
  if (!uploadedData) return <div className="spatial-data-property-mapping"> {t("NO_DATA_TO_DO_MAPPING")}</div>;

  const itemRefs = useRef([]);
  const [expandedIndex, setExpandedIndex] = useState(null);
  // State to track the render cycle count
  const [renderCycle, setRenderCycle] = useState(0);
  const scrollContainerRef = useRef(null);

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
            top: scrollContainer.scrollTop + offset - SCROLL_OFFSET,
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

  // State variables
  const [userColumns, setUserColumns] = useState([]);
  const [templateColumns, setTemplateColumns] = useState([]);

  // Fetch template columns when schema changes
  useEffect(() => {
    if (!schema || !schema["schema"] || !schema.schema["Properties"])
      return setToast({ state: "error", message: t("ERROR_VALIDATION_SCHEMA_ABSENT") });

    const columns = Object.keys(schema.schema["Properties"]);
    if (columns) {
      const newTemplateColumns = schema && !schema.doHierarchyCheckInUploadedData ? columns : [...hierarchy, ...columns];
      setTemplateColumns(newTemplateColumns);
    }
  }, [schema]);

  // Update user columns when uploaded data changes
  useEffect(() => {
    const userUploadedColumns = new Set();
    uploadedData?.["features"]?.forEach((item) => {
      Object.keys(item["properties"]).forEach((key) => userUploadedColumns.add(key));
    });

    //field level validations
    for (const item of userUploadedColumns) {
      if (item.length < 2) {
        setToast({ state: "error", message: t("ERROR_FIELD_LENGTH") });
        close();
      }
    }
    setUserColumns((preUserColumns) => [...preUserColumns, ...userUploadedColumns]);
  }, [uploadedData]);

  // Dropdown component for selecting user columns
  const DropDownUserColumnSelect = ({ id, index }) => {
    const [selectedOption, setSelectedOption] = useState("");
    useEffect(() => {
      const obj = resourceMapping.find((item) => item["mappedTo"] === id);
      if (obj) setSelectedOption({ code: obj["mappedFrom"] });
      else setSelectedOption();
    }, [id, resourceMapping]);

    const handleSelectChange = (event) => {
      const newValue = event.code;
      setSelectedOption(event);
      setResourceMapping((previous) => {
        const revisedData = previous.filter((item) => !(item["mappedTo"] === id || item["mappedFrom"] === newValue));
        return [...revisedData, { mappedTo: id, mappedFrom: newValue }];
      });
    };

    const toggleExpand = (index) => {
      setExpandedIndex(index === expandedIndex ? null : index);
    };

    return (
      <div
        ref={(el) => {
          itemRefs.current[index] = el;
        }}
        onClick={() => toggleExpand(index)}
        onKeyDown={() => toggleExpand(index)}
      >
        <Dropdown
          variant="select-dropdown"
          t={t}
          isMandatory={false}
          option={userColumns?.map((item) => ({ code: item }))}
          selected={selectedOption}
          optionKey="code"
          select={handleSelectChange}
          style={{ width: "100%", backgroundColor: "rgb(0,0,0,0)" }}
          showToolTip={true}
        />
      </div>
    );
  };

  const tableColumns = useMemo(
    () => [
      {
        Header: t("COLUMNS_IN_TEMPLATE"),
        accessor: "COLUMNS_IN_TEMPLATE",
      },
      {
        Header: t("COLUMNS_IN_USER_UPLOAD"),
        accessor: "COLUMNS_IN_USER_UPLOAD",
        Cell: ({ cell: { value }, row: { index } }) =>
          useMemo(() => <DropDownUserColumnSelect key={value} id={value} index={index} />, [value, index]),
      },
    ],
    [userColumns, setResourceMapping, resourceMapping, t, itemRefs]
  );
  const data = useMemo(() => templateColumns.map((item) => ({ COLUMNS_IN_TEMPLATE: t(item), COLUMNS_IN_USER_UPLOAD: item })), [templateColumns]);
  return (
    <div className="spatial-data-property-mapping" ref={scrollContainerRef}>
      <Table
        customTableWrapperClassName=""
        t={t}
        disableSort={true}
        autoSort={false}
        manualPagination={false}
        isPaginationRequired={true}
        data={data}
        columns={tableColumns}
        getCellProps={(cellInfo) => {
          return { style: {} };
        }}
        getHeaderProps={(cellInfo) => {
          return { style: {} };
        }}
      />
    </div>
  );
};
