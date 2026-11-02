import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faHouse } from '@fortawesome/free-solid-svg-icons'
import CreateAccount from './CreateAccount.jsx'
import {
    deleteAdminUser,
    getAdminUsers,
    getWarehouses,
    assignAdminWarehouse,
    removeAllAdminWarehouses,
    removeAdminWarehouse,
    replaceAdminWarehouse,
    updateAdminUserRole,
    updateAdminUserStatus,
} from './api.js'

function AdminAccountManagement({ adminEmail }) {
    const [users, setUsers] = useState([])
    const [warehouses, setWarehouses] = useState([])
    const [selectedUserId, setSelectedUserId] = useState(null)
    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('ALL')
    const [isCreateOpen, setIsCreateOpen] = useState(false)
    const [deleteTarget, setDeleteTarget] = useState(null)
    const [clearWarehousesTarget, setClearWarehousesTarget] = useState(null)
    const [loading, setLoading] = useState(true)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')
    const [notice, setNotice] = useState('')
    const navigate = useNavigate()

    async function reload() {
        setLoading(true)
        setError('')
        try {
            const [userRows, warehouseRows] = await Promise.all([getAdminUsers(), getWarehouses()])
            setUsers(Array.isArray(userRows) ? userRows : [])
            setWarehouses(Array.isArray(warehouseRows) ? warehouseRows : [])
        } catch (requestError) {
            setError(requestError.message || 'Could not load user accounts.')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        let current = true
        Promise.all([getAdminUsers(), getWarehouses()])
            .then(([userRows, warehouseRows]) => {
                if (!current) return
                setUsers(Array.isArray(userRows) ? userRows : [])
                setWarehouses(Array.isArray(warehouseRows) ? warehouseRows : [])
            })
            .catch((requestError) => {
                if (current) setError(requestError.message || 'Could not load user accounts.')
            })
            .finally(() => {
                if (current) setLoading(false)
            })
        return () => { current = false }
    }, [])

    const currentAdmin = users.find((user) => user.email?.toLowerCase() === adminEmail?.toLowerCase())
    const selectedUser = users.find((user) => user.userId === selectedUserId)
    const visibleUsers = users.filter((user) => {
        const matchesStatus = statusFilter === 'ALL' || user.status === statusFilter
        const query = search.trim().toLowerCase()
        const matchesSearch = !query || [user.firstName, user.lastName, user.username, user.email]
            .some((value) => String(value || '').toLowerCase().includes(query))
        return matchesStatus && matchesSearch
    })

    async function runAction(action) {
        setBusy(true)
        setError('')
        setNotice('')
        try {
            await action()
            await reload()
        } catch (requestError) {
            setError(requestError.message || 'Could not complete the account action.')
        } finally {
            setBusy(false)
        }
    }

    async function assign(user, warehouseId, replacingWarehouseId = null) {
        const selectedWarehouseId = Number(warehouseId)
        await runAction(() => replacingWarehouseId
            ? replaceAdminWarehouse(user.userId, replacingWarehouseId, selectedWarehouseId)
            : assignAdminWarehouse(user.userId, selectedWarehouseId))
    }

    function handleCreated() {
        setIsCreateOpen(false)
        setNotice('User account created and warehouse access assigned.')
        reload()
    }

    return (
        <main className="account-requests-page admin-account-management">
            <header className="account-page-header">
                <div>
                    <div className="account-breadcrumb">TR Upload <span>/</span> Administration <span>/</span> User Management</div>
                    <h1>User management</h1>
                    <p>Create user accounts and manage warehouse access.</p>
                </div>
                <div className="admin-header-actions">
                    <button className="account-clear-button admin-home-button" type="button" onClick={() => navigate('/trupload')}><FontAwesomeIcon icon={faHouse} /> TRUpload Home</button>
                    <div className="admin-profile-summary">
                        <span className="admin-profile-mark">{(currentAdmin?.firstName || 'A').slice(0, 1)}{(currentAdmin?.lastName || 'D').slice(0, 1)}</span>
                        <span><strong>{currentAdmin ? `${currentAdmin.firstName} ${currentAdmin.lastName}` : 'Administrator'}</strong><small>{adminEmail} · ADMIN</small></span>
                    </div>
                </div>
            </header>

            <div className="admin-users-toolbar">
                <label className="admin-users-search"><span>Find a user</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, username, or email" /></label>
                <label><span>Account status</span><select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                    <option value="ALL">All accounts</option><option value="ACTIVE">Active</option><option value="PENDING">Pending</option><option value="DISABLED">Disabled</option><option value="REJECTED">Rejected</option>
                </select></label>
                <span className="admin-user-count">{visibleUsers.length} user{visibleUsers.length === 1 ? '' : 's'}</span>
                <button className="modal-primary-button" type="button" onClick={() => setIsCreateOpen(true)}>Create user</button>
            </div>

            {error && <p className="form-message" role="alert">{error}</p>}
            {notice && <p className="form-message" role="status">{notice}</p>}
            {loading ? <p className="empty-state">Loading user accounts...</p> : !visibleUsers.length ? <p className="empty-state">No users match these filters.</p> : (
                <section className="admin-user-table" aria-label="User accounts">
                    <div className="admin-user-table-head"><span>User</span><span>Status</span><span>Role</span><span>Warehouse access</span></div>
                    {visibleUsers.map((user) => {
                        return <button className="admin-user-table-row" type="button" key={user.userId} onClick={() => setSelectedUserId(user.userId)}>
                            <span className="admin-user-cell"><strong>{user.firstName} {user.lastName}</strong><small>{user.username} · {user.email}</small></span>
                            <span><i className={`admin-status-dot ${user.status.toLowerCase()}`} />{user.status}</span>
                            <span>{user.role}</span>
                            <span>{user.warehouses.length} assigned</span>
                        </button>
                    })}
                </section>
            )}

            {isCreateOpen && <div className="modal-backdrop-custom admin-create-backdrop" role="presentation" onClick={(event) => {
                if (event.target === event.currentTarget) setIsCreateOpen(false)
            }}>
                <section className="admin-create-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-create-title">
                    <CreateAccount
                        embedded
                        onBackToLogin={() => setIsCreateOpen(false)}
                        onSignupSuccess={handleCreated}
                    />
                </section>
            </div>}

            {selectedUser && <div className="modal-backdrop-custom admin-user-detail-backdrop" role="presentation" onClick={(event) => {
                if (event.target === event.currentTarget) setSelectedUserId(null)
            }}>
                <section className="admin-user-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-user-detail-title">
                    <header className="admin-user-detail-header">
                        <div><small>USER ACCOUNT · {selectedUser.userId}</small><h2 id="admin-user-detail-title">{selectedUser.firstName} {selectedUser.lastName}</h2><p>{selectedUser.username} · {selectedUser.email}</p></div>
                        <button className="modal-close-button" type="button" onClick={() => setSelectedUserId(null)} aria-label="Close user details">×</button>
                    </header>

                    <section className="admin-user-detail-section">
                        <div className="admin-user-section-title"><h3>Account settings</h3></div>
                        <div className="admin-user-settings">
                            <label>Role<select value={selectedUser.role} disabled={busy || selectedUser.email?.toLowerCase() === adminEmail?.toLowerCase()} onChange={(event) => runAction(() => updateAdminUserRole(selectedUser.userId, event.target.value))}>
                                <option value="USER">USER</option><option value="ADMIN">ADMIN</option>
                            </select></label>
                            <label>Status<select value={selectedUser.status} disabled={busy || selectedUser.email?.toLowerCase() === adminEmail?.toLowerCase()} onChange={(event) => runAction(() => updateAdminUserStatus(selectedUser.userId, event.target.value))}>
                                {selectedUser.status === 'PENDING' && <option value="PENDING">PENDING</option>}
                                {selectedUser.status === 'REJECTED' && <option value="REJECTED">REJECTED</option>}
                                <option value="ACTIVE">ACTIVE</option><option value="DISABLED">DISABLED</option>
                            </select></label>
                        </div>
                    </section>

                    {String(selectedUser.role).toUpperCase() === 'USER' && <section className="admin-user-detail-section">
                        <div className="admin-user-section-title"><h3>Warehouse assignments</h3><span>{selectedUser.warehouses.length} active</span></div>
                        {selectedUser.warehouses.length > 0 && <button className="user-assignment-remove remove-all-assignments" type="button" disabled={busy} onClick={() => setClearWarehousesTarget(selectedUser)}>Remove all assignments</button>}
                        <label className="admin-assign-select">Assign warehouse<select value="" disabled={busy} onChange={(event) => event.target.value && assign(selectedUser, event.target.value)}>
                            <option value="">Choose a warehouse</option>{warehouses.filter((warehouse) => !selectedUser.warehouses.some((item) => item.warehouseId === warehouse.warehouseId)).map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.facility} · {warehouse.database} · {warehouse.uploadCode}</option>)}
                        </select></label>
                        <div className="admin-staged-warehouse-list">{selectedUser.warehouses.map((item) => <div className="admin-staged-warehouse" key={item.warehouseId}>
                            <span><strong>{item.facility}</strong><small>{item.division || 'No division'} · {item.database} · {item.uploadCode || 'No upload code'}</small></span>
                            <select aria-label={`Replace ${item.facility}`} value="" disabled={busy} onChange={(event) => event.target.value && assign(selectedUser, event.target.value, item.warehouseId)}>
                                <option value="">Replace...</option>{warehouses.filter((warehouse) => !selectedUser.warehouses.some((assigned) => assigned.warehouseId === warehouse.warehouseId)).map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.facility} · {warehouse.database}</option>)}
                            </select>
                            <button className="user-assignment-remove" type="button" disabled={busy} onClick={() => runAction(() => removeAdminWarehouse(selectedUser.userId, item.warehouseId))}>Remove</button>
                        </div>)}</div>
                    </section>}

                    <footer className="admin-user-detail-footer">
                        <button className="user-delete-button" type="button" disabled={busy || selectedUser.role === 'ADMIN' || selectedUser.email?.toLowerCase() === adminEmail?.toLowerCase()} onClick={() => setDeleteTarget(selectedUser)}>Delete user</button>
                        <button className="modal-secondary-button" type="button" onClick={() => setSelectedUserId(null)}>Close</button>
                    </footer>
                </section>
            </div>}

            {deleteTarget && <div className="modal-backdrop-custom" role="presentation">
                <section className="action-modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-user-title">
                    <h2 id="delete-user-title">Delete user account?</h2>
                    <p>This permanently removes {deleteTarget.firstName} {deleteTarget.lastName}, their warehouse assignments, and account requests. Audit history is retained.</p>
                    <div className="action-modal-footer">
                        <button className="modal-secondary-button" type="button" onClick={() => setDeleteTarget(null)}>Cancel</button>
                        <button className="user-delete-button" type="button" disabled={busy} onClick={() => runAction(async () => {
                            await deleteAdminUser(deleteTarget.userId)
                            setDeleteTarget(null)
                            setSelectedUserId(null)
                        })}>Delete user</button>
                    </div>
                </section>
            </div>}
            {clearWarehousesTarget && <div className="modal-backdrop-custom" role="presentation">
                <section className="action-modal" role="alertdialog" aria-modal="true" aria-labelledby="remove-all-warehouses-title">
                    <h2 id="remove-all-warehouses-title">Remove all warehouse access?</h2>
                    <p>This will revoke all {clearWarehousesTarget.warehouses.length} active warehouse assignment(s) for {clearWarehousesTarget.firstName} {clearWarehousesTarget.lastName}. The account will remain active.</p>
                    <div className="action-modal-footer">
                        <button className="modal-secondary-button" type="button" onClick={() => setClearWarehousesTarget(null)}>Cancel</button>
                        <button className="user-delete-button" type="button" disabled={busy} onClick={() => runAction(async () => {
                            await removeAllAdminWarehouses(clearWarehousesTarget.userId)
                            setClearWarehousesTarget(null)
                        })}>Remove all access</button>
                    </div>
                </section>
            </div>}
        </main>
    )
}

export default AdminAccountManagement