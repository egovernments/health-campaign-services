import axiosInstance from './axiosInstance';

// CONFIGURATIONS
export const postPerformAction = (data) => {
  return axiosInstance.post(`/api/v1/configurations`, data);
};

export const updateConfigurationById = (data, id) => {
  return axiosInstance.put(`/api/v1/configurations/` + id, data);
};

export const updateConfigurationStatus = (data, id) => {
  return axiosInstance.put(`/api/v1/configurations/${id}/updateStatus`, data);
};

export const getAllConfigurations = () => {
  return axiosInstance.get(`/api/v1/configurations`);
};

export const deleteConfiguration = (id) => {
  return axiosInstance.delete(`/api/v1/configurations/` + id);
};

export const getConfigurationById = (id) => {
  return axiosInstance.get(`/api/v1/configurations/` + id);
};

export const getDashboardsByConfiguration = (id) => {
  return axiosInstance.get(`/api/v1/configurations/${id}/dashboards`);
};

export const getMacros = () => {
  return axiosInstance.get(`api/v1/configurations/macros`);
};

export const testConnection = (id) => {
  return axiosInstance.post(`api/v1/connections/${id}/test`);
};

export const getSupportedConnections = () => {
  return axiosInstance.get('api/v1/connections/supported');
};

// PLATFORM CONFIGURATION
export const getPlatformConfig = () => {
  return axiosInstance.get(`/api/v1/configs`);
};

export const platformLogin = (data) => {
  return axiosInstance.get(`/api/v1/users/login`, data);
};

export const getSessionData = (id) => {
  return axiosInstance.get(`/api/v1/chats/` + id);
};

export const updatePlatformCofigByName = (name, data) => {
  return axiosInstance.put(`/api/v1/configs/${name}`, data);
};

export const getDefaultPrompt = (db_type) => {
  return axiosInstance.get('/api/v1/configurations/defaultPrompt', {
    params: { db_type },
  });
};

export const getVersion = () => {
  return axiosInstance.get('/api/v1/version');
};

// TAGS
export const createNewTag = (data) => {
  return axiosInstance.post('/api/v1/tags', data);
};

export const getAllTags = () => {
  return axiosInstance.get('/api/v1/tags');
};

export const editTag = (id, data) => {
  return axiosInstance.put(`/api/v1/tags/${id}`, data);
};

export const deleteTag = (id) => {
  return axiosInstance.delete(`/api/v1/tags/${id}`);
};

export const getTagsById = (id) => {
  return axiosInstance.get(`/api/v1/tags/${id}`);
};

// USER
export const postCreateUser = (data) => {
  return axiosInstance.post(`/api/v1/users`, data);
};

export const updateUserStatus = (data, id) => {
  return axiosInstance.put(`/api/v1/users/${id}/updateStatus`, data);
};

export const getAllUsers = () => {
  return axiosInstance.get('/api/v1/users');
};

export const getUserById = (id) => {
  return axiosInstance.get(`/api/v1/users/${id}`);
};
export const getUserDetailsByGuid = (id) => {
  return axiosInstance.get(`/api/v1/users/guid/${id}`);
};

export const deleteUser = (id) => {
  return axiosInstance.delete(`/api/v1/users/` + id);
};

export const updateUserById = (id, data) => {
  return axiosInstance.put(`/api/v1/users/${id}`, data);
};

// PASSWORD & SETTINGS
export const changePassword = (id, data) => {
  return axiosInstance.put(`/api/v1/users/${id}/updatePassword`, data);
};

export const getUISettings = () => {
  return axiosInstance.get(`/api/v1/configs/uiSettings`);
};

export const getAllHistory = (page = 1, perPage = 10) => {
  return axiosInstance.get(`/api/v1/chats`, {
    params: {
      page: page,
      per_page: perPage,
    },
  });
};

export const updateResponseFeedback = (data, id) => {
  return axiosInstance.put(`/api/v1/chats/${id}/updateFeedback`, data);
};

export const getHistoryCount = () => {
  return axiosInstance.get(`/api/v1/chats/count`);
};

// LLM Configurations
export const createNewModel = (data) => {
  return axiosInstance.post(`/api/v1/models`, data);
};

export const getAllModels = () => {
  return axiosInstance.get(`/api/v1/models`);
};

export const getModelById = (id) => {
  return axiosInstance.get(`/api/v1/models/${id}`);
};

export const editModel = (id, data) => {
  return axiosInstance.put(`/api/v1/models/${id}`, data);
};

export const deleteModel = (id) => {
  return axiosInstance.delete(`/api/v1/models/` + id);
};

export const updateModelStatus = (data, id) => {
  return axiosInstance.put(`/api/v1/models/${id}/updateStatus`, data);
};

export const updateDefaultStatus = (data, id) => {
  return axiosInstance.put(`/api/v1/models/${id}/default`, data);
};

export const getDefaultModel = () => {
  return axiosInstance.get(`/api/v1/models/default`);
};

export const getSupportedModel = () => {
  return axiosInstance.get('/api/v1/models/supported');
};

export const getModelSettingsTemplate = (ModelName) => {
  return axiosInstance.get('/api/v1/models/getSettingsTemplate', {
    params: { type: ModelName },
  });
};

// Favourite Questions
export const addToFavorites = (data, guid) => {
  return axiosInstance.post(`/api/v1/users/${guid}/favorites`, data);
};

export const getAllFavorites = (guid) => {
  return axiosInstance.get(`/api/v1/users/${guid}/favorites`);
};

export const updateFavoriteById = (guid, fav_id, data) => {
  return axiosInstance.put(`/api/v1/users/${guid}/favorites/${fav_id}`, data);
};

export const deleteFavoriteById = (guid, fav_id) => {
  return axiosInstance.delete(`/api/v1/users/${guid}/favorites/${fav_id}`);
};

// ChatBot
export const getChatResponse = (data) => {
  return axiosInstance.post(`/api/v1/chats/sendQuery`, data);
};

export const getChatHistory = (session_id) => {
  return axiosInstance.get(`/api/v1/chats/${session_id}`);
};

export const addToExample = (id, data) => {
  return axiosInstance.post(`/api/v1/configurations/${id}/examples`, data);
};

// Connections
export const getAllConnections = () => {
  return axiosInstance.get(`/api/v1/connections`);
};

export const createNewConnection = (data) => {
  return axiosInstance.post(`/api/v1/connections`, data);
};

export const editConnection = (id, data) => {
  return axiosInstance.put(`/api/v1/connections/${id}`, data);
};

export const getConnectionById = (id) => {
  return axiosInstance.get(`/api/v1/connections/${id}`);
};

export const deleteConnection = (id) => {
  return axiosInstance.delete(`/api/v1/connections/${id}`);
};
