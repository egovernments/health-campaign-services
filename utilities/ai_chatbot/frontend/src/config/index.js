/* eslint-disable import/no-anonymous-default-export */
 
const isProd = process.env.NODE_ENV === 'production';
 
const host = isProd ? window.location.hostname : process.env.REACT_APP_LOCATION;
const backendPort = isProd ? process.env.REACT_APP_PROD_NGINX_PORT : process.env.REACT_APP_BACKEND_PORT;
const supersetPort = isProd ? process.env.REACT_APP_PROD_SUPERSET_PORT : process.env.REACT_APP_SUPERSET_PORT;
 
const config = {
  backendHost: `http://${host}:${backendPort}`,
  supersetUrl: `http://${host}:${supersetPort}`,
};
 
export default config;