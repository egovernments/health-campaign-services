import React, { useState, useEffect, createContext } from 'react';
import axios from 'axios';

const AuthContext = createContext({
  token: '',
  roles: '',
  isLoggedIn: false,
  login: (token, roles) => {},
  logout: () => {},
});

export const AuthContextProvider = (props) => {
  const [token, setToken] = useState(localStorage.getItem('access_token') || '');
  const [roles, setRoles] = useState(localStorage.getItem('roles') || '');
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  // Log current state
  // console.log('AuthContext initialized:', { token, roles, isLoggedIn });

  // Update isLoggedIn whenever token & role changes
  useEffect(() => {
    const loggedIn = !!token && !!roles;
    setIsLoggedIn(loggedIn);
    // console.log('isLoggedIn updated:', loggedIn);
  }, [token, roles]);

  const loginHandler = (access_token, roles, guid, user_name) => {
    // console.log('loginHandler called:', { access_token, roles, guid, user_name });

    if (!access_token || !roles) {
      console.warn('Invalid login data. Logging out...');
      logoutUser();
      return;
    }

    setToken(access_token);
    setRoles(roles);

    localStorage.setItem('access_token', access_token);
    localStorage.setItem('roles', roles);
    localStorage.setItem('guid', guid);
    localStorage.setItem('user_name', user_name);
  };

  const logoutUser = () => {
    // console.log('Logging out user.......');

    setToken('');
    setRoles('');
    setIsLoggedIn(false);

    localStorage.removeItem('access_token');
    localStorage.removeItem('roles');
    localStorage.removeItem('guid');
    localStorage.removeItem('user_name');
  };

  // Axios interceptor to add token
  // useEffect(() => {
  //   const requestInterceptor = axios.interceptors.request.use(
  //     (config) => {
  //       if (token) {
  //         config.headers.Authorization = `Bearer ${token}`;
  //       }
  //       return config;
  //     },
  //     (error) => Promise.reject(error)
  //   );

  //   const responseInterceptor = axios.interceptors.response.use(
  //     (response) => response,
  //     (error) => {
  //       if (error?.response?.status === 401) {
  //         console.warn("Unauthorized. Logging out user.");
  //         logoutUser();
  //       }
  //       return Promise.reject(error);
  //     }
  //   );

  //   return () => {
  //     axios.interceptors.request.eject(requestInterceptor);
  //     axios.interceptors.response.eject(responseInterceptor);
  //   };
  // }, [token]);

  return <AuthContext.Provider value={{ token, roles, isLoggedIn, login: loginHandler, logout: logoutUser }}>{props.children}</AuthContext.Provider>;
};

export default AuthContext;
