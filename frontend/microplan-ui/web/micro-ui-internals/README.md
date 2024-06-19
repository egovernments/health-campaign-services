# microplan ui

A React App built on top of DIGIT UI Core.

# DIGIT UI

DIGIT (Digital Infrastructure for Governance, Impact & Transformation) is India's largest platform for governance services. Visit https://www.digit.org for more details.

This repository contains source code for web implementation of the new Digit UI modules with dependencies and libraries.

Microplan module is used to Manage the master data (MDMS V2 Service) used across the DIGIT Services / Applications

It is also used to manage the Localisation data present in the system (Localisation service)

## Run Locally

Clone the project

```bash
  git clone https://github.com/egovernments/Digit-Core.git
```

Go to the Sub directory to run UI

```bash
    cd into frontend/micro-ui/web/micro-ui-internals
```

Install dependencies

```bash
  yarn install
```

Add .env file

```bash
    frontend/micro-ui/web/micro-ui-internals/example/.env
```

Start the server

```bash
  yarn start
```

## Environment Variables

To run this project, you will need to add the following environment variables to your .env file

`REACT_APP_PROXY_API` :: `{{server url}}`

`REACT_APP_GLOBAL` :: `{{server url}}`

`REACT_APP_PROXY_ASSETS` :: `{{server url}}`

`REACT_APP_USER_TYPE` :: `{{EMPLOYEE||CITIZEN}}`

`SKIP_PREFLIGHT_CHECK` :: `true`

[sample .env file]()

## Tech Stack

**Libraries:**

[React](https://react.dev/)

[React Hook Form](https://www.react-hook-form.com/)

[React Query](https://tanstack.com/query/v3/)

[Tailwind CSS](https://tailwindcss.com/)

[Webpack](https://webpack.js.org/)

## License

[MIT](https://choosealicense.com/licenses/mit/)

# Section Information

Form composer has not been used

## Create Flow

- Users start by selecting a campaign to create a new microplan.
- They fill in basic microplan details such as campaign name, type, start date, and end date.
- After selecting a campaign, users upload relevant files or data required for the microplan.
- They list out any hypotheses or assumptions related to the microplan.
- Users configure any necessary formulas or calculations for the microplan.
- They have the option to visualize uploaded data on a map for better understanding.
- The microplan is generated based on the input provided in previous steps.
- Users can edit hypotheses and observe how they fit their use case best.
- The process is managed by the Create Microplan wrapper, which handles state initialization, session storage for temporary data storage
- and navigation through different creation steps using a stepper component.

## Opening Saved Microplan:

- Users navigate to a screen displaying their saved microplan drafts upon clicking "Open Saved Microplan."
- Upon landing, the system initiates a search for plan configurations to populate the table by default with microplan drafts.
- Users can refine their search using fields like Microplan Name and Status.
- Clicking on a row in the table redirects users to edit the selected microplan draft, pre-filling data from the chosen draft.
- Triggered upon row selection, it manages tasks such as setting the current page to the first step of microplan creation, establishing status for sections like MICROPLAN_DETAILS, UPLOAD_DATA, HYPOTHESIS, and FORMULA_CONFIGURATION, and setting necessary data and identifiers (planConfigurationId and auditDetails).

## Microplan Details

- The first card contains details about the campaign.
- The second card is a blank field where a name for the microplan needs to be entered.

## Upload Section

- Users upload files (Excel, GeoJSON, Shapefile) associated with MDMS configurations, campaign details, and hierarchy data.
- Users trigger UploadFileToFileStorage function upon file selection.
- Validates file name against system requirements.
- Uses switch case based on file type:
- Excel: Validations and conversion via handleExcelFile.
- GeoJSON: Validations and handling with handleGeojsonFile.
- Shapefile: Similar handling to GeoJSON.
- Halts upload on validation failure, provides specific error messages-
- Successfully validated files are uploaded to the file store.
- Requires resource mapping for GeoJSON and Shapefile uploads.
- Modal "SpatialDataPropertyMapping" assists in column mapping.
- Validates against MDMS schemas using Ajv library.
- Fetches missing hierarchy data in case of facility from session storage or API.
- Utilizes fetchBoundaryDataWrapper to process and integrate boundary data.
- Double-clicking stored file displays data in Excel-like format (JsonPreviewInExcelForm).
- Uses Ajv library for validating JSON data against MDMS schemas.

## Hypothesis Section:

- The hypothesis autofill data is extracted and inserted into the user interface (UI).
- This action pre-populates the UI with a list of hypothesis keys, leaving the corresponding values blank for the user to fill.
- Customized selects and inputs are employed to enforce constraints on the inputs.
- Users are prompted to select only one hypothesis key from the provided list.

## Rule Engine Section:

- Combines input data, DBMS operators, and selected hypotheses to compute flexible outputs.
- Efficiently stores and retrieves rules using Session Storage for consistency.
- Manages rule data and session-specific information to maintain continuity.
- Uses MDMS autofill data to populate rules dynamically based on predefined constraints.
- Includes functions like setRuleEngineDataFromSsn to streamline rule utilization and setAutoFillRules for automated rule configuration.

## Mapping:

- The "init" function uses Leaflet to initialize the map, configuring parameters like the map element ID and base map data.
- The system processes uploaded data by sequentially executing functions to extract, format, and plot GeoJSON features on the map.
- Users select boundaries from a dropdown, and the system uses this selection to generate and plot GeoJSON features on the map, enhancing spatial visualization.
- Functions like extractGeoData, processHierarchyAndData, prepareGeoJson, and addGeojsonToMap handle data extraction, hierarchical processing, GeoJSON preparation, and visualization on the map, utilizing helper functions for comprehensive data management and display.

## Microplan Preview:

- React component responsible for displaying and managing microplan data.
- Supports viewing, editing, and finalizing changes to microplan data.
- Handles modal states and user interactions effectively.
- Resource Calculation Includes functions like calculateResource, findInputValue, and findResult.
- Calculates resource values based on formulas, assumptions, and input data.
- Facilitates dynamic calculation and display of resource data within the preview.
- Utility Functions provide essential utilities like getRequiredColumnsFromSchema, innerJoinLists, and filterObjects.
- Supports data validation, merging, and filtering operations.
- Enhances data management capabilities within the microplan preview.
- Hierarchy and Data Filtering are the Functionality for filtering microplan data based on user-selected hierarchy levels.
- Improves data visibility and relevance by applying hierarchical filters effectively.

## Author

- [@jagankumar-egov](https://www.github.com/jagankumar-egov)

## Documentation

[Documentation](https://https://core.digit.org/guides/developer-guide/ui-developer-guide/digit-ui)

## Support

For support, add the issues in https://github.com/egovernments/DIGIT-core/issues.

![Logo](https://s3.ap-south-1.amazonaws.com/works-dev-asset/mseva-white-logo.png)
