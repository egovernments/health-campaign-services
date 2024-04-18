const envVariables = {
    SERVER_PORT: 
      process.env.SERVER_PORT || 8085,
    EGOV_USER_HOST:
      process.env.EGOV_USER_HOST || "http://localhost:8081/",
    EGOV_USER_SEARCH:
      process.env.EGOV_USER_SEARCH || "user/_details",
    KIBANA_HOST:
      process.env.KIBANA_HOST || "http://localhost:30001/",
    KIBANA_BASE_PATH:
      process.env.KIBANA_BASE_PATH || "kibana"
  };
  
  module.exports = envVariables;