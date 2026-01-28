import { Response } from "express"; // Importing necessary module Response from Express
import { getFormattedStringForDebug, logger } from "./logger"; // Importing logger from logger module
import { throwErrorViaRequest } from "./genericUtils"; // Importing necessary functions from genericUtils module
import config from "../config";

var Axios = require("axios").default; // Importing axios library
var get = require("lodash/get"); // Importing get function from lodash library
const axiosInstance = Axios.create({
  timeout: 0, // Set timeout to 0 to wait indefinitely
  maxContentLength: Infinity,
  maxBodyLength: Infinity,
});

// Axios interceptor to handle response errors
axiosInstance.interceptors.response.use(
  (res: Response) => {
    return res;
  },
  (err: any) => {
    // If there is no response object in the error, create one with status 400
    if (err && !err.response) {
      err.response = {
        status: 400,
      };
    }
    // If there is a response but no data, create an error object with the error message
    if (err && err.response && !err.response.data) {
      err.response.data = {
        Errors: [{ code: err.message }],
      };
    }
    throw err; // Throw the error
  }
);

// Default header for HTTP requests
export const defaultheader = {
  "content-type": "application/json;charset=UTF-8",
  accept: "application/json, text/plain, */*",
};

// Function to extract service name from URL
const getServiceName = (url = "") => url && url.slice && url.slice(url.lastIndexOf(url.split("/")[3]));


/**
 * Used to Make API call through axios library
 * @author jagankumar-egov
 * 
 * @param {string} _url - The URL to make the HTTP request to
 * @param {Object} _requestBody - The request body
 * @param {Object} _params - The request parameters
 * @param {string} _method - The HTTP method (default to post)
 * @param {string} responseType - The response type
 * @param {Object} headers - The request headers
 * @param {any} sendStatusCode - Flag to determine whether to send status code along with response data
 * @returns {Promise<any>} - Returns the response data or throws an error
 */
const httpRequest = async (
  _url: string,
  _requestBody: any,
  _params: any = {},
  _method: string = "post",
  responseType: string = "",
  headers: any = defaultheader,
  sendStatusCode: any = false,
  retry: any = false,
  dontThrowError: any = false
): Promise<any> => {
  let attempt = 0;
  const maxAttempts = parseInt(config.values.maxHttpRetries) || 4;

  while (attempt < maxAttempts) {
    try {
      logger.info(
        "INTER-SERVICE :: REQUEST :: " +
        getServiceName(_url) +
        " CRITERIA :: " +
        JSON.stringify(_params)
      );
      logger.debug("INTER-SERVICE :: REQUESTBODY :: " + getFormattedStringForDebug(_requestBody));

      const response = await axiosInstance({
        method: _method,
        url: _url,
        data: _requestBody,
        params: _params,
        headers: { ...defaultheader, ...headers },
        responseType,
      });

      const responseStatus = parseInt(get(response, "status"), 10);
      logger.info(
        "INTER-SERVICE :: SUCCESS :: " +
        getServiceName(_url) +
        ":: CODE :: " +
        responseStatus
      );
      logger.debug("INTER-SERVICE :: RESPONSEBODY :: " + getFormattedStringForDebug(response.data));

      if ([200, 201, 202].includes(responseStatus)) {
        return sendStatusCode ? { ...response.data, statusCode: responseStatus } : response.data;
      }
      else{
        logger.warn(`Error occurred while making request to ${getServiceName(_url)}: with error response ${JSON.stringify(response.data)}`);
        return sendStatusCode ? { ...response.data, statusCode: responseStatus } : response.data;
      }
    } catch (error: any) {
      const errorResponse = error?.response;
      logger.error(
        "INTER-SERVICE :: FAILURE :: " +
        getServiceName(_url) +
        ":: CODE :: " +
        errorResponse?.status +
        ":: ERROR :: " +
        (errorResponse?.data?.Errors?.[0]?.code || error) +
        ":: DESCRIPTION :: " +
        errorResponse?.data?.Errors?.[0]?.description
      );
      logger.error(
        "error occurred while making request to " +
        getServiceName(_url) +
        ": error response :" +
        (errorResponse ? parseInt(errorResponse?.status, 10) : error?.message)
      );
      logger.error(":: ERROR STACK :: " + (error?.stack || error));
      logger.warn(
        `Error occurred while making request to ${getServiceName(_url)}: with error response ${JSON.stringify(
          errorResponse?.data || { Errors: [{ code: error.message, description: error.stack }] }
        )}`
      );
      if (
        retry ||
        (config.values.autoRetryIfHttpError &&
          config.values.autoRetryIfHttpError?.includes(
            errorResponse?.data?.Errors?.[0]?.code
          ))
      ) {
        logger.info(
          `retrying the failed api call since retry is enabled or error is equal to configured ${config.values.autoRetryIfHttpError}`
        );
        attempt++;
        if (attempt >= maxAttempts) {
          if (dontThrowError) {
            logger.warn(
              `Maximum retry attempts reached for httprequest with url ${_url}`
            );
            return (
              errorResponse?.data || {
                Errors: [{ code: error.message, description: error.stack }],
              }
            );
          } else {
            throwTheHttpError(errorResponse, error, _url);
          }
        }
        logger.warn(
          `Attempt ${attempt} failed for httprequest with url ${_url}. Waiting for 20 seconds before retrying httprequest with url ${_url}`
        );
        await new Promise((resolve) => setTimeout(resolve, 20000));
      } else if (dontThrowError) {
        logger.warn(
          `Error occurred while making request to ${getServiceName(
            _url
          )}: returning error response ${JSON.stringify(
            errorResponse?.data || {
              Errors: [{ code: error.message, description: error.stack }],
            }
          )}`
        );
        return (
          errorResponse?.data || {
            Errors: [{ code: error.message, description: error.stack }],
          }
        );
      } else {
        throwTheHttpError(errorResponse, error, _url);
      }
    }
  }
};

function throwTheHttpError(errorResponse?: any, error?: any, _url?: string) {
  // Throw error response via request if error response contains errors
  if (errorResponse?.data?.Errors?.[0]) {
    errorResponse.data.Errors[0].status = errorResponse?.data?.Errors?.[0]?.status || errorResponse?.status;
    throwErrorViaRequest(errorResponse?.data?.Errors?.[0]);
  } else {
    // Throw error message via request
    throwErrorViaRequest(
      "error occurred while making request to " +
      getServiceName(_url) +
      ": error response :" +
      (errorResponse ? parseInt(errorResponse?.status, 10) : error?.message)
    );
  }
}

export { httpRequest }; // Exporting the httpRequest function for use in other modules
