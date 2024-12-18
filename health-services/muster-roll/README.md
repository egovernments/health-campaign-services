# Muster Roll Service

This is a calculator service dependent on the Attendance service. Attendance logs basic attendance. Muster computes aggregates based on the attendance and business logic. Muster roll will have to be customised depending on the implementation. 

### Service Dependencies

- DIGIT backbone services
- Idgen
- Persister
- Indexer
- Workflow
- User
- Attendance

## Service Details
- Estimate wages of a wage seeker based on his/her attendance logs
- Create a muster roll with a list of wage seekers
- Update a muster roll with modified aggregate attendance
- Search for a muster roll based on some defined parameters. For more info, please see Swagger contract.

### API Specs
https://github.com/egovernments/DIGIT-Specs/blob/master/Domain%20Services/Works/Muster-Roll-Service-v1.0.0.yaml

### Postman Collection
https://raw.githubusercontent.com/egovernments/DIGIT-Works/master/backend/muster-roll/src/main/resources/Muster%20Roll%20Service.postman_collection.json

 
