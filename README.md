# Crypto Dashboard

A JavaFX desktop application that displays cryptocurrency data and related news.

## Prerequisites

- Java 17 (JDK 17) installed and JAVA_HOME configured
- Maven 3.6+ (for building and running via the JavaFX Maven plugin)

## Application properties

For the crypto price data a CoinGecko API key is needed and set to `src/main/resources/application.properties`, template available at `src/main/resources/application.properties.template`

## Build

To compile the project and package it (tests run by default):

```bash
mvn package
```

This creates build artifacts in `target/`, including a `jar-with-dependencies` assembly.

## Run

```bash
mvn javafx:run
```

## Tests

Run unit tests with:

```bash
mvn test
```

## Project structure

- `src/main/java` — application sources (UI, services, models)
- `src/main/resources` — CSS and other resources
- `src/test/java` — unit tests
- `pom.xml` — Maven build configuration
