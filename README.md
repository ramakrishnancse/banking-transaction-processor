
# Banking Transaction Processor 

Accounts, deposits, withdrawals, transfers and a per-account ledger, persisted in H2
and safe under concurrent requests.

## Run


mvn test
mvn spring-boot:run          # data is stored in ./data/bankdb (survives restarts)


H2 console (development only): http://localhost:8080/h2-console
JDBC URL `jdbc:h2:file:./data/bankdb`, user `sa`, empty password. Stop the app first if the
console reports the database is locked.

## API (`/api/v1`)

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/accounts` | `{"ownerName":"Alice","initialDeposit":100.00}` | 201 + account (number assigned by the bank) |
| GET | `/accounts/{n}` | | balance |
| GET | `/accounts/{n}/transactions?page=0&size=20` | | paged ledger, newest first |
| POST | `/accounts/{n}/deposits` | `{"amount":25.50}` | 200 + ledger receipt |
| POST | `/accounts/{n}/withdrawals` | `{"amount":10}` | 200 + ledger receipt |
| POST | `/transfers` | `{"fromAccount":1000000001,"toAccount":1000000002,"amount":40}` | 200 + sender's receipt |

Money-moving POSTs accept an optional `Idempotency-Key` header (max 64 chars).

| Status | Meaning |
|---|---|
| 400 | Invalid amount (zero, negative, > 2 decimals), malformed body, same-account transfer |
| 404 | Unknown account |
| 409 | Idempotency key reused for a different operation |
| 422 | Insufficient funds |
| 503 | Lock timeout, nothing was applied, safe to retry |

Postman request
------------------
curl -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' \
     -d '{"ownerName":"Alice","initialDeposit":100.00}'
curl -X POST localhost:8080/api/v1/accounts/1000000001/deposits \
     -H 'Content-Type: application/json' -H 'Idempotency-Key: order-42' -d '{"amount":25.50}'

## How it behaves like a real banking core

- **Atomic:** each operation is one DB transaction; balance changes and ledger rows commit together or not at all.
- **Row locks:** `SELECT ... FOR UPDATE` on the account rows; concurrent operations on one account queue up.
- **No deadlocks:** a transfer always locks the lower account number first.
- **Idempotent:** a retried request with the same key returns the original receipt and moves no money. The key is checked after the lock is held, so even simultaneous retries apply once.
- **Auditable ledger:** append-only, `balanceAfter` on every row, both transfer legs share a `reference`, opening deposits are ledger entries, so the ledger always sums to the balance (tested).
- **Money:** `BigDecimal`, `DECIMAL(19,2)`, extra precision is rejected, never rounded.
- **Optimistic `@Version`** on the account row as a second line of defence.

## Assumptions and trade-offs

- Single currency. No authentication or authorisation.
- `ddl-auto=update` keeps setup simple. For production use Flyway/Liquibase migrations and `validate`.
- H2 is embedded and single-process. For production run PostgreSQL or similar: swap the driver and URL, the locking code is standard JPA.
- Failed requests are not audited in the ledger; they return errors only.
- "Real time" here means synchronous, immediately consistent processing. There is no event streaming or push notifications.

## Next steps

Account status (frozen/closed), per-account overdraft limits, date-range filters on history,
currency-aware `Money` type, outbox + events for notifications, metrics and health endpoints.

