import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCheck, faChevronDown, faChevronLeft, faChevronRight, faCircleUser, faCloudArrowUp, faCloud, faFile, faFolder, faHouse, faSearch, faSpinner, faTableList, faTriangleExclamation, faXmark, faUndo } from '@fortawesome/free-solid-svg-icons'
import { useNavigate } from 'react-router-dom'
import { getDatabaseInstances, getFailedUploads, uploadBillingGroups } from './api.js'
const uploadStages = [{ label: 'Validation', icon: faSpinner }]

function formatDispatchDates(routes) {
    return [...new Set(routes
        .map((route) => String(route.dispatchDateTime ?? '').match(/^\d{4}-(\d{2})-(\d{2})/) )
        .filter(Boolean)
        .map(([, month, day]) => `${month}/${day}`))]
}


function TRUpload({ username, role, onLogout }) {
    const [search, setSearch] = useState('')
    const [page, setPage] = useState(1)
    const [databaseInstances, setDatabaseInstances] = useState([])
    const [selectedDatabase, setSelectedDatabase] = useState('')
    const [billingGroups, setBillingGroups] = useState([])
    const [isLoadingGroups, setIsLoadingGroups] = useState(true)
    const [groupsError, setGroupsError] = useState('')
    const [loadAttempt, setLoadAttempt] = useState(0)
    const [selected, setSelected] = useState([])
    const [notice, setNotice] = useState('')
    const [isProfileOpen, setIsProfileOpen] = useState(false)
    const [isRteModalOpen, setIsRteModalOpen] = useState(false)
    const [isResetBillings, setIsResetBillings] = useState(false);
    const [isResetRoutingSuccessOpen, setIsResetRoutingSuccessOpen] = useState(false)
    const [rteFileName, setRteFileName] = useState('')
    const [isRteConfirmationOpen, setIsRteConfirmationOpen] = useState(false)
    const [isUploadProgressOpen, setIsUploadProgressOpen] = useState(false)
    const [isUploadSuccessOpen, setIsUploadSuccessOpen] = useState(false)
    const [uploadResult, setUploadResult] = useState(null)
    const [isShuttleWarningOpen, setIsShuttleWarningOpen] = useState(false)
    const [isFailedView, setIsFailedView] = useState(false)
    const [failedRecords, setFailedRecords] = useState([])
    const [isLoadingFailures, setIsLoadingFailures] = useState(false)
    const [failuresError, setFailuresError] = useState('')
    const profileMenuRef = useRef(null)
    const normalizedRole = String(role).toUpperCase()
    const isAdmin = normalizedRole === 'ADMIN'
    const menuItems = isAdmin
        ? ['Make RTE File', 'One Shuttle File', 'Load Shuttle File', 'Audit Logs', 'Account Requests', 'About']
        : ['Audit Logs', 'About']
    const pageSize = 10

    useEffect(() => {
        let isCurrentRequest = true

        async function loadDatabaseInstances() {
            setIsLoadingGroups(true)
            setGroupsError('')

            try {
                const databases = await getDatabaseInstances()
                const instances = Array.isArray(databases) ? databases : []
                console.log('Database instances:', instances);
                const groups = instances.flatMap(({ databaseName, routeGroups = [] }) =>
                    routeGroups.map((routeGroup) => ({
                        id: `${databaseName}:${routeGroup.routeGroupId}`,
                        groupId: routeGroup.routeGroupId,
                        databaseName,
                        totalOrders: routeGroup.numberOfOrders ?? 0,
                        totalStops: routeGroup.numberOfStops ?? 0,
                        totalRoutes: routeGroup.numberOfRoutes ?? 0,
                        status: routeGroup.validationErrors?.length
                            ? 'Mismatch'
                            : 'Un-Uploaded',
                        routes: routeGroup.routes ?? [],
                    })),
                );
                
                if (isCurrentRequest) {
                    setDatabaseInstances(instances)
                    setBillingGroups(groups)
                }
            } catch (error) {
                if (isCurrentRequest) {
                    setGroupsError(error.message || 'Could not load database instances.')
                }
            } finally {
                if (isCurrentRequest) {
                    setIsLoadingGroups(false)
                }
            }
        }

        loadDatabaseInstances()
        return () => { isCurrentRequest = false }
    }, [loadAttempt])

    const databaseOptions = [...new Set(databaseInstances.map((instance) => instance.databaseName).filter(Boolean))].sort()
    const normalizedSearch = search.trim().toLowerCase()
    const filteredGroups = billingGroups.filter((group) =>
        (!selectedDatabase || group.databaseName === selectedDatabase)
        && (group.groupId.toLowerCase().includes(normalizedSearch)
            || group.databaseName.toLowerCase().includes(normalizedSearch)))
    
    const pageCount = Math.max(1, Math.ceil(filteredGroups.length / pageSize))
    const currentPage = Math.min(page, pageCount)
    const visibleGroups = filteredGroups.slice((currentPage - 1) * pageSize, currentPage * pageSize)
    const allVisibleSelected = visibleGroups.length > 0 && visibleGroups.every((group) => selected.includes(group.id))
    const selectedGroups = billingGroups.filter((group) => selected.includes(group.id))
    const shuttleGroups = selectedGroups.filter((group) => String(group.groupId).toUpperCase().endsWith('S'))
    const navigate = useNavigate()
    useEffect(() => {
        function closeProfileMenu(event) {
            if (!profileMenuRef.current?.contains(event.target)) {
                setIsProfileOpen(false)
            }
        }

        function closeOnEscape(event) {
            if (event.key === 'Escape') {
                setIsProfileOpen(false)
            }
        }

        document.addEventListener('mousedown', closeProfileMenu)
        document.addEventListener('keydown', closeOnEscape)
        return () => {
            document.removeEventListener('mousedown', closeProfileMenu)
            document.removeEventListener('keydown', closeOnEscape)
        }
    }, [])

    function updateSearch(value) { setSearch(value); setPage(1) }
    function toggleGroup(id) { setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]) }
    function toggleVisibleGroups() {
        setSelected((current) => allVisibleSelected
            ? current.filter((id) => !visibleGroups.some((group) => group.id === id))
            : [...new Set([...current, ...visibleGroups.map((group) => group.id)])])
    }
    function showNotice(message) { setNotice(message); window.setTimeout(() => setNotice(''), 3000) }
    async function uploadSelected() {
        if (!selectedGroups.length) {
            showNotice('Select at least one billing group to upload.')
            return
        }
        if (shuttleGroups.length) {
            setIsShuttleWarningOpen(true)
            return
        }
        await runUpload()
    }

    async function runUpload() {
        setIsShuttleWarningOpen(false)
        setIsUploadProgressOpen(true)
        setUploadResult(null)
        try {
            const result = await uploadBillingGroups(selectedGroups.map(({ databaseName, groupId }) => ({ databaseName, routeGroupId: groupId })))
            setUploadResult(result)
            const processedIds = new Set(selectedGroups.map((group) => group.id))
            setBillingGroups((current) => current.filter((group) => !processedIds.has(group.id)))
            setSelected([])
            setIsUploadProgressOpen(false)
            setIsUploadSuccessOpen(true)
        } catch (error) {
            setIsUploadProgressOpen(false)
            showNotice(error.message || 'Upload validation failed.')
        }
    }

    async function openFailedRecords() {
        setIsFailedView(true)
        setIsLoadingFailures(true)
        setFailuresError('')
        try {
            const records = await getFailedUploads()
            setFailedRecords(Array.isArray(records) ? records : [])
        } catch (error) {
            setFailuresError(error.message || 'Could not load failed upload records.')
        } finally {
            setIsLoadingFailures(false)
        }
    }

    function closeUploadResult() {
        setIsUploadSuccessOpen(false)
    }

    function confirmResetBillings() {
        setSearch('')
        setPage(1)
        setSelected([])
        setIsResetBillings(false)
        setIsResetRoutingSuccessOpen(true)
        window.setTimeout(() => setIsResetRoutingSuccessOpen(false), 1800)
    }

    function resetRouting() {
        setIsResetBillings(true)
    }

    function changePage(nextPage) {
        setPage(Math.min(Math.max(nextPage, 1), pageCount))
        setSelected([])
    }

    function handleMenuItem(item) {
        setIsProfileOpen(false)
        if (item === 'Make RTE File') {
            setRteFileName('')
            setIsRteModalOpen(true)
            return
        }
        if (item === 'Audit Logs') {
            navigate('/audit-logs')
            return
        }
        if (item === 'Account Requests') {
            navigate('/admin/account-requests')
            return
        }
        showNotice(`${item} selected.`)
    }

    function submitRteFile(event) {
        event.preventDefault()
        const fileName = rteFileName.trim()
        setIsRteModalOpen(false)
        setIsRteConfirmationOpen(true)
        setRteFileName('')
        window.setTimeout(() => setIsRteConfirmationOpen(false), 1600)
        showNotice(`RTE file ${fileName} submitted.`)
    }

    return (
        <main className={`upload-page ${isAdmin ? 'admin-upload-page' : 'user-upload-page'}`}>
            <header className="upload-header d-flex align-items-center">
                <div className="upload-brand">TR<span>UPLOAD</span></div>
                <div className="upload-nav"><FontAwesomeIcon className="nav-home" icon={faHouse} /><span>TR Upload</span></div>
                <div className="account-controls d-flex align-items-center ms-auto" ref={profileMenuRef}>
                    <span className="environment-pill"><i /> DEV</span>
                    <button
                        className="profile-toggle"
                        type="button"
                        onClick={() => setIsProfileOpen((open) => !open)}
                        aria-expanded={isProfileOpen}
                        aria-haspopup="menu"
                        aria-label={`${username || 'User'} profile menu`}
                        title={username || 'User'}
                    >
                        <FontAwesomeIcon className="user-avatar" icon={faCircleUser} />
                        <FontAwesomeIcon className="profile-chevron" icon={faChevronDown} />
                    </button>
                    {isProfileOpen && <div className="profile-dropdown" role="menu">
                        <div className="profile-role" role="presentation">{isAdmin ? 'Admin' : 'User'}</div>
                        {menuItems.map((item) => <button className="profile-action" type="button" role="menuitem" key={item} onClick={() => handleMenuItem(item)}>{item}</button>)}
                        <button className="profile-action profile-logout" type="button" role="menuitem" onClick={onLogout}>Logout</button>
                    </div>}
                </div>
            </header>
            <div className="upload-layout container-fluid row g-4">
                <section className="upload-content">
                    <div className="workspace-heading d-flex align-items-center">
                        <div className="cloud-icon"><FontAwesomeIcon icon={faCloud} /></div>
                        <div><h1 style={{ color: isAdmin ? '#12263e' : '#fff' }}>TR Upload</h1><p style={{ color: isAdmin ? '#12263e' : '#fff' }}>Select a source database and choose the billing group(s) to upload.</p></div>
                    </div>
                    <div className="workspace-toolbar d-flex flex-wrap align-items-end gap-3">
                        <label className="source-select"><span>Source Database</span><select value={selectedDatabase} onChange={(event) => { setSelectedDatabase(event.target.value); setPage(1) }} aria-label="Filter by source database"><option value="">All Databases</option>{databaseOptions.map((database) => <option value={database} key={database}>{database}</option>)}</select></label>
                        <label className="billing-search"><FontAwesomeIcon icon={faSearch} /><input value={search} onChange={(event) => updateSearch(event.target.value)} placeholder="Search billing group name or number..." aria-label="Search billing groups" />{search && <button type="button" onClick={() => updateSearch('')} aria-label="Clear search"><FontAwesomeIcon icon={faXmark} /></button>}</label>
                        <button className="upload-action" type="button" onClick={uploadSelected}><FontAwesomeIcon icon={faCloudArrowUp} /> Upload Selected Billings</button>
                        <button className="reset-action" type="button" onClick={resetRouting}><FontAwesomeIcon icon={faUndo} /> Reset Routing</button>
                    </div>
                    <section className="billing-panel">
                        <div className="billing-heading d-flex align-items-center justify-content-between">
                            <div className="billing-heading-title"><h2><FontAwesomeIcon icon={isFailedView ? faTriangleExclamation : faTableList} /> {isFailedView ? 'Upload Failed Records' : 'Active Billings'}</h2></div>
                            <div className="billing-heading-actions">
                                <button className={!isFailedView ? 'selected' : ''} type="button" onClick={() => setIsFailedView(false)}>Active Billings</button>
                                <button className={isFailedView ? 'selected' : ''} type="button" onClick={openFailedRecords}>Upload Failed Records</button>
                                {!isFailedView && <span className="billing-count">{selected.length} selected</span>}
                            </div>
                        </div>
                        {isFailedView ? <div className="failed-records-list">
                            {isLoadingFailures && <p className="empty-state"><FontAwesomeIcon icon={faSpinner} spin /> Loading failed records...</p>}
                            {!isLoadingFailures && failuresError && <div className="empty-state api-error-state"><p>{failuresError}</p><button type="button" onClick={openFailedRecords}>Retry</button></div>}
                            {!isLoadingFailures && !failuresError && !failedRecords.length && <p className="empty-state">No failed upload records.</p>}
                            {!isLoadingFailures && !failuresError && failedRecords.length > 0 && <div className="failed-records-table-wrap"><table className="failed-records-table">
                                <thead><tr><th>Database</th><th>Group ID</th><th>Route ID</th><th>Dispatch date - MM/DD</th><th>Failure reason</th><th>Attempted</th></tr></thead>
                                <tbody>{failedRecords.map((record) => <tr key={record.id}>
                                    <td>{record.databaseName}</td><td>{record.routeGroupId}</td><td>{record.routeId || '—'}</td>
                                    <td>{record.dispatchDate || '—'}</td><td>{record.failureReason}</td>
                                    <td>{record.attemptedAt ? new Date(record.attemptedAt).toLocaleString() : '—'}</td>
                                </tr>)}</tbody>
                            </table></div>}
                        </div> : <>
                        <div className="billing-list">
                            <div className="billing-list-header row align-items-center g-0 px-3 py-2">
                                <div className="col-auto billing-select-column"><input type="checkbox" checked={allVisibleSelected} onChange={toggleVisibleGroups} aria-label="Select visible billing groups" /></div>
                                <div className="col billing-name-column">Group ID</div>
                                <div className="col-2 col-lg-2 billing-value-column">Total Routes</div>
                                <div className="col-1 col-lg-1 billing-value-column">Total Orders</div>
                                <div className="col-1 col-lg-1 billing-value-column">Total Stops</div>
                                <div className="col-1 col-lg-1 billing-value-column">Dispatch Date - MM/DD</div>
                                <div className="col-2 col-lg-2 billing-value-column">Database</div>
                                <div className="col-2 col-lg-2 billing-value-column">Status</div>
                            </div>
                            {visibleGroups.map((group) => <label
                                    className="billing-list-row row align-items-center g-0 px-3 py-2"
                                    key={group.id}
                                >
                                <div className="col-auto billing-select-column"><input type="checkbox" checked={selected.includes(group.id)} onChange={() => toggleGroup(group.id)} aria-label={`Select ${group.groupId}`} /></div>
                                <div className="col billing-name-column d-flex align-items-center gap-3"><span className="folder-icon"><FontAwesomeIcon icon={faFolder} /></span>
                                    <strong>{group.groupId}</strong>
                                </div>
                                <div className="col-2 col-lg-2 billing-value-column">{group.totalRoutes}</div>
                                <div className="col-1 col-lg-1 billing-value-column">{group.totalOrders}</div>
                                <div className="col-1 col-lg-1 billing-value-column">{group.totalStops}</div>
                                <div className="col-1 col-lg-1 billing-value-column" title={formatDispatchDates(group.routes).join(', ') || 'No dispatch date'}>{formatDispatchDates(group.routes).join(', ') || '—'}</div>
                                <div className="col-2 col-lg-2 billing-value-column"><span className="database-name-pill">{group.databaseName}</span></div>
                                <div className="col-2 col-lg-2 billing-value-column"><span className={`status-pill ${group.status === 'Validation Failed' ? 'status-pill-error' : ''}`}>{group.status}</span></div>
                            </label>)}
                            {isLoadingGroups && <p className="empty-state"><FontAwesomeIcon icon={faSpinner} spin /> Loading database instances...</p>}
                            {!isLoadingGroups && groupsError && <div className="empty-state api-error-state"><p>{groupsError}</p><button type="button" onClick={() => setLoadAttempt((attempt) => attempt + 1)}>Retry</button></div>}
                            {!isLoadingGroups && !groupsError && !visibleGroups.length && <p className="empty-state">{billingGroups.length ? 'No groups match your search.' : 'No database instances were returned.'}</p>}
                        </div>
                        <div className="billing-footer d-flex align-items-center justify-content-between"><span>Showing {visibleGroups.length ? (currentPage - 1) * pageSize + 1 : 0}-{Math.min(currentPage * pageSize, filteredGroups.length)} of {filteredGroups.length} billing groups</span><div className="pagination d-flex align-items-center gap-2" aria-label="Billing group pages"><button type="button" onClick={() => changePage(currentPage - 1)} disabled={currentPage === 1} aria-label="Previous page"><FontAwesomeIcon icon={faChevronLeft} /></button>{Array.from({ length: pageCount }, (_, index) => index + 1).slice(0, 4).map((number) => <button className={number === currentPage ? 'active' : ''} type="button" key={number} onClick={() => changePage(number)}>{number}</button>)}<button type="button" onClick={() => changePage(currentPage + 1)} disabled={currentPage === pageCount} aria-label="Next page"><FontAwesomeIcon icon={faChevronRight} /></button></div></div>
                        </>}
                    </section>
                </section>
                {/* {isAdmin && <aside className="admin-panel col-lg-4 col-xl-3"><button className="settings-button" type="button" aria-label="Settings"><FontAwesomeIcon icon={faGear} /></button><div className="admin-menu"><button className="admin-menu-title" type="button"><FontAwesomeIcon icon={faServer} /> Admin Functions <b><FontAwesomeIcon icon={faChevronDown} /></b></button>{['Make RTE File', 'One Shuttle File', 'Load Shuttle File', 'Commands', 'View Logs', 'Admin'].map((action) => <button className="admin-action" type="button" key={action} onClick={() => showNotice(`${action} selected.`)}><FontAwesomeIcon icon={faServer} />{action}<b><FontAwesomeIcon icon={faChevronRight} /></b></button>)}</div></aside>} */}
            </div>
            {notice && <div className="toast-message" role="status">{notice}</div>}
            {isShuttleWarningOpen && <div className="modal-backdrop-custom" role="presentation">
                <section className="action-modal shuttle-warning-modal" role="dialog" aria-modal="true" aria-labelledby="shuttle-warning-title">
                    <div className="action-modal-header"><div className="action-modal-icon shuttle-warning-icon"><FontAwesomeIcon icon={faTriangleExclamation} /></div></div>
                    <h2 id="shuttle-warning-title">Shuttle delivery warning</h2>
                    <p>These billing groups are marked for warehouse delivery rather than store delivery. They can still be uploaded.</p>
                    <ul className="shuttle-warning-list">{shuttleGroups.map((group) => <li key={group.id}><strong>{group.databaseName} / {group.groupId}</strong><span>{group.routes.length ? group.routes.map((route) => `${route.routeId} · ${String(route.dispatchDateTime || 'Date unavailable').slice(0, 10)}`).join(' | ') : `${group.totalRoutes} routes · ${group.totalOrders} orders · ${group.totalStops} stops`}</span></li>)}</ul>
                    <div className="action-modal-footer">
                        <button className="modal-secondary-button" type="button" onClick={() => setIsShuttleWarningOpen(false)}>Cancel</button>
                        <button className="modal-primary-button" type="button" onClick={runUpload}>OK</button>
                    </div>
                </section>
            </div>}
            {isUploadProgressOpen && <div className="upload-progress-backdrop" role="dialog" aria-modal="true" aria-labelledby="upload-progress-title">
                <section className="upload-progress-modal">
                    <h2 id="upload-progress-title">Uploading selected billings</h2>
                    <div className="upload-stage-track">
                        {uploadStages.map((stage) => <div className="upload-stage current" key={stage.label}>
                            <div className="upload-stage-node"><FontAwesomeIcon icon={stage.icon} spin /></div>
                            <strong>{stage.label}</strong><span>In Progress</span>
                        </div>)}
                    </div>
                    <div className="upload-progress-message"><FontAwesomeIcon icon={faSpinner} spin /><span>Checking route weekdays, date limits, and duplicate upload records...</span></div>
                </section>
            </div>}
            {isUploadSuccessOpen && <div className="upload-success-backdrop" role="dialog" aria-modal="true" aria-labelledby="upload-success-title">
                <section className="upload-success-modal">
                    <div className="upload-success-icon"><FontAwesomeIcon icon={faCheck} /></div>
                    <h2 id="upload-success-title">Validation complete</h2>
                    <p>{uploadResult?.uploadedCount ?? 0} route record(s) uploaded; {uploadResult?.failedCount ?? 0} failed. Failed records are available in Upload Failed Records.</p>
                    <div className="action-modal-footer">
                        {uploadResult?.failedCount > 0 && <button className="modal-secondary-button" type="button" onClick={() => { closeUploadResult(); openFailedRecords() }}>View failed records</button>}
                        <button className="modal-primary-button" type="button" onClick={closeUploadResult}>OK</button>
                    </div>
                </section>
            </div>}
            {isRteModalOpen && <div className="modal-backdrop-custom" role="presentation">
                <section className="action-modal" role="dialog" aria-modal="true" aria-labelledby="rte-modal-title">
                    <div className="action-modal-header">
                        <div className="action-modal-icon"><FontAwesomeIcon icon={faFile} /></div>
                        <button className="modal-close-button" type="button" onClick={() => setIsRteModalOpen(false)} aria-label="Close Make RTE File dialog"><FontAwesomeIcon icon={faXmark} /></button>
                    </div>
                    <h2 id="rte-modal-title">Make RTE File</h2>
                    <p>Enter a billing group name for the file.</p>
                    <form onSubmit={submitRteFile}>
                        <label className="modal-field-label" htmlFor="rte-file-name">Billing group name</label>
                        <input className="form-control" id="rte-file-name" type="text" value={rteFileName} onChange={(event) => setRteFileName(event.target.value)} placeholder="Example: billing-group.rte" autoFocus required />
                        <div className="action-modal-footer">
                            <button className="modal-secondary-button" type="button" onClick={() => setIsRteModalOpen(false)}>Cancel</button>
                            <button className="modal-primary-button" type="submit">Submit</button>
                        </div>
                    </form>
                </section>
            </div>}
            {isResetBillings && <div className="modal-backdrop-custom" role="presentation">
                <section className="action-modal" role="dialog" aria-modal="true" aria-labelledby="rte-modal-title">
                    <div className="action-modal-header">
                        <div className="action-modal-icon"><FontAwesomeIcon icon={faFile} /></div>
                        <button className="modal-close-button" type="button" onClick={() => setIsResetBillings(false)} aria-label="Close Reset Billings dialog"><FontAwesomeIcon icon={faXmark} /></button>
                    </div>
                    <h2 id="reset-routing-title">Reset Routing</h2>
                    <p>Are you sure you want to reset routing for the active billings?</p>
                    <div className="action-modal-footer">
                        <button className="modal-secondary-button" type="button" onClick={() => setIsResetBillings(false)}>Cancel</button>
                        <button className="modal-primary-button" type="button" onClick={confirmResetBillings}>Confirm Reset</button>
                    </div>
                </section>
            </div>}
            {isResetRoutingSuccessOpen && <div className="modal-backdrop-custom confirmation-backdrop" role="presentation">
                <section className="confirmation-modal" role="status" aria-live="polite">
                    <div className="confirmation-icon">✓</div>
                    <h2>Resetting Routing Successfully</h2>
                </section>
            </div>}
            {isRteConfirmationOpen && <div className="modal-backdrop-custom confirmation-backdrop" role="presentation">
                <section className="confirmation-modal" role="status" aria-live="polite">
                    <div className="confirmation-icon">✓</div>
                    <h2>Action completed</h2>
                    <p>The RTE file request was submitted successfully.</p>
                </section>
            </div>}
        </main>
    )
}

export default TRUpload
