# TRUpload

A full-stack application with a React frontend and Java Spring Boot backend.

## Project structure

```text
TRUpload/
├── frontend/       # React + Vite application
├── backend/        # Spring Boot REST API
├── docs/            # Architecture and development notes
├── .editorconfig
├── .gitignore
└── README.md
```

## Prerequisites

- Node.js 20+
- npm 10+
- Java 17+
- Maven 3.9+ (or use the Maven wrapper once generated)

## Run locally

### Backend

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080`. Verify it with:

```bash
curl http://localhost:8080/api/health
```

### Frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend runs at `http://localhost:5173` and proxies `/api` requests to the backend.

## Useful commands

### Frontend

```bash
npm run dev       # Start the Vite development server
npm run build     # Create a production build
npm run lint      # Run ESLint
npm run preview   # Preview the production build
```

### Backend

```bash
mvn test          # Run backend tests
mvn package       # Build the application JAR
```

See [docs/architecture.md](docs/architecture.md) for the initial architecture and development conventions.
