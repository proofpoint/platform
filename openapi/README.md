# Open API Module
OpenApiModule enables support to generate Open API Specification documents in both JSON and YAML formats from the JAX-RS resources added to the application.

## How to use
   1. In order to generate the OpenAPI documentation, swagger-core offers a set of annotations to declare and manipulate the output.
        https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations
   2. Include OpenApiModule to your service

## URL to access the specification
Following resources are avaibale through admin server of the service
    1. /openapi/api.json
    2. /openapi/api.yaml