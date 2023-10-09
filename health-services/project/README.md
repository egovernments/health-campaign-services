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

* POST `/project/v1/_create` - Create Project, This API is used to create/add a new Project.

* POST `/project/v1/_update` - Update Project, This API is used to update the details of an existing Project.

* POST `/project/v1/_search` - Search Project, This API is used to search details of an existing Project.


* POST `/project/beneficiary/v1/_create` - Create Project Beneficiary, This API is used to create/add a new beneficiary for Project.

* POST `/project/beneficiary/v1/_update` - Update Project Beneficiary, This API is used to update beneficiary registration for Project.

* POST `/project/beneficiary/v1/_search` - Search Project Beneficiary, This API is used to search beneficiary registration for Project.

* POST `/project/beneficiary/v1/_delete` - Delete Project Beneficiary, This API is used to soft delete beneficiary registration for project.

* POST `/project/beneficiary/v1/bulk/_create` - Create Project Beneficiaries, This API is used to create/add a new beneficiaries for Project.

* POST `/project/beneficiary/v1/bulk/_update` - Update Project Beneficiaries, This API is used to update beneficiaries registration for project.

* POST `/project/beneficiary/v1/bulk/_delete` - Delete Project Beneficiaries, This API is used to soft delete beneficiaries registration for project.


* POST `/project/task/v1/_create` - Create Project Task, This API is used to create task for the project .

* POST `/project/task/v1/_update` - Update Project Task, This API is used to update task request for Project.

* POST `/project/task/v1/_search` - Search Project Task, This API is used to search task for Project.

* POST `/project/task/v1/_delete` - Delete Project Task, This API is used to soft delete task for project.

* POST `/project/task/v1/bulk/_create` - Create Project Tasks, This API is used to create tasks for the project in bulk.

* POST `/project/task/v1/bulk/_update` - Update Project Tasks, This API is used to update task Request in bulk for a project.

* POST `/project/task/v1/bulk/_delete` - Delete Project Tasks, This API is used to Soft delete tasks for a project.


* POST `/project/staff/v1/_create` - Create Project Staff, This API is used to Link Staff users to Project for a certain time period .

* POST `/project/staff/v1/_update` - Update Project Staff, This API is used to update Project Staff users.

* POST `/project/staff/v1/_search` - Search Project Staff, This API is used to search Project Staff users.

* POST `/project/staff/v1/_delete` - Delete Project Staff, This API is used to soft delete linkage of Project Staff users with project.

* POST `/project/staff/v1/bulk/_create` - Create Project Staff in bulk, This API is used to Link bulk Staff users to Project for a certain time period .

* POST `/project/staff/v1/bulk/_update` - Update Project Staff in bulk, This API is used to update Project Staff users using bulk payload .

* POST `/project/staff/v1/bulk/_delete` - Delete Project Staff in bulk, This API is used to soft delete linkage of Project Staff users with project in bulk.


* POST `/project/facility/v1/_create` - Create Project Facility, This API is used to Link Facility to Project.

* POST `/project/facility/v1/_update` - Update Project Facility, This API is used to update Project Facilities.

* POST `/project/facility/v1/_search` - Search Project Facility, This API is used to search Project Facilities.

* POST `/project/facility/v1/_delete` - Delete Project Facility, This API is used to soft delete Project Facility.

* POST `/project/facility/v1/bulk/_create` - Create Project Facilities, This API is used to link Facilities to Project.

* POST `/project/facility/v1/bulk/_update` - Update Project Facilities, This API is used to update Project Facilities.

* POST `/project/facility/v1/bulk/_delete` - Delete Project Facilities, This API is used to soft delete Project Facilities in bulk .


* POST `/project/resource/v1/_create` - Create Project Resource, This API is used to Link Resources to Project.

* POST `/project/resource/v1/_update` - Update Project Resource, This API is used to update Project Resource linkage.

* POST `/project/resource/v1/_search` - Search Project Resource, This API is used to search Project Resources.

* POST `/project/resource/v1/_delete` - Delete Project Resource, This API is used to delete Project Resource linkage.

* POST `/project/resource/v1/bulk/_create` - Create Project Resources, This API is used to link Resources to Project.

* POST `/project/resource/v1/bulk/_update` - Update Project Resources, This API is used to update Project Resource linkage in bulk.

* POST `/project/resource/v1/bulk/_delete` - Delete Project Resources, This API is used to delete Project Resource linkage in bulk.


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
