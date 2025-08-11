# beneficiary-idgen service

The beneficiary-idgen service generates new IDs based on the ID formats provided. The application exposes a REST API to process requests and return generated IDs.

## DB UML Diagram

- TBD

## Service Dependencies

- egov-mdms-service

## Service Details

The application can be run like any other Spring Boot application, but you must add the Lombok extension to your IDE to enable annotation processing.

For IntelliJ IDEA, install the plugin directly. For Eclipse, add the Lombok JAR to the `eclipse.ini` file using:

```ini
-javaagent:lombok.jar
```

### API Details

- POST /id/id_pool/_generate
- POST /id/id_pool/_dispatch
- POST /id/id_pool/_search
- POST /id/id_pool/_update

## Reference document

Details on every parameter and its significance are mentioned in the document: `https://digit-discuss.atlassian.net/l/c/eH501QE3`

### Kafka Consumers

- id-gen-consumer-bulk-update-topic

### Kafka Producers

- save-in-id-pool
- update-id-pool-status
- save-dispatch-id-log
