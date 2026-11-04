const fs = require("fs");
const path = require("path");
const yaml = require("js-yaml");

const projectRoot = path.resolve(__dirname, "..");
const controllersDir = path.join(projectRoot, "src", "server", "controllers");
const outputFile = path.join(projectRoot, "swagger.generated.json");
const legacySwaggerPath = path.join(projectRoot, "swagger.yml");

const tagDescriptions = {
  "Campaign Management": "Health campaign lifecycle management operations",
  "Data Management (V1)": "Legacy data generation and processing endpoints",
  "Sheet Management (V2)":
    "Modern template-based Excel processing with validation",
  "Crypto Operations": "Encryption and decryption utilities",
  Localization: "Multi-language support and message management (Internal APIs)",
};

function listControllerFiles(dirPath) {
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  const files = [];

  entries.forEach((entry) => {
    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      files.push(...listControllerFiles(fullPath));
      return;
    }

    if (entry.isFile() && entry.name.endsWith(".controller.ts")) {
      files.push(fullPath);
    }
  });

  return files;
}

function extractTagValue(routeBlock, defaultTag) {
  const match =
    routeBlock.match(/#swagger\.tags\s*=\s*\[\s*'([^']+)'\s*\]/) ||
    routeBlock.match(/#swagger\.tags\s*=\s*\[\s*"([^"]+)"\s*\]/);
  return match ? match[1] : defaultTag;
}

function extractSummaryValue(routeBlock) {
  const match =
    routeBlock.match(/#swagger\.summary\s*=\s*'([^']+)'/) ||
    routeBlock.match(/#swagger\.summary\s*=\s*"([^"]+)"/);
  return match ? match[1] : "API operation";
}

function extractDescriptionValue(routeBlock, fallbackSummary) {
  const match =
    routeBlock.match(/#swagger\.description\s*=\s*'([^']+)'/) ||
    routeBlock.match(/#swagger\.description\s*=\s*"([^"]+)"/);
  return match ? match[1] : fallbackSummary;
}

function extractControllerBasePath(fileContent) {
  const pathMatch = fileContent.match(
    /public\s+path\s*=\s*["'`]([^"'`]+)["'`]\s*;/
  );
  return pathMatch ? pathMatch[1] : "";
}

function resolveEndpointPath(rawPath, basePath) {
  if (!rawPath) return rawPath;
  const resolvedPath = rawPath
    .replace(/\$\{\s*this\.path\s*\}/g, basePath || "")
    .replace(/\/{2,}/g, "/");
  return resolvedPath.startsWith("/") ? resolvedPath : `/${resolvedPath}`;
}

function buildDefaultTagFromFilePath(filePath) {
  const baseName = path.basename(filePath, ".controller.ts");
  return toPascalCase(baseName).replace(/([A-Z])/g, " $1").trim();
}

function extractRoutes(fileContent, filePath) {
  const routes = [];
  const basePath = extractControllerBasePath(fileContent);
  const defaultTag = buildDefaultTagFromFilePath(filePath);

  const routeRegex =
    /this\.router\.(get|post|put|patch|delete)\s*\(\s*["'`]([^"'`]+)["'`]\s*,([\s\S]*?)\)\s*;/g;
  let match;

  while ((match = routeRegex.exec(fileContent)) !== null) {
    const [, method, endpointPath, routeBlock] = match;
    const summary = extractSummaryValue(routeBlock);
    routes.push({
      method: method.toLowerCase(),
      endpointPath: resolveEndpointPath(endpointPath, basePath),
      tag: extractTagValue(routeBlock, defaultTag),
      summary,
      description: extractDescriptionValue(routeBlock, summary),
    });
  }

  return routes;
}

function toPascalCase(value) {
  return value
    .replace(/[^a-zA-Z0-9]+/g, " ")
    .trim()
    .split(/\s+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join("");
}

function buildSchemaBaseName(method, endpointPath) {
  const normalizedPath = endpointPath.replace(/^\/+/, "").replace(/\//g, " ");
  return `${toPascalCase(method)}${toPascalCase(normalizedPath)}`;
}

function loadLegacySwagger() {
  if (!fs.existsSync(legacySwaggerPath)) {
    return null;
  }

  const content = fs.readFileSync(legacySwaggerPath, "utf8");
  return yaml.load(content);
}

function buildSwaggerSpec() {
  const endpointFiles = listControllerFiles(controllersDir);
  if (endpointFiles.length === 0) {
    throw new Error(`No controller files found under ${controllersDir}`);
  }

  const contextPath = process.env.CONTEXT_PATH || "/project-factory";
  const paths = {};
  const usedTags = new Set();
  const legacySwagger = loadLegacySwagger();
  const legacyPaths = legacySwagger?.paths || {};
  const legacySchemas = legacySwagger?.components?.schemas || {};

  const components = {
    schemas: {
      ErrorResponse: {
        type: "object",
        properties: {
          Errors: {
            type: "array",
            items: {
              type: "object",
              properties: {
                code: { type: "string" },
                message: { type: "string" },
                description: { type: "string" },
              },
            },
          },
        },
      },
      ...legacySchemas,
    },
  };

  endpointFiles.forEach((file) => {
    const content = fs.readFileSync(file, "utf8");
    const routes = extractRoutes(content, file);

    routes.forEach(({ method, endpointPath, tag, summary, description }) => {
      usedTags.add(tag);
      if (!paths[endpointPath]) {
        paths[endpointPath] = {};
      }

      const schemaBaseName = buildSchemaBaseName(method, endpointPath);
      const requestSchemaName = `${schemaBaseName}Request`;
      const responseSchemaName = `${schemaBaseName}Response`;
      const legacyOperation = legacyPaths?.[endpointPath]?.[method];

      if (!legacyOperation?.requestBody && !components.schemas[requestSchemaName]) {
        components.schemas[requestSchemaName] = {
          type: "object",
          description: `Request schema for ${method.toUpperCase()} ${endpointPath}`,
          properties: {
            RequestInfo: {
              $ref: "#/components/schemas/RequestInfo",
            },
            payload: {
              type: "object",
              description:
                "Replace this placeholder payload with the exact contract for this endpoint.",
              additionalProperties: true,
            },
          },
          additionalProperties: true,
        };
      }

      if (!legacyOperation?.responses?.["200"] && !components.schemas[responseSchemaName]) {
        components.schemas[responseSchemaName] = {
          type: "object",
          description: `Response schema for ${method.toUpperCase()} ${endpointPath}`,
          properties: {
            ResponseInfo: {
              type: "object",
              additionalProperties: true,
            },
            data: {
              type: "object",
              description:
                "Replace this placeholder response data with the exact response contract.",
              additionalProperties: true,
            },
          },
          additionalProperties: true,
        };
      }

      const operation = {
        tags: [tag],
        summary,
        description,
        requestBody:
          legacyOperation?.requestBody ||
          {
            required: true,
            content: {
              "application/json": {
                schema: {
                  $ref: `#/components/schemas/${requestSchemaName}`,
                },
              },
            },
          },
        responses: {
          200:
            legacyOperation?.responses?.["200"] ||
            {
              description: "Success",
              content: {
                "application/json": {
                  schema: {
                    $ref: `#/components/schemas/${responseSchemaName}`,
                  },
                },
              },
            },
          400:
            legacyOperation?.responses?.["400"] ||
            {
              description: "Bad Request",
              content: {
                "application/json": {
                  schema: {
                    $ref: "#/components/schemas/ErrorResponse",
                  },
                },
              },
            },
          500:
            legacyOperation?.responses?.["500"] ||
            {
              description: "Internal Server Error",
              content: {
                "application/json": {
                  schema: {
                    $ref: "#/components/schemas/ErrorResponse",
                  },
                },
              },
            },
        },
      };

      if (method === "get" || method === "delete") {
        delete operation.requestBody;
      }

      paths[endpointPath][method] = operation;
    });
  });

  return {
    openapi: "3.0.0",
    info: {
      title: "Project Factory API",
      description: "Auto-generated OpenAPI specification for project-factory.",
      version: "1.0.0",
    },
    servers: [{ url: contextPath }],
    tags: Array.from(usedTags)
      .sort((a, b) => a.localeCompare(b))
      .map((name) => ({
        name,
        description:
          tagDescriptions[name] || `${name} API endpoints for project-factory`,
      }))
      .concat(
        usedTags.has("Localization")
          ? []
          : [
              {
                name: "Localization",
                description: tagDescriptions.Localization,
              },
            ]
      ),
    paths,
    components,
  };
}

function generateSwaggerSpec() {
  const spec = buildSwaggerSpec();
  fs.writeFileSync(outputFile, JSON.stringify(spec, null, 2));
  console.log(`Swagger spec generated at ${outputFile}`);
}

try {
  generateSwaggerSpec();
} catch (error) {
  console.error("Failed to generate swagger specification", error);
  process.exit(1);
}
