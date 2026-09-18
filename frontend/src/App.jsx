import { useEffect, useState } from 'react'
import CreateAccount from './CreateAccount.jsx'
import ForgotPassword from './ForgotPassword.jsx'
import Login from './Login.jsx'
import TRUpload from './TRUpload.jsx'

function App() {
    const [user, setUser] = useState(null)
    const [page, setPage] = useState(getPageFromHash)

    useEffect(() => {
        function handleHashChange() {
            setPage(getPageFromHash())
        }

        window.addEventListener('hashchange', handleHashChange)
        return () => window.removeEventListener('hashchange', handleHashChange)
    }, [])

    function navigate(nextPage) {
        window.location.hash = nextPage === 'login' ? '/login' : `/${nextPage}`
    }

    if (user) {
        return <TRUpload username={user} onLogout={() => setUser(null)} />
    }

    if (page === 'create-account') {
        return <CreateAccount onBackToLogin={() => navigate('login')} />
    }

    if (page === 'forgot-password') {
        return <ForgotPassword onBackToLogin={() => navigate('login')} />
    }

    return <Login onLogin={setUser} onCreateAccount={() => navigate('create-account')} onForgotPassword={() => navigate('forgot-password')} />
}

function getPageFromHash() {
    const page = window.location.hash.replace(/^#\//, '')
    return ['create-account', 'forgot-password'].includes(page) ? page : 'login'
}

export default App
