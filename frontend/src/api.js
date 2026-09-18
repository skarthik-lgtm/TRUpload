async function request(path, options = {}) {
    const response = await fetch(path, {
        headers: {
            'Content-Type': 'application/json',
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


export function signup(account) {
    return request('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify(account),
    })
}