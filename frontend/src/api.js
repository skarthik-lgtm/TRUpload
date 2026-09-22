async function request(path, options = {}) {
    const token = window.localStorage.getItem('trupload_token')
    const response = await fetch(path, {
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...options.headers,
        },
        ...options,
    })

    const body = await response.json().catch(() => null)

    if (!response.ok) {
        throw new Error(body?.message || 'The request could not be completed.')
    }

    return body
}

export function login(credentials) {
    return request('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials),
    })
}

export function clearAuthToken() {

    window.localStorage.removeItem('trupload_token')

}

export function signup(account) {
    return request('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify(account),
    })
}