import { useState } from 'react'

function ForgotPassword({ onBackToLogin }) {
    const [email, setEmail] = useState('')
    const [message, setMessage] = useState('')

    function handleSubmit(event) {
        event.preventDefault()
        setMessage('If an account exists for this email, reset instructions will be sent shortly.')
        setEmail('')
    }

    return (
        <main className="login-page container-fluid p-0">
            <div className="row g-0 min-vh-100">
                <section className="login-intro col-lg-6 d-flex flex-column justify-content-between" aria-label="TRUpload introduction">
                    <div className="brand-mark">TR<span>U</span></div>
                    <div className="intro-copy">
                        <p className="eyebrow">Account recovery</p>
                        <h1>Find your way back in.</h1>
                        <p>We will help you get back to your workspace securely and keep your workflow moving.</p>
                    </div>
                    <p className="intro-footer">Simple workflows. Clear progress.</p>
                </section>

                <section className="login-form-panel col-lg-6 d-flex align-items-center justify-content-center">
                    <div className="login-card w-100">
                        <div className="card-heading">
                            <p className="card-kicker">TRUpload</p>
                            <h2>Forgot your password?</h2>
                            <p>Enter your email and we will send you reset instructions.</p>
                        </div>

                        <form onSubmit={handleSubmit}>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="email">Email address</label>
                                <input className="form-control" id="email" name="email" type="email" placeholder="you@example.com" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
                            </div>
                            <button className="submit-button btn w-100" type="submit">Send reset instructions</button>
                            <p className="form-message" aria-live="polite">{message}</p>
                        </form>

                        <div className="signup-prompt">
                            <span>Remembered your password?</span>
                            <button className="signup-button" type="button" onClick={onBackToLogin}>Back to sign in</button>
                        </div>
                    </div>
                </section>
            </div>
        </main>
    )
}

export default ForgotPassword
