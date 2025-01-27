const envVariables = require('./EnvironmentVariables');
const logger = require('./logger');
const express = require('express');
const proxy = require('express-http-proxy');
const { createProxyMiddleware } = require('http-proxy-middleware');
const axios = require('axios');
const deasync = require('deasync');
const querystring = require('querystring');
const app = express();

let serverPort = envVariables.SERVER_PORT;
let kibanaHost = envVariables.KIBANA_HOST;
let kibanaServerBasePath = envVariables.KIBANA_BASE_PATH;
let allowedContextPaths = envVariables.KIBANA_ACCEPTED_CONTEXT_UI_PATHS;
let acceptedDomain = envVariables.KIBANA_ACCEPTED_DOMAIN_NAME;
let excludeUrls = envVariables.KIBANA_EXCLUDE_URL_PATTERNS;

// Authenticate token
async function authenticateToken(token) {
    const url = envVariables.EGOV_USER_HOST + envVariables.EGOV_USER_SEARCH;
    const queryParams = { access_token: token };

    logger.info("Making API call to - " + url);

    const isAuthenticated = await axios.post(url, null, { params: queryParams })
        .then(response => {
            logger.info("User call response: ", response?.status, response?.data);
            return response.status === 200
        })
        .catch(error => {
            logger.error('Error during authentication: {}', error?.response?.data || error?.response || error);
            return false;
        });
    logger.info("Is authenticated: ", isAuthenticated);
    return isAuthenticated;
}

function bypassAuthBasedOnUrl(url) {
    const excluded = excludeUrls.split(",");
    return excluded.some(substring => url.includes(substring));
}

function validateReferer(url) {
    try {
        if(!url) {
            logger.error("Error: Referer is null");
            return false;
        }
        // Create a URL object from the input string
        const urlObj = new URL(url);

        // Extract the hostname (domain) from the URL object
        const domain = urlObj.hostname;

        // Split the pathname into parts
        const pathParts = urlObj.pathname.split('/').filter(part => part.length > 0);

        // Extract the path just ahead of the domain, which is the first part of the path
        const contextPath = pathParts.length > 0 ? pathParts[0] : '';
        logger.info("pathname" + urlObj?.pathname);
        logger.info("current domain: " + domain);
        logger.info("context path: " + contextPath);

        //based on domain and contextPath return true or false
        if (domain === acceptedDomain && allowedContextPaths?.split(",")?.some(path => ((path === contextPath) || urlObj?.pathname?.startsWith(path)))) {
            return true;
        } else {
            return false;
        }

    } catch (error) {
        // Handle invalid URL errors
        console.error('Unable to validate referer:', error);
        return false;
    }
}

// Intercept Kibana requests, extract auth token and perform authentication
app.use(async (req, res, next) => {
    logger.info("Received request");

    // Check if "replace-url" header is present
    // const replaceUrl = req.headers['replace-url'];
    // if (replaceUrl) {
    //     logger.info("Replace URL header found, fetching from URL - " + replaceUrl);
    //     try {
    //         const fetchResponse = await axios.get(replaceUrl);

    //         res.status(fetchResponse.status);
    //         for (const [name, value] of Object.entries(fetchResponse.headers)) {
    //             res.setHeader(name, value);
    //         }
    //         res.send(fetchResponse.data);
    //         return; // Exit the middleware chain
    //     } catch (error) {
    //         console.error('Error fetching replace-url:', error);
    //         res.status(500).send('Internal Server Error');
    //         return; // Exit the middleware chain
    //     }
    // }

    // Extract auth token from headers
    const authToken = req.headers['authorization'];
    const typeReq = req.headers['type-req'];
    const acceptHeader = req.headers['accept'];
    const referer = req.headers['referer'];
    logger.info("Received request path - " + req.url);

    //---------new code -----------

    //first check for calls where authentication is not required
    if (bypassAuthBasedOnUrl(req.originalUrl)) {
        logger.info("Bypassing auth based on url: {}", req.originalUrl);
        next();
        return;
    }

    //now checking for document 
    if (acceptHeader && acceptHeader.includes('text/html')) {
        //if referer is digit ui then bypass
        if (validateReferer(referer)) {
            next();
            return;
        } else {
            res.status(403).send('Access denied');
            return;
        }
    }

    //this means either fetch or xhr -> proceed to authenticate this request
    if (typeReq) {
        if (!authToken || authToken.trim() === '') {
            res.status(401).send('Unauthorized: No auth token provided');
            return;
        }
        const isAuthenticated = await  authenticateToken(authToken);
        logger.info("Is authenticated: ", isAuthenticated);
        if (!isAuthenticated) {
            res.status(403).send('Access denied'); // Send a 403 error if not authenticated
            return
        }
    }

    logger.info("Continuing with request for kibana proxy");
    next();
});

const kibanaProxy = createProxyMiddleware({
    target: kibanaHost,
    changeOrigin: true,
    headers: {
        'kbn-xsrf': 'true'
    },
    followRedirects: true
});

app.use('/', kibanaProxy);

// Listen on configured port
app.listen(serverPort, () => {
    logger.info(`Server running at http:${serverPort}/`);
    logger.info("Debug enabled: " + envVariables.DEBUG_ENABLED);
});
