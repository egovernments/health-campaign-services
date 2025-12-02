import axios from 'axios';
import config from '../config';
import { notification } from 'antd';

const axiosInstance = axios.create({
  baseURL: config.backendHost,
  headers: {
    'Content-Type': 'application/json',
  },
});

// let isLogoutMessageDisplayed = false;

// REQUEST INTERCEPTOR
axiosInstance.interceptors.request.use(
  async (config) => {
    try {
      const accessToken = localStorage.getItem('access_token');
      const method = config.method?.toLowerCase();

      if (accessToken) {
        if (method === 'get') {
          // For GET: send in headers
          config.headers['auth-token'] = accessToken;
        } else if (['post', 'put', 'delete'].includes(method)) {
          // For POST/PUT/DELETE: send in body → RequestInfo.authToken
          if (!config.data || typeof config.data !== 'object') {
            config.data = { RequestInfo: { authToken: accessToken } };
          } else {
            config.data = {
              ...config.data,
              RequestInfo: {
                ...(config.data.RequestInfo || {}),
                authToken: accessToken,
              },
            };
          }
        }
      }
    } catch (error) {
      console.error('Error attaching authentication token:', error);
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// // LOGOUT FUNCTION
// const logOut = async () => {
//   try {
//     localStorage.removeItem("access_token");
//     localStorage.removeItem("refreshToken");
//     window.location.href = "/";
//   } catch (err) {
//     console.error(err);
//   }
// };

// // 401 ERROR HANDLER
// const handleUnauthorizedError = (error) => {
//   if (!isLogoutMessageDisplayed) {
//     notification.error({
//       message: "Unauthorized",
//       description: error?.response?.data?.error || "Session expired. Logging out.",
//     });
//     isLogoutMessageDisplayed = true;
//   }

//   setTimeout(() => {
//     logOut();
//   }, 2000);
// };

// // RESPONSE INTERCEPTOR
// axiosInstance.interceptors.response.use(
//   (response) => response,
//   (error) => {
//     if (error?.response?.status === 401) {
//       handleUnauthorizedError(error);
//     }
//     return Promise.reject(error);
//   }
// );

export default axiosInstance;
