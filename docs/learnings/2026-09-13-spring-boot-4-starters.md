# 2026-09-13 — Spring Boot 4 dependencies
Boot 4 renamed and split starters: use `spring-boot-starter-aspectj` (not `-aop`) and add
`spring-boot-starter-restclient` for `RestClient`; HTTP client timeouts are `spring.http.client.*`.
Jackson is 3 (`tools.jackson.*`). Tracing needs `spring-boot-micrometer-tracing-brave` plus
`micrometer-tracing-bridge-brave`; Elasticsearch metrics need `micrometer-registry-elastic`.
Source: T-m0-m1, T-observability
