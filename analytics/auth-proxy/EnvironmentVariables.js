const envVariables = {
    SERVER_PORT: 
      process.env.SERVER_PORT || 8085,
    DEBUG_ENABLED: 
      process.env.DEBUG_ENABLED || false,
    EGOV_USER_HOST:
      process.env.EGOV_USER_HOST || "http://localhost:8081/",
    EGOV_USER_SEARCH:
      process.env.EGOV_USER_SEARCH || "user/_details",
    KIBANA_HOST:
      process.env.KIBANA_HOST || "http://localhost:30001/",
    KIBANA_BASE_PATH:
      process.env.KIBANA_BASE_PATH || "kibana",
    KIBANA_ACCEPTED_CONTEXT_UI_PATHS:
      process.env.KIBANA_ACCEPTED_CONTEXT_UI_PATHS || "workbench-ui,sanitation-ui,works-ui,digit-ui,health-ui,/kibana/app/dashboards,/kibana/login",
    KIBANA_ACCEPTED_DOMAIN_NAME:
      process.env.KIBANA_ACCEPTED_DOMAIN_NAME || "localhost",
    KIBANA_EXCLUDE_URL_PATTERNS:
    process.env.KIBANA_EXCLUDE_URL_PATTERNS || ".js,.css,.html,fonts,favicons,telemetry,.json"

  };
  
  module.exports = envVariables;