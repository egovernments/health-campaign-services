import gjv from "geojson-validation";
import Ajv from "ajv";
const ajv = new Ajv({ allErrors: true });
ajv.addKeyword("isRequired");
ajv.addKeyword("isLocationDataColumns");
ajv.addKeyword("isRuleConfigureInputs");
ajv.addKeyword("isFilterPropertyOfMapSection");
ajv.addKeyword("isVisualizationPropertyOfMapSection");
ajv.addKeyword("toShowInMicroplanPreview");

//the postion must be valid point on the earth, x between -180 and 180
gjv.define("Position", (position) => {
  let errors = [];
  if (position[0] < -180 || position[0] > 180) {
    errors.push("Location Coordinates Error: the x must be between -180 and 180");
  }
  if (position[1] < -90 || position[1] > 90) {
    errors.push("Location Coordinates Error: the y must be between -90 and 90");
  }
  return errors;
});

// Main functino for geojson validation that includes structural and property validations
export const geojsonValidations = (data, schemaData, t) => {
  const valid = geojsonStructureValidation(data);
  return valid.valid ? { valid: true } : { valid: false, message: valid.message || ["ERROR_INVALID_GEOJSON"] };
};

// Funciton responsible for structural verification of geojson data
export const geojsonStructureValidation = (data) => {
  let valid = true;
  const trace = {};
  for (let i = 0; i < data["features"].length; i++) {
    const check = gjv.valid(data["features"][i]);
    valid = valid && check;
    const errors = gjv.isFeature(data["features"][i], true);
    // check if the location coordinates are according to the provided guidlines
    if (errors.some((str) => str.includes("Location Coordinates Error:"))) return { valid: false, message: ["ERROR_INCORRECT_LOCATION_COORDINATES"] };
    if (!check) trace[i] = [errors];
    // let error;
    // Object.keys(data["features"][i]["properties"]).forEach((j) => {
    //   if (j.length > 10) error = { valid: false, trace, message: ["ERROR_FIELD_NAME"] };
    //   return j;
    // });
    // if (error) return error;
  }
  return { valid, trace };
};

const geometryValidation = (data) => {
  let firstType;
  for (const feature of data.features) {
    if (!feature.geometry || !feature.geometry.type) {
      return false; // Missing geometry or geometry type
    }
    if (!firstType) {
      firstType = feature.geometry.type;
    } else {
      // Check if the current geometry type matches the first one
      if (feature.geometry.type !== firstType) {
        return false; // Different geometry types found
      }
    }
  }
  return true;
};

// Function responsible for property verification of geojson data
export const geojsonPropertiesValidation = (data, schemaData, name, t) => {
  const translate = () => {
    const required = Object.entries(schemaData?.Properties || {}).reduce((acc, [key, value]) => {
      if (value?.isRequired) {
        acc.push(key);
      }
      return acc;
    }, []);

    // const properties = prepareProperties(schemaData.Properties, t);
    return { required, properties: schemaData.Properties };
  };
  const { required, properties } = translate();
  const schema = {
    type: "object",
    properties: {
      type: { const: "FeatureCollection" },
    },
    patternProperties: {
      "^features$": {
        type: "array",
        items: {
          type: "object",
          patternProperties: {
            "^properties$": {
              type: "object",
              patternProperties: properties,
              required: required,
              additionalProperties: true,
            },
          },
        },
      },
    },
    additionalProperties: true,
  };
  const validateGeojson = ajv.compile(schema);
  const valid = validateGeojson(data);
  const errors = {};
  let hasDataErrors = "false"; // true, false, missing_properties, unknown
  const missingColumnsList = new Set();
  let errorMessages = [];
  if (!valid) {
    for (let i = 0; i < validateGeojson.errors.length; i++) {
      let tempErrorStore = "";
      let instancePathTypeGlobal = validateGeojson.errors[i].instancePath.split("/");
      switch (validateGeojson.errors[i].keyword) {
        case "additionalProperties": {
          tempErrorStore = "ERROR_ADDITIONAL_PROPERTIES";
          hasDataErrors = "true";
          break;
        }
        case "type":
          {
            const instancePathType = validateGeojson.errors[i].instancePath.split("/");
            const neededType = validateGeojson.errors[i].params?.type;
            instancePathTypeGlobal = instancePathType;
            tempErrorStore = neededType === "number" ? "ERROR_MUST_BE_A_NUMBER" : "ERROR_MUST_BE_A_STRING";
            hasDataErrors = "true";
          }
          break;
        case "const": {
          if (validateGeojson.errors[i].params.allowedValue === "FeatureCollection") tempErrorStore = "ERROR_FEATURECOLLECTION";
          hasDataErrors = "true";
          break;
        }
        case "required": {
          const missing = validateGeojson.errors[i].params.missingProperty;
          const instancePathType = validateGeojson.errors[i].instancePath.split("/");
          instancePathTypeGlobal = [...instancePathType, missing];
          tempErrorStore = "ERROR_MANDATORY_FIELDS_CANT_BE_EMPTY";
          missingColumnsList.add(missing);
          // hasDataErrors = "missing_properties";
          hasDataErrors = "true";
          break;
        }
        case "pattern":
          tempErrorStore = "ERROR_VALUE_NOT_ALLOWED";
          hasDataErrors = "true";
          break;
        case "minProperties": {
          hasDataErrors = "minProperties";
          break;
        }
        case "enum": {
          const instancePathType = validateGeojson.errors[i].instancePath.split("/");
          instancePathTypeGlobal = instancePathType;
          tempErrorStore = {
            error: "ERROR_UPLOAD_DATA_ENUM",
            values: { allowedValues: validateGeojson.errors[i]?.params?.allowedValues?.map((item) => t(item)).join(", ") },
          };
          hasDataErrors = "true";
          break;
        }
        default:
          hasDataErrors = "unknown";
          break;
      }
      if (tempErrorStore)
        errors[name] = {
          ...(errors[name] ? errors[name] : {}),
          [instancePathTypeGlobal[2]]: {
            ...(errors?.[name]?.[instancePathTypeGlobal[2]] ? errors?.[name]?.[instancePathTypeGlobal[2]] : {}),
            [instancePathTypeGlobal[4]]: [
              ...new Set(
                ...(errors?.[name]?.[instancePathTypeGlobal[2]]?.[instancePathTypeGlobal[4]]
                  ? errors?.[name]?.[instancePathTypeGlobal[2]]?.[instancePathTypeGlobal[4]]
                  : [])
              ),
              tempErrorStore,
            ],
          },
        };

      switch (hasDataErrors) {
        case "true":
          errorMessages = { ...errorMessages, dataError: t("ERROR_REFER_UPLOAD_PREVIEW_TO_SEE_THE_ERRORS") };
          break;
        case "unknown":
          errorMessages = { ...errorMessages, unkown: t("ERROR_UNKNOWN") };
          break;
        case "missing_properties":
          errorMessages = {
            ...errorMessages,
            missingProperty: t("ERROR_MISSING_PROPERTY", { properties: [...missingColumnsList].map((item) => t(item)).join(", ") }),
          };
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
      validationError: validateGeojson.errors,
    };
  }
  ajv.removeSchema();
  if (!geometryValidation(data)) return { valid: false, message: t("ERROR_MULTIPLE_GEOMETRY_TYPES") };
  return { valid: true };
};

////////////////////////////
// // Might be needed
// function filterOutWordAndLocalise(inputString, operation) {
//   // Define a regular expression to match the string parts
//   var regex = /(\w+)/g; // Matches one or more word characters

//   // Replace each match using the provided function
//   var replacedString = inputString.replace(regex, function (match) {
//     // Apply the function to each matched string part
//     return operation(match);
//   });

//   return replacedString;
// }
// const prepareProperties = (properties, t) => {
//   let newProperties = {};
//   Object.keys(properties).forEach((item) => (newProperties[filterOutWordAndLocalise(item, t)] = properties[item]));
//   return newProperties;
// };

////////////////////////////
