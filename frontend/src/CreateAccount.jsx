import { useState } from 'react'
import { signup } from './api.js'

function CreateAccount({ onBackToLogin }) {
    const [showPassword, setShowPassword] = useState(false)
    const [message, setMessage] = useState('')
    const [isSubmitting, setIsSubmitting] = useState(false)

    async function handleSubmit(event) {
        event.preventDefault()
        const form = event.currentTarget
        const formData = new FormData(form)
        setMessage('')
        setIsSubmitting(true)

        try {
            const response = await signup({
                firstName: formData.get('firstName'),
                lastName: formData.get('lastName'),
                username: formData.get('username'),
                email: formData.get('email'),
                password: formData.get('password'),
                role: 'USER',
            })
            setMessage(response.message)
            form.reset()
        } catch (requestError) {
            setMessage(requestError.message)
        } finally {
            setIsSubmitting(false)
        }
    }

    return (
        <main className="login-page container-fluid p-0">
            <div className="row g-0 min-vh-100">
                <section className="login-intro col-lg-6 d-flex flex-column justify-content-between" aria-label="TRUpload introduction">
                    <div className="brand-mark">TR<span>U</span></div>
                    <div className="intro-copy">
                        <p className="eyebrow">Start here</p>
                        <h1>Make every handoff count.</h1>
                        <p>Create your TRUpload account and bring your upload workflow into one clear workspace.</p>
                    </div>
                    <p className="intro-footer">Simple workflows. Clear progress.</p>
                </section>

                <section className="login-form-panel col-lg-6 d-flex align-items-center justify-content-center">
                    <div className="login-card w-100">
                        <div className="card-heading">
                            <p className="card-kicker">TRUpload</p>
                            <h2>Create your account</h2>
                            <p>Set up your account to get started.</p>
                        </div>

                        <form onSubmit={handleSubmit}>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="firstName">First name</label>
                                <input className="form-control" id="firstName" name="firstName" type="text" placeholder="Enter your first name" autoComplete="given-name" required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="lastName">Last name</label>
                                <input className="form-control" id="lastName" name="lastName" type="text" placeholder="Enter your last name" autoComplete="family-name" required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="email">Email address</label>
                                <input className="form-control" id="email" name="email" type="email" placeholder="you@example.com" autoComplete="email" required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="username">Username</label>
                                <input className="form-control" id="username" name="username" type="text" placeholder="Choose a username" autoComplete="username" required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="password">Password</label>
                                <div className="password-field">
                                    <input className="form-control" id="password" name="password" type={showPassword ? 'text' : 'password'} placeholder="Create a password" autoComplete="new-password" minLength="8" required />
                                    <button className="show-button" type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? 'Hide password' : 'Show password'}>
                                        {showPassword ? 'Hide' : 'Show'}
                                    </button>
                                </div>
                            </div>
                            <button className="submit-button btn w-100" type="submit" disabled={isSubmitting}>
                                {isSubmitting ? 'Creating account...' : 'Create account'}
                            </button>
                            <p className="form-message" aria-live="polite">{message}</p>
                        </form>

                        <div className="signup-prompt">
                            <span>Already have an account?</span>
                            <button className="signup-button" type="button" onClick={onBackToLogin}>Sign in</button>
                        </div>
                    </div>
                </section>
            </div>
        </main>
    )
}

export default CreateAccount
