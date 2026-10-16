const express = require("express");
const swaggerUi = require("swagger-ui-express");
const fs = require("fs");
const path = require("path");

const app = express();
const port = Number(process.env.SWAGGER_PORT || 8099);
const contextPath = process.env.CONTEXT_PATH || "/project-factory";
const specPath = path.resolve(__dirname, "..", "swagger.generated.json");

if (!fs.existsSync(specPath)) {
  console.error(
    `Swagger spec not found at ${specPath}. Run "yarn swagger:generate" first.`
  );
  process.exit(1);
}

const swaggerSpec = JSON.parse(fs.readFileSync(specPath, "utf-8"));
const swaggerUiPath = `${contextPath}/swagger`;
const swaggerJsonPath = `${contextPath}/swagger.json`;

app.get(swaggerJsonPath, (_req, res) => {
  res.setHeader("Content-Type", "application/json");
  res.send(swaggerSpec);
});

app.use(swaggerUiPath, swaggerUi.serve, swaggerUi.setup(swaggerSpec));

app.listen(port, () => {
  console.log(`Swagger UI: http://localhost:${port}${swaggerUiPath}`);
  console.log(`Swagger JSON: http://localhost:${port}${swaggerJsonPath}`);
});
