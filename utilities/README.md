# Utilities

#### Code generator

[![Build Status](https://travis-ci.org/joemccann/dillinger.svg?branch=master)](https://travis-ci.org/joemccann/dillinger)

Code generator is used to generate digit frameworks new micorservices which are based on the platform or mission
specific using swagger api specification [ uses swagger v2.0]

This is done so before development starts, everyone is aware of the context and parallel development can proceed as the
server code is generated and can be deployed. This provides the agility required for the product and project team to
move faster to accomplish the goals

## Supported options

Codegen mostly works with command line tools and command line arguments

The following table mentions the options which can be used to generate code

| Option | Command line argument meaning | Required | Remarks
| ------ | ------ |  ------ | ------ |
| u | url | YES | URL of the Swagger YAML |
| b | basePackage | YES | Base Package |
| a | artifactId | YES | Artifact ID / context path of the artifact |
| g | groupId | YES | Group ID of the artifact, default org.digit.health |
| l | Use Lombok | NO | Add lombok dependency and templates |
| t | Use Tracer | NO | Add tracer dependency and templates |
| rc | Enable Redis Cache | NO | Add redis dependencies and templates | 
| fw | Enable Flyway Migration | NO | Generated db migration folder |

## HOW TO BUILD AND GENERATE

Go to the base folder

```
cd <Path to codegen codebase>
mvn clean package
```

```
cd target

java -jar codegen-1.0-SNAPSHOT-jar-with-dependencies.jar -u file:/<path-to-apisepc> -rc -fw -a death-registration-service -b org.digit.health -l -t
```

