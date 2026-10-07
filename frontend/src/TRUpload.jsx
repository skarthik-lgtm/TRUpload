import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCheck, faChevronDown, faChevronLeft, faChevronRight, faCircleUser, faCloudArrowUp, faCloud, faFile, faFlagCheckered, faFolder, faHouse, faNetworkWired, faSearch, faServer, faSpinner, faTableList, faXmark, faUndo } from '@fortawesome/free-solid-svg-icons'
import { useNavigate } from 'react-router-dom'
import { getDatabaseInstances } from './api.js'
const uploadStages = [
    { label: 'Validation', icon: faCheck },
    { label: 'File Generation', icon: faCheck },
    { label: 'Processing Files', icon: faFile },
    { label: 'Mainframe Transfer', icon: faServer },
    { label: 'Downstream Systems', icon: faNetworkWired },
    { label: 'Complete', icon: faFlagCheckered },
]

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
    const [uploadStage, setUploadStage] = useState(2)
    const [isUploadSuccessOpen, setIsUploadSuccessOpen] = useState(false)
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
                const groups = instances.flatMap(({ database, groupIds = [] }) =>
                    groupIds.map((groupId) => ({
                        id: `${database}:${groupId}`,
                        name: groupId,
                        database,
                        status: 'Un-Uploaded',
                    })),
                )

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

    const databaseOptions = [...new Set(databaseInstances.map((instance) => instance.database).filter(Boolean))].sort()
    const normalizedSearch = search.trim().toLowerCase()
    const filteredGroups = billingGroups.filter((group) =>
        (!selectedDatabase || group.database === selectedDatabase)
        && (group.name.toLowerCase().includes(normalizedSearch)
            || group.database.toLowerCase().includes(normalizedSearch)))
    const pageCount = Math.max(1, Math.ceil(filteredGroups.length / pageSize))
    const currentPage = Math.min(page, pageCount)
    const visibleGroups = filteredGroups.slice((currentPage - 1) * pageSize, currentPage * pageSize)
    const allVisibleSelected = visibleGroups.length > 0 && visibleGroups.every((group) => selected.includes(group.id))
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

    useEffect(() => {
        if (!isUploadProgressOpen) {
            return undefined
        }

        if (uploadStage < uploadStages.length - 1) {
            const timer = window.setTimeout(() => setUploadStage((stage) => stage + 1), 1400)
            return () => window.clearTimeout(timer)
        }

        const timer = window.setTimeout(() => {
            setIsUploadProgressOpen(false)
            setIsUploadSuccessOpen(true)
            setSelected([])
            window.setTimeout(() => setIsUploadSuccessOpen(false), 1800)
        }, 700)
        return () => window.clearTimeout(timer)
    }, [isUploadProgressOpen, uploadStage])

    function updateSearch(value) { setSearch(value); setPage(1) }
    function toggleGroup(id) { setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]) }
    function toggleVisibleGroups() {
        setSelected((current) => allVisibleSelected
            ? current.filter((id) => !visibleGroups.some((group) => group.id === id))
            : [...new Set([...current, ...visibleGroups.map((group) => group.id)])])
    }
    function showNotice(message) { setNotice(message); window.setTimeout(() => setNotice(''), 3000) }
    function uploadSelected() {
        if (!selected.length) {
            showNotice('Select at least one billing group to upload.')
            return
        }

        setUploadStage(2)
        setIsUploadProgressOpen(true)
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
        setIsResetBillings(true);
        return
    }
    function changePage(nextPage) { setPage(Math.min(Math.max(nextPage, 1), pageCount)); setSelected([]) }
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
                        <div className="billing-heading d-flex align-items-center justify-content-between"><h2><FontAwesomeIcon icon={faTableList} /> Active Billings</h2><span className="billing-count">{selected.length} selected</span></div>
                        <div className="billing-list">
                            <div className="billing-list-header row align-items-center g-0 px-3 py-2">
                                <div className="col-auto billing-select-column"><input type="checkbox" checked={allVisibleSelected} onChange={toggleVisibleGroups} aria-label="Select visible billing groups" /></div>
                                <div className="col billing-name-column">Group ID</div>
                                <div className="col-6 col-sm-2 billing-value-column">Database</div>
                                <div className="col-6 col-sm-2 billing-value-column">Status</div>
                            </div>
                            {visibleGroups.map((group) => <label className="billing-list-row row align-items-center g-0 px-3 py-2" key={group.id}>
                                <div className="col-auto billing-select-column"><input type="checkbox" checked={selected.includes(group.id)} onChange={() => toggleGroup(group.id)} aria-label={`Select ${group.name}`} /></div>
                                <div className="col billing-name-column d-flex align-items-center gap-3"><span className="folder-icon"><FontAwesomeIcon icon={faFolder} /></span><strong>{group.name}</strong></div>
                                <div className="col-6 col-sm-2 billing-value-column"><span className="database-name-pill">{group.database}</span></div>
                                <div className="col-6 col-sm-2 billing-value-column"><span className="status-pill">{group.status}</span></div>
                            </label>)}
                            {isLoadingGroups && <p className="empty-state"><FontAwesomeIcon icon={faSpinner} spin /> Loading database instances...</p>}
                            {!isLoadingGroups && groupsError && <div className="empty-state api-error-state"><p>{groupsError}</p><button type="button" onClick={() => setLoadAttempt((attempt) => attempt + 1)}>Retry</button></div>}
                            {!isLoadingGroups && !groupsError && !visibleGroups.length && <p className="empty-state">{billingGroups.length ? 'No groups match your search.' : 'No database instances were returned.'}</p>}
                        </div>
                        <div className="billing-footer d-flex align-items-center justify-content-between"><span>Showing {visibleGroups.length ? (currentPage - 1) * pageSize + 1 : 0}-{Math.min(currentPage * pageSize, filteredGroups.length)} of {filteredGroups.length} billing groups</span><div className="pagination d-flex align-items-center gap-2" aria-label="Billing group pages"><button type="button" onClick={() => changePage(currentPage - 1)} disabled={currentPage === 1} aria-label="Previous page"><FontAwesomeIcon icon={faChevronLeft} /></button>{Array.from({ length: pageCount }, (_, index) => index + 1).slice(0, 4).map((number) => <button className={number === currentPage ? 'active' : ''} type="button" key={number} onClick={() => changePage(number)}>{number}</button>)}<button type="button" onClick={() => changePage(currentPage + 1)} disabled={currentPage === pageCount} aria-label="Next page"><FontAwesomeIcon icon={faChevronRight} /></button></div></div>
                    </section>
                </section>
                {/* {isAdmin && <aside className="admin-panel col-lg-4 col-xl-3"><button className="settings-button" type="button" aria-label="Settings"><FontAwesomeIcon icon={faGear} /></button><div className="admin-menu"><button className="admin-menu-title" type="button"><FontAwesomeIcon icon={faServer} /> Admin Functions <b><FontAwesomeIcon icon={faChevronDown} /></b></button>{['Make RTE File', 'One Shuttle File', 'Load Shuttle File', 'Commands', 'View Logs', 'Admin'].map((action) => <button className="admin-action" type="button" key={action} onClick={() => showNotice(`${action} selected.`)}><FontAwesomeIcon icon={faServer} />{action}<b><FontAwesomeIcon icon={faChevronRight} /></b></button>)}</div></aside>} */}
            </div>
            {notice && <div className="toast-message" role="status">{notice}</div>}
            {isUploadProgressOpen && <div className="upload-progress-backdrop" role="dialog" aria-modal="true" aria-labelledby="upload-progress-title">
                <section className="upload-progress-modal">
                    <h2 id="upload-progress-title">Uploading selected billings</h2>
                    <div className="upload-stage-track">
                        {uploadStages.map((stage, index) => {
                            const isComplete = index < uploadStage
                            const isCurrent = index === uploadStage
                            return <div className={`upload-stage ${isComplete ? 'complete' : ''} ${isCurrent ? 'current' : ''}`} key={stage.label}>
                                <div className="upload-stage-node"><FontAwesomeIcon icon={stage.icon} /></div>
                                <strong>{stage.label}</strong>
                                <span>{isComplete ? 'Completed' : isCurrent ? 'In Progress' : 'Pending'}</span>
                            </div>
                        })}
                    </div>
                    <div className="upload-progress-message"><FontAwesomeIcon icon={faSpinner} spin /><span>Please keep this page open. We&apos;ll notify you when it&apos;s complete.</span></div>
                </section>
            </div>}
            {isUploadSuccessOpen && <div className="upload-success-backdrop" role="dialog" aria-modal="true" aria-labelledby="upload-success-title">
                <section className="upload-success-modal">
                    <div className="upload-success-icon"><FontAwesomeIcon icon={faCheck} /></div>
                    <h2 id="upload-success-title">Uploaded successfully</h2>
                    <p>The selected billing groups have been uploaded successfully.</p>
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
