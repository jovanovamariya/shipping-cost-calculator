# Browser Access

**As a developer, I want the backend to accept browser requests only from our own frontend so that other websites can't call our API from users' browsers.**

## Rules and Examples

### Rule: Must accept browser requests only from the configured frontend origins

The default allowed origin is `http://localhost:5173` (development).

| Origin header | Outcome |
|---|---|
| `http://localhost:5173` | Allowed |
| `https://evil.example` | Refused by the CORS policy |
| `http://localhost:3000` | Refused — same host, different port |
| none (curl, server-to-server) | Not affected by CORS |

The `localhost:3000` row is the boundary: an origin must match exactly, including port.

---

## Resolved decisions (for implementation)

- **Allowed origins:** a configurable list, default `http://localhost:5173`; production sets its own. Documented in the README.
