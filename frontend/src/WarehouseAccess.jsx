import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    getMyWarehouses,
    getMyAccountRequests,
    getWarehouses,
    getWarehouseAccessRequests,
    submitWarehouseAccessRequest,
} from './api.js'

function WarehouseAccess() {
    const [assigned, setAssigned] = useState([])
    const [catalog, setCatalog] = useState([])
    const [requests, setRequests] = useState([])
    const [accountRequests, setAccountRequests] = useState([])
    const [requestType, setRequestType] = useState('ADD')
    const [currentWarehouseId, setCurrentWarehouseId] = useState('')
    const [requestedWarehouseId, setRequestedWarehouseId] = useState('')
    const [message, setMessage] = useState('')
    const [error, setError] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const navigate = useNavigate()

    useEffect(() => {
        let ignore = false

        Promise.all([
            getMyWarehouses(),
            getWarehouses(),
            getWarehouseAccessRequests(),
            getMyAccountRequests(),
        ])
            .then(([activeWarehouses, allWarehouses, accessRequests, accountStatus]) => {
                if (ignore) return
                setAssigned(Array.isArray(activeWarehouses) ? activeWarehouses : [])
                setCatalog(Array.isArray(allWarehouses) ? allWarehouses : [])
                setRequests(Array.isArray(accessRequests) ? accessRequests : [])
                setAccountRequests(Array.isArray(accountStatus) ? accountStatus : [])
            })
            .catch((requestError) => {
                if (!ignore) {
                    setError(requestError.message || 'Could not load warehouse access details.')
                }
            })

        return () => { ignore = true }
    }, [])

    const unassigned = catalog.filter((warehouse) => !assigned.some((item) => item.warehouseId === warehouse.warehouseId))

    async function submit(event) {
        event.preventDefault()
        setSubmitting(true)
        setError('')
        setMessage('')
        try {
            await submitWarehouseAccessRequest({
                requestType,
                currentWarehouseId: requestType === 'ADD' ? null : Number(currentWarehouseId),
                requestedWarehouseId: requestType === 'REMOVE' ? null : Number(requestedWarehouseId),
            })
            setMessage('Your warehouse access request was submitted for review.')
            setCurrentWarehouseId('')
            setRequestedWarehouseId('')
        } catch (requestError) {
            setError(requestError.message || 'Could not submit warehouse access request.')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <main className="account-requests-page user-warehouse-access">
            <header className="account-page-header">
                <div>
                    <div className="account-breadcrumb">TR Upload <span>/</span> Account <span>/</span> Warehouse Access</div>
                    <h1>Warehouse access</h1>
                    <p>Your current assignments and access requests.</p>
                </div>
                <div className="page-header-actions">
                    <button type="button" className="account-clear-button dashboard-back-button" onClick={() => navigate('/trupload')}>Dashboard</button>
                </div>
            </header>

            {error && <p className="form-message" role="alert">{error}</p>}
            {message && <p className="form-message" role="status">{message}</p>}

            {accountRequests.map((accountRequest) => <section className="warehouse-access-section" key={accountRequest.accountRequestId}>
                <h2>Account request status: {accountRequest.requestStatus}</h2>
                <p>Account status: {accountRequest.accountStatus} · Submitted {accountRequest.requestedAt ? new Date(accountRequest.requestedAt).toLocaleString() : 'date unavailable'}</p>
                <div className="warehouse-assignment-list">{accountRequest.warehouses.map((warehouse) => <article key={warehouse.accountRequestWarehouseId}>
                    <strong>{warehouse.facility}</strong><span>{warehouse.database} · {warehouse.division || 'No division'} · {warehouse.requestStatus}</span>
                </article>)}</div>
            </section>)}

            <section className="warehouse-access-section">
                <h2>Assigned warehouses</h2>
                {assigned.length ? <div className="warehouse-assignment-list">{assigned.map((warehouse) => (
                    <article key={warehouse.warehouseId}>
                        <strong>{warehouse.facility}</strong>
                        <span>{warehouse.division || 'No division'} · {warehouse.database} · Upload code {warehouse.uploadCode || '—'}</span>
                    </article>
                ))}</div> : <p className="empty-state">No warehouses are currently assigned.</p>}
            </section>

            <section className="warehouse-access-section">
                <h2>Request an access change</h2>
                <form className="warehouse-access-form" onSubmit={submit}>
                    <label>Request type<select value={requestType} onChange={(event) => setRequestType(event.target.value)}>
                        <option value="ADD">Add warehouse</option><option value="REMOVE">Remove warehouse</option><option value="REPLACE">Replace warehouse</option>
                    </select></label>
                    {requestType !== 'ADD' && <label>Assigned warehouse<select required value={currentWarehouseId} onChange={(event) => setCurrentWarehouseId(event.target.value)}>
                        <option value="">Select warehouse</option>{assigned.map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.facility} · {warehouse.database}</option>)}
                    </select></label>}
                    {requestType !== 'REMOVE' && <label>{requestType === 'REPLACE' ? 'Replacement warehouse' : 'Requested warehouse'}<select required value={requestedWarehouseId} onChange={(event) => setRequestedWarehouseId(event.target.value)}>
                        <option value="">Select warehouse</option>{unassigned.map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.facility} · {warehouse.database}</option>)}
                    </select></label>}
                    <button className="submit-button btn" type="submit" disabled={submitting}>{submitting ? 'Submitting...' : 'Submit request'}</button>
                </form>
            </section>

            <section className="warehouse-access-section">
                <h2>Request history</h2>
                {requests.length ? <div className="warehouse-assignment-list">{requests.map((request) => (
                    <article key={request.warehouseAccessRequestId}>
                        <strong>{request.requestType}: {request.currentFacility || '—'}{request.requestedFacility ? ` → ${request.requestedFacility}` : ''}</strong>
                        <span>{request.requestedAt ? new Date(request.requestedAt).toLocaleString() : 'Date unavailable'} · {request.reviewComments || 'No review comments'}</span>
                        <span className={`request-state state-${request.requestStatus.toLowerCase()}`}>{request.requestStatus}</span>
                    </article>
                ))}</div> : <p className="empty-state">No warehouse access requests yet.</p>}
            </section>
        </main>
    )
}

export default WarehouseAccess