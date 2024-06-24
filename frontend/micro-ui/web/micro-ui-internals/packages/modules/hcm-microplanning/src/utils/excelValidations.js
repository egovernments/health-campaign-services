import Ajv from "ajv";
const ajv = new Ajv({ allErrors: true });
ajv.addKeyword("isRequired");
ajv.addKeyword("isLocationDataColumns");
ajv.addKeyword("isRuleConfigureInputs");
ajv.addKeyword("isFilterPropertyOfMapSection");
ajv.addKeyword("isVisualizationPropertyOfMapSection");
ajv.addKeyword("toShowInMicroplanPreview");

// Function responsible for excel data validation with respect to the template/schema provided
const translateSchema = (schemaData) => {
  const required = Object.entries(schemaData?.Properties || {}).reduce((acc, [key, value]) => {
    if (value?.isRequired) {
      acc.push(key);
    }
    return acc;
  }, []);

  return { required, properties: schemaData.Properties };
};

const createSchema = (properties, required) => {
  return {
    type: "object",
    patternProperties: {
      ".*": {
        type: "array",
        items: {
          type: "object",
          properties: properties,
          required: required,
          additionalProperties: true,
        },
      },
    },
    minProperties: 1,
    additionalProperties: false,
  };
};

const extractLocationDataColumns = (schemaData) => {
  return Object.entries(schemaData?.Properties || {}).reduce((acc, [key, value]) => {
    if (value?.isLocationDataColumns) {
      acc.push(key);
    }
    return acc;
  }, []);
};

const setNestedError = (errors, path, error) => {
  if (!path.length) return;

  let current = errors;
  for (let i = 0; i < path.length - 1; i++) {
    if (!current[path[i]]) {
      current[path[i]] = {};
    }
    current = current[path[i]];
  }

  if (!current[path[path.length - 1]]) {
    current[path[path.length - 1]] = [];
  }

  current[path[path.length - 1]] = [...new Set([...current[path[path.length - 1]], error])];
};

const formatErrors = (validateExcelErrors, locationDataColumns, t) => {
  const errors = {};
  let hasDataErrors = "false"; // true, false, missing_properties, unknown
  const missingColumnsList = new Set();
  let errorMessages = {};

  validateExcelErrors.forEach((error) => {
    let tempErrorStore = "";
    let instancePathTypeGlobal;

    switch (error.keyword) {
      case "additionalProperties":
        tempErrorStore = "ERROR_ADDITIONAL_PROPERTIES";
        hasDataErrors = "true";
        break;
      case "type":
        {
          const instancePathType = error.instancePath.split("/");
          const neededType = error.params?.type;
          instancePathTypeGlobal = instancePathType;
          tempErrorStore = locationDataColumns.includes(instancePathType[instancePathType.length - 1])
            ? "ERROR_INCORRECT_LOCATION_COORDINATES"
            : neededType === "number"
            ? "ERROR_MUST_BE_A_NUMBER"
            : "ERROR_MUST_BE_A_STRING";
          hasDataErrors = "true";
        }
        break;
      case "required":
        {
          const missing = error.params.missingProperty;
          const instancePathType = error.instancePath.split("/");
          instancePathTypeGlobal = [...instancePathType, missing];
          tempErrorStore = "ERROR_MANDATORY_FIELDS_CANT_BE_EMPTY";
          missingColumnsList.add(missing);
          hasDataErrors = "true";
        }
        break;
      case "maximum":
      case "minimum":
        {
          const instancePathMinMax = error.instancePath.split("/");
          instancePathTypeGlobal = instancePathMinMax;
          tempErrorStore = locationDataColumns.includes(instancePathMinMax[instancePathTypeGlobal.length - 1])
            ? "ERROR_INCORRECT_LOCATION_COORDINATES"
            : "ERROR_DATA_EXCEEDS_LIMIT_CONSTRAINTS";
          hasDataErrors = "true";
        }
        break;
      case "pattern":
        tempErrorStore = "ERROR_VALUE_NOT_ALLOWED";
        hasDataErrors = "true";
        break;
      case "minProperties":
        hasDataErrors = "minProperties";
        break;
      case "enum":
        {
          const instancePathType = error.instancePath.split("/");
          instancePathTypeGlobal = instancePathType;
          tempErrorStore = {
            error: "ERROR_UPLOAD_DATA_ENUM",
            values: { allowedValues: error.params?.allowedValues?.map((item) => t(item)).join(", ") },
          };
          hasDataErrors = "true";
        }
        break;
      default:
        hasDataErrors = "unknown";
    }

    if (tempErrorStore && instancePathTypeGlobal) {
      setNestedError(errors, instancePathTypeGlobal.slice(1, 4), tempErrorStore);
    }

    switch (hasDataErrors) {
      case "true":
        errorMessages = { dataError: "ERROR_REFER_UPLOAD_PREVIEW_TO_SEE_THE_ERRORS" };
        break;
      case "minProperties":
        errorMessages = { minProperties: "ERROR_UPLOADED_DATA_IS_EMPTY" };
        break;
      case "unknown":
        errorMessages = { unknown: "ERROR_UNKNOWN" };
        break;
      case "false":
        break;
    }
  });

  return {
    valid: !hasDataErrors,
    message: errorMessages ? [...new Set(Object.values(errorMessages))] : [],
    errors,
    missingColumnsList,
  };
};

export const excelValidations = (data, schemaData, t) => {
  const { required, properties } = translateSchema(schemaData);
  const schema = createSchema(properties, required);
  const validateExcel = ajv.compile(schema);
  const valid = validateExcel(data);
  const locationDataColumns = extractLocationDataColumns(schemaData);

  if (!valid) {
    const validationResult = formatErrors(validateExcel.errors, locationDataColumns, t);
    ajv.removeSchema();
    return validationResult;
  }

  ajv.removeSchema();
  return { valid };
};

export const checkForErrorInUploadedFileExcel = async (fileInJson, schemaData, t) => {
  try {
    const valid = excelValidations(fileInJson, schemaData, t);
    if (valid.valid) {
      return { valid: true };
    }
    return {
      valid: false,
      message: valid.message,
      errors: valid.errors,
      missingProperties: valid.missingColumnsList,
    };
  } catch (error) {
    console.error("Error in excel validations: ", error?.message);
    return { valid: false, message: ["ERROR_PARSING_FILE"] };
  }
};
