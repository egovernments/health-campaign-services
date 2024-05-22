const envVariables = require('./EnvironmentVariables');
const logger = require('./logger');
const express = require('express');
const proxy = require('express-http-proxy');
const axios = require('axios');
const deasync = require('deasync');
const app = express();

let serverPort = envVariables.SERVER_PORT;
let kibanaHost = envVariables.KIBANA_HOST;
let kibanaServerBasePath = envVariables.KIBANA_BASE_PATH;

// Authenticate token
function authenticateToken(token) {
    const url = envVariables.EGOV_USER_HOST + envVariables.EGOV_USER_SEARCH;
    const queryParams = { access_token: token };
    let result;

    logger.info("Making API call to - " + url);

    axios.post(url, null, { params: queryParams })
        .then(response => {
            result = response.status === 200;
        })
        .catch(error => {
            console.error('Error during authentication:', error.response.data);
            result = false;
        });

    // Synchronously wait until the API call completes
    deasync.loopWhile(() => result === undefined);

    return result;
}

// Intercept Kibana requests, extract auth token and perform authentication
app.use((req, res, next) => {
    logger.info("Received request");

    // Extract auth token from headers
    const authToken = req.headers['authorization'];
    const typeReq = req.headers['type-req'];
    const acceptHeader = req.headers['accept'];

    logger.info("Received request path - " + req.url);

    if (typeReq === 'document') {
        logger.info("Bypassing authentication for document type request");
        next(); // Bypass authentication for document type requests
    } else if (acceptHeader && acceptHeader.includes('text/html')) {
        logger.info("Bypassing authentication for requests with Accept header containing text/html");
        next(); // Bypass authentication for requests with Accept header containing text/html
    } else if (req.originalUrl.includes('/app/kibana') && !req.originalUrl.includes('/bundles/')) {
        // Check if authToken is empty or null
        if (!authToken || authToken.trim() === '') {
            res.status(401).send('Unauthorized: No auth token provided');
            return;
        }

        if (authenticateToken(authToken)) {
            next(); // Proceed to the proxy if authenticated
        } else {
            res.status(403).send('Access denied'); // Send a 403 error if not authenticated
            return;
        }
    } else {
        next();
    }
});

// Proxy request to Kibana if authentication is successful
app.use('/', proxy(kibanaHost + kibanaServerBasePath));

// Listen on configured port
app.listen(serverPort, () => {
    logger.info(`Server running at http:${serverPort}/`);
});
