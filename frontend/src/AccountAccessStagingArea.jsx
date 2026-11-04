import { useMemo, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
    faUserClock,
    faUser,
    faLocationDot,
    faBuilding,
    faClock,
    faCheck,
    faXmark,
    faEye,
    faMagnifyingGlass,
    faFilter,
    faChevronLeft,
    faChevronRight,
    faCircleCheck,
    faCircleXmark,
    faTriangleExclamation
} from '@fortawesome/free-solid-svg-icons'


function AccountAccessStagingArea() {

    /*
     * Temporary data.
     *
     * Later this will come from Spring Boot:
     *
     * GET /api/admin/account-requests
     *
     * The important part is that the structure here
     * represents what we eventually want from the backend.
     */

    const [requests, setRequests] = useState([
        {
            requestId: 'AR-20261005-001',
            firstName: 'John',
            lastName: 'Smith',
            email: 'john.smith@example.com',
            phone: '(555) 201-4587',

            username: 'john.smith',

            region: 'Northeast',
            state: 'New York',

            requestedFacilities: [
                'CSTRAB',
                'CSTRBE'
            ],

            requestedAt: 'Oct 05, 2026 10:32 AM',

            status: 'PENDING',

            comments: '',

            address: {
                city: 'Albany',
                state: 'New York',
                zipCode: '12207'
            }
        },

        {
            requestId: 'AR-20261005-002',
            firstName: 'Sarah',
            lastName: 'Johnson',
            email: 'sarah.johnson@example.com',
            phone: '(555) 384-9012',

            username: 'sarah.johnson',

            region: 'Southeast',
            state: 'Georgia',

            requestedFacilities: [
                'CSTRED',
                'CSTRSG'
            ],

            requestedAt: 'Oct 05, 2026 09:15 AM',

            status: 'PENDING',

            comments: '',

            address: {
                city: 'Atlanta',
                state: 'Georgia',
                zipCode: '30303'
            }
        },

        {
            requestId: 'AR-20261004-008',
            firstName: 'Michael',
            lastName: 'Wilson',
            email: 'michael.wilson@example.com',
            phone: '(555) 451-6723',

            username: 'michael.wilson',

            region: 'Midwest',
            state: 'Illinois',

            requestedFacilities: [
                'CSTRDB'
            ],

            requestedAt: 'Oct 04, 2026 04:47 PM',

            status: 'APPROVED',

            comments: 'Verified with operations team.',

            address: {
                city: 'Chicago',
                state: 'Illinois',
                zipCode: '60601'
            }
        },

        {
            requestId: 'AR-20261004-006',
            firstName: 'David',
            lastName: 'Brown',
            email: 'david.brown@example.com',
            phone: '(555) 672-3412',

            username: 'david.brown',

            region: 'West',
            state: 'California',

            requestedFacilities: [
                'CSTRSG'
            ],

            requestedAt: 'Oct 04, 2026 02:21 PM',

            status: 'REJECTED',

            comments: 'Facility assignment could not be verified.',

            address: {
                city: 'Sacramento',
                state: 'California',
                zipCode: '95814'
            }
        }
    ])


    const [search, setSearch] = useState('')
    const [regionFilter, setRegionFilter] = useState('ALL')
    const [statusFilter, setStatusFilter] = useState('ALL')

    const [selectedRequest, setSelectedRequest] = useState(null)


    /*
     * Filter requests
     */

    const filteredRequests = useMemo(() => {

        return requests.filter((request) => {

            const searchValue = search.toLowerCase()

            const matchesSearch =
                request.firstName.toLowerCase().includes(searchValue) ||
                request.lastName.toLowerCase().includes(searchValue) ||
                request.email.toLowerCase().includes(searchValue) ||
                request.username.toLowerCase().includes(searchValue) ||
                request.requestId.toLowerCase().includes(searchValue)

            const matchesRegion =
                regionFilter === 'ALL' ||
                request.region === regionFilter

            const matchesStatus =
                statusFilter === 'ALL' ||
                request.status === statusFilter

            return (
                matchesSearch &&
                matchesRegion &&
                matchesStatus
            )
        })

    }, [requests, search, regionFilter, statusFilter])


    /*
     * Status badge
     */

    function getStatusBadge(status) {

        if (status === 'PENDING') {

            return (
                <span className="account-status pending">
                    <FontAwesomeIcon icon={faClock} />
                    Pending
                </span>
            )
        }

        if (status === 'APPROVED') {

            return (
                <span className="account-status approved">
                    <FontAwesomeIcon icon={faCircleCheck} />
                    Approved
                </span>
            )
        }

        return (
            <span className="account-status rejected">
                <FontAwesomeIcon icon={faCircleXmark} />
                Rejected
            </span>
        )
    }


    /*
     * Approve request
     */

    function approveRequest(requestId) {

        setRequests((currentRequests) =>

            currentRequests.map((request) => {

                if (request.requestId === requestId) {

                    return {
                        ...request,
                        status: 'APPROVED',
                        comments: 'Account approved by administrator.'
                    }

                }

                return request
            })

        )

        setSelectedRequest((current) => {

            if (!current) {
                return current
            }

            if (current.requestId !== requestId) {
                return current
            }

            return {
                ...current,
                status: 'APPROVED',
                comments: 'Account approved by administrator.'
            }
        })
    }


    /*
     * Reject request
     */

    function rejectRequest(requestId) {

        setRequests((currentRequests) =>

            currentRequests.map((request) => {

                if (request.requestId === requestId) {

                    return {
                        ...request,
                        status: 'REJECTED',
                        comments: 'Account request rejected by administrator.'
                    }

                }

                return request
            })

        )

        setSelectedRequest((current) => {

            if (!current) {
                return current
            }

            if (current.requestId !== requestId) {
                return current
            }

            return {
                ...current,
                status: 'REJECTED',
                comments: 'Account request rejected by administrator.'
            }
        })
    }


    /*
     * Clear filters
     */

    function clearFilters() {

        setSearch('')
        setRegionFilter('ALL')
        setStatusFilter('ALL')
    }


    return (

        <main className="account-requests-page">


            {/* =========================================
                PAGE HEADER
            ========================================= */}

            <div className="account-page-header">

                <div>

                    <div className="account-breadcrumb">
                        TR Upload
                        <span>/</span>
                        Administration
                        <span>/</span>
                        Account Requests
                    </div>

                    <h1>
                        Account Requests
                    </h1>

                    <p>
                        Review and manage user account creation requests.
                    </p>

                </div>


                <div className="account-header-icon">

                    <FontAwesomeIcon icon={faUserClock} />

                </div>

            </div>


            {/* =========================================
                SUMMARY CARDS
            ========================================= */}

            <div className="row g-3 mb-4">

                <div className="col-xl-4 col-md-6">

                    <div className="account-summary-card">

                        <div className="summary-icon pending-icon">
                            <FontAwesomeIcon icon={faClock} />
                        </div>

                        <div>

                            <span>
                                Pending Requests
                            </span>

                            <strong>
                                {
                                    requests.filter(
                                        (request) =>
                                            request.status === 'PENDING'
                                    ).length
                                }
                            </strong>

                        </div>

                    </div>

                </div>


                <div className="col-xl-4 col-md-6">

                    <div className="account-summary-card">

                        <div className="summary-icon approved-icon">
                            <FontAwesomeIcon icon={faCheck} />
                        </div>

                        <div>

                            <span>
                                Approved
                            </span>

                            <strong>
                                {
                                    requests.filter(
                                        (request) =>
                                            request.status === 'APPROVED'
                                    ).length
                                }
                            </strong>

                        </div>

                    </div>

                </div>


                <div className="col-xl-4 col-md-6">

                    <div className="account-summary-card">

                        <div className="summary-icon rejected-icon">
                            <FontAwesomeIcon icon={faXmark} />
                        </div>

                        <div>

                            <span>
                                Rejected
                            </span>

                            <strong>
                                {
                                    requests.filter(
                                        (request) =>
                                            request.status === 'REJECTED'
                                    ).length
                                }
                            </strong>

                        </div>

                    </div>

                </div>

            </div>


            {/* =========================================
                FILTERS
            ========================================= */}

            <section className="account-filter-card">

                <div className="account-filter-heading">

                    <div>

                        <h5>
                            <FontAwesomeIcon
                                icon={faFilter}
                            />

                            Filters
                        </h5>

                        <span>
                            Find account creation requests
                        </span>

                    </div>


                    <button
                        type="button"
                        className="account-clear-button"
                        onClick={clearFilters}
                    >
                        Clear filters
                    </button>

                </div>


                <div className="row g-3">


                    {/* SEARCH */}

                    <div className="col-xl-5 col-md-6">

                        <label>
                            Search
                        </label>

                        <div className="account-search">

                            <FontAwesomeIcon
                                icon={faMagnifyingGlass}
                            />

                            <input
                                type="text"
                                className="form-control"
                                placeholder="Search by name, email or request ID..."
                                value={search}
                                onChange={(event) =>
                                    setSearch(event.target.value)
                                }
                            />

                        </div>

                    </div>


                    {/* REGION */}

                    <div className="col-xl-3 col-md-3">

                        <label>
                            Region
                        </label>

                        <select
                            className="form-select"
                            value={regionFilter}
                            onChange={(event) =>
                                setRegionFilter(event.target.value)
                            }
                        >

                            <option value="ALL">
                                All Regions
                            </option>

                            <option value="Northeast">
                                Northeast
                            </option>

                            <option value="Southeast">
                                Southeast
                            </option>

                            <option value="Midwest">
                                Midwest
                            </option>

                            <option value="West">
                                West
                            </option>

                        </select>

                    </div>


                    {/* STATUS */}

                    <div className="col-xl-3 col-md-3">

                        <label>
                            Status
                        </label>

                        <select
                            className="form-select"
                            value={statusFilter}
                            onChange={(event) =>
                                setStatusFilter(event.target.value)
                            }
                        >

                            <option value="ALL">
                                All Statuses
                            </option>

                            <option value="PENDING">
                                Pending
                            </option>

                            <option value="APPROVED">
                                Approved
                            </option>

                            <option value="REJECTED">
                                Rejected
                            </option>

                        </select>

                    </div>

                </div>

            </section>


            {/* =========================================
                REQUEST TABLE
            ========================================= */}

            <section className="account-request-card">


                <div className="account-table-header">

                    <div>

                        <h4>
                            Account Creation Requests
                        </h4>

                        <p>
                            {filteredRequests.length} request
                            {filteredRequests.length !== 1 ? 's' : ''}
                        </p>

                    </div>

                </div>


                <div className="table-responsive">

                    <table className="table account-request-table">

                        <thead>

                            <tr>

                                <th>
                                    User
                                </th>

                                <th>
                                    Email
                                </th>

                                <th>
                                    Region
                                </th>

                                <th>
                                    State
                                </th>

                                <th>
                                    Requested On
                                </th>

                                <th>
                                    Status
                                </th>

                                <th className="text-center">
                                    Action
                                </th>

                            </tr>

                        </thead>


                        <tbody>

                            {filteredRequests.map((request) => (

                                <tr key={request.requestId}>


                                    {/* USER */}

                                    <td>

                                        <div className="request-user">

                                            <div className="request-avatar">

                                                {request.firstName[0]}
                                                {request.lastName[0]}

                                            </div>

                                            <div>

                                                <strong>
                                                    {request.firstName}{' '}
                                                    {request.lastName}
                                                </strong>

                                                <small>
                                                    @{request.username}
                                                </small>

                                            </div>

                                        </div>

                                    </td>


                                    {/* EMAIL */}

                                    <td>
                                        <span className="request-email">
                                            {request.email}
                                        </span>
                                    </td>


                                    {/* REGION */}

                                    <td>

                                        <span className="region-badge">
                                            {request.region}
                                        </span>

                                    </td>


                                    {/* STATE */}

                                    <td>
                                        {request.state}
                                    </td>


                                    {/* REQUESTED */}

                                    <td>

                                        <div className="requested-time">

                                            <strong>
                                                {request.requestedAt}
                                            </strong>

                                            <small>
                                                {request.requestId}
                                            </small>

                                        </div>

                                    </td>


                                    {/* STATUS */}

                                    <td>
                                        {getStatusBadge(request.status)}
                                    </td>


                                    {/* ACTION */}

                                    <td className="text-center">

                                        <button
                                            type="button"
                                            className="account-view-button"
                                            onClick={() =>
                                                setSelectedRequest(request)
                                            }
                                        >

                                            <FontAwesomeIcon
                                                icon={faEye}
                                            />

                                            View

                                        </button>

                                    </td>

                                </tr>

                            ))}


                            {filteredRequests.length === 0 && (

                                <tr>

                                    <td
                                        colSpan="7"
                                        className="account-empty"
                                    >

                                        <FontAwesomeIcon
                                            icon={faUserClock}
                                        />

                                        <h5>
                                            No account requests found
                                        </h5>

                                        <p>
                                            Try changing your filters.
                                        </p>

                                    </td>

                                </tr>

                            )}

                        </tbody>

                    </table>

                </div>


                {/* PAGINATION */}

                <div className="account-pagination">

                    <span>
                        Showing {filteredRequests.length} requests
                    </span>

                    <div>

                        <button type="button" disabled>
                            <FontAwesomeIcon icon={faChevronLeft} />
                        </button>

                        <button
                            type="button"
                            className="active"
                        >
                            1
                        </button>

                        <button type="button">
                            2
                        </button>

                        <button type="button">
                            3
                        </button>

                        <button type="button">
                            <FontAwesomeIcon icon={faChevronRight} />
                        </button>

                    </div>

                </div>

            </section>


            {/* =========================================
                REQUEST DETAILS MODAL
            ========================================= */}

            {selectedRequest && (

                <div
                    className="account-modal-backdrop"
                    onClick={(event) => {

                        if (
                            event.target ===
                            event.currentTarget
                        ) {
                            setSelectedRequest(null)
                        }

                    }}
                >

                    <div className="account-detail-modal">


                        {/* MODAL HEADER */}

                        <div className="account-modal-header">

                            <div>

                                <span>
                                    ACCOUNT CREATION REQUEST
                                </span>

                                <h3>
                                    {selectedRequest.requestId}
                                </h3>

                                <p>
                                    Review the user's account
                                    information and requested access.
                                </p>

                            </div>


                            <button
                                type="button"
                                className="account-modal-close"
                                onClick={() =>
                                    setSelectedRequest(null)
                                }
                            >

                                <FontAwesomeIcon
                                    icon={faXmark}
                                />

                            </button>

                        </div>


                        {/* MODAL BODY */}

                        <div className="account-modal-body">


                            {/* =================================
                                REQUEST STATUS
                            ================================= */}

                            <div className="request-review-status">

                                <div>

                                    <span>
                                        REQUEST STATUS
                                    </span>

                                    {getStatusBadge(
                                        selectedRequest.status
                                    )}

                                </div>


                                <div>

                                    <span>
                                        REQUESTED ON
                                    </span>

                                    <strong>
                                        {selectedRequest.requestedAt}
                                    </strong>

                                </div>

                            </div>


                            {/* =================================
                                BASIC INFORMATION
                            ================================= */}

                            <section className="account-detail-section">

                                <div className="account-section-title">

                                    <FontAwesomeIcon
                                        icon={faUser}
                                    />

                                    Basic Information

                                </div>


                                <div className="account-detail-grid">

                                    <div>

                                        <span>
                                            First Name
                                        </span>

                                        <strong>
                                            {selectedRequest.firstName}
                                        </strong>

                                    </div>


                                    <div>

                                        <span>
                                            Last Name
                                        </span>

                                        <strong>
                                            {selectedRequest.lastName}
                                        </strong>

                                    </div>


                                    <div>

                                        <span>
                                            Username
                                        </span>

                                        <strong>
                                            {selectedRequest.username}
                                        </strong>

                                    </div>


                                    <div>

                                        <span>
                                            Email Address
                                        </span>

                                        <strong>
                                            {selectedRequest.email}
                                        </strong>

                                    </div>


                                    <div>

                                        <span>
                                            Phone Number
                                        </span>

                                        <strong>
                                            {selectedRequest.phone}
                                        </strong>

                                    </div>

                                </div>

                            </section>


                            {/* =================================
                                REGION / FACILITY
                            ================================= */}

                            <section className="account-detail-section">

                                <div className="account-section-title">

                                    <FontAwesomeIcon
                                        icon={faLocationDot}
                                    />

                                    Operational Assignment

                                </div>


                                <div className="assignment-card">

                                    <div className="assignment-item">

                                        <span>
                                            Region
                                        </span>

                                        <strong>
                                            {selectedRequest.region}
                                        </strong>

                                    </div>


                                    <div className="assignment-item">

                                        <span>
                                            State
                                        </span>

                                        <strong>
                                            {selectedRequest.state}
                                        </strong>

                                    </div>


                                    <div className="assignment-item">

                                        <span>
                                            City
                                        </span>

                                        <strong>
                                            {selectedRequest.address.city}
                                        </strong>

                                    </div>


                                    <div className="assignment-item">

                                        <span>
                                            ZIP Code
                                        </span>

                                        <strong>
                                            {selectedRequest.address.zipCode}
                                        </strong>

                                    </div>

                                </div>


                                {/* FACILITIES */}

                                <div className="facility-assignment">

                                    <div>

                                        <span>
                                            Requested Facilities
                                        </span>

                                        <small>
                                            Facilities the user is requesting
                                            access to.
                                        </small>

                                    </div>


                                    <div className="facility-list">

                                        {selectedRequest.requestedFacilities.map(
                                            (facility) => (

                                                <span
                                                    key={facility}
                                                    className="facility-tag"
                                                >
                                                    <FontAwesomeIcon
                                                        icon={faBuilding}
                                                    />

                                                    {facility}

                                                </span>

                                            )
                                        )}

                                    </div>

                                </div>

                            </section>


                            {/* =================================
                                ADMIN REVIEW
                            ================================= */}

                            {selectedRequest.status === 'PENDING' && (

                                <section className="admin-review-box">

                                    <div className="review-warning">

                                        <FontAwesomeIcon
                                            icon={faTriangleExclamation}
                                        />

                                        <div>

                                            <strong>
                                                Admin Review Required
                                            </strong>

                                            <p>
                                                Verify the user's region,
                                                state and requested facility
                                                access before approving this
                                                account.
                                            </p>

                                        </div>

                                    </div>


                                    <div className="review-actions">

                                        <button
                                            type="button"
                                            className="reject-request-button"
                                            onClick={() =>
                                                rejectRequest(
                                                    selectedRequest.requestId
                                                )
                                            }
                                        >

                                            <FontAwesomeIcon
                                                icon={faXmark}
                                            />

                                            Reject Request

                                        </button>


                                        <button
                                            type="button"
                                            className="approve-request-button"
                                            onClick={() =>
                                                approveRequest(
                                                    selectedRequest.requestId
                                                )
                                            }
                                        >

                                            <FontAwesomeIcon
                                                icon={faCheck}
                                            />

                                            Approve Account

                                        </button>

                                    </div>

                                </section>

                            )}


                            {/* =================================
                                APPROVED / REJECTED MESSAGE
                            ================================= */}

                            {selectedRequest.status !== 'PENDING' && (

                                <div
                                    className={
                                        selectedRequest.status ===
                                        'APPROVED'
                                            ? 'request-final-message approved-message'
                                            : 'request-final-message rejected-message'
                                    }
                                >

                                    <FontAwesomeIcon
                                        icon={
                                            selectedRequest.status ===
                                            'APPROVED'
                                                ? faCircleCheck
                                                : faCircleXmark
                                        }
                                    />

                                    <div>

                                        <strong>
                                            {selectedRequest.status ===
                                            'APPROVED'
                                                ? 'Account Approved'
                                                : 'Account Rejected'}
                                        </strong>

                                        <p>
                                            {selectedRequest.comments}
                                        </p>

                                    </div>

                                </div>

                            )}

                        </div>


                        {/* MODAL FOOTER */}

                        <div className="account-modal-footer">

                            <button
                                type="button"
                                className="account-close-button"
                                onClick={() =>
                                    setSelectedRequest(null)
                                }
                            >
                                Close
                            </button>

                        </div>

                    </div>

                </div>

            )}

        </main>
    )
}


export default AccountAccessStagingArea