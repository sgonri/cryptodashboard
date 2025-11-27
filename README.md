# Crypto Dashboard

A small JavaFX desktop application that displays cryptocurrency market data and related news.

This repository contains the UI, service layers that fetch data from third-party APIs (CoinGecko and SerpApi), an in-memory cache, and unit tests.

**Key technologies:** Java 17, JavaFX, Maven, Jackson (JSON), java.net.http.HttpClient, JUnit + Mockito.

**Table of contents**

- **Project**: short description and goals
- **Features**: what the app shows and how data is fetched
- **Getting Started**: prerequisites and quick start commands
- **Configuration**: where to put API keys and important properties
- **Development**: structure, tests, and tips for contributors
- **Troubleshooting**: common issues and debugging tips

## Project

`Crypto Dashboard` provides a compact desktop UI to view:

- Top cryptocurrencies by market cap (price, 24h change, market cap, volume)
- Historical price and volume charts for selectable time windows
- News articles related to cryptocurrencies (via SerpApi)

The app separates concerns into:

- `services/` — API integration, caching, polling
- `models/` — domain objects (`Crypto`, `HistoricalData`, `ChartPoint`)
- `controllers/` and `views/` — UI logic and JavaFX views

## Features

- Fetches top coins and market chart data from CoinGecko
- Caches top list and historical series to reduce API calls (`CryptoCache`)
- Background polling of live prices (`PricePollingService`) to keep the UI current
- News fetching using SerpApi (`SerpAPINewsService`) with configurable API key
- Unit tests that mock HTTP calls so CI doesn't depend on external services

## Getting started

Requirements

- Java 17 (set `JAVA_HOME` to a JDK 17 installation)
- Maven 3.6+

Build

```powershell
mvn package
```

Run (JavaFX)

```powershell
mvn javafx:run
```

Run tests

```powershell
mvn test
```

Notes

- Tests use mocked `HttpClient` instances (see `src/test/java/.../CryptoServiceTest.java`) so they run offline.
- The application is a desktop app — launching `mvn javafx:run` starts the JavaFX UI.

## Configuration

Edit `src/main/resources/application.properties` to configure API endpoints and keys. Common properties:

- `coingecko.api.url` — base URL for CoinGecko (default: `https://api.coingecko.com/api/v3`)
- `coingecko.api.key` — optional demo API key header for CoinGecko (recommended for increased ratelimit)
- `serp.api.key` — SerpApi key used by the news service

Example `src/main/resources/application.properties`:

```properties
# CoinGecko
coingecko.api.url=https://api.coingecko.com/api/v3
# coingecko.api.key=

# SerpApi (news)
serp.api.key=YOUR_SERPAPI_KEY
```

## Development notes

- Code is organized under `src/main/java/com/mycompany/app`.
- Main services to review when changing API behavior:
  - `services/CryptoService.java` — top coins, historical data, preload logic
  - `services/PricePollingService.java` — frequent lightweight polling for live prices
  - `services/SerpAPINewsService.java` — news search and parsing
  - `services/CryptoCache.java` — in-memory cache to reduce API load

- Prefer using `ApiConfig` for centralized access to properties if refactoring configuration.
- Unit tests are under `src/test/java`; run them frequently during changes.

### Running and debugging

- To reproduce API responses during development, tests use Mockito to mock `HttpClient`.
- Logs are printed to stdout/stderr; watch console for rate-limit (429) messages from CoinGecko.

## Troubleshooting & Known issues

- CoinGecko rate-limits: the free tier can be strict. If you see many `429` responses, reduce polling frequency or increase delays between batch requests.
- UI quirks: chart axis labels or news filtering may need a toggle to refresh; these are UX workarounds in the app.

## Testing

- Unit tests cover parsing and cache behavior. Key test files:
  - `src/test/java/.../CryptoServiceTest.java`
  - `src/test/java/.../CryptoCacheTest.java`
  - `src/test/java/.../SerpAPINewsServiceTest.java`

- To add tests for HTTP interactions, mock `HttpClient` and return a mocked `HttpResponse<String>`.

## Contributing

1. Fork the repository and create a feature branch.
2. Add tests for new behavior where applicable.
3. Run `mvn test` and ensure all tests pass.
4. Open a pull request describing your changes.

## Where to look next (for maintainers)

- Improve configuration centralization: use `ApiConfig` consistently across services.
- Consider adding a simple CI GitHub Actions workflow that runs `mvn -q test` on PRs.

## License

No license specified. Add a `LICENSE` file if you want to make the project's license explicit.

---

If you'd like, I can:

- Run the test suite now and share results.
- Add a sample `application.properties.example` file to the repo.
- Create a GitHub Actions workflow that runs tests on each push/PR.

Tell me which of these you'd like me to do next.
