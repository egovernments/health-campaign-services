# Local Setup

To set up the muster roll service in your local system, clone the git repo(https://github.com/egovernments/DIGIT-Works).

## Dependencies

- MDMS
- IDGen
- Workflow service
- Individual


### Infra Dependency

- [X] Postgres DB
- [ ] Redis
- [ ] Elasticsearch
- [X] Kafka

## Running Locally

To run the service locally, you need to port forward below services.

```bash
function kgpt(){kubectl get pods -n egov --selector=app=$1 --no-headers=true | head -n1 | awk '{print $1}'}

kubectl port-forward -n egov $(kgpt egov-user) 8085:8080
kubectl port-forward -n egov $(kgpt egov-idgen) 8086:8080
kubectl port-forward -n egov $(kgpt egov-mdms-service) 8087:8080
kubectl port-forward -n egov $(kgpt egov-workflow) 8088:8080
kubectl port-forward -n works $(kgpt project-management-service) 8089:8080
``` 

Update below listed properties in `application.properties` before running the project:

```ini
egov.idgen.hostname = http://127.0.0.1:8086

# can use non port forwarded environment host as well
egov.mdms.host = http://127.0.0.1:8087
egov.workflow.host = http://127.0.0.1:8088
```
