const envVariables = require('./EnvironmentVariables');
const logger = require('./logger');
const express = require('express')
const proxy = require('express-http-proxy')
const axios = require('axios');
const deasync = require('deasync');
const app = express()

let serverPort = envVariables.SERVER_PORT;
let kibanaHost = envVariables.KIBANA_HOST;
let kibanaServerBasePath = envVariables.KIBANA_BASE_PATH;

// Authenticate token
function authenticateToken(token) {
    const url = envVariables.EGOV_USER_HOST + envVariables.EGOV_USER_SEARCH;
    const queryParams = { access_token: token };
    let result;

    // try {
    //     const response = axios.post(url, null, { params: queryParams });
    //     logger.info(response);
    //     // Assuming the authentication is successful if the request is successful
    //     return response.status === 200;
    // } catch (error) {
    //     // Log and handle any errors
    //     console.error('Error during authentication:', error.response.data);
    //     return false; // Return false if authentication fails
    // }

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

    // Extract auth token from params
    const authToken = req.query['x-auth-token'];

    logger.info("Received request path - " + req.url);

    // if(req.originalUrl.includes('kibana/app/kibana')) {
    //     // Check if authToken is empty or null
    //     if (!authToken || authToken.trim() === '') {
    //         res.status(401).send('Unauthorized: No auth token provided');
    //         return;
    //     }

    //     if (authenticateToken(authToken)) {
    //         next(); // Proceed to the proxy if authenticated
    //     } else {
    //         res.status(403).send('Access denied'); // Send a 403 error if not authenticated
    //     }
    // }

    next();
});

// Proxy request to Kibana if authentication is successful
app.use('/', proxy(kibanaHost + kibanaServerBasePath));

// Listen on configured port
app.listen(serverPort, () => {
    logger.info(`Server running at http:${serverPort}/`);
})