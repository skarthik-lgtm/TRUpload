# Development notes

## Branch and commit habits

- Keep changes focused by feature or fix.
- Run the relevant frontend and backend checks before opening a pull request.
- Update API or architecture documentation when behavior changes.

## Configuration

Local defaults are intentionally safe for development. Add environment-specific configuration outside source control. The frontend uses Vite's `/api` proxy, while deployment configuration can provide the equivalent reverse proxy or API base URL.

## Testing strategy

- Frontend: component and interaction tests for user-visible behavior.
- Backend: unit tests for services and MVC tests for HTTP contracts.
- End to end: cover the primary upload workflow once it is implemented.
