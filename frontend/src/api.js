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

export function getDatabaseInstances() {
    return request('/api/database/instances')
}

export function clearAuthToken() {
    window.localStorage.removeItem('trupload_token')
    window.localStorage.removeItem('trupload_user')
    window.localStorage.removeItem('trupload_last_activity')
}

export function signup(account) {
    return request('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify(account),
    })
}

export function uploadBillingGroups(selections) {
    return request('/api/uploads', {
        method: 'POST',
        body: JSON.stringify({ selections }),
    })
}

export function getFailedUploads() {
    return request('/api/uploads/failed')
}

export function getSuccessfulUploads() {
    return request('/api/uploads/successful')
}

export function getWarehouses() {
    return request('/api/warehouses')
}

export function getAccountRequests() {
    return request('/api/admin/account-requests')
}

export function decideAccountRequest(id, decision, comments = '') {
    return request(`/api/admin/account-requests/${id}/account`, {
        method: 'PATCH',
        body: JSON.stringify({ decision, comments }),
    })
}

export function decideRequestedWarehouse(id, decision, comments = '') {
    return request(`/api/admin/account-requests/warehouses/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ decision, comments }),
    })
}

export function getMyAccountRequests() {
    return request('/api/account/requests')
}

export function getMyWarehouses() {
    return request('/api/warehouses/mine')
}

export function submitWarehouseAccessRequest(accessRequest) {
    return request('/api/warehouse-access-requests', {
        method: 'POST',
        body: JSON.stringify(accessRequest),
    })
}

export function getWarehouseAccessRequests(admin = false) {
    return request(admin ? '/api/admin/warehouse-access-requests' : '/api/warehouse-access-requests')
}

export function decideWarehouseAccessRequest(id, decision, comments = '') {
    return request(`/api/admin/warehouse-access-requests/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ decision, comments }),
    })
}

export function getAdminUsers() {
    return request('/api/admin/users')
}

export function updateAdminUserStatus(id, status) {
    return request(`/api/admin/users/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
    })
}

export function updateAdminUserRole(id, role) {
    return request(`/api/admin/users/${id}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role }),
    })
}

export function assignAdminWarehouse(userId, warehouseId) {
    return request(`/api/admin/users/${userId}/warehouses`, {
        method: 'POST',
        body: JSON.stringify({ warehouseId }),
    })
}

export function removeAdminWarehouse(userId, warehouseId) {
    return request(`/api/admin/users/${userId}/warehouses/${warehouseId}`, { method: 'DELETE' })
}

export function replaceAdminWarehouse(userId, currentWarehouseId, requestedWarehouseId) {
    return request(`/api/admin/users/${userId}/warehouses/replace`, {
        method: 'POST',
        body: JSON.stringify({ currentWarehouseId, requestedWarehouseId }),
    })
}

export function getAccessAuditLogs() {
    return request('/api/admin/audit-logs')
}