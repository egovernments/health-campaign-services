import { Response } from "express";
import { logger } from "./logger";
import { cacheResponse, getCachedResponse, throwErrorViaRequest } from "./genericUtils";

var Axios = require("axios").default;
var get = require("lodash/get");

Axios.interceptors.response.use(
  (res: Response) => {
    return res;
  },
  (err: any) => {
    if (err && !err.response) {
      err.response = {
        status: 400,
      };
    }
    if (err && err.response && !err.response.data) {
      err.response.data = {
        Errors: [{ code: err.message }],
      };
    }
    throw err;
  }
);

export const defaultheader = {
  "content-type": "application/json;charset=UTF-8",
  accept: "application/json, text/plain, */*",
};

const getServiceName = (url = "") => url && url.slice && url.slice(url.lastIndexOf(url.split("/")[3]));
const cacheEnabled = true;
/*
 
Used to Make API call through axios library

  @author jagankumar-egov

 * @param {string} _url
 * @param {Object} _requestBody
 * @param {Object} _params
 * @returns {string} _method default to post
 * @returns {string} responseType
 * @param {Object} headers

*/
const httpRequest = async (
  _url: string,
  _requestBody: any,
  _params: any = {},
  _method: string = "post",
  responseType: string = "",
  headers: any = defaultheader,
  sendStatusCode: any = false
) => {
  try {
    if (headers && headers.cachekey && cacheEnabled) {
      const cacheData = getCachedResponse(headers.cachekey);
      if (cacheData) {
        return cacheData;
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
    logger.debug(JSON.stringify(_requestBody))
    const response = await Axios({
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
    if (responseStatus === 200 || responseStatus === 201 || responseStatus === 202) {
      if (headers && headers.cachekey) {
        cacheResponse(response.data, headers.cachekey)
      }
      if (!sendStatusCode)
        return response.data;
      else return { ...response.data, "statusCode": responseStatus }
    }
  } catch (error: any) {
    var errorResponse = error?.response;
    logger.error(
      "INTER-SERVICE :: FAILURE :: " +
      getServiceName(_url) +
      ":: CODE :: " +
      errorResponse?.status +
      ":: ERROR :: " +
      errorResponse?.data?.Errors?.[0]?.code || error
    );
    logger.error("error occured while making request to " +
      getServiceName(_url) +
      ": error response :" +
      (errorResponse ? parseInt(errorResponse?.status, 10) : error?.message))
    logger.error(":: ERROR STACK :: " + error?.stack || error);
    if (errorResponse?.data?.Errors) {
      throwErrorViaRequest(JSON.stringify(errorResponse?.data?.Errors));
    }
    else {
      throwErrorViaRequest(
        "error occured while making request to " +
        getServiceName(_url) +
        ": error response :" +
        (errorResponse ? parseInt(errorResponse?.status, 10) : error?.message)
      );
    }
  }
};

export { httpRequest };