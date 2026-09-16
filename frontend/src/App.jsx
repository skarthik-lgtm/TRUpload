import { useEffect, useState } from 'react'

function App() {
    const [status, setStatus] = useState('Checking API...')

    useEffect(() => {
        fetch('/api/health')
            .then((response) => {
                if (!response.ok) throw new Error('API unavailable')
                return response.json()
            })
            .then((body) => setStatus(body.status === 'ok' ? 'API connected' : 'API responded'))
            .catch(() => setStatus('API unavailable'))
    }, [])

    return (
        <main className="app-shell">
            <section className="hero-panel">
                <p className="eyebrow">TRUpload / workspace</p>
                <h1>A clear starting point for your next upload workflow.</h1>
                <p className="lede">
                    The React frontend is connected to a Spring Boot API and ready for the first feature slice.
                </p>
                <div className="status-row" aria-live="polite">
                    <span className="status-dot" />
                    <span>{status}</span>
                </div>
            </section>
            <section className="next-panel">
                <p className="panel-label">Next step</p>
                <h2>Build the domain around the workflow.</h2>
                <p>Start with the upload contract, validation rules, and the first persistent resource.</p>
            </section>
        </main>
    )
}

export default App
