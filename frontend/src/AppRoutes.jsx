import { useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import CreateAccount from './CreateAccount.jsx'
import ForgotPassword from './ForgotPassword.jsx'
import Login from './Login.jsx'
import TRUpload from './TRUpload.jsx'
import { clearAuthToken } from './api.js'
function AppRoutes() {
    const [user, setUser] = useState(getStoredUser)
    const navigate = useNavigate()
    const location = useLocation()
    const isPreviewMode = new URLSearchParams(window.location.search).get('preview') === 'trupload'

    if (isPreviewMode) {
        return <TRUpload username="Preview User" role="ADMIN" onLogout={() => { window.location.href = '/' }} />
    }

    if (location.hash) {
        return <NotFound />
    }

    function handleLogin(response) {
        const role = Number(response.roleId) === 2 ? 'ADMIN' : 'USER'
        const authenticatedUser = { email: response.email, role }
        window.localStorage.setItem('trupload_user', JSON.stringify(authenticatedUser))
        setUser(authenticatedUser)
        navigate('/trupload')
    }

    function handleLogout() {
        clearAuthToken()
        window.localStorage.removeItem('trupload_user')
        setUser(null)
        navigate('/login')
    }

    return (
        <Routes>
            <Route path="/" element={<Navigate to="/login" replace />} />
            <Route
                path="/login"
                element={user ? <Navigate to="/trupload" replace /> : <Login onLogin={handleLogin} onCreateAccount={() => navigate('/create-account')} onForgotPassword={() => navigate('/forgot-password')} />}
            />
            <Route
                path="/create-account"
                element={user ? <Navigate to="/trupload" replace /> : <CreateAccount onBackToLogin={() => navigate('/login')} onSignupSuccess={() => navigate('/login')} />}
            />
            <Route
                path="/forgot-password"
                element={user ? <Navigate to="/trupload" replace /> : <ForgotPassword onBackToLogin={() => navigate('/login')} />}
            />
            <Route
                path="/trupload"
                element={user ? <TRUpload username={user.email} role={user.role} onLogout={handleLogout} /> : <Navigate to="/login" replace />}
            />
            <Route path="*" element={<NotFound />} />
        </Routes>
    )
}

function NotFound() {
    return <main><h1>Page not found</h1></main>
}

function getStoredUser() {
    const token = window.localStorage.getItem('trupload_token')
    const storedUser = window.localStorage.getItem('trupload_user')

    if (!token || !storedUser) {
        return null
    }

    try {
        return JSON.parse(storedUser)
    } catch {
        window.localStorage.removeItem('trupload_user')
        return null
    }
}

export default AppRoutes