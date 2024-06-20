import Ajv from "ajv";
const ajv = new Ajv({ allErrors: true });
ajv.addKeyword("isRequired");
ajv.addKeyword("isLocationDataColumns");
ajv.addKeyword("isRuleConfigureInputs");
ajv.addKeyword("isFilterPropertyOfMapSection");
ajv.addKeyword("isVisualizationPropertyOfMapSection");
ajv.addKeyword("toShowInMicroplanPreview");

// Function responsible for excel data validation with respect to the template/schema provided
export const excelValidations = (data, schemaData, t) => {
  const translate = () => {
    const required = Object.entries(schemaData?.Properties || {})
      .reduce((acc, [key, value]) => {
        if (value?.isRequired) {
          acc.push(key);
        }
        return acc;
      }, [])
      .map((item) => item);
    return { required, properties: schemaData.Properties };
  };
  const { required, properties } = translate();
  const schema = {
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
  const validateExcel = ajv.compile(schema);
  const valid = validateExcel(data);
  let locationDataColumns = Object.entries(schemaData?.Properties || {}).reduce((acc, [key, value]) => {
    if (value?.isLocationDataColumns) {
      acc.push(key);
    }
    return acc;
  }, []);
  if (!valid) {
    let errors = {};
    let hasDataErrors = "false"; // true, false, missing_properties, unknown
    let missingColumnsList = new Set();
    let errorMessages = {};
    for (let i = 0; i < validateExcel.errors.length; i++) {
      let tempErrorStore = "";
      let instancePathTypeGlobal; // = validateExcel.errors[i].instancePath.split("/");
      switch (validateExcel.errors[i].keyword) {
        case "additionalProperties": {
          tempErrorStore = "ERROR_ADDITIONAL_PROPERTIES";
          hasDataErrors = "true";
          break;
        }
        case "type":
          {
            const instancePathType = validateExcel.errors[i].instancePath.split("/");
            const neededType = validateExcel.errors[i].params?.type;
            instancePathTypeGlobal = instancePathType;
            tempErrorStore = locationDataColumns.includes(instancePathType[instancePathType.length - 1])
              ? "ERROR_INCORRECT_LOCATION_COORDINATES"
              : neededType === "number"
              ? "ERROR_MUST_BE_A_NUMBER"
              : "ERROR_MUST_BE_A_STRING";
            hasDataErrors = "true";
          }
          break;
        case "required": {
          const missing = validateExcel.errors[i].params.missingProperty;
          const instancePathType = validateExcel.errors[i].instancePath.split("/");
          instancePathTypeGlobal = [...instancePathType, missing];
          tempErrorStore = "ERROR_MANDATORY_FIELDS_CANT_BE_EMPTY";
          missingColumnsList.add(missing);
          // hasDataErrors = "missing_properties";
          hasDataErrors = "true";
          break;
        }
        case "maximum":
        case "minimum": {
          const instancePathMinMax = validateExcel.errors[i].instancePath.split("/");
          instancePathTypeGlobal = instancePathMinMax;
          tempErrorStore = locationDataColumns.includes(instancePathMinMax[instancePathTypeGlobal.length - 1])
            ? "ERROR_INCORRECT_LOCATION_COORDINATES"
            : "ERROR_DATA_EXCEEDS_LIMIT_CONSTRAINTS";
          hasDataErrors = "true";
          break;
        }
        case "pattern": {
          tempErrorStore = "ERROR_VALUE_NOT_ALLOWED";
          hasDataErrors = "true";
          break;
        }
        case "minProperties": {
          hasDataErrors = "minProperties";
          break;
        }
        case "enum": {
          const instancePathType = validateExcel.errors[i].instancePath.split("/");
          instancePathTypeGlobal = instancePathType;
          tempErrorStore = {
            error: "ERROR_UPLOAD_DATA_ENUM",
            values: { allowedValues: validateExcel.errors[i]?.params?.allowedValues?.map((item) => t(item)).join(", ") },
          };
          hasDataErrors = "true";
          break;
        }
        default: {
          hasDataErrors = "unknown";
        }
      }
      if (tempErrorStore && instancePathTypeGlobal)
        errors[instancePathTypeGlobal[1]] = {
          ...(errors[instancePathTypeGlobal[1]] ? errors[instancePathTypeGlobal[1]] : {}),
          [instancePathTypeGlobal[2]]: {
            ...(errors?.[instancePathTypeGlobal[1]]?.[instancePathTypeGlobal[2]]
              ? errors?.[instancePathTypeGlobal[1]]?.[instancePathTypeGlobal[2]]
              : {}),
            [instancePathTypeGlobal[3]]: [
              ...new Set(
                ...(errors?.[instancePathTypeGlobal[1]]?.[instancePathTypeGlobal[2]]?.[instancePathTypeGlobal[3]]
                  ? errors?.[instancePathTypeGlobal[1]]?.[instancePathTypeGlobal[2]]?.[instancePathTypeGlobal[3]]
                  : [])
              ),
              tempErrorStore,
            ],
          },
        };

      switch (hasDataErrors) {
        case "true":
          errorMessages = { dataError: "ERROR_REFER_UPLOAD_PREVIEW_TO_SEE_THE_ERRORS" };
          break;
        case "minProperties":
          errorMessages = { minProperties: "ERROR_UPLOADED_DATA_IS_EMPTY" };
          break;
        case "unknown":
          errorMessages = { unkown: "ERROR_UNKNOWN" };
          break;
        case "false":
          break;
      }
    }

    ajv.removeSchema();

    return {
      valid: !hasDataErrors,
      message: errorMessages ? [...new Set(Object.values(errorMessages))] : [],
      errors,
      validationError: validateExcel.errors,
      missingColumnsList,
    };
  }
  ajv.removeSchema();
  return { valid };
};

export const checkForErrorInUploadedFileExcel = async (fileInJson, schemaData, t) => {
  try {
    const valid = excelValidations(fileInJson, schemaData, t);
    if (valid.valid) {
      return { valid: true };
    } else {
      return {
        valid: false,
        message: valid.message,
        errors: valid.errors,
        missingProperties: valid.missingColumnsList,
      };
    }
  } catch (error) {
    console.error("Error in excel validations: ", error?.message);
    return { valid: false, message: ["ERROR_PARSING_FILE"] };
  }
};
