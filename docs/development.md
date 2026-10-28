# Development notes

## Branch and commit habits

- Keep changes focused by feature or fix.
- Run the relevant frontend and backend checks before opening a pull request.
- Update API or architecture documentation when behavior changes.

## Configuration

The backend uses SQL Server for authentication, warehouse access, upload, and audit data. Set these environment variables before starting it:

- `SQLSERVER_HOST`
- `SQLSERVER_PORT` (defaults to `1433`)
- `SQLSERVER_USERNAME`
- `SQLSERVER_PASSWORD`
- `JWT_SECRET` (at least 32 characters; changing it invalidates existing tokens)
- `JWT_EXPIRATION_MS` (optional; defaults to eight hours)

The configured SQL Server login must be able to access the `TRUpload` database and the source warehouse databases queried by the upload workflow. The account/access workflow expects the tables in `TRUpload.dbo`: `Roles`, `Users`, `UserRoles`, `Warehouses`, `AccountRequests`, `AccountRequestWarehouses`, `UserWarehouses`, `WarehouseAccessRequests`, and `AuditLogs`. Seed `Roles` with `ADMIN` and `USER` before using signup.

Provision the first active administrator through a controlled DBA operation before relying on the admin UI. Public signup always creates a `PENDING` `USER`; it cannot create or request an administrator role. The account and warehouse decision flows then write their results and audit records through the backend.

The frontend uses Vite's `/api` proxy, while deployment configuration can provide the equivalent reverse proxy or API base URL.

The SQL Server connection check runs after the application starts. SQL Server credentials are required; the application will not fall back to embedded or local database credentials. If SQL Server is unavailable, startup may fail when a required upload table check runs.

## Testing strategy

- Frontend: component and interaction tests for user-visible behavior.
- Backend: unit tests for services and MVC tests for HTTP contracts.
- End to end: cover the primary upload workflow once it is implemented.
