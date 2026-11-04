import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCheck, faChevronDown, faChevronLeft, faChevronRight, faCircleUser, faCloudArrowUp, faCloud, faFile, faFolder, faHouse, faSearch, faSpinner, faTableList, faTriangleExclamation, faXmark, faUndo } from '@fortawesome/free-solid-svg-icons'
import { useNavigate } from 'react-router-dom'
import { getDatabaseRoutes, getFailedUploads, getMyWarehouses, getSuccessfulUploads, getWarehouses, preflightBillingGroup, uploadBillingGroups } from './api.js'

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
    const [isValidationPreviewOpen, setIsValidationPreviewOpen] = useState(false)
    const [isPreflighting, setIsPreflighting] = useState(false)
    const [validationProgress, setValidationProgress] = useState([])
    const [isUploadSuccessOpen, setIsUploadSuccessOpen] = useState(false)
    const [isValidationFailureOpen, setIsValidationFailureOpen] = useState(false)
    const [uploadResult, setUploadResult] = useState(null)
    const [isShuttleWarningOpen, setIsShuttleWarningOpen] = useState(false)
    const [isFailedView, setIsFailedView] = useState(false)
    const [failedRecords, setFailedRecords] = useState([])
    const [isLoadingFailures, setIsLoadingFailures] = useState(false)
    const [failuresError, setFailuresError] = useState('')
    const [isSuccessfulView, setIsSuccessfulView] = useState(false)
    const [successfulRecords, setSuccessfulRecords] = useState([])
    const [isLoadingSuccessful, setIsLoadingSuccessful] = useState(false)
    const [successfulError, setSuccessfulError] = useState('')
    const profileMenuRef = useRef(null)
    const normalizedRole = String(role).toUpperCase()
    const isAdmin = normalizedRole === 'ADMIN'
    const menuItems = isAdmin
        ? ['Make RTE File', 'One Shuttle File', 'Load Shuttle File', 'Audit Logs', 'Account Requests', 'About']
        : ['Audit Logs', 'About']
    const pageSize = 10

    useEffect(() => {
        let isCurrentRequest = true

        async function loadWarehouseOptions() {
            try {
                const warehouses = await (isAdmin ? getWarehouses() : getMyWarehouses())
                if (isCurrentRequest) {
                    setDatabaseInstances(Array.isArray(warehouses) ? warehouses : [])
                }
            } catch (error) {
                if (isCurrentRequest) setGroupsError(error.message || 'Could not load warehouses.')
            }
        }

        loadWarehouseOptions()
        return () => { isCurrentRequest = false }
    }, [isAdmin, loadAttempt])

    useEffect(() => {
        let isCurrentRequest = true

        async function loadSelectedDatabase() {
            if (!selectedDatabase) {
                setBillingGroups([])
                setIsLoadingGroups(false)
                return
            }

            setIsLoadingGroups(true)
            setGroupsError('')
            try {
                const instance = await getDatabaseRoutes(selectedDatabase)
                const groups = (instance?.routeGroups ?? []).map((routeGroup) => ({
                    id: `${instance.databaseName}:${routeGroup.routeGroupId}`,
                    groupId: routeGroup.routeGroupId,
                    databaseName: instance.databaseName,
                    totalOrders: routeGroup.numberOfOrders ?? 0,
                    totalStops: routeGroup.numberOfStops ?? 0,
                    totalRoutes: routeGroup.numberOfRoutes ?? 0,
                    status: routeGroup.validationErrors?.length ? 'Mismatch' : 'Un-Uploaded',
                    routes: routeGroup.routes ?? [],
                }))
                if (isCurrentRequest) setBillingGroups(groups)
            } catch (error) {
                if (isCurrentRequest) setGroupsError(error.message || 'Could not load the selected database.')
            } finally {
                if (isCurrentRequest) setIsLoadingGroups(false)
            }
        }

        loadSelectedDatabase()
        return () => { isCurrentRequest = false }
    }, [selectedDatabase, loadAttempt])

    const databaseOptions = [...new Set(databaseInstances.map((warehouse) => warehouse.database).filter(Boolean))].sort()
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

    function updateSearch(value) {
        if (!selectedDatabase) return
        setSearch(value)
        setPage(1)
    }
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
        await startValidationPreview()
    }

    async function startValidationPreview() {
        setIsShuttleWarningOpen(false)
        setIsValidationPreviewOpen(true)
        setIsPreflighting(true)

        const groupsToCheck = selectedGroups.map((group) => ({
            id: group.id,
            databaseName: group.databaseName,
            routeGroupId: group.groupId,
            status: 'WAITING',
            response: null,
            error: '',
        }))
        setValidationProgress(groupsToCheck)

        for (let index = 0; index < groupsToCheck.length; index += 1) {
            const group = groupsToCheck[index]
            setValidationProgress((current) => current.map((item, itemIndex) =>
                itemIndex === index ? { ...item, status: 'RUNNING' } : item))
            try {
                const results = await preflightBillingGroup({
                    databaseName: group.databaseName,
                    routeGroupId: group.routeGroupId,
                })
                const response = Array.isArray(results) ? results[0] : null
                if (!response) throw new Error('No validation result was returned for this billing group.')
                setValidationProgress((current) => current.map((item, itemIndex) =>
                    itemIndex === index
                        ? { ...item, status: response.valid ? 'READY' : 'FAILED', response }
                        : item))
            } catch (preflightError) {
                setValidationProgress((current) => current.map((item, itemIndex) =>
                    itemIndex === index
                        ? { ...item, status: 'ERROR', error: preflightError.message || 'Validation request failed.' }
                        : item))
            }
        }

        setIsPreflighting(false)
    }

    async function confirmPreflightUpload() {
        const hasValidationFailures = validationProgress.some((item) => item.status === 'FAILED')
        setIsValidationPreviewOpen(false)

        if (hasValidationFailures) {
            await runUpload()
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
            const successfulIds = new Set(selectedGroups
                .filter((group) => {
                    const groupResults = (result.results ?? []).filter((record) =>
                        record.databaseName === group.databaseName
                        && record.routeGroupId.replace(/wave$/i, '') === group.groupId.replace(/wave$/i, ''))
                    return groupResults.length > 0
                        && groupResults.every((record) => record.status === 'UPLOADED')
                })
                .map((group) => group.id))
            setBillingGroups((current) => current.filter((group) => !successfulIds.has(group.id)))
            setSelected((current) => current.filter((id) => !successfulIds.has(id)))
            setIsUploadProgressOpen(false)
            if ((result.failedCount ?? 0) > 0 || (result.results ?? []).some((record) => record.status === 'FAILED')) {
                setIsValidationFailureOpen(true)
            } else {
                setIsUploadSuccessOpen(true)
            }
        } catch (error) {
            setIsUploadProgressOpen(false)
            showNotice(error.message || 'Upload validation failed.')
        }
    }

    async function openFailedRecords() {
        setIsFailedView(true)
        setIsSuccessfulView(false)
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

    async function openSuccessfulRecords() {
        setIsSuccessfulView(true)
        setIsFailedView(false)
        setIsLoadingSuccessful(true)
        setSuccessfulError('')
        try {
            const records = await getSuccessfulUploads()
            setSuccessfulRecords(Array.isArray(records) ? records : [])
        } catch (error) {
            setSuccessfulError(error.message || 'Could not load uploaded records.')
        } finally {
            setIsLoadingSuccessful(false)
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
                        <label className="source-select"><span>Source Database</span><select value={selectedDatabase} onChange={(event) => { setSelectedDatabase(event.target.value); setPage(1); setSelected([]); setSearch('') }} aria-label="Select source database"><option value="">Select Database</option>{databaseOptions.map((database) => <option value={database} key={database}>{database}</option>)}</select></label>
                        <label className={`billing-search${selectedDatabase ? '' : ' is-disabled'}`}><FontAwesomeIcon icon={faSearch} /><input value={search} onChange={(event) => updateSearch(event.target.value)} disabled={!selectedDatabase} placeholder={selectedDatabase ? 'Search billing group name or number...' : 'Select a database to search'} aria-label="Search billing groups" />{search && <button type="button" onClick={() => updateSearch('')} disabled={!selectedDatabase} aria-label="Clear search"><FontAwesomeIcon icon={faXmark} /></button>}</label>
                        <button className="upload-action" type="button" onClick={uploadSelected}><FontAwesomeIcon icon={faCloudArrowUp} /> Upload Selected Billings</button>
                        <button className="reset-action" type="button" onClick={resetRouting}><FontAwesomeIcon icon={faUndo} /> Reset Routing</button>
                    </div>
                    <section className="billing-panel">
                        <div className="billing-heading d-flex align-items-center justify-content-between">
                            <div className="billing-heading-title"><h2><FontAwesomeIcon icon={isFailedView ? faTriangleExclamation : faTableList} /> {isFailedView ? 'Upload Failed Records' : isSuccessfulView ? 'Uploaded Records' : 'Active Billings'}</h2></div>
                            <div className="billing-heading-actions">
                                <button className={!isFailedView && !isSuccessfulView ? 'selected' : ''} type="button" onClick={() => { setIsFailedView(false); setIsSuccessfulView(false) }}>Active Billings</button>
                                <button className={isSuccessfulView ? 'selected' : ''} type="button" onClick={openSuccessfulRecords}>Uploaded Records</button>
                                <button className={isFailedView ? 'selected' : ''} type="button" onClick={openFailedRecords}>Upload Failed Records</button>
                                {!isFailedView && !isSuccessfulView && <span className="billing-count">{selected.length} selected</span>}
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
                        </div> : isSuccessfulView ? <div className="failed-records-list">
                            {isLoadingSuccessful && <p className="empty-state"><FontAwesomeIcon icon={faSpinner} spin /> Loading uploaded records...</p>}
                            {!isLoadingSuccessful && successfulError && <div className="empty-state api-error-state"><p>{successfulError}</p><button type="button" onClick={openSuccessfulRecords}>Retry</button></div>}
                            {!isLoadingSuccessful && !successfulError && !successfulRecords.length && <p className="empty-state">No successful upload records.</p>}
                            {!isLoadingSuccessful && !successfulError && successfulRecords.length > 0 && <div className="failed-records-table-wrap"><table className="failed-records-table">
                                <thead><tr><th>Database</th><th>Group ID</th><th>Route ID</th><th>Dispatch date</th><th>Uploaded at</th><th>Uploaded by</th><th>Warnings</th></tr></thead>
                                <tbody>{successfulRecords.map((record) => <tr key={record.id}>
                                    <td>{record.databaseName}</td><td>{record.routeGroupId}</td><td>{record.routeId || '—'}</td>
                                    <td>{record.dispatchDate || '—'}</td>
                                    <td>{record.uploadedAt ? new Date(record.uploadedAt).toLocaleString() : '—'}</td>
                                    <td>{record.uploadedBy || '—'}</td><td>{record.warnings || '—'}</td>
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
                            {!isLoadingGroups && !groupsError && !visibleGroups.length && <p className="empty-state">{!selectedDatabase ? 'Select a source database to view billing groups.' : billingGroups.length ? 'No groups match your search.' : 'No database instances were returned.'}</p>}
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
                        <button className="modal-primary-button" type="button" onClick={startValidationPreview}>Continue to validation</button>
                    </div>
                </section>
            </div>}
            {isValidationPreviewOpen && <div className="upload-progress-backdrop validation-preview-backdrop" role="dialog" aria-modal="true" aria-labelledby="validation-preview-title">
                <section className="upload-progress-modal validation-preview-modal">
                    <div className="validation-preview-heading">
                        <div>
                            <p>{isPreflighting ? 'READ-ONLY CHECK' : 'READY FOR REVIEW'}</p>
                            <h2 id="validation-preview-title">{isPreflighting ? 'Validating selected billings' : 'Review before upload'}</h2>
                        </div>
                        {isPreflighting && <FontAwesomeIcon className="validation-spinner" icon={faSpinner} spin />}
                    </div>
                    <p className="validation-preview-caption">
                        {isPreflighting
                            ? 'Checking route weekday, dispatch date, and duplicate status for each group.'
                            : 'These checks did not write upload history. Upload will repeat them before processing.'}
                    </p>
                    <div className="validation-group-list">
                        {validationProgress.map((item) => <article className={`validation-group-row validation-${item.status.toLowerCase()}`} key={item.id}>
                            <div className="validation-group-summary">
                                <span className="validation-state-icon">
                                    {item.status === 'RUNNING' ? <FontAwesomeIcon icon={faSpinner} spin /> : item.status === 'READY' ? <FontAwesomeIcon icon={faCheck} /> : item.status === 'FAILED' || item.status === 'ERROR' ? <FontAwesomeIcon icon={faTriangleExclamation} /> : null}
                                </span>
                                <span><strong>{item.databaseName} / {item.routeGroupId}</strong><small>{item.status === 'RUNNING' ? 'Checking routes and duplicates...' : item.status === 'WAITING' ? 'Waiting' : item.status === 'READY' ? `${item.response?.routeCount ?? 0} routes ready` : item.status === 'ERROR' ? item.error : `${item.response?.routeCount ?? 0} routes need attention`}</small></span>
                                <b>{item.status}</b>
                            </div>
                            {item.response?.groupReasons?.map((reason) => <p className="validation-route-reason" key={reason}>{reason}</p>)}
                            {item.response?.routeValidations?.map((route) => <div className="validation-route-detail" key={`${route.routeId}-${route.dispatchDate}`}>
                                <strong>Route {route.routeId || 'unknown'} · {route.dispatchDate || 'date missing'}</strong>
                                <ul>{route.checks.map((check) => <li className={check.passed ? 'passed' : 'failed'} key={check.name}>
                                    <FontAwesomeIcon icon={check.passed ? faCheck : faTriangleExclamation} />
                                    <span><b>{check.name}:</b> {check.message}</span>
                                </li>)}</ul>
                            </div>)}
                        </article>)}
                    </div>
                    <div className="action-modal-footer validation-preview-footer">
                        <button className="modal-secondary-button" type="button" disabled={isPreflighting} onClick={() => setIsValidationPreviewOpen(false)}>Cancel</button>
                        <button className="modal-primary-button" type="button" disabled={isPreflighting || validationProgress.some((item) => item.status === 'ERROR')} onClick={confirmPreflightUpload}>
                            {isPreflighting ? 'Validating...' : validationProgress.some((item) => item.status === 'FAILED') ? 'Continue with validation result' : 'Confirm upload'}
                        </button>
                    </div>
                </section>
            </div>}
            {isUploadProgressOpen && <div className="upload-progress-backdrop" role="dialog" aria-modal="true" aria-labelledby="upload-progress-title">
                <section className="upload-progress-modal">
                    <h2 id="upload-progress-title">Uploading selected billings</h2>
                    <div className="upload-progress-message"><FontAwesomeIcon icon={faSpinner} spin /><span>Applying final checks and recording upload results...</span></div>
                </section>
            </div>}
            {isValidationFailureOpen && <div className="upload-success-backdrop" role="dialog" aria-modal="true" aria-labelledby="validation-failure-title">
                <section className="upload-success-modal">
                    <div className="upload-success-icon" style={{ background: '#b74747' }}><FontAwesomeIcon icon={faTriangleExclamation} /></div>
                    <h2 id="validation-failure-title">Upload validation failed</h2>
                    <p>{uploadResult?.failedCount ?? 0} route record(s) failed validation and were moved to Failed Upload Records. No route data was uploaded for the failed billing groups.</p>
                    <div className="action-modal-footer">
                        <button className="modal-secondary-button" type="button" onClick={() => setIsValidationFailureOpen(false)}>Close</button>
                        <button className="modal-primary-button" type="button" onClick={() => { setIsValidationFailureOpen(false); openFailedRecords() }}>View failed records</button>
                    </div>
                </section>
            </div>}
            {isUploadSuccessOpen && <div className="upload-success-backdrop" role="dialog" aria-modal="true" aria-labelledby="upload-success-title">
                <section className="upload-success-modal">
                    <div className="upload-success-icon"><FontAwesomeIcon icon={faCheck} /></div>
                    <h2 id="upload-success-title">Upload complete</h2>
                    <p>{uploadResult?.uploadedCount ?? 0} route record(s) uploaded; {uploadResult?.failedCount ?? 0} failed. Successful groups were removed from the list; failed groups remain available to retry.</p>
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
