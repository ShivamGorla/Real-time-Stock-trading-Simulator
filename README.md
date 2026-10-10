# Stock Trading Simulator

Real-Time Stock Trading Simulator — MCA Project.

Practice buying and selling stocks with virtual money. No real money involved.

## Tech Stack

- Java 21, Spring Boot 3.4.1, Maven
- MySQL, Spring Data JPA, Hibernate
- Spring Security, Bean Validation
- HTML, CSS, vanilla JavaScript

## Prerequisites

- Java 21 (OpenJDK / Temurin)
- Maven 3.9+
- MySQL 8+ with a running instance

## Database Setup

```sql
CREATE DATABASE IF NOT EXISTS stock_trading_simulator;
CREATE USER IF NOT EXISTS 'stock_app'@'localhost' IDENTIFIED BY '<your-password>';
GRANT ALL PRIVILEGES ON stock_trading_simulator.* TO 'stock_app'@'localhost';
FLUSH PRIVILEGES;
```

## Run

Set your MySQL password and start the app:

```bash
# PowerShell
$env:DB_PASSWORD="<your-password>"; mvn spring-boot:run

# Bash / macOS
DB_PASSWORD=<your-password> mvn spring-boot:run
```

The app starts at **http://localhost:8080**.

## Verify

| Check                | URL / Command                     | Expected                          |
|----------------------|-----------------------------------|-----------------------------------|
| Landing page         | http://localhost:8080              | Simulator homepage loads          |
| Health endpoint      | http://localhost:8080/api/health   | `{"status":"UP","timestamp":"…"}` |
| Database connection  | Check console on startup          | No connection errors              |
| Tests                | `mvn test`                        | All tests pass                    |

## Run Tests

```bash
mvn test
```

Tests use an embedded H2 database — no MySQL required.

## Project Structure

```
src/main/java/com/stocktrading/simulator/
├── StockTradingSimulatorApplication.java
├── config/SecurityConfig.java
├── controller/HealthController.java
├── model/User.java
└── repository/UserRepository.java

src/main/resources/
├── application.properties
└── static/
    ├── index.html
    ├── css/style.css
    └── js/app.js
```

## Environment Variables

| Variable      | Default                                                  | Description        |
|---------------|----------------------------------------------------------|--------------------|
| `DB_URL`      | `jdbc:mysql://localhost:3306/stock_trading_simulator`     | JDBC URL           |
| `DB_USERNAME` | `stock_app`                                              | MySQL user         |
| `DB_PASSWORD` | *(none — required)*                                      | MySQL password     |

## License

Academic project — not for production use.
