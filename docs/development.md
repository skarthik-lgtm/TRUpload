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

### RTR generation in QA

After a billing group passes validation, the backend invokes `ie.exe` on the configured RTR server through Windows PowerShell Remoting (WinRM). It creates `<routeGroupId>.rtr` in the remote output directory. Successful attempts are recorded in `UploadBatch` and `UploadGroupResult` with `RTR_GENERATED` status; `UploadedBilling` is not written and source routes are not marked as uploaded during this phase.

Configure these environment variables on the backend host:

- `RTR_REMOTE_SERVER` (defaults to `optmsctxman01qv`)
- `RTR_REMOTE_EXECUTABLE_PATH` (defaults to `D:/Program Files/Fleet Management/bin/ie.exe`)
- `RTR_OUTPUT_DIRECTORY` (defaults to `D:/RTE`)
- `RTR_DATABASE_USERNAME` and `RTR_DATABASE_PASSWORD` (required; credentials used by `ie.exe`)
- `RTR_REMOTE_USERNAME` and `RTR_REMOTE_PASSWORD` (optional; when omitted, WinRM uses the backend process identity)
- `RTR_LOG_DIRECTORY` (defaults to `D:/TRUpload/Logs/RTR`; one redacted log file is written per attempt)
- `RTR_POWERSHELL_EXECUTABLE` (defaults to `powershell.exe`; configure `pwsh` if that is the installed WinRM client)
- `RTR_FILE_EXTENSION`, `RTR_CONNECTION_TIMEOUT`, and `RTR_EXECUTION_TIMEOUT` (optional)

The backend preserves any existing RTR at the target path and does not reject zero-byte output. It verifies that the target file exists and was created or updated during the current attempt, then records its size (including zero), result, and `ie.exe` console output in the per-attempt log. The backend host must be able to create files in `RTR_LOG_DIRECTORY`; the remote identity must be able to run `ie.exe`, create or update files under the output directory, and access its configured data source. Keep all passwords in deployment secrets or environment variables; do not commit them to properties files.

The configured SQL Server login must be able to access the `TRUpload` database and the source warehouse databases queried by the upload workflow. The account/access workflow expects the tables in `TRUpload.dbo`: `Roles`, `Users`, `UserRoles`, `Warehouses`, `AccountRequests`, `AccountRequestWarehouses`, `UserWarehouses`, `WarehouseAccessRequests`, and `AuditLogs`. Seed `Roles` with `ADMIN` and `USER` before using signup.

Provision the first active administrator through a controlled DBA operation before relying on the admin UI. Public signup always creates a `PENDING` `USER`; it cannot create or request an administrator role. The account and warehouse decision flows then write their results and audit records through the backend.

The frontend uses Vite's `/api` proxy, while deployment configuration can provide the equivalent reverse proxy or API base URL.

The SQL Server connection check runs after the application starts. SQL Server credentials are required; the application will not fall back to embedded or local database credentials. If SQL Server is unavailable, startup may fail when a required upload table check runs.

## Testing strategy

- Frontend: component and interaction tests for user-visible behavior.
- Backend: unit tests for services and MVC tests for HTTP contracts.
- End to end: cover the primary upload workflow once it is implemented.
