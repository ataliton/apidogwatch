# ApiDogWatch

<p align="center">
  <img src="https://img.shields.io/badge/ApiDogWatch-runtime%20OpenAPI%20guard-0f9f6e?style=for-the-badge&labelColor=141c1c" alt="ApiDogWatch badge" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square" alt="Java 17+" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-6db33f?style=flat-square" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Jakarta%20Servlet-6-blue?style=flat-square" alt="Jakarta Servlet" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey?style=flat-square" alt="Apache 2.0" />
  <img src="https://img.shields.io/badge/JitPack-ready-black?style=flat-square" alt="JitPack" />
</p>

**ApiDogWatch** is a lightweight Java library that acts as a runtime *watchdog* for your HTTP API.

It inspects live traffic, compares real response payloads against your OpenAPI/Swagger contract, and surfaces divergences in a modern embedded web dashboard.

---

## The problem

Your Swagger looks perfect. Production tells another story.

Static OpenAPI documents drift from reality:

- fields appear in responses but never in the contract
- required properties silently disappear
- types change (`string` vs `integer`) without anyone noticing
- undocumented routes sneak into production

ApiDogWatch closes that gap by validating **what the API actually returns** while the application is running.

```text
   Client ──HTTP──▶ Your API
                      │
                      ▼
               ApiDogWatch Filter
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
   Live response body      OpenAPI schema
          │                       │
          └───────────┬───────────┘
                      ▼
              Schema comparator
                      │
                      ▼
           Embedded Dashboard UI
              /apidogwatch/ui
```

---

## Features

- **Runtime inspection** of method, path, status, latency and response body
- **JSON/schema comparison** for extra fields, missing fields and type mismatches
- **Thread-safe in-memory store** with bounded history
- **Spring Boot starter** with auto-configuration
- **Servlet / Jakarta EE adapter** with optional embedded `HttpServer` dashboard
- **Jersey 1 / JAX-RS 1.1 adapter** for legacy EE / ERP stacks
- **Modern dashboard** (Tailwind CSS) with metrics, filters and side-by-side payload vs contract drawer

---

## Modules

| Module | Description |
|---|---|
| `apidogwatch-core` | Inspection engine, OpenAPI loader, schema comparator, dashboard assets |
| `apidogwatch-spring` | Spring Boot starter (filter + interceptor + UI controller) |
| `apidogwatch-servlet` | Jakarta Servlet filter + embedded dashboard server |
| `apidogwatch-jersey1` | Jersey 1 adapter (`ResourceFilterFactory` global + dashboard) |
| `apidogwatch-jersey1-shaded` | Fat jar (core + Jackson + slf4j) for thin EE/plugin classloaders |

---

## Install via JitPack

### 1. Add the repository

**Maven**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

**Gradle**

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
```

### 2. Add the dependency

Use the GitHub user [`ataliton`](https://github.com/ataliton) and release tag `v1.0.6`.

#### Spring Boot

**Maven**

```xml
<dependency>
  <groupId>com.github.ataliton.apidogwatch</groupId>
  <artifactId>apidogwatch-spring</artifactId>
  <version>v1.0.6</version>
</dependency>
```

**Gradle**

```kotlin
implementation("com.github.ataliton.apidogwatch:apidogwatch-spring:v1.0.6")
```

#### Java Servlet / Jakarta EE

**Maven**

```xml
<dependency>
  <groupId>com.github.ataliton.apidogwatch</groupId>
  <artifactId>apidogwatch-servlet</artifactId>
  <version>v1.0.6</version>
</dependency>
```

**Gradle**

```kotlin
implementation("com.github.ataliton.apidogwatch:apidogwatch-servlet:v1.0.6")
```

#### Jersey 1 / JAX-RS 1.1 (legacy EE / ERP)

Prefer the **shaded** artifact on thin plugin classloaders (Liberty / Systêxtil):

**Maven**

```xml
<dependency>
  <groupId>com.github.ataliton.apidogwatch</groupId>
  <artifactId>apidogwatch-jersey1-shaded</artifactId>
  <version>v1.0.6</version>
</dependency>
```

**Gradle**

```kotlin
implementation("com.github.ataliton.apidogwatch:apidogwatch-jersey1-shaded:v1.0.6")
```

Thin dependency (host already provides Jackson):

```xml
<dependency>
  <groupId>com.github.ataliton.apidogwatch</groupId>
  <artifactId>apidogwatch-jersey1</artifactId>
  <version>v1.0.6</version>
</dependency>
```

> Local development without JitPack:
>
> ```bash
> mvn clean install
> ```
>
> then depend on `io.apidogwatch:apidogwatch-spring:1.0.0-SNAPSHOT`.

---

## Quick start — Spring Boot

1. Add `apidogwatch-spring`
2. Optional: put `openapi.json` / `openapi.yaml` on the classpath — or leave `apidogwatch.openapi=auto` (default) to probe classpath then springdoc `/v3/api-docs`
3. Configure (optional):

```yaml
apidogwatch:
  enabled: true
  openapi: auto   # or classpath:openapi.json / http://host/v3/api-docs
  path: /apidogwatch
  max-body-chars: 64000
  exclude-paths:
    - /actuator
    - /swagger-ui
    - /v3/api-docs
```

That’s it. Auto-configuration registers:

- a content-caching filter
- a timing interceptor
- dashboard + JSON API controllers

### Open the dashboard

```text
http://localhost:8080/apidogwatch/ui
```

---

## Quick start — Servlet / Java SE

```java
import io.apidogwatch.servlet.ApiDogWatchBootstrap;
import io.apidogwatch.servlet.ApiDogWatchFilter;

public class App {
    public static void main(String[] args) throws Exception {
        ApiDogWatchBootstrap bootstrap = ApiDogWatchBootstrap.start(
                "classpath:openapi.yaml",
                9099
        );

        ApiDogWatchFilter filter = bootstrap.getFilter();
        // register `filter` in your Servlet container (web.xml / @WebFilter / programmatic)

        // Dashboard:
        // http://localhost:9099/apidogwatch/ui
    }
}
```

Or register the filter manually:

```xml
<filter>
  <filter-name>ApiDogWatchFilter</filter-name>
  <filter-class>io.apidogwatch.servlet.ApiDogWatchFilter</filter-class>
  <init-param>
    <param-name>openapi</param-name>
    <param-value>classpath:openapi.json</param-value>
  </init-param>
</filter>
<filter-mapping>
  <filter-name>ApiDogWatchFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
```

Then start the dashboard server:

```java
ApiDogWatchDashboardServer.start(engine, 9099);
```

---

## Quick start — Jersey 1 / JAX-RS 1.1

Designed for legacy EE / ERP plugins (Jersey 1.19 / JAX-RS 1.1).

### Zero-friction (recommended)

```java
static {
    ApiDogWatchJersey.auto(cfg -> cfg
            .openApiLocation("classpath:openapi.json") // or http://host/v3/api-docs
            .uiPathPrefix("/apidogwatch"));
}

@Override
public Set<Class<?>> getServices() {
    Set<Class<?>> classes = new HashSet<>();
    // ... your resources
    classes.addAll(ApiDogWatchJersey.resources()); // dashboard at /apidogwatch/ui
    return classes;
}

@Override
public Set<Object> getSingletons() {
    return ApiDogWatchJersey.singletons(); // watches ALL APIs
}
```

Enable:

```bash
-Dapidogwatch.enabled=true
```

Open `/apidogwatch/ui`. Locale follows the signed-in principal reflectively (`idioma` / `getIdioma()`), then `Accept-Language`.

For a plugin base path (e.g. Systêxtil), subclass only to set `@Path`:

```java
@Path("/recebimento/apidogwatch")
public class ApiDogWatchUi extends ApiDogWatchDashboardEndpoints {}
```

### Thin EE classloaders

Use `apidogwatch-jersey1-shaded` and unpack/shade it into the plugin JAR (Jackson + slf4j are bundled; Jersey APIs stay `provided` by the host).

### Optional: per-resource annotation

Still supported if you prefer opt-in watching:

```java
@Path("/orders")
@ResourceFilters(ApiDogWatchResourceFilter.class)
public class OrdersResource { ... }
```

---
## Dashboard

Open:

```text
/apidogwatch/ui
```

You get:

- **Metric cards** — total requests, alerts, clean responses, undocumented routes
- **Traffic table** — filter by method, path, HTTP status and alerts-only
- **Detail drawer** — side-by-side **Real payload** vs **OpenAPI schema**, plus divergence list

JSON endpoints used by the UI:

| Method | Path | Description |
|---|---|---|
| `GET` | `/apidogwatch/api/metrics` | Aggregated counters |
| `GET` | `/apidogwatch/api/requests` | Filtered history |
| `GET` | `/apidogwatch/api/requests/{id}` | Full inspection detail |
| `DELETE` | `/apidogwatch/api/requests` | Clear in-memory history |
| `GET` | `/apidogwatch/api/health` | Library health probe |

---

## What gets detected?

| Divergence | Meaning |
|---|---|
| `EXTRA_FIELD` | Present in the live payload, absent from schema properties |
| `MISSING_FIELD` | Declared/required in the contract, absent in the payload |
| `TYPE_MISMATCH` | JSON type does not match OpenAPI `type` |
| `UNDOCUMENTED_ROUTE` | No matching path/operation in the OpenAPI document |
| `UNEXPECTED_STATUS` | Status code not declared for the operation |
| `INVALID_JSON` | Body is not valid JSON while a schema is expected |

---

## Configuration reference

| Key | Default | Description |
|---|---|---|
| `apidogwatch.enabled` | `true` | Enable/disable the watchdog |
| `apidogwatch.openapi` | `classpath:openapi.json` | OpenAPI location (`classpath:`, file path or URL) |
| `apidogwatch.path` | `/apidogwatch` | UI + API base path |
| `apidogwatch.max-body-chars` | `64000` | Max captured body size |
| `apidogwatch.exclude-paths` | actuator/swagger defaults | Path prefixes ignored by inspection |

Supported OpenAPI locations:

- `classpath:openapi.json`
- `classpath:openapi.yaml`
- `/absolute/or/relative/path/openapi.yaml`
- `https://example.com/v3/api-docs`

---

## Build from source

```bash
git clone https://github.com/ataliton/apidogwatch.git
cd apidogwatch
mvn clean install
```

Requirements:

- JDK 17+
- Maven 3.9+

---

## Contributing

Contributions are welcome — bug reports, docs, adapters and comparator improvements.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-improvement`
3. Keep changes focused and add tests when touching the comparator/engine
4. Run `mvn clean test`
5. Open a Pull Request describing **why** the change helps

### Good first issues

- richer OpenAPI keyword coverage (`oneOf`, `anyOf`, `allOf`)
- persistence adapters (Redis / JDBC) for the inspection store
- authentication gate in front of the dashboard
- request-body validation (currently focused on responses)

Please be respectful in issues and PRs. By contributing, you agree that your work is licensed under the Apache License 2.0.

---

## Security note

ApiDogWatch is intended for **local, staging and trusted environments**.  
The dashboard may expose response payloads — do **not** enable it publicly without an access control layer.

---

## License

Licensed under the [Apache License 2.0](LICENSE).

---

<p align="center">
  <strong>ApiDogWatch</strong> — keep your contract honest.
</p>
