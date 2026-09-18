import { useEffect, useState } from 'react'

function TRUpload({ username, onLogout }) {
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
        <main className="upload-page">
            <header className="upload-header">
                <div className="brand-mark dark">TR<span>U</span></div>
                <div className="account-controls">
                    <span>Signed in as {username}</span>
                    <button className="logout-button" type="button" onClick={onLogout}>Sign out</button>
                </div>
            </header>
            <section className="upload-content">
                <p className="eyebrow dark-eyebrow">TRUpload / workspace</p>
                <h1>A clear starting point for your next upload workflow.</h1>
                <p className="lede">Your workspace is ready for the first upload feature.</p>
                <div className="status-row" aria-live="polite">
                    <span className="status-dot" />
                    <span>{status}</span>
                </div>
                <div className="upload-placeholder">
                    <p className="panel-label">Next step</p>
                    <h2>Build the upload workflow.</h2>
                    <p>Connect this workspace to the upload contract and persistence layer.</p>
                </div>
            </section>
        </main>
    )
}

export default TRUpload
