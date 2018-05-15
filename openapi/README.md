# Open API Module

OpenApiModule() supports generating Open API specification documents in both JSON and YAML formats
from the application's annotated JAX-RS resources.

To use, include the OpenApiModule() Guice module when bootstrapping your application.
The "/admin/openapi.json" and "/admin/openapi.yaml" will then be available on the
service's admin port, returning the specification documents in JSON and YAML
format, respectively.

Swagger-core offers a set of annotations to place on JAX-RS resources in order to
adjust the returned specification document. For details, see 
https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations
