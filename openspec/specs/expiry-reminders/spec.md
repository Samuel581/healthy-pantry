# Spec: Expiry Reminders

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain. See note in
> `specs/pantry-stock/spec.md`.

## ADDED Requirements

### Requirement: Expiry Notification Scheduling
The system MUST run a daily background check (WorkManager periodic work) over all
`StockBatch` rows with a non-null `expiryDate`, and post a local notification for items
expiring within a defined lookahead window (e.g. 3 days). This requires a notification
channel and, on API 33+, the `POST_NOTIFICATIONS` runtime permission.

#### Scenario: Item expiring soon triggers a notification
- **Given** a `StockBatch` of "Yogurt" has an expiry date 2 days from now and the lookahead
  window is 3 days
- **When** the daily `ExpiryCheckWorker` run executes
- **Then** a local notification is posted listing "Yogurt" as expiring soon

#### Scenario: Item outside the lookahead window is not flagged
- **Given** a `StockBatch` expires 10 days from now
- **When** the daily worker run executes with a 3-day lookahead
- **Then** no notification is posted for that batch on this run

### Requirement: Notification-Denied Fallback
If the user denies `POST_NOTIFICATIONS`, the system MUST still surface expiring-soon items
through an in-app fallback surface (e.g. banner or badge on the pantry screen), driven by
the same `ExpiryCheckWorker` result, so expiry visibility does not depend solely on the
notification permission.

#### Scenario: Notification permission denied, in-app fallback still shows expiring items
- **Given** the user has denied `POST_NOTIFICATIONS`
- **When** the daily `ExpiryCheckWorker` run finds items expiring within the lookahead
  window
- **Then** no system notification is posted, but the pantry screen displays an in-app
  banner/badge listing the same expiring items
