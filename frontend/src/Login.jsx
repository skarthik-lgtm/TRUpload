import { useState } from 'react'

function Login({ onLogin, onCreateAccount, onForgotPassword }) {
    const [showPassword, setShowPassword] = useState(false)

    function handleSubmit(event) {
        event.preventDefault()
        const formData = new FormData(event.currentTarget)
        onLogin(formData.get('username'))
    }

    return (
        <main className="login-page container-fluid p-0">
            <div className="row g-0 min-vh-100">
            <section className="login-intro col-lg-6 d-flex flex-column justify-content-between" aria-label="TRUpload introduction">
                <div className="brand-mark">TR<span>U</span></div>
                <div className="intro-copy">
                    <p className="eyebrow">Welcome back</p>
                    <h1>Move your work forward.</h1>
                    <p>Sign in to manage your uploads, keep data moving, and stay in control of every handoff.</p>
                </div>
                <p className="intro-footer">Simple workflows. Clear progress.</p>
            </section>

            <section className="login-form-panel col-lg-6 d-flex align-items-center justify-content-center">
                <div className="login-card w-100">
                    <div className="card-heading">
                        <p className="card-kicker">TRUpload</p>
                        <h2>Sign in to your account</h2>
                        <p>Enter your details below to continue.</p>
                    </div>

                    <form onSubmit={handleSubmit}>
                        <div className="mb-3">
                            <label className="form-label" htmlFor="username">Username</label>
                            <input className="form-control" id="username" name="username" type="text" placeholder="Enter your username" autoComplete="username" required />
                        </div>

                        <div className="password-label-row mb-2">
                            <label className="form-label mb-0" htmlFor="password">Password</label>
                            <button className="text-button" type="button" onClick={onForgotPassword}>
                                Forgot password?
                            </button>
                        </div>
                        <div className="password-field mb-3">
                            <input className="form-control" id="password" name="password" type={showPassword ? 'text' : 'password'} placeholder="Enter your password" autoComplete="current-password" required />
                            <button className="show-button" type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? 'Hide password' : 'Show password'}>
                                {showPassword ? 'Hide' : 'Show'}
                            </button>
                        </div>

                        <button className="submit-button btn w-100" type="submit">Sign in</button>
                    </form>

                    <div className="signup-prompt">
                        <span>Don&apos;t have an account?</span>
                            <button className="signup-button" type="button" onClick={onCreateAccount}>Create an account</button>
                    </div>
                </div>
            </section>
            </div>
        </main>
    )
}

export default Login
