# Project

### Project Service
Project registry is a Health Campaign Service that facilitate management of project on the DIGIT platform. The functionality is exposed via REST API.

### DB UML Diagram

<img width="586" alt="Screenshot 2023-03-29 at 2 45 35 PM" src="https://user-images.githubusercontent.com/123379163/228487047-bd14b481-dc81-44b2-826d-975d212e7f36.png">


### Service Dependencies
- Idgen Service
- Facility Service
- Household Service
- Product Service

### Swagger API Contract
Link to the swagger API contract yaml and editor link like below

https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/health-campaign-services/v1.0.0/docs/health-api-specs/contracts/project.yml

### Service Details

#### API Details
BasePath `/project`

Project service APIs - contains create, update, delete and search end point

a) POST `/project/v1/_create` - Create Project, This API is internally call from Project controller to create/add a new Project.

b) POST `/project/v1/_update` - Update Project, This API is internally call from Project controller to update the details of an existing Project.

c) POST `/project/v1/_search` - Search Project, This API is internally call from Project controller to search details of an existing Project.


d) POST `/project/beneficiary/v1/_create` - Create Project Beneficiary, This API is internally call from Project controller to create/add a new beneficiary for Project.

e) POST `/project/beneficiary/v1/_update` - Update Project Beneficiary, This API is internally call from Project controller to update beneficiary registration for Project.

f) POST `/project/beneficiary/v1/_search` - Search Project Beneficiary, This API is internally call from Project controller to search beneficiary registration for Project.

g) POST `/project/beneficiary/v1/_delete` - Delete Project Beneficiary, This API is internally call from Project controller to soft delete beneficiary registration for project.

h) POST `/project/beneficiary/v1/bulk/_create` - Create Project Beneficiaries, This API is internally call from Project controller to create/add a new beneficiaries for Project.

i) POST `/project/beneficiary/v1/bulk/_update` - Update Project Beneficiaries, This API is internally call from Project controller to update beneficiaries registration for project.

j) POST `/project/beneficiary/v1/bulk/_delete` - Delete Project Beneficiaries, This API is internally call from Project controller to soft delete beneficiaries registration for project.


k) POST `/project/task/v1/_create` - Create Project Task, This API is internally call from Project controller to create task for the project .

l) POST `/project/task/v1/_update` - Update Project Task, This API is internally call from Project controller to update task request for Project.

m) POST `/project/task/v1/_search` - Search Project Task, This API is internally call from Project controller to search task for Project.

n) POST `/project/task/v1/_delete` - Delete Project Task, This API is internally call from Project controller to soft delete task for project.

o) POST `/project/task/v1/bulk/_create` - Create Project Tasks, This API is internally call from Project controller to create tasks for the project in bulk.

p) POST `/project/task/v1/bulk/_update` - Update Project Tasks, This API is internally call from Project controller to update task Request in bulk for a project.

q) POST `/project/task/v1/bulk/_delete` - Delete Project Tasks, This API is internally call from Project controller to Soft delete tasks for a project.


r) POST `/project/staff/v1/_create` - Create Project Staff, This API is internally call from Project controller to Link Staff users to Project for a certain time period .

s) POST `/project/staff/v1/_update` - Update Project Staff, This API is internally call from Project controller to update Project Staff users.

t) POST `/project/staff/v1/_search` - Search Project Staff, This API is internally call from Project controller to search Project Staff users.

u) POST `/project/staff/v1/_delete` - Delete Project Staff, This API is internally call from Project controller to soft delete linkage of Project Staff users with project.

v) POST `/project/staff/v1/bulk/_create` - Create Project Staff in bulk, This API is internally call from Project controller to Link bulk Staff users to Project for a certain time period .

w) POST `/project/staff/v1/bulk/_update` - Update Project Staff in bulk, This API is internally call from Project controller to update Project Staff users using bulk payload .

x) POST `/project/staff/v1/bulk/_delete` - Delete Project Staff in bulk, This API is internally call from Project controller to soft delete linkage of Project Staff users with project in bulk.


r) POST `/project/facility/v1/_create` - Create Project Facility, This API is internally call from Project controller to Link Facility to Project.

s) POST `/project/facility/v1/_update` - Update Project Facility, This API is internally call from Project controller to update Project Facilities.

t) POST `/project/facility/v1/_search` - Search Project Facility, This API is internally call from Project controller to search Project Facilities.

u) POST `/project/facility/v1/_delete` - Delete Project Facility, This API is internally call from Project controller to soft delete Project Facility.

v) POST `/project/facility/v1/bulk/_create` - Create Project Facilities, This API is internally call from Project controller to link Facilities to Project.

w) POST `/project/facility/v1/bulk/_update` - Update Project Facilities, This API is internally call from Project controller to update Project Facilities.

x) POST `/project/facility/v1/bulk/_delete` - Delete Project Facilities, This API is internally call from Project controller to soft delete Project Facilities in bulk .


y) POST `/project/resource/v1/_create` - Create Project Resource, This API is internally call from Project controller to Link Resources to Project.

z) POST `/project/resource/v1/_update` - Update Project Resource, This API is internally call from Project controller to update Project Resource linkage.

aa) POST `/project/resource/v1/_search` - Search Project Resource, This API is internally call from Project controller to search Project Resources.

ab) POST `/project/resource/v1/_delete` - Delete Project Resource, This API is internally call from Project controller to delete Project Resource linkage.

ac) POST `/project/resource/v1/bulk/_create` - Create Project Resources, This API is internally call from Project controller to link Resources to Project.

ad) POST `/project/resource/v1/bulk/_update` - Update Project Resources, This API is internally call from Project controller to update Project Resource linkage in bulk.

ae) POST `/project/resource/v1/bulk/_delete` - Delete Project Resources, This API is internally call from Project controller to delete Project Resource linkage in bulk.


### Kafka Consumers

- save-project-staff-bulk-topic
- update-project-staff-bulk-topic
- delete-project-staff-bulk-topic

- save-project-facility-bulk-topic
- update-project-facility-bulk-topic
- delete-project-facility-bulk-topic

- project-beneficiary-consumer-bulk-create-topic
- project-beneficiary-consumer-bulk-update-topic
- project-beneficiary-consumer-bulk-delete-topic

- save-project-task-bulk-topic
- update-project-task-bulk-topic
- delete-project-task-bulk-topic

- save-project-resource-bulk-topic
- update-project-resource-bulk-topic
- delete-project-resource-bulk-topic


### Kafka Producers

- save-project-staff-topic
- update-project-staff-topic
- delete-project-staff-topic

- save-project-facility-topic
- update-project-facility-topic
- delete-project-facility-topic

- save-project-beneficiary-topic
- update-project-beneficiary-topic
- delete-project-beneficiary-topic

- save-project-task-topic
- update-project-task-topic
- delete-project-task-topic

- save-project-resource-topic
- update-project-resource-topic
- delete-project-resource-topic


## Pre commit script

[commit-msg](https://gist.github.com/jayantp-egov/14f55deb344f1648503c6be7e580fa12)
