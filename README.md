# en-monitor-measurement-service

EnergyMonitor measurement microservice: stores the readings the ESP32 + PZEM-004T meters publish,
and serves the consumption history, statistics and levels of a device and of a home. Spring Boot 4,
Java 21, hexagonal architecture.

It is being extracted from the `measurement` module of `energy-monitor-backend`, following the same
steps as the security and device services.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL from the `infra` repository: the schema is shared with `energy-monitor-backend`, the
  security service and the device service.

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env in both files
./mvnw spring-boot:run          # http://localhost:8085/actuator/health
```

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`.

## Docker

```bash
docker build -t energy-monitor-measurement .
```

Multi-stage build (Maven on JDK 21, then JRE 21), running as uid 10001, port 8085.
