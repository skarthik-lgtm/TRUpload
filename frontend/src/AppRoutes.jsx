import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import AuditLogs from './AuditLogs.jsx'
import CreateAccount from './CreateAccount.jsx'
import ForgotPassword from './ForgotPassword.jsx'
import Login from './Login.jsx'
import TRUpload from './TRUpload.jsx'
import { clearAuthToken } from './api.js'
import AccountAccessStagingArea from './AccountAccessStagingArea.jsx'

const SESSION_IDLE_TIMEOUT_MS = 15 * 60 * 1000

function AppRoutes() {
    const [user, setUser] = useState(getStoredUser)
    const navigate = useNavigate()
    const location = useLocation()
    const isPreviewMode = new URLSearchParams(window.location.search).get('preview') === 'trupload'

    useEffect(() => {
        if (!user) {
            return undefined
        }

        let idleTimer

        function expireSession() {
            clearAuthToken()
            setUser(null)
            navigate('/login', { replace: true })
        }

        function scheduleExpiry() {
            window.clearTimeout(idleTimer)
            const lastActivity = Number(window.localStorage.getItem('trupload_last_activity'))
            const remainingTime = SESSION_IDLE_TIMEOUT_MS - (Date.now() - lastActivity)

            if (!lastActivity || remainingTime <= 0) {
                expireSession()
                return
            }

            idleTimer = window.setTimeout(expireSession, remainingTime)
        }

        function recordActivity() {
            window.localStorage.setItem('trupload_last_activity', String(Date.now()))
            scheduleExpiry()
        }

        function handleStorageChange(event) {
            if (event.key === 'trupload_last_activity') {
                scheduleExpiry()
            } else if (event.key === 'trupload_token' && !event.newValue) {
                setUser(null)
                navigate('/login', { replace: true })
            }
        }

        const activityEvents = ['pointerdown', 'keydown', 'touchstart', 'scroll']
        activityEvents.forEach((eventName) => window.addEventListener(eventName, recordActivity, { passive: true }))
        window.addEventListener('storage', handleStorageChange)
        scheduleExpiry()

        return () => {
            window.clearTimeout(idleTimer)
            activityEvents.forEach((eventName) => window.removeEventListener(eventName, recordActivity))
            window.removeEventListener('storage', handleStorageChange)
        }
    }, [user, navigate])

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
        window.localStorage.setItem('trupload_last_activity', String(Date.now()))
        setUser(authenticatedUser)
        navigate('/trupload')
    }

    function handleLogout() {
        clearAuthToken()
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
            <Route
                path="/admin/account-requests"
                element={<AccountAccessStagingArea />}
            />
            <Route path="/audit-logs" element={user ? <AuditLogs username={user.email} role={user.role} onLogout={handleLogout} /> : <Navigate to="/login" replace />} />
            <Route path="/trupload/*" element={<NotFound />} />
            <Route path="/audit-logs/*" element={<NotFound />} />
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