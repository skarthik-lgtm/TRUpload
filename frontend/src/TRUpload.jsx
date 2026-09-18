import { useMemo, useState } from 'react'

function TRUpload({ username, onLogout }) {
    const [search, setSearch] = useState('')
    const [page, setPage] = useState(1)
    const [selected, setSelected] = useState([])
    const [notice, setNotice] = useState('')
    const pageSize = 10
    const billingGroups = useMemo(() => Array.from({ length: 35 }, (_, index) => {
        const number = String(92112 + index * 120).padStart(6, '0')
        return { id: number, name: number, type: index % 4 === 3 ? 'Premium' : 'Standard' }
    }), [])
    const filteredGroups = billingGroups.filter((group) => group.name.includes(search.trim().toLowerCase()))
    const pageCount = Math.max(1, Math.ceil(filteredGroups.length / pageSize))
    const currentPage = Math.min(page, pageCount)
    const visibleGroups = filteredGroups.slice((currentPage - 1) * pageSize, currentPage * pageSize)
    const allVisibleSelected = visibleGroups.length > 0 && visibleGroups.every((group) => selected.includes(group.id))

    function updateSearch(value) { setSearch(value); setPage(1) }
    function toggleGroup(id) { setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]) }
    function toggleVisibleGroups() {
        setSelected((current) => allVisibleSelected
            ? current.filter((id) => !visibleGroups.some((group) => group.id === id))
            : [...new Set([...current, ...visibleGroups.map((group) => group.id)])])
    }
    function showNotice(message) { setNotice(message); window.setTimeout(() => setNotice(''), 3000) }
    function uploadSelected() { showNotice(selected.length ? `${selected.length} billing group${selected.length === 1 ? '' : 's'} queued for upload.` : 'Select at least one billing group to upload.') }
    function changePage(nextPage) { setPage(Math.min(Math.max(nextPage, 1), pageCount)); setSelected([]) }

    return (
        <main className="upload-page">
            <header className="upload-header d-flex align-items-center">
                <div className="upload-brand">TR<span>UPLOAD</span></div>
                <div className="upload-nav"><span className="nav-home">⌂</span><span>TR Upload</span></div>
                <div className="account-controls d-flex align-items-center ms-auto">
                    <span className="environment-pill"><i /> DEV</span>
                    <span className="user-avatar">{username?.charAt(0).toUpperCase() || 'U'}</span>
                    <button className="user-menu" type="button" onClick={onLogout}>{username || 'User'} <span>⌄</span></button>
                </div>
            </header>
            <div className="upload-layout container-fluid row g-4">
                <section className="upload-content col-lg-8 col-xl-9">
                    <div className="workspace-heading d-flex align-items-center">
                        <div className="cloud-icon">☁</div>
                        <div><h1>TR Upload</h1><p>Select a source database and choose the billing group(s) to upload.</p></div>
                    </div>
                    <div className="workspace-toolbar d-flex flex-wrap align-items-end gap-3">
                        <label className="source-select"><span>Source Database</span><select defaultValue="CSTRMS" aria-label="Source database"><option>CSTRMS</option><option>Production</option><option>Archive</option></select></label>
                        <label className="billing-search"><span>⌕</span><input value={search} onChange={(event) => updateSearch(event.target.value)} placeholder="Search billing group name or number..." aria-label="Search billing groups" />{search && <button type="button" onClick={() => updateSearch('')} aria-label="Clear search">×</button>}</label>
                        <button className="upload-action" type="button" onClick={uploadSelected}><span>☁</span> Upload Selected Billings</button>
                    </div>
                    <section className="billing-panel">
                        <div className="billing-heading d-flex align-items-center justify-content-between"><h2><span>▤</span> Active Billings</h2><span className="billing-count">{selected.length} selected</span></div>
                        <div className="billing-table-wrap">
                            <table className="billing-table">
                                <thead>
                                    <tr>
                                        <th className="col-1 text-start" style={{"lineHeight":"35px"}}><input type="checkbox" checked={allVisibleSelected} onChange={toggleVisibleGroups} aria-label="Select visible billing groups" style={{"lineHeight": '1.5'}}/></th>
                                        <th className="col-5 text-start" style={{"lineHeight":"35px"}}>Billing Group Name</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {visibleGroups.map((group) => 
                                    <tr key={group.id}>
                                        <td className="col-1 text-start" style={{"lineHeight":"35px"}}><input type="checkbox" checked={selected.includes(group.id)} onChange={() => toggleGroup(group.id)} aria-label={`Select ${group.name}`} style={{"lineHeight": '35px !important'}} /></td>
                                        <td className="col-5 text-start"><span className="folder-icon">■</span><strong>{group.name}</strong></td>
                                    </tr>)}
                                </tbody>
                            </table>
                            {!visibleGroups.length && <p className="empty-state">No billing groups match your search.</p>}
                        </div>
                        <div className="billing-footer d-flex align-items-center justify-content-between"><span>Showing {visibleGroups.length ? (currentPage - 1) * pageSize + 1 : 0}-{Math.min(currentPage * pageSize, filteredGroups.length)} of {filteredGroups.length} billing groups</span><div className="pagination d-flex align-items-center gap-2" aria-label="Billing group pages"><button type="button" onClick={() => changePage(currentPage - 1)} disabled={currentPage === 1} aria-label="Previous page">‹</button>{Array.from({ length: pageCount }, (_, index) => index + 1).slice(0, 4).map((number) => <button className={number === currentPage ? 'active' : ''} type="button" key={number} onClick={() => changePage(number)}>{number}</button>)}<button type="button" onClick={() => changePage(currentPage + 1)} disabled={currentPage === pageCount} aria-label="Next page">›</button></div></div>
                    </section>
                </section>
                <aside className="admin-panel col-lg-4 col-xl-3"><button className="settings-button" type="button" aria-label="Settings">⚙</button><div className="admin-menu"><button className="admin-menu-title" type="button"><span>♙</span> Admin Functions <b>⌃</b></button>{['Make RTE File', 'One Shuttle File', 'Load Shuttle File', 'Commands', 'View Logs', 'Admin'].map((action, index) => <button className={index === 4 ? 'admin-action separated' : 'admin-action'} type="button" key={action} onClick={() => showNotice(`${action} selected.`)}><span>{['▣', '⤢', '☁', '▣', '▤', '♙'][index]}</span>{action}<b>›</b></button>)}</div></aside>
            </div>
            {notice && <div className="toast-message" role="status">{notice}</div>}
        </main>
    )
}

export default TRUpload
