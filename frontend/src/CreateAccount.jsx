import { useEffect, useState } from 'react'
import { createAdminAccount, getWarehouses } from './api.js'

function CreateAccount({ onBackToLogin, onSignupSuccess, embedded = false }) {
    const [form, setForm] = useState({ firstName: '', lastName: '', email: '', username: '', password: '', warehouseIds: [] })
    const [showPassword, setShowPassword] = useState(false)
    const [message, setMessage] = useState('')
    const [isSubmitting, setIsSubmitting] = useState(false)
    const [warehouses, setWarehouses] = useState([])
    const [warehouseError, setWarehouseError] = useState('')
    const [divisionFilter, setDivisionFilter] = useState('ALL')

    useEffect(() => {
        getWarehouses()
            .then((result) => setWarehouses(Array.isArray(result) ? result : []))
            .catch((error) => setWarehouseError(error.message || 'Could not load warehouses.'))
    }, [])
    async function handleSubmit(event) {
        event.preventDefault()
        setMessage('')
        setIsSubmitting(true)

        try {
            const result = await createAdminAccount(form)
            setMessage(result.message || 'User account created and warehouse access assigned.')
            setForm({ firstName: '', lastName: '', email: '', username: '', password: '', warehouseIds: [] })
            setDivisionFilter('ALL')
            onSignupSuccess?.(result)
        } catch (requestError) {
            setMessage(requestError.message)
        } finally {
            setIsSubmitting(false)
        }
    }

    function updateField(field, value) {
        setForm((current) => ({ ...current, [field]: value }))
    }

    function toggleWarehouse(id) {
        setForm((current) => ({
            ...current,
            warehouseIds: current.warehouseIds.includes(id)
                ? current.warehouseIds.filter((warehouseId) => warehouseId !== id)
                : [...current.warehouseIds, id],
        }))
    }

    const divisions = [...new Set(warehouses.map((warehouse) => String(warehouse.division || '').trim()).filter(Boolean))].sort()
    const filteredWarehouses = warehouses.filter((warehouse) =>
        divisionFilter === 'ALL' || String(warehouse.division || '').trim() === divisionFilter)
    const selectedWarehouses = warehouses.filter((warehouse) => form.warehouseIds.includes(warehouse.warehouseId))

    return (
        <main className={embedded ? 'admin-create-embedded' : 'login-page container-fluid p-0'}>
            <div className={embedded ? 'admin-create-layout' : 'row g-0 min-vh-100'}>
                {!embedded && <section className="login-intro col-lg-6 d-flex flex-column justify-content-between" aria-label="TRUpload introduction">
                    <div className="brand-mark">TR<span>U</span></div>
                    <div className="intro-copy">
                        <p className="eyebrow">Start here</p>
                        <h1>Make every handoff count.</h1>
                        <p>Create your TRUpload account and bring your upload workflow into one clear workspace.</p>
                    </div>
                    <p className="intro-footer">Simple workflows. Clear progress.</p>
                </section>}

                <section className={embedded ? 'admin-create-form-panel' : 'login-form-panel col-lg-6 d-flex align-items-center justify-content-center'}>
                    <div className={`login-card w-100 ${embedded ? 'admin-create-card' : ''}`}>
                        <div className="card-heading">
                            <p className="card-kicker">ADMINISTRATION</p>
                            <h2>Create a user account</h2>
                            <p>The account is activated immediately, and selected warehouses are assigned.</p>
                        </div>

                        <form onSubmit={handleSubmit}>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="firstName">First name</label>
                                <input className="form-control" id="firstName" name="firstName" type="text" placeholder="Enter your first name" autoComplete="given-name" value={form.firstName} onChange={(event) => updateField('firstName', event.target.value)} required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="lastName">Last name</label>
                                <input className="form-control" id="lastName" name="lastName" type="text" placeholder="Enter your last name" autoComplete="family-name" value={form.lastName} onChange={(event) => updateField('lastName', event.target.value)} required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="email">Email address</label>
                                <input className="form-control" id="email" name="email" type="email" placeholder="you@example.com" autoComplete="email" value={form.email} onChange={(event) => updateField('email', event.target.value)} required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="username">Username</label>
                                <input className="form-control" id="username" name="username" type="text" placeholder="Choose a username" autoComplete="username" value={form.username} onChange={(event) => updateField('username', event.target.value)} required />
                            </div>
                            <div className="mb-3">
                                <label className="form-label" htmlFor="password">Password</label>
                                <div className="password-field">
                                    <input className="form-control" id="password" name="password" type={showPassword ? 'text' : 'password'} placeholder="Create a password" autoComplete="new-password" minLength="8" value={form.password} onChange={(event) => updateField('password', event.target.value)} required />
                                    <button className="show-button" type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? 'Hide password' : 'Show password'}>
                                        {showPassword ? 'Hide' : 'Show'}
                                    </button>
                                </div>
                            </div>
                            <fieldset className="mb-3">
                                <legend className="form-label">Assign warehouses</legend>
                                {warehouseError && <p className="form-message" role="alert">{warehouseError}</p>}
                                {!warehouseError && !warehouses.length && <p className="form-message">Loading available warehouses...</p>}
                                {!warehouseError && warehouses.length > 0 && <>
                                    <div className="create-warehouse-filters">
                                        <label>Division<select value={divisionFilter} onChange={(event) => setDivisionFilter(event.target.value)}>
                                            <option value="ALL">All divisions</option>{divisions.map((division) => <option key={division} value={division}>{division}</option>)}
                                        </select></label>
                                        <label>Warehouse<select value="" onChange={(event) => {
                                            const warehouseId = Number(event.target.value)
                                            if (warehouseId && !form.warehouseIds.includes(warehouseId)) toggleWarehouse(warehouseId)
                                        }}>
                                            <option value="">Choose a warehouse to add</option>{filteredWarehouses.map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId} disabled={form.warehouseIds.includes(warehouse.warehouseId)}>{warehouse.facility} · {warehouse.database} · {warehouse.uploadCode || 'No upload code'}{form.warehouseIds.includes(warehouse.warehouseId) ? ' · Selected' : ''}</option>)}
                                        </select></label>
                                    </div>
                                    {selectedWarehouses.length > 0 && <div className="create-selected-warehouses" aria-label="Selected warehouses">
                                        {selectedWarehouses.map((warehouse) => <button type="button" key={warehouse.warehouseId} onClick={() => toggleWarehouse(warehouse.warehouseId)} title="Remove warehouse">
                                            <strong>{warehouse.facility}</strong><small>{warehouse.division || 'Unassigned division'} · {warehouse.database}</small><span aria-hidden="true">×</span>
                                        </button>)}
                                    </div>}
                                    <div className="warehouse-choice-list">
                                        {filteredWarehouses.map((warehouse) => <label className="warehouse-choice" key={warehouse.warehouseId}>
                                            <input
                                                type="checkbox"
                                                checked={form.warehouseIds.includes(warehouse.warehouseId)}
                                                onChange={() => toggleWarehouse(warehouse.warehouseId)}
                                            />
                                            <span><strong>{warehouse.facility}</strong><small>{warehouse.division || 'Unassigned division'} · {warehouse.database} · {warehouse.uploadCode || 'No upload code'}</small></span>
                                        </label>)}
                                    </div>
                                </>}
                            </fieldset>
                            <button className="submit-button btn w-100" type="submit" disabled={isSubmitting || !warehouses.length || !form.warehouseIds.length}>
                                {isSubmitting ? 'Creating user account...' : 'Create user account'}
                            </button>
                            <p className="form-message" aria-live="polite">{message}</p>
                        </form>

                        <div className="action-modal-footer">
                            <button className="modal-secondary-button" type="button" onClick={onBackToLogin}>Cancel</button>
                        </div>
                    </div>
                </section>
            </div>
        </main>
    )
}

export default CreateAccount
