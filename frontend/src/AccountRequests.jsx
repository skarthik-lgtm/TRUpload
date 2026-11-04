import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    decideAccountRequest,
    decideRequestedWarehouse,
    decideWarehouseAccessRequest,
    assignAdminWarehouse,
    getAccountRequests,
    getAdminUsers,
    getWarehouses,
    getWarehouseAccessRequests,
    removeAdminWarehouse,
    replaceAdminWarehouse,
    updateAdminUserRole,
    updateAdminUserStatus,
} from './api.js'

function AccountRequests() {
    const [requests, setRequests] = useState([])
    const [warehouseRequests, setWarehouseRequests] = useState([])
    const [users, setUsers] = useState([])
    const [warehouseCatalog, setWarehouseCatalog] = useState([])
    const [selectedId, setSelectedId] = useState(null)
    const [statusFilter, setStatusFilter] = useState('PENDING')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [busyKey, setBusyKey] = useState('')
    const [warehouseConflict, setWarehouseConflict] = useState(null)
    const navigate = useNavigate()

    async function reload() {
        setLoading(true)
        setError('')
        try {
            const [records, accessRecords, userRecords, warehouses] = await Promise.all([
                getAccountRequests(), getWarehouseAccessRequests(true), getAdminUsers(), getWarehouses(),
            ])
            const list = Array.isArray(records) ? records : []
            setRequests(list)
            setWarehouseRequests(Array.isArray(accessRecords) ? accessRecords : [])
            setUsers(Array.isArray(userRecords) ? userRecords : [])
            setWarehouseCatalog(Array.isArray(warehouses) ? warehouses : [])
            setSelectedId((current) => list.some((item) => item.accountRequestId === current)
                ? current
                : list[0]?.accountRequestId ?? null)
        } catch (requestError) {
            setError(requestError.message || 'Could not load account requests.')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        Promise.all([getAccountRequests(), getWarehouseAccessRequests(true), getAdminUsers(), getWarehouses()])
            .then(([records, accessRecords, userRecords, warehouses]) => {
                const list = Array.isArray(records) ? records : []
                setRequests(list)
                setWarehouseRequests(Array.isArray(accessRecords) ? accessRecords : [])
                setUsers(Array.isArray(userRecords) ? userRecords : [])
                setWarehouseCatalog(Array.isArray(warehouses) ? warehouses : [])
                setSelectedId(list[0]?.accountRequestId ?? null)
            })
            .catch((requestError) => setError(requestError.message || 'Could not load account requests.'))
            .finally(() => setLoading(false))
    }, [])

    const visibleRequests = requests.filter((request) =>
        statusFilter === 'ALL' || request.requestStatus === statusFilter)
    const selected = requests.find((request) => request.accountRequestId === selectedId)

    async function decideAccount(request, decision) {
        const key = `account-${request.accountRequestId}`
        setBusyKey(key)
        try {
            await decideAccountRequest(request.accountRequestId, decision)
            await reload()
        } catch (requestError) {
            setError(requestError.message || 'Could not save account decision.')
        } finally {
            setBusyKey('')
        }
    }

    async function decideWarehouse(warehouse, decision) {
        const key = `warehouse-${warehouse.accountRequestWarehouseId}`
        setBusyKey(key)
        try {
            await decideRequestedWarehouse(warehouse.accountRequestWarehouseId, decision)
            await reload()
        } catch (requestError) {
            setError(requestError.message || 'Could not save warehouse decision.')
        } finally {
            setBusyKey('')
        }
    }

    async function decideAccessRequest(request, decision) {
        const key = `access-${request.warehouseAccessRequestId}`
        setBusyKey(key)
        try {
            await decideWarehouseAccessRequest(request.warehouseAccessRequestId, decision)
            await reload()
        } catch (requestError) {
            setError(requestError.message || 'Could not save warehouse access decision.')
        } finally {
            setBusyKey('')
        }
    }

    async function updateUser(user, action, value) {
        const key = `${action}-${user.userId}`
        setBusyKey(key)
        setError('')
        try {
            if (action === 'status') await updateAdminUserStatus(user.userId, value)
            if (action === 'role') await updateAdminUserRole(user.userId, value)
            if (action === 'assign') await assignAdminWarehouse(user.userId, Number(value))
            if (action === 'remove') await removeAdminWarehouse(user.userId, Number(value))
            if (action.startsWith('replace:')) {
                const currentWarehouseId = Number(action.split(':')[1])
                await replaceAdminWarehouse(user.userId, currentWarehouseId, Number(value))
            }
            await reload()
        } catch (requestError) {
            setError(requestError.message || 'Could not update user access.')
        } finally {
            setBusyKey('')
        }
    }

    function requestedWarehouseForUser(user, warehouseId) {
        const accountRequest = requests.find((request) => request.userId === user.userId)
        return accountRequest?.warehouses?.find((warehouse) =>
            warehouse.warehouseId === warehouseId && warehouse.requestStatus !== 'REJECTED') ?? null
    }

    async function handleAssignmentSelection(user, warehouseId) {
        const selectedWarehouseId = Number(warehouseId)
        if (!selectedWarehouseId) return

        const requestedWarehouse = requestedWarehouseForUser(user, selectedWarehouseId)
        if (requestedWarehouse) {
            const warehouse = warehouseCatalog.find((item) => item.warehouseId === selectedWarehouseId)
            setWarehouseConflict({
                user,
                facility: warehouse?.facility || 'This warehouse',
                status: requestedWarehouse.requestStatus,
            })
            return
        }

        await updateUser(user, 'assign', String(selectedWarehouseId))
    }

    async function handleReplacementSelection(user, currentWarehouseId, warehouseId) {
        const selectedWarehouseId = Number(warehouseId)
        if (!selectedWarehouseId) return

        const requestedWarehouse = requestedWarehouseForUser(user, selectedWarehouseId)
        if (requestedWarehouse) {
            const warehouse = warehouseCatalog.find((item) => item.warehouseId === selectedWarehouseId)
            setWarehouseConflict({
                user,
                facility: warehouse?.facility || 'This warehouse',
                status: requestedWarehouse.requestStatus,
            })
            return
        }

        await updateUser(user, `replace:${currentWarehouseId}`, String(selectedWarehouseId))
    }

    return (
        <main className="account-requests-page live-account-requests">
            <header className="account-page-header">
                <div>
                    <div className="account-breadcrumb">TR Upload <span>/</span> Administration <span>/</span> Requests</div>
                    <h1>Access requests</h1>
                    <p>Review account activation and each warehouse request independently.</p>
                </div>
                <div className="page-header-actions">
                    <button className="account-clear-button dashboard-back-button" type="button" onClick={() => navigate('/trupload')}>Dashboard</button>
                    <button className="account-clear-button" type="button" onClick={reload} disabled={loading}>Refresh</button>
                </div>
            </header>

            <div className="live-request-toolbar">
                <label htmlFor="request-status-filter">Request status</label>
                <select id="request-status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                    <option value="PENDING">Pending</option>
                    <option value="APPROVED">Approved</option>
                    <option value="REJECTED">Rejected</option>
                    <option value="ALL">All requests</option>
                </select>
                <span>{visibleRequests.length} request{visibleRequests.length === 1 ? '' : 's'}</span>
            </div>

            {error && <p className="form-message" role="alert">{error}</p>}
            {loading ? <p className="empty-state">Loading requests...</p> : !visibleRequests.length ? <p className="empty-state">No requests in this status.</p> : (
                <div className="live-request-layout">
                    <section className="live-request-list" aria-label="Account requests">
                        {visibleRequests.map((request) => (
                            <button
                                className={`live-request-item ${selectedId === request.accountRequestId ? 'selected' : ''}`}
                                type="button"
                                key={request.accountRequestId}
                                onClick={() => setSelectedId(request.accountRequestId)}
                            >
                                <span className="live-request-item-top"><strong>{request.firstName} {request.lastName}</strong><span className={`request-state state-${request.requestStatus.toLowerCase()}`}>{request.requestStatus}</span></span>
                                <span>{request.email}</span>
                                <small>{request.warehouses.length} warehouse request{request.warehouses.length === 1 ? '' : 's'} · {request.requestedAt ? new Date(request.requestedAt).toLocaleString() : 'Date unavailable'}</small>
                            </button>
                        ))}
                    </section>

                    {selected && <section className="live-request-detail" aria-label="Selected account request">
                        <div className="live-detail-heading">
                            <div><small>ACCOUNT REQUEST {selected.accountRequestId}</small><h2>{selected.firstName} {selected.lastName}</h2><p>{selected.username} · {selected.email}</p></div>
                            <span className={`request-state state-${selected.requestStatus.toLowerCase()}`}>{selected.requestStatus}</span>
                        </div>
                        <div className="account-decision-row">
                            <div><strong>Account access</strong><small>Current account state: {selected.accountStatus}</small></div>
                            <div className="request-actions">
                                <button type="button" disabled={selected.requestStatus !== 'PENDING' || busyKey !== ''} onClick={() => decideAccount(selected, 'APPROVED')}>Approve account</button>
                                <button type="button" className="reject" disabled={selected.requestStatus !== 'PENDING' || busyKey !== ''} onClick={() => decideAccount(selected, 'REJECTED')}>Reject account</button>
                            </div>
                        </div>

                        <div className="requested-warehouse-list">
                            <h3>Requested warehouses</h3>
                            {selected.warehouses.map((warehouse) => (
                                <article className="requested-warehouse" key={warehouse.accountRequestWarehouseId}>
                                    <div><strong>{warehouse.facility}</strong><small>{warehouse.division || 'No division'} · {warehouse.database} · Upload code {warehouse.uploadCode || '—'}</small></div>
                                    <span className={`request-state state-${warehouse.requestStatus.toLowerCase()}`}>{warehouse.requestStatus}</span>
                                    <div className="request-actions">
                                        <button type="button" disabled={warehouse.requestStatus !== 'PENDING' || busyKey !== ''} onClick={() => decideWarehouse(warehouse, 'APPROVED')}>Approve</button>
                                        <button type="button" className="reject" disabled={warehouse.requestStatus !== 'PENDING' || busyKey !== ''} onClick={() => decideWarehouse(warehouse, 'REJECTED')}>Reject</button>
                                    </div>
                                </article>
                            ))}
                        </div>
                    </section>}
                </div>
            )}
            <section className="warehouse-access-section admin-access-queue">
                <h2>Warehouse access changes</h2>
                {!warehouseRequests.filter((request) => request.requestStatus === 'PENDING').length
                    ? <p className="empty-state">No pending warehouse access changes.</p>
                    : <div className="warehouse-assignment-list">{warehouseRequests
                        .filter((request) => request.requestStatus === 'PENDING')
                        .map((request) => <article key={request.warehouseAccessRequestId}>
                            <strong>{request.username} · {request.requestType}</strong>
                            <span>{request.currentFacility || '—'}{request.requestedFacility ? ` → ${request.requestedFacility}` : ''} · {request.email}</span>
                            <div className="request-actions">
                                <button type="button" disabled={busyKey !== ''} onClick={() => decideAccessRequest(request, 'APPROVED')}>Approve</button>
                                <button type="button" className="reject" disabled={busyKey !== ''} onClick={() => decideAccessRequest(request, 'REJECTED')}>Reject</button>
                            </div>
                        </article>)}</div>}
            </section>
            <section className="warehouse-access-section admin-users-section">
                <h2>User accounts and assignments</h2>
                <div className="admin-users-list">{users.map((user) => {
                    const assignedIds = new Set(user.warehouses.map((warehouse) => warehouse.warehouseId))
                    const available = warehouseCatalog.filter((warehouse) => !assignedIds.has(warehouse.warehouseId))
                    const isSelf = user.email?.toLowerCase() === JSON.parse(window.localStorage.getItem('trupload_user') || '{}').email?.toLowerCase()
                    return <article className="admin-user-row" key={user.userId}>
                        <div className="admin-user-identity"><strong>{user.firstName} {user.lastName}</strong><span>{user.username} · {user.email}</span><small>{user.status} · {user.role}</small></div>
                        <div className="admin-user-controls">
                            <select aria-label={`Role for ${user.username}`} value={user.role} disabled={isSelf || busyKey !== ''} onChange={(event) => updateUser(user, 'role', event.target.value)}>
                                <option value="USER">USER</option><option value="ADMIN">ADMIN</option>
                            </select>
                            <select aria-label={`Status for ${user.username}`} value={user.status} disabled={isSelf || busyKey !== '' || !['ACTIVE', 'DISABLED'].includes(user.status)} onChange={(event) => updateUser(user, 'status', event.target.value)}>
                                {user.status === 'PENDING' && <option value="PENDING">PENDING</option>}
                                {user.status === 'REJECTED' && <option value="REJECTED">REJECTED</option>}
                                <option value="ACTIVE">ACTIVE</option><option value="DISABLED">DISABLED</option>
                            </select>
                            {String(user.role).toUpperCase() === 'USER' && <select aria-label={`Add warehouse for ${user.username}`} value="" disabled={isSelf || busyKey !== '' || !available.length} onChange={(event) => event.target.value && handleAssignmentSelection(user, event.target.value)}>
                                <option value="">Assign warehouse...</option>{available.map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.facility} · {warehouse.database}</option>)}
                            </select>}
                        </div>
                        {user.warehouses.length > 0 && <div className="admin-user-warehouses">{user.warehouses.map((warehouse) => <span className="admin-user-warehouse" key={warehouse.warehouseId}>
                            {warehouse.facility}
                            {String(user.role).toUpperCase() === 'USER' && <select aria-label={`Replace ${warehouse.facility} for ${user.username}`} value="" disabled={isSelf || busyKey !== '' || !available.length} onChange={(event) => event.target.value && handleReplacementSelection(user, warehouse.warehouseId, event.target.value)}>
                                <option value="">Replace...</option>{available.map((option) => <option key={option.warehouseId} value={option.warehouseId}>{option.facility}</option>)}
                            </select>}
                            {String(user.role).toUpperCase() === 'USER' && <button type="button" title={`Remove ${warehouse.facility}`} aria-label={`Remove ${warehouse.facility} from ${user.username}`} disabled={isSelf || busyKey !== ''} onClick={() => updateUser(user, 'remove', warehouse.warehouseId)}>×</button>}
                        </span>)}</div>}
                    </article>
                })}</div>
            </section>
            {warehouseConflict && <div className="modal-backdrop-custom" role="presentation">
                <section className="action-modal" role="dialog" aria-modal="true" aria-labelledby="warehouse-conflict-title">
                    <div className="action-modal-header"><div className="action-modal-icon">!</div></div>
                    <h2 id="warehouse-conflict-title">Warehouse request already raised</h2>
                    <p>
                        {warehouseConflict.facility} was already requested during account creation for {warehouseConflict.user.firstName} {warehouseConflict.user.lastName}.
                        Its current request status is {warehouseConflict.status}. Please review that registration warehouse request instead of assigning it directly.
                    </p>
                    <div className="action-modal-footer">
                        <button className="modal-primary-button" type="button" onClick={() => setWarehouseConflict(null)}>OK</button>
                    </div>
                </section>
            </div>}
        </main>
    )
}

export default AccountRequests