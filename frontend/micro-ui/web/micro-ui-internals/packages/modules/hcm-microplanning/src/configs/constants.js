export const LOCALITY = "Locality";

export const EXCEL = "Excel";

export const GEOJSON = "GeoJSON";

export const SHAPEFILE = "Shapefile";

export const commonColumn = "boundaryCode";

export const ACCEPT_HEADERS = {
  GeoJSON: "application/geo+json",
  Shapefile: "application/shapefile",
  Excel: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
};

// Define the colors of the gradient for choropleth mapping
export const MapChoroplethGradientColors = [
  { percent: 0, color: "#edd1cf" },
  { percent: 100, color: "#b52626" },
];

export const PRIMARY_THEME_COLOR = "#C84C0E";

export const BOUNDARY_DATA_SHEET = "MICROPLAN_BOUNDARY_DATA_SHEET";
export const FACILITY_DATA_SHEET = "MICROPLAN_FACILITY_DATA_SHEET";

export const FILE_STORE = "microplan";

export const SHEET_PASSWORD = "eGov_sheet_password";

export const SHEET_COLUMN_WIDTH = 40;

export const SCHEMA_PROPERTIES_PREFIX = "DISPLAY";

export const UNPROTECT_TILL_ROW = "10000";
