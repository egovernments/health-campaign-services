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
let allowedContextPaths = envVariables.KIBANA_ACCEPTED_CONTEXT_UI_PATHS
let acceptedDomain = envVariables.KIBANA_ACCEPTED_DOMAIN_NAME
let excludeUrls = envVariables.KIBANA_EXCLUDE_URL_PATTERNS
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

function bypassAuthBasedOnUrl(url) {
    const excluded = excludeUrls.split(",")
    return excluded.some(substring => url.includes(substring));
}

function validateReferer(url) {
    try {
        // Create a URL object from the input string
        const urlObj = new URL(url);
        
        // Extract the hostname (domain) from the URL object
        const domain = urlObj.hostname;

        // Split the pathname into parts
        const pathParts = urlObj.pathname.split('/').filter(part => part.length > 0);

        // Extract the path just ahead of the domain, which is the first part of the path
        const contextPath = pathParts.length > 0 ? pathParts[0] : '';

        //based on domain and contextPath return true or false
        if(domain === acceptedDomain && allowedContextPaths.split(",").some(path=> path ===contextPath )){
            return true
        }else{
            return false
        }

    } catch (error) {
        // Handle invalid URL errors
        console.error('Unable to validate referer:', error);
        return false;
    }
}

// Intercept Kibana requests, extract auth token and perform authentication
app.use((req, res, next) => {
    logger.info("Received request");

    // Extract auth token from headers
    const authToken = req.headers['authorization'];
    const typeReq = req.headers['type-req'];
    const acceptHeader = req.headers['accept'];
    const referer = req.headers['referer']
    logger.info("Received request path - " + req.url);

    //---------new code -----------

    //first check for calls where authentication is not required
    if(bypassAuthBasedOnUrl(req.originalUrl)){
        next()
        return
    }

    //now checking for document 
    if(acceptHeader && acceptHeader.includes('text/html')){

        //if referer is digit ui then bypass
        
        if(validateReferer(referer)){
            next()
            return;
        }else{
            res.status(403).send('Access denied');
            return
        }
    }

    //this means either fetch or xhr -> proceed to authenticate this request
    if(typeReq){
        if (!authToken || authToken.trim() === '') {
            res.status(401).send('Unauthorized: No auth token provided');
            return;
        }

        if (authenticateToken(authToken)) {
            next(); // Proceed to the proxy if authenticated
            return;
        } else {
            res.status(403).send('Access denied'); // Send a 403 error if not authenticated
            return;
        }
    }


    // ------- new code ----------

    // if (typeReq === 'document') {
    //     logger.info("Bypassing authentication for document type request");
    //     next(); // Bypass authentication for document type requests
    // } else if (acceptHeader && acceptHeader.includes('text/html')) {
    //     logger.info("Bypassing authentication for requests with Accept header containing text/html");
    //     next(); // Bypass authentication for requests with Accept header containing text/html
    // } else if ((req.originalUrl.includes('/app/kibana') || req.originalUrl.includes('/kibana/api') || req.originalUrl.includes('/kibana/elasticsearch')) && !req.originalUrl.includes('/bundles/')) {
    //     // Check if authToken is empty or null
    //     if (!authToken || authToken.trim() === '') {
    //         res.status(401).send('Unauthorized: No auth token provided');
    //         return;
    //     }

    //     if (authenticateToken(authToken)) {
    //         next(); // Proceed to the proxy if authenticated
    //     } else {
    //         res.status(403).send('Access denied'); // Send a 403 error if not authenticated
    //         return;
    //     }
    // } else {
    //     //let all requests go without authorization
    //     next();
    // }
});

// Proxy request to Kibana if authentication is successful
app.use('/', proxy(kibanaHost + kibanaServerBasePath,{
    proxyReqOptDecorator: function(proxyReqOpts, srcReq) {
        proxyReqOpts.headers['kbn-xsrf'] = 'true';
        return proxyReqOpts;
    },
}));

// Listen on configured port
app.listen(serverPort, () => {
    logger.info(`Server running at http:${serverPort}/`);
});
