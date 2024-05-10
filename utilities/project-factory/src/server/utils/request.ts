import { Response } from "express"; // Importing necessary module Response from Express
import { logger } from "./logger"; // Importing logger from logger module
import { cacheResponse, getCachedResponse, throwErrorViaRequest } from "./genericUtils"; // Importing necessary functions from genericUtils module

var Axios = require("axios").default; // Importing axios library
var get = require("lodash/get"); // Importing get function from lodash library

// Axios interceptor to handle response errors
Axios.interceptors.response.use(
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

const cacheEnabled = true; // Variable to indicate whether caching is enabled or not

const getFormattedString =(obj:any)=>JSON.stringify(obj)?.slice(0,100)+(JSON.stringify(obj)?.length>100?"\n ---more":"")

/**
 * Used to Make API call through axios library
 * 
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
  sendStatusCode: any = false
): Promise<any> => {
  try {
    if (headers && headers.cachekey && cacheEnabled) {
      const cacheData = getCachedResponse(headers.cachekey); // Get cached response data
      if (cacheData) {
        return cacheData; // Return cached data if available
      }
      logger.info(
        "NO CACHE FOUND :: REQUEST :: " +
        JSON.stringify(headers.cachekey)
      );
    }
    logger.info(
      "INTER-SERVICE :: REQUEST :: " +
      getServiceName(_url) +
      " CRITERIA :: " +
      JSON.stringify(_params)
    );
    logger.debug("INTER-SERVICE :: REQUESTBODY :: " + getFormattedString(_requestBody))
    // Make HTTP request using Axios
    const response = await Axios({
      method: _method,
      url: _url,
      data: _requestBody,
      params: _params,
      headers: { ...defaultheader, ...headers },
      responseType,
    });

    const responseStatus = parseInt(get(response, "status"), 10); // Get response status
    logger.info(
      "INTER-SERVICE :: SUCCESS :: " +
      getServiceName(_url) +
      ":: CODE :: " +
      responseStatus
    );
    logger.debug("INTER-SERVICE :: RESPONSEBODY :: " +getFormattedString(response.data));

    // If response status is successful, cache the response data if caching is enabled
    if (responseStatus === 200 || responseStatus === 201 || responseStatus === 202) {
      if (headers && headers.cachekey) {
        cacheResponse(response.data, headers.cachekey)
      }
      // Return response data with status code if sendStatusCode flag is false
      if (!sendStatusCode)
        return response.data;
      else return { ...response.data, "statusCode": responseStatus }
    }
  } catch (error: any) {
    var errorResponse = error?.response; // Get error response
    // Log error details
    logger.error(
      "INTER-SERVICE :: FAILURE :: " +
      getServiceName(_url) +
      ":: CODE :: " +
      errorResponse?.status +
      ":: ERROR :: " +
      errorResponse?.data?.Errors?.[0]?.code || error +
      ":: DESCRIPTION :: " +
      errorResponse?.data?.Errors?.[0]?.description
    );
    logger.error("error occured while making request to " +
      getServiceName(_url) +
      ": error response :" +
      (errorResponse ? parseInt(errorResponse?.status, 10) : error?.message))
    logger.error(":: ERROR STACK :: " + error?.stack || error);
    // Throw error response via request if error response contains errors
    if (errorResponse?.data?.Errors?.[0]) {
      errorResponse.data.Errors[0].status = errorResponse?.data?.Errors?.[0]?.status || errorResponse?.status
      throwErrorViaRequest(errorResponse?.data?.Errors?.[0]);
    }
    else {
      // Throw error message via request
      throwErrorViaRequest(
        "error occured while making request to " +
        getServiceName(_url) +
        ": error response :" +
        (errorResponse ? parseInt(errorResponse?.status, 10) : error?.message)
      );
    }
  }
};

export { httpRequest }; // Exporting the httpRequest function for use in other modules