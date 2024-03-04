# Attendance Service

The attendance service provides generic attendance logging functionality based on "in" and "out" timestamps.
IN and OUT timestamps are recorded per individual. Aggregating and calculating attendance based on these timestamps 
is the function of the muster roll service.


### Service Dependencies

- DIGIT backbone services
- Individual
- MDMS
- ID-GEN
- Persister
- Indexer

## Service Details

- Allows creation/updation/search of an attendance register
- Allows mapping of staff and attendees to a register and enforces permissions.
- Logs entry and exit timestamps in epoch time for a referenced entity

### API Specs

https://raw.githubusercontent.com/egovernments/DIGIT-Specs/master/Domain%20Services/Works/Attendance-Service-v1.0.0.yaml

### Postman Collection

https://raw.githubusercontent.com/egovernments/DIGIT-Works/master/backend/attendance/Attendace%20Service%20Postman%20Scripts.postman_collection.json