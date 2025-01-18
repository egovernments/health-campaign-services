import config from "../config";
import { defaultRequestInfo } from "./coreApis";
import { httpRequest } from "../utils/request";
import { throwError } from "../utils/genericUtils";

export async function getProjectsWithProjectIds(projectIds: string[], tenantId: string) {
    const requestBody = {
        RequestInfo: defaultRequestInfo?.RequestInfo,
        Projects: projectIds.map((projectId) => ({
            id: projectId,
            tenantId
        }))
    };
    const params = {
        tenantId: tenantId,
        offset: 0,
        limit: projectIds?.length + 1,
    }
    const url = config.host.projectHost + config.paths.projectSearch;
    const response = await httpRequest(url, requestBody, params);

    if (Array.isArray(response?.Project)) {
        return response?.Project;
    } else {
        throwError("PROJECT", 500, "PROJECT_SEARCH_ERROR", "Error occurred during project search");
        return [];
    }
}

export async function getProjectsCountsWithProjectIds(projectIds: string[], tenantId: string) {
    const requestBody = {
        RequestInfo: defaultRequestInfo?.RequestInfo,
        Projects: projectIds.map((projectId) => ({
            id: projectId,
            tenantId
        }))
    };
    const url = config.host.projectHost + config.paths.projectSearch;
    const params = {
        tenantId : tenantId,
        offset : 0,
        limit: 1
    }
    const response = await httpRequest(url, requestBody, params);

    if (Array.isArray(response?.Project)) {
        return response?.TotalCount;
    } else {
        throwError("PROJECT", 500, "PROJECT_SEARCH_ERROR", "Error occurred during project search");
        return [];
    }
}

export async function updateProjects(projects: any[]) {
    const requestBody = {
        RequestInfo: defaultRequestInfo?.RequestInfo,
        Projects : projects
    }

    const url = config.host.projectHost + config.paths.projectUpdate;
    await httpRequest(url, requestBody);
}

export async function createProjectsAndGetCreatedProjects(projects: any[], userUuid: string) {
    const RequestInfo = {
        ...defaultRequestInfo?.RequestInfo,
        userInfo: {
            uuid : userUuid
        }
    }
    const requestBody = {
        RequestInfo,
        Projects : projects
    }

    const url = config.host.projectHost + config.paths.projectCreate;
    const response = await httpRequest(url, requestBody);

    if (Array.isArray(response?.Project)) {
        return response?.Project;
    } else {
        throwError("PROJECT", 500, "PROJECT_CREATION_ERROR", "Error occurred during project creation");
        return [];
    }
}
