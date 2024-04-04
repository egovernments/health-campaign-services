# Backend For Frontend service

<!--TODO TO BE UPDATED -->
# mukta-service

mukta-service service work in between multiple microservices. Earlier client used to directly call multiple microservice, but with introduction of this new service one can provide just few parameters ex:- applicationnumber, tenantId to this new service to get a complete required details.

## Introduction

This repo is a **barebone minimalistic** starter-kit for TypeScript-based ExpressJS (https://expressjs.com/) app.

## Simplicity

In order to build and run the app, you have to execute only 2 NPM script commands.

## Dependencies

* ExpressJS
* ExpressJS Middlewares
    * Compression - https://github.com/expressjs/compression
    * Helmet - https://helmetjs.github.io/
* TypeScript
* NPM (or) Yarn

### Service Dependencies

- egov-mdms-service
- contract
- estimate
- measurement
- muster-roll-service
- User-service
- Workflow-service


## Service Details

project-factory-service service is new service being added which can work in between existing microservices.  With this service the existing API endpoints need not be exposed to frontend.

For any new requirement one new endpoint with validations and logic for getting data from multiple microservice has to be added in the code. With separate endpoint for each pdf we can define access rules per pdf basis. Currently project-factory-service service has endpoint for following pdfs used in our system:-

#### Configurations

**Steps/guidelines for adding support for new API's:**


- Follow code of [existing supported PDFs](https://github.com/egovernments/utilities/tree/master/egov-pdf/src/routes) and create new endpoint with suitable search parameters for each api

- Put parameters validations, module level validations ex:- application status,applicationtype and api error responses with proper error messages and error codes

- Add access to endpoint in MDMS for suitable roles

### API Details
Currently below endpoints are in use for 'EMPLOYEE’ roles

###Flow Diagram
![Flow](https://github.com/egovernments/DIGIT-Works/blob/04689228d238592a34e832be2997a0f05ac956f8/utilities/project-factory/docs/flowdiagram.png?raw=true)



## Install, Build, Run

Install node package dependencies:

`$ npm install`

Build:

`$ npm run build`

Run ExpressJS server:

`$ npm start`

## Recommendation

Keep all TypeScript source files in the `src` folder.

## Future Goals

* Add more sample code like Routes, Controllers, and Views
* Add Webpack
* Add unit-test sample code for Jest


### Kafka Consumers
NA

### Kafka Producers
NA

## License

MIT © [jagankumar-egov](https://github.com/jagankumar-egov)
