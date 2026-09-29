import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
	faArrowDown,
	faArrowLeft,
	faBarsProgress,
	faCalendarDays,
	faCheck,
	faChevronDown,
	faChevronLeft,
	faChevronRight,
	faCircleUser,
	faCloudArrowDown,
	faHouse,
	faMagnifyingGlass,
	faNetworkWired,
	faServer,
	faTableList,
	faTriangleExclamation,
	faXmark,
} from '@fortawesome/free-solid-svg-icons'

const sampleRecords = [
	{ id: 'UP-20260924-00125', user: 'Sarah Johnson', time: '09:12 AM', date: '2026-09-24', region: 'Northeast', facility: 'Aberdeen', instance: 'CSTRAB', groups: 3, status: 'Completed', duration: '1m 24s' },
	{ id: 'UP-20260924-00124', user: 'John Smith', time: '10:45 AM', date: '2026-09-24', region: 'Northeast', facility: 'Edison', instance: 'CSTRED', groups: 1, status: 'Completed', duration: '58s' },
	{ id: 'UP-20260923-00123', user: 'Mike Davis', time: '02:18 PM', date: '2026-09-23', region: 'Midwest', facility: 'Bethlehem', instance: 'CSTRBE', groups: 2, status: 'Warning', duration: '2m 06s' },
	{ id: 'UP-20260923-00122', user: 'Emily Carter', time: '11:03 AM', date: '2026-09-23', region: 'West', facility: 'Brattleboro', instance: 'CSTRBR', groups: 1, status: 'Failed', duration: '34s' },
	{ id: 'UP-20260922-00121', user: 'David Wilson', time: '04:27 PM', date: '2026-09-22', region: 'South', facility: 'Dubuois', instance: 'CSTRDB', groups: 4, status: 'Completed', duration: '1m 42s' },
	{ id: 'UP-20260922-00120', user: 'Jennifer Lee', time: '09:55 AM', date: '2026-09-22', region: 'Northeast', facility: 'Edison', instance: 'CSTRED', groups: 1, status: 'Completed', duration: '1m 12s' },
	{ id: 'UP-20260921-00119', user: 'Sarah Johnson', time: '08:31 AM', date: '2026-09-21', region: 'Northeast', facility: 'Aberdeen', instance: 'CSTRAB', groups: 2, status: 'Completed', duration: '1m 09s' },
	{ id: 'UP-20260920-00118', user: 'Mike Davis', time: '03:40 PM', date: '2026-09-20', region: 'Midwest', facility: 'Bethlehem', instance: 'CSTRBE', groups: 3, status: 'Warning', duration: '2m 18s' },
	{ id: 'UP-20260919-00117', user: 'John Smith', time: '01:05 PM', date: '2026-09-19', region: 'West', facility: 'Brattleboro', instance: 'CSTRBR', groups: 1, status: 'Completed', duration: '49s' },
	{ id: 'UP-20260918-00116', user: 'Emily Carter', time: '07:52 AM', date: '2026-09-18', region: 'South', facility: 'Dubuois', instance: 'CSTRDB', groups: 2, status: 'Failed', duration: '41s' },
]

const processingStages = [
	{ name: 'Validation', result: 'Completed', time: '09:12:03 AM', icon: faCheck },
	{ name: 'RTE Creation', result: 'Completed', time: '09:12:08 AM', icon: faCheck },
	{ name: 'File Generation', result: 'Completed', time: '09:12:14 AM', icon: faCheck },
	{ name: 'Mainframe Transfer', result: 'Completed', time: '09:12:31 AM', icon: faServer },
	{ name: 'TRACS Transfer', result: 'Completed', time: '09:12:48 AM', icon: faNetworkWired },
	{ name: 'Downstream Systems', result: 'Completed', time: '09:13:27 AM', icon: faCheck },
]

function AuditLogs({ username, role, onLogout }) {
	const navigate = useNavigate()
	const isAdmin = String(role).toUpperCase() === 'ADMIN'
	const [records] = useState(sampleRecords)
	const [filters, setFilters] = useState({ user: '', region: '', facility: '', instance: '', groupId: '', startDate: '', endDate: '', status: 'All Statuses' })
	const [page, setPage] = useState(1)
	const [selectedRecord, setSelectedRecord] = useState(null)
	const [isProfileOpen, setIsProfileOpen] = useState(false)
	const pageSize = 6

	const filteredRecords = useMemo(() => records.filter((record) => {
		const matches = (value, filter) => !filter || value.toLowerCase().includes(filter.trim().toLowerCase())
		return matches(record.user, filters.user)
			&& matches(record.region, filters.region)
			&& matches(record.facility, filters.facility)
			&& matches(record.instance, filters.instance)
			&& matches(String(record.groups), filters.groupId)
			&& (!filters.startDate || record.date >= filters.startDate)
			&& (!filters.endDate || record.date <= filters.endDate)
			&& (filters.status === 'All Statuses' || record.status === filters.status)
	}), [records, filters])
	const pageCount = Math.max(1, Math.ceil(filteredRecords.length / pageSize))
	const currentPage = Math.min(page, pageCount)
	const visibleRecords = filteredRecords.slice((currentPage - 1) * pageSize, currentPage * pageSize)

	function updateFilter(field, value) {
		setFilters((current) => ({ ...current, [field]: value }))
		setPage(1)
	}

	function clearFilters() {
		setFilters({ user: '', region: '', facility: '', instance: '', groupId: '', startDate: '', endDate: '', status: 'All Statuses' })
		setPage(1)
	}

	function downloadRecord(record) {
		const report = new Blob([JSON.stringify({ transaction: record, stages: processingStages }, null, 2)], { type: 'application/json' })
		const url = URL.createObjectURL(report)
		const link = document.createElement('a')
		link.href = url
		link.download = `${record.id}-audit.json`
		link.click()
		URL.revokeObjectURL(url)
	}

	return (
		<main className="audit-page">
			<header className="upload-header d-flex align-items-center">
				<div className="upload-brand">TR<span>UPLOAD</span></div>
				<nav className="audit-nav" aria-label="Main navigation">
					<button type="button" onClick={() => navigate('/trupload')}><FontAwesomeIcon icon={faHouse} /> TR Upload</button>
					<button className="active" type="button" aria-current="page"><FontAwesomeIcon icon={faTableList} /> Audit Log</button>
				</nav>
				<div className="audit-account ms-auto">
					<button className="audit-profile-toggle" type="button" onClick={() => setIsProfileOpen((open) => !open)} aria-expanded={isProfileOpen} aria-haspopup="menu">
						<FontAwesomeIcon icon={faCircleUser} /> <span>{isAdmin ? 'Admin' : username || 'User'}</span> <FontAwesomeIcon icon={faChevronDown} />
					</button>
					{isProfileOpen && <div className="profile-dropdown" role="menu">
						<div className="profile-role">{isAdmin ? 'Admin' : 'User'}</div>
						<button type="button" className="profile-action" role="menuitem" onClick={() => navigate('/trupload')}>TR Upload</button>
						<button type="button" className="profile-action profile-logout" role="menuitem" onClick={onLogout}>Logout</button>
					</div>}
				</div>
			</header>

			<div className="audit-shell container-fluid">
				{selectedRecord ? (
					<TransactionDetails record={selectedRecord} isAdmin={isAdmin} onBack={() => setSelectedRecord(null)} onDownload={() => downloadRecord(selectedRecord)} />
				) : (
					<>
						<div className="audit-heading">
							<div className="audit-heading-icon"><FontAwesomeIcon icon={faTableList} /></div>
							<div><h1>Audit Log</h1><p>{isAdmin ? 'View and search all user activity and upload processing.' : 'Track your upload history and processing status.'}</p></div>
						</div>

						<section className={`audit-filters ${isAdmin ? 'admin-filters' : 'user-filters'}`} aria-label="Audit log filters">
							{isAdmin && <FilterSelect label="User" value={filters.user} onChange={(value) => updateFilter('user', value)} placeholder="All Users" options={sampleRecords.map((record) => record.user)} />}
							{isAdmin && <FilterSelect label="Region" value={filters.region} onChange={(value) => updateFilter('region', value)} placeholder="All Regions" options={sampleRecords.map((record) => record.region)} />}
							<FilterSelect label="Facility" value={filters.facility} onChange={(value) => updateFilter('facility', value)} placeholder="All Facilities" options={sampleRecords.map((record) => record.facility)} />
							{isAdmin && <FilterSelect label="Instance" value={filters.instance} onChange={(value) => updateFilter('instance', value)} placeholder="All Instances" options={sampleRecords.map((record) => record.instance)} />}
							<FilterField label="Group ID" value={filters.groupId} onChange={(value) => updateFilter('groupId', value)} placeholder="Search Group ID..." search />
							<div className="audit-filter-field audit-date-filter"><label>Upload Date Range</label><div className="audit-date-inputs"><input type="date" aria-label="Start date" value={filters.startDate} onChange={(event) => updateFilter('startDate', event.target.value)} /><span>to</span><input type="date" aria-label="End date" value={filters.endDate} onChange={(event) => updateFilter('endDate', event.target.value)} /><FontAwesomeIcon icon={faCalendarDays} /></div></div>
							<div className="audit-filter-field"><label htmlFor="audit-status">Status</label><select id="audit-status" value={filters.status} onChange={(event) => updateFilter('status', event.target.value)}><option>All Statuses</option><option>Completed</option><option>Warning</option><option>Failed</option></select></div>
							{isAdmin && <button className="audit-search-button" type="button" onClick={() => setPage(1)}><FontAwesomeIcon icon={faMagnifyingGlass} /> Search</button>}
							<button className="audit-clear-button" type="button" onClick={clearFilters}>Clear</button>
						</section>

						<section className="audit-table-panel" aria-label="Upload audit records">
							<div className="audit-table-wrap">
								<table className="audit-table">
									<thead><tr>
										{isAdmin && <th>User</th>}
										<th>Upload Time</th>
										{isAdmin && <th>Region</th>}
										<th>Facility</th><th>Instance</th><th>Groups</th><th>Status</th><th>Actions</th>
									</tr></thead>
									<tbody>
										{visibleRecords.map((record) => <tr key={record.id}>
											{isAdmin && <td>{record.user}</td>}
											<td><span className="audit-time">{record.time}</span><small>{record.date}</small></td>
											{isAdmin && <td>{record.region}</td>}
											<td>{record.facility}</td><td className="audit-instance">{record.instance}</td><td>{record.groups}</td>
											<td><span className={`audit-status ${record.status.toLowerCase()}`}><i />{record.status}</span></td>
											<td><div className="audit-row-actions"><button type="button" onClick={() => setSelectedRecord(record)}>View</button><button type="button" onClick={() => downloadRecord(record)} aria-label={`Download ${record.id} report`} title="Download report"><FontAwesomeIcon icon={faArrowDown} /></button></div></td>
										</tr>)}
										{!visibleRecords.length && <tr><td colSpan={isAdmin ? 8 : 6} className="audit-empty">No audit records match these filters.</td></tr>}
									</tbody>
								</table>
							</div>
							<footer className="audit-table-footer"><span>Showing {visibleRecords.length ? (currentPage - 1) * pageSize + 1 : 0}-{Math.min(currentPage * pageSize, filteredRecords.length)} of {filteredRecords.length} records</span><Pagination page={currentPage} pageCount={pageCount} onChange={setPage} /></footer>
						</section>
					</>
				)}
			</div>
		</main>
	)
}

function FilterField({ label, value, onChange, placeholder, search = false }) {
	return <div className="audit-filter-field">
		<label>{label}</label>
		<div className={`audit-filter-input ${search ? 'with-search-icon' : ''}`}>
			{search && <FontAwesomeIcon icon={faMagnifyingGlass} />}
			<input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
		</div>
	</div>
}

function FilterSelect({ label, value, onChange, placeholder, options }) {
	return <div className="audit-filter-field">
		<label>{label}</label>
		<select value={value} onChange={(event) => onChange(event.target.value)}>
			<option value="">{placeholder}</option>
			{[...new Set(options)].map((option) => <option key={option} value={option}>{option}</option>)}
		</select>
	</div>
}

function Pagination({ page, pageCount, onChange }) {
	const pages = Array.from({ length: pageCount }, (_, index) => index + 1).slice(Math.max(0, page - 3), page + 2)
	return <nav className="audit-pagination" aria-label="Audit log pages">
		<button type="button" aria-label="Previous page" onClick={() => onChange(page - 1)} disabled={page === 1}><FontAwesomeIcon icon={faChevronLeft} /></button>
		{pages.map((pageNumber) => <button className={pageNumber === page ? 'active' : ''} type="button" key={pageNumber} onClick={() => onChange(pageNumber)}>{pageNumber}</button>)}
		<button type="button" aria-label="Next page" onClick={() => onChange(page + 1)} disabled={page === pageCount}><FontAwesomeIcon icon={faChevronRight} /></button>
	</nav>
}

function TransactionDetails({ record, isAdmin, onBack, onDownload }) {
	const groups = Array.from({ length: record.groups }, (_, index) => ({ id: `09182${index + 1}DT`, orders: 42 - index * 7, routes: 8 - index, type: index === record.groups - 1 && record.status === 'Failed' ? 'Premium' : 'Standard' }))
	return <>
		<button className="audit-back-link" type="button" onClick={onBack}><FontAwesomeIcon icon={faArrowLeft} /> Back to Audit Log</button>
		<div className="transaction-heading">
			<div className="audit-heading-icon"><FontAwesomeIcon icon={faTableList} /></div>
			<div><h1>Upload Transaction <span className={`audit-status ${record.status.toLowerCase()}`}><i />{record.status}</span></h1><p>View details of your selected upload and group processing information.</p></div>
			<button className="audit-download-report" type="button" onClick={onDownload}><FontAwesomeIcon icon={faCloudArrowDown} /> Download Report</button>
		</div>
		<div className={`transaction-grid ${isAdmin ? 'admin-transaction-grid' : 'user-transaction-grid'}`}>
			<section className="transaction-card upload-summary-card">
				<h2>Upload Summary</h2>
				<div className="summary-grid">
					<SummaryItem label="Transaction ID" value={record.id} />
					{isAdmin && <SummaryItem label="Uploaded By" value={record.user} />}
					<SummaryItem label="Region" value={record.region} />
					<SummaryItem label="Facility" value={record.facility} />
					<SummaryItem label="Instance" value={record.instance} />
					<SummaryItem label="Upload Started" value={`${record.date} ${record.time}`} />
					<SummaryItem label="Upload Completed" value={`${record.date} ${record.time}`} />
					<SummaryItem label="Duration" value={record.duration} />
				</div>
				<div className="overall-status"><strong>Overall Status</strong><span className={`audit-status ${record.status.toLowerCase()}`}><i />{record.status}</span></div>
			</section>
			<section className="transaction-card processing-card">
				<h2><FontAwesomeIcon icon={faBarsProgress} /> Processing Stages (Logs)</h2>
				<ol className="processing-stage-list">{processingStages.map((stage) => <li key={stage.name}>
					<span className={`processing-stage-icon ${record.status === 'Failed' && stage.name === 'Mainframe Transfer' ? 'failed' : ''}`}><FontAwesomeIcon icon={record.status === 'Failed' && stage.name === 'Mainframe Transfer' ? faXmark : stage.icon} /></span>
					<strong>{stage.name}</strong><time>{stage.time}</time><span className="stage-result">{record.status === 'Failed' && stage.name === 'Mainframe Transfer' ? 'Failed' : stage.result}</span>
				</li>)}</ol>
				{record.status === 'Failed' && <div className="audit-failure-note"><FontAwesomeIcon icon={faTriangleExclamation} /><div><strong>Failure Reason</strong><p>Mainframe transfer failed. Check connection timeout and try again.</p></div></div>}
			</section>
			<section className="transaction-card group-info-card">
				<div className="transaction-card-heading"><div><h2>Group Information</h2><span>{groups.length} groups</span></div><div className="transaction-tabs"><button className="active" type="button">Group Information</button><button type="button">Order Details</button></div></div>
				<div className="audit-table-wrap"><table className="audit-table group-info-table"><thead><tr><th>Group ID</th><th>Orders</th><th>Routes</th><th>Dispatch Date</th><th>Type</th><th>Status</th><th>Actions</th></tr></thead><tbody>{groups.map((group) => <tr key={group.id}><td className="audit-instance">{group.id}</td><td>{group.orders}</td><td>{group.routes}</td><td>{record.date}</td><td>{group.type}</td><td><span className={`audit-status ${record.status.toLowerCase()}`}><i />{record.status}</span></td><td><button className="audit-view-orders" type="button">View Orders <FontAwesomeIcon icon={faChevronRight} /></button></td></tr>)}</tbody></table></div>
			</section>
		</div>
	</>
}

function SummaryItem({ label, value }) {
	return <div className="summary-item"><span>{label}</span><strong>{value}</strong></div>
}

export default AuditLogs
