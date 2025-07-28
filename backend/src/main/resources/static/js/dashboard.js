// 글로벌 설정 및 상태 정보
const API_BASE = '/api';
let systemTime = new Date('2026-06-30T14:00:00');

// HTTP 인증 헤더 획득 헬퍼
const getHeaders = () => {
    const token = localStorage.getItem('jwtToken');
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
};

// 1. 웹 초기 구동 및 이벤트 등록
document.addEventListener('DOMContentLoaded', () => {
    // 실시간 시스템 시간 갱신
    setInterval(updateClock, 1000);

    // 로그인 토큰 유효성 체크
    checkAuth();

    // 로그인/로그아웃 및 새로고침 이벤트 바인딩
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('btnLogout').addEventListener('click', handleLogout);
    document.getElementById('btnRefresh').addEventListener('click', () => {
        refreshAllData();
    });

    // 배치 및 Mock 연동 바인딩
    document.getElementById('btnTriggerRecon').addEventListener('click', () => triggerVerification(false));
    document.getElementById('btnTriggerReconError').addEventListener('click', () => triggerVerification(true));
    document.getElementById('btnCreateMock').addEventListener('click', handleCreateMock);
    document.getElementById('selectMockScenario').addEventListener('change', handleScenarioChange);

    // 모달 제어 바인딩
    document.getElementById('btnOpenRequestModal').addEventListener('click', () => toggleRequestModal(true));
    document.getElementById('requestForm').addEventListener('submit', handleCreateSettlement);
    
    // 상세 조회 모달 내 버튼 바인딩
    document.getElementById('btnAssign').addEventListener('click', handleAssignOperator);
    document.getElementById('btnApprove').addEventListener('click', () => processApproval('approve'));
    document.getElementById('btnReject').addEventListener('click', () => processApproval('reject'));
    document.getElementById('btnUploadAttachment').addEventListener('click', handleDetailUpload);

    // 콘솔 청소
    document.getElementById('btnClearConsole').addEventListener('click', clearConsole);

    // 파일 업로드 바인딩
    setupFileUpload();
});

// 실시간 시계 함수
function updateClock() {
    systemTime.setSeconds(systemTime.getSeconds() + 1);
    const year = systemTime.getFullYear();
    const month = String(systemTime.getMonth() + 1).padStart(2, '0');
    const date = String(systemTime.getDate()).padStart(2, '0');
    const hours = String(systemTime.getHours()).padStart(2, '0');
    const minutes = String(systemTime.getMinutes()).padStart(2, '0');
    const seconds = String(systemTime.getSeconds()).padStart(2, '0');
    document.getElementById('currentTime').innerHTML = `<i class="fa-regular fa-clock"></i> ${year}-${month}-${date} ${hours}:${minutes}:${seconds}`;
}

// 2. 로그인 권한 인증 체크
function checkAuth() {
    const token = localStorage.getItem('jwtToken');
    const overlay = document.getElementById('loginOverlay');
    const app = document.getElementById('dashboardApp');
    
    if (token) {
        overlay.classList.remove('active');
        app.style.display = 'flex';
        
        // 유저 정보 표시 바인딩
        document.getElementById('displayName').innerText = localStorage.getItem('userName') + ' 님';
        
        const rawRole = localStorage.getItem('userRole');
        let displayedRole = rawRole ? rawRole.replace('ROLE_', '') : 'USER';
        document.getElementById('displayRole').innerText = displayedRole;
        
        // 권한별 UI 제어
        if (displayedRole === 'USER') {
            document.getElementById('btnOpenRequestModal').style.display = 'inline-flex';
            // 일반 사용자는 배치나 Mock 입력을 숨김
            document.getElementById('btnTriggerRecon').disabled = true;
            document.getElementById('btnTriggerReconError').disabled = true;
            document.getElementById('btnCreateMock').disabled = true;
        } else {
            document.getElementById('btnOpenRequestModal').style.display = 'inline-flex';
            document.getElementById('btnTriggerRecon').disabled = false;
            document.getElementById('btnTriggerReconError').disabled = false;
            document.getElementById('btnCreateMock').disabled = false;
        }

        writeConsole('[SYSTEM] 플랫폼 인증 완료. 데이터를 로드합니다.', 'success');
        refreshAllData();
    } else {
        overlay.classList.add('active');
        app.style.display = 'none';
    }
}

async function handleLogin(e) {
    e.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    writeConsole(`[LOGIN] 사용자 ${username} 인증 요청 중...`, 'system');

    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            throw new Error('인증 실패. 아이디 및 비밀번호를 확인하세요.');
        }

        const data = await response.json();
        localStorage.setItem('jwtToken', data.token);
        localStorage.setItem('userName', data.name);
        localStorage.setItem('userRole', data.role);

        writeConsole('[LOGIN] 토큰 획득 성공.', 'success');
        checkAuth();
    } catch (err) {
        writeConsole(`[ERROR] 로그인 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

function handleLogout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('userName');
    localStorage.removeItem('userRole');
    writeConsole('[SYSTEM] 로그아웃 완료.', 'system');
    checkAuth();
}

// 3. 통합 데이터 리프레시
function refreshAllData() {
    fetchDashboardSummary();
    fetchSettlements();
    fetchBatchLogs();
    fetchReconciliationResults();
}

// 4. 대시보드 요약 정보 동기화
async function fetchDashboardSummary() {
    try {
        const response = await fetch(`${API_BASE}/dashboard/summary`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) throw new Error('대시보드 요약 데이터 동기화 실패');

        const summary = await response.json();
        
        // 카드 엘리먼트 바인딩
        document.getElementById('settlementTotal').innerText = `${summary.totalRequests} 건 / 대기 ${summary.pendingRequests} 건`;
        document.getElementById('settlementApproved').innerText = `${summary.approvedRequests} 건 / 반려 ${summary.rejectedRequests} 건`;
        document.getElementById('reconcileMatched').innerText = `${summary.matchedCount} 건`;
        document.getElementById('reconcileMismatch').innerText = `${summary.retryRequiredCount} 건 (불일치 ${summary.mismatchedCount}, 누락 ${summary.missingExternalCount})`;

        // 배치 간략 정보 바인딩
        document.getElementById('txtLastBatchStatus').innerText = summary.lastBatchStatus;
        if (summary.lastBatchStatus === 'FAILED') {
            document.getElementById('txtLastBatchStatus').className = 'text-danger font-weight-bold';
        } else if (summary.lastBatchStatus === 'SUCCESS') {
            document.getElementById('txtLastBatchStatus').className = 'text-success font-weight-bold';
        } else {
            document.getElementById('txtLastBatchStatus').className = 'text-warning';
        }
        document.getElementById('txtLastBatchError').innerText = summary.lastBatchErrorMessage;

    } catch (err) {
        writeConsole(`[ERROR] 대시보드 요약 조회 실패: ${err.message}`, 'error');
    }
}

// 5. 정산 요청 목록 및 드롭다운 연동
async function fetchSettlements() {
    try {
        const response = await fetch(`${API_BASE}/settlements`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) throw new Error('정산 목록 동기화 실패');

        const list = await response.json();
        renderSettlementsTable(list);
        updateMockDropdown(list);
    } catch (err) {
        writeConsole(`[ERROR] 정산 목록 조회 실패: ${err.message}`, 'error');
    }
}

function renderSettlementsTable(list) {
    const tbody = document.getElementById('settlementTableBody');
    tbody.innerHTML = '';

    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center">등록된 정산 요청이 없습니다.</td></tr>';
        return;
    }

    list.forEach(item => {
        let statusBadge = '';
        if (item.status === 'REQUESTED') statusBadge = '<span class="badge badge-warning">요청됨</span>';
        else if (item.status === 'ASSIGNED') statusBadge = '<span class="badge badge-warning" style="background:rgba(59,130,246,0.15); color:var(--primary);">배정완료</span>';
        else if (item.status === 'APPROVED') statusBadge = '<span class="badge badge-success">승인완료</span>';
        else if (item.status === 'REJECTED') statusBadge = '<span class="badge badge-danger">반려됨</span>';

        let reconBadge = '';
        if (item.reconciliationStatus === 'MATCHED') reconBadge = '<span class="badge badge-success"><i class="fa-solid fa-circle-check"></i> 일치</span>';
        else if (item.reconciliationStatus === 'MISMATCHED') reconBadge = '<span class="badge badge-danger" style="animation: heartbeat 1.5s infinite;"><i class="fa-solid fa-triangle-exclamation"></i> 불일치</span>';
        else if (item.reconciliationStatus === 'MISSING_EXTERNAL') reconBadge = '<span class="badge badge-danger"><i class="fa-solid fa-circle-minus"></i> 외부실적누락</span>';
        else if (item.reconciliationStatus === 'INVALID_STATUS') reconBadge = '<span class="badge badge-danger"><i class="fa-solid fa-ban"></i> 상태오류</span>';
        else reconBadge = '<span class="badge badge-warning" style="background:rgba(255,255,255,0.05); color:var(--text-secondary);">미검증</span>';

        let fileLink = '-';
        if (item.originalFileName !== '-') {
            fileLink = `<a href="#" onclick="event.stopPropagation(); downloadFile(${item.id}, '${item.originalFileName}')" class="text-success" style="text-decoration:none;"><i class="fa-solid fa-file-pdf"></i> ${item.originalFileName}</a>`;
        }

        const tr = document.createElement('tr');
        tr.style.cursor = 'pointer';
        tr.addEventListener('click', () => {
            openDetailModal(item.id);
        });

        tr.innerHTML = `
            <td>${item.id}</td>
            <td><strong>${item.title}</strong></td>
            <td>${item.amount.toLocaleString()}원</td>
            <td>${item.requesterName}</td>
            <td>${item.assigneeName}</td>
            <td>${statusBadge}</td>
            <td>${fileLink}</td>
            <td>${reconBadge}</td>
            <td><button class="btn btn-mini btn-primary" onclick="event.stopPropagation(); openDetailModal(${item.id})"><i class="fa-solid fa-magnifying-glass"></i> 상세</button></td>
        `;
        tbody.appendChild(tr);
    });
}

// Mock 생성 드롭다운 갱신
function updateMockDropdown(list) {
    const select = document.getElementById('selectMockTargetRequest');
    select.innerHTML = '';

    const scenario = document.getElementById('selectMockScenario').value;
    
    // 시나리오별로 필터하여 드롭다운 구성
    let targets = [];
    if (scenario === 'matched' || scenario === 'mismatched') {
        // APPROVED인 건들
        targets = list.filter(item => item.status === 'APPROVED');
    } else if (scenario === 'invalid') {
        // APPROVED가 아닌 건들
        targets = list.filter(item => item.status !== 'APPROVED');
    }

    if (targets.length === 0) {
        select.innerHTML = '<option value="">선택 가능한 대상 없음</option>';
        return;
    }

    targets.forEach(item => {
        const option = document.createElement('option');
        option.value = item.id;
        option.dataset.amount = item.amount;
        option.innerText = `[ID: ${item.id}] ${item.title} (${item.amount.toLocaleString()}원) - 상태: ${item.status}`;
        select.appendChild(option);
    });
}

function handleScenarioChange() {
    const scenario = document.getElementById('selectMockScenario').value;
    const group = document.getElementById('mockRequestSelectGroup');
    
    if (scenario === 'unknown') {
        group.style.display = 'none';
    } else {
        group.style.display = 'block';
        fetchSettlements(); // 드롭다운 재구성
    }
}

// 6. 외부 Mock 실적 데이터 생성
async function handleCreateMock() {
    const scenario = document.getElementById('selectMockScenario').value;
    const selectTarget = document.getElementById('selectMockTargetRequest');
    const token = localStorage.getItem('jwtToken');

    let settlementRequestId = null;
    let externalAmount = 0;
    let externalStatus = "APPROVED";
    
    const randomTxId = "TX_MOCK_" + Date.now();

    if (scenario !== 'unknown') {
        if (!selectTarget.value) {
            alert('대상 정산 요청 건을 선택해 주십시오.');
            return;
        }
        settlementRequestId = parseInt(selectTarget.value);
        const selectedOption = selectTarget.options[selectTarget.selectedIndex];
        const baseAmount = parseInt(selectedOption.dataset.amount);

        if (scenario === 'matched' || scenario === 'invalid') {
            externalAmount = baseAmount;
        } else if (scenario === 'mismatched') {
            externalAmount = baseAmount - 100000; // 금액 불일치 (10만원 오차)
        }
    } else {
        // UNKNOWN_EXTERNAL: 내부 정요 ID 없음, 랜덤 금액
        externalAmount = 800000; 
    }

    writeConsole(`[MOCK] 외부 Mock 연계 실적 강제 주입 시도: TxId=${randomTxId}`, 'system');

    try {
        const response = await fetch(`${API_BASE}/external-results/mock`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({
                externalTransactionId: randomTxId,
                settlementRequestId,
                externalAmount,
                externalStatus
            })
        });

        if (!response.ok) throw new Error('Mock 데이터 주입에 실패했습니다.');

        writeConsole(`[MOCK] Mock 데이터 수신 완료. (외부 거래 ID: ${randomTxId}, 금액: ${externalAmount.toLocaleString()}원)`, 'success');
        refreshAllData();
    } catch (err) {
        writeConsole(`[ERROR] Mock 주입 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 7. 정합성 검증 배치 가동
async function triggerVerification(mockFailure = false) {
    const mode = mockFailure ? "오류 강제유입" : "정상 대조";
    writeConsole(`[BATCH] 정합성 대조 검증 배치 기동 요청 (${mode})`, 'system');

    try {
        const response = await fetch(`${API_BASE}/batches/reconciliation/run?mockFailure=${mockFailure}`, {
            method: 'POST',
            headers: getHeaders()
        });

        if (!response.ok) {
            const errorMsg = await response.text();
            throw new Error(errorMsg);
        }

        writeConsole('[BATCH] 배치 기동 전송 성공. 작업을 수행합니다.', 'success');
        
        // 배치 처리가 끝난 후 리프레시하기 위해 약간의 딜레이 부여
        setTimeout(() => {
            refreshAllData();
            writeConsole('[BATCH] 화면 동기화 완료.', 'success');
        }, 1500);

    } catch (err) {
        writeConsole(`[ERROR] 배치 수행 실패: ${err.message}`, 'error');
        alert("배치 수행 실패: " + err.message);
        refreshAllData();
    }
}

// 8. 실패 배치 재처리 (ADMIN용)
async function handleRetry(logId) {
    const userRole = localStorage.getItem('userRole');
    if (userRole !== 'ROLE_ADMIN') {
        alert('실패 건 재처리는 ADMIN 권한을 보유한 관리자만 가능합니다.');
        return;
    }

    writeConsole(`[BATCH] 실패 배치 재기동 요청 (로그 ID: ${logId})`, 'system');

    try {
        const response = await fetch(`${API_BASE}/batches/reconciliation/retry`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ logId })
        });

        if (!response.ok) {
            throw new Error(await response.text());
        }

        writeConsole(`[BATCH] 재처리 실행 신호 송신 성공.`, 'success');
        setTimeout(() => {
            refreshAllData();
        }, 1500);
    } catch (err) {
        writeConsole(`[ERROR] 재처리 실행 에러: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 9. 배치 상세 로그 조회
async function fetchBatchLogs() {
    try {
        const response = await fetch(`${API_BASE}/batches/logs`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) throw new Error('배치 로그 조회 실패');

        const logs = await response.json();
        renderBatchLogs(logs);
    } catch (err) {
        writeConsole(`[ERROR] 배치 로그 조회 실패: ${err.message}`, 'error');
    }
}

function renderBatchLogs(logs) {
    const tbody = document.getElementById('batchLogTableBody');
    tbody.innerHTML = '';

    if (logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">배치 실행 내역이 존재하지 않습니다.</td></tr>';
        return;
    }

    logs.forEach(logItem => {
        let statusBadge = '';
        if (logItem.status === 'SUCCESS') statusBadge = '<span class="badge badge-success">성공</span>';
        else if (logItem.status === 'FAILED') statusBadge = '<span class="badge badge-danger">실패</span>';
        else statusBadge = '<span class="badge badge-warning"><i class="fa-solid fa-spinner fa-spin"></i> 진행중</span>';

        const startTime = new Date(logItem.startedAt).toLocaleString('ko-KR');
        
        let actionBtn = '-';
        if (logItem.status === 'FAILED') {
            const userRole = localStorage.getItem('userRole');
            if (userRole === 'ROLE_ADMIN') {
                actionBtn = `<button class="btn btn-mini btn-primary" onclick="handleRetry(${logItem.id})"><i class="fa-solid fa-rotate-right"></i> 재처리</button>`;
            } else {
                actionBtn = `<span class="text-muted" style="font-size:0.75rem;">권한없음</span>`;
            }
        }

        let retryCountText = logItem.retryCount > 0 ? `<strong class="text-warning">${logItem.retryCount}회</strong>` : '0회';
        let retriedBy = logItem.isRetried ? `<span class="text-muted">${logItem.retriedBy}</span>` : '-';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${logItem.id}</td>
            <td><strong>${logItem.jobName}</strong></td>
            <td>${startTime}</td>
            <td>${statusBadge}</td>
            <td>${logItem.successCount}건 / ${logItem.failCount}건</td>
            <td>${retryCountText} (${retriedBy})</td>
            <td>${actionBtn}</td>
        `;
        tbody.appendChild(tr);
    });
}

// 10. 정합성 검증 결과 내역 조회
async function fetchReconciliationResults() {
    try {
        const response = await fetch(`${API_BASE}/reconciliation-results`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) throw new Error('정합성 결과 조회 실패');

        const results = await response.json();
        renderReconciliationResults(results);
    } catch (err) {
        writeConsole(`[ERROR] 정합성 결과 조회 실패: ${err.message}`, 'error');
    }
}

function renderReconciliationResults(results) {
    const tbody = document.getElementById('reconResultTableBody');
    tbody.innerHTML = '';

    if (results.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">정합성 검증 대조 내역이 없습니다.</td></tr>';
        return;
    }

    results.forEach(item => {
        let typeBadge = '';
        if (item.resultType === 'MATCHED') typeBadge = '<span class="badge badge-success">MATCHED</span>';
        else if (item.resultType === 'MISMATCHED') typeBadge = '<span class="badge badge-danger">MISMATCHED</span>';
        else if (item.resultType === 'MISSING_EXTERNAL') typeBadge = '<span class="badge badge-danger">MISSING_EXTERNAL</span>';
        else if (item.resultType === 'UNKNOWN_EXTERNAL') typeBadge = '<span class="badge badge-danger" style="background:rgba(239, 68, 68, 0.2); color:white;">UNKNOWN_EXTERNAL</span>';
        else if (item.resultType === 'INVALID_STATUS') typeBadge = '<span class="badge badge-warning">INVALID_STATUS</span>';

        const reqIdText = item.settlementRequestId ? item.settlementRequestId : '<span class="text-muted">N/A</span>';
        const internalAmt = item.internalAmount ? `${item.internalAmount.toLocaleString()}원` : '-';
        const externalAmt = item.externalAmount ? `${item.externalAmount.toLocaleString()}원` : '-';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${item.id}</td>
            <td>${reqIdText}</td>
            <td>${typeBadge}</td>
            <td>${internalAmt}</td>
            <td>${externalAmt}</td>
            <td><span style="font-size:0.75rem;" class="text-muted">${item.reason}</span></td>
        `;
        tbody.appendChild(tr);
    });
}

// 11. 정산 요청 신규 등록
async function handleCreateSettlement(e) {
    e.preventDefault();
    const title = document.getElementById('reqTitle').value;
    const amount = parseInt(document.getElementById('reqAmount').value);
    const attachmentIdVal = document.getElementById('uploadedAttachmentId').value;
    const attachmentId = attachmentIdVal ? parseInt(attachmentIdVal) : null;

    writeConsole(`[PROCESS] 신규 정산 요청 등록: ${title}`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ title, amount, attachmentId })
        });

        if (!response.ok) throw new Error('정산 요청 등록에 실패했습니다.');

        writeConsole('[PROCESS] 정산 요청 등록 완료.', 'success');
        toggleRequestModal(false);
        document.getElementById('requestForm').reset();
        document.getElementById('modalFileName').innerText = '선택된 파일 없음';
        document.getElementById('uploadedAttachmentId').value = '';
        
        refreshAllData();
    } catch (err) {
        writeConsole(`[ERROR] 등록 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 12. 상세 조회 및 모달 작업
let currentDetailId = null;

async function openDetailModal(id) {
    currentDetailId = id;
    writeConsole(`[PROCESS] 정산 ID: ${id} 상세 내역 동기화 요청`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements/${id}`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) throw new Error('상세 정보를 가져올 수 없습니다.');

        const item = await response.json();
        
        // 상세 데이터 바인딩
        document.getElementById('detailId').innerText = item.id;
        document.getElementById('detailTitle').innerText = item.title;
        document.getElementById('detailAmount').innerText = `${item.amount.toLocaleString()} 원`;
        document.getElementById('detailRequester').innerText = item.requesterName;
        document.getElementById('detailAssignee').innerText = item.assigneeName;
        document.getElementById('detailUpdatedAt').innerText = new Date(item.updatedAt).toLocaleString('ko-KR');
        document.getElementById('detailExtAmount').innerText = item.externalAmount ? `${item.externalAmount.toLocaleString()} 원` : '-';

        // 상태 배지 표시
        let statusBadge = '';
        if (item.status === 'REQUESTED') statusBadge = '<span class="badge badge-warning">요청됨</span>';
        else if (item.status === 'ASSIGNED') statusBadge = '<span class="badge badge-warning" style="background:rgba(59,130,246,0.15); color:var(--primary);">배정완료</span>';
        else if (item.status === 'APPROVED') statusBadge = '<span class="badge badge-success">승인완료</span>';
        else if (item.status === 'REJECTED') statusBadge = '<span class="badge badge-danger">반려됨</span>';
        document.getElementById('detailStatus').innerHTML = statusBadge;

        let reconBadge = '';
        if (item.reconciliationStatus === 'MATCHED') reconBadge = '<span class="badge badge-success">일치 (MATCHED)</span>';
        else if (item.reconciliationStatus === 'MISMATCHED') reconBadge = '<span class="badge badge-danger">불일치 (MISMATCHED)</span>';
        else if (item.reconciliationStatus === 'MISSING_EXTERNAL') reconBadge = '<span class="badge badge-danger">외부실적누락 (MISSING_EXTERNAL)</span>';
        else if (item.reconciliationStatus === 'INVALID_STATUS') reconBadge = '<span class="badge badge-danger">상태오류 (INVALID_STATUS)</span>';
        else reconBadge = '<span class="badge badge-warning" style="background:rgba(255,255,255,0.05); color:var(--text-secondary);">미검증</span>';
        document.getElementById('detailRecon').innerHTML = reconBadge;

        // 첨부파일 바인딩
        if (item.originalFileName && item.originalFileName !== '-') {
            document.getElementById('detailFile').innerHTML = `<a href="#" onclick="downloadFile(${item.id}, '${item.originalFileName}')" class="text-success" style="text-decoration:none;"><i class="fa-solid fa-file-pdf"></i> ${item.originalFileName} (다운로드)</a>`;
        } else {
            document.getElementById('detailFile').innerText = '첨부된 서류 없음';
        }

        // 권한에 따른 액션 패널 노출 조절
        const userRole = localStorage.getItem('userRole');
        const hasAdminOrOperator = userRole === 'ROLE_ADMIN' || userRole === 'ROLE_OPERATOR';

        if (hasAdminOrOperator) {
            document.getElementById('detailActions').style.display = 'flex';
            document.getElementById('detailUploadArea').style.display = 'none';

            // 상태에 따른 승인/반려/배정 버튼 활성화
            if (item.status === 'REQUESTED') {
                document.getElementById('assignArea').style.display = 'flex';
                document.getElementById('btnApprove').style.display = 'none';
                document.getElementById('btnReject').style.display = 'none';
            } else if (item.status === 'ASSIGNED') {
                document.getElementById('assignArea').style.display = 'none';
                document.getElementById('btnApprove').style.display = 'inline-flex';
                document.getElementById('btnReject').style.display = 'inline-flex';
            } else {
                // 이미 승인 또는 반려된 상태
                document.getElementById('detailActions').style.display = 'none';
            }
        } else {
            // 일반 사용자는 승인/반려/배정 불가
            document.getElementById('detailActions').style.display = 'none';
            
            // 본인 요청이며, 아직 첨부파일이 없는 경우 업로드 활성화
            if (item.originalFileName === '-' || !item.originalFileName) {
                document.getElementById('detailUploadArea').style.display = 'block';
            } else {
                document.getElementById('detailUploadArea').style.display = 'none';
            }
        }

        toggleDetailModal(true);

    } catch (err) {
        writeConsole(`[ERROR] 상세 정보 획득 실패: ${err.message}`, 'error');
    }
}

// 담당자 배정 액션
async function handleAssignOperator() {
    const operator = document.getElementById('selectOperator').value;
    writeConsole(`[PROCESS] 정산 ID: ${currentDetailId} -> 담당자 배정 처리: ${operator}`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements/${currentDetailId}/assign`, {
            method: 'PATCH',
            headers: getHeaders(),
            body: JSON.stringify({ assigneeUsername: operator })
        });

        if (!response.ok) throw new Error('담당자 배정에 실패했습니다.');

        writeConsole('[PROCESS] 담당자 지정 완료.', 'success');
        toggleDetailModal(false);
        refreshAllData();
    } catch (err) {
        writeConsole(`[ERROR] 배정 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 승인/반려 액션
async function processApproval(action) {
    const actionK = action === 'approve' ? '승인' : '반려';
    writeConsole(`[PROCESS] 정산 ID: ${currentDetailId} -> ${actionK} 처리 시도`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements/${currentDetailId}/${action}`, {
            method: 'PATCH',
            headers: getHeaders()
        });

        if (!response.ok) throw new Error(`${actionK} 처리에 실패했습니다.`);

        writeConsole(`[PROCESS] ${actionK} 완료.`, 'success');
        toggleDetailModal(false);
        refreshAllData();
    } catch (err) {
        writeConsole(`[ERROR] 처리 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 상세창 내 파일 즉시 업로드 (USER용)
async function handleDetailUpload() {
    const fileInput = document.getElementById('detailFileInput');
    if (fileInput.files.length === 0) {
        alert('업로드할 파일을 선택하세요.');
        return;
    }

    const file = fileInput.files[0];
    writeConsole(`[FILE] 정산 ID: ${currentDetailId} 증빙자료 업로드 시도: ${file.name}`, 'system');

    const formData = new FormData();
    formData.append('file', file);
    const token = localStorage.getItem('jwtToken');

    try {
        const response = await fetch(`${API_BASE}/settlements/${currentDetailId}/attachments`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData
        });

        if (!response.ok) throw new Error('서버 파일 수신 오류');

        writeConsole('[FILE] 증빙 파일 수신 및 연계 완료.', 'success');
        toggleDetailModal(false);
        refreshAllData();
    } catch (err) {
        writeConsole(`[ERROR] 파일 업로드 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 13. S3 파일 업로드 및 다운로드 연동 (등록 창)
function setupFileUpload() {
    const modalUploadZone = document.getElementById('modalUploadZone');
    const modalFileInput = document.getElementById('modalFileInput');
    const modalFileName = document.getElementById('modalFileName');

    modalUploadZone.addEventListener('click', () => modalFileInput.click());
    modalUploadZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        modalUploadZone.style.borderColor = 'var(--primary)';
    });
    modalUploadZone.addEventListener('dragleave', () => {
        modalUploadZone.style.borderColor = 'rgba(255, 255, 255, 0.15)';
    });
    modalUploadZone.addEventListener('drop', (e) => {
        e.preventDefault();
        modalUploadZone.style.borderColor = 'rgba(255, 255, 255, 0.15)';
        if (e.dataTransfer.files.length > 0) {
            uploadFileImmediately(e.dataTransfer.files[0]);
        }
    });
    modalFileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            uploadFileImmediately(e.target.files[0]);
        }
    });

    async function uploadFileImmediately(file) {
        writeConsole(`[FILE] (임시 업로드) 증빙 파일 S3 전송: ${file.name}`, 'system');
        modalFileName.innerText = '업로드 중...';

        const formData = new FormData();
        formData.append('file', file);
        const token = localStorage.getItem('jwtToken');

        try {
            // S3 임시 업로드는 정산 요청 ID 없이 일단 /api/files/upload 형태로 쏠 수 있게 호환 API를 사용하거나,
            // 아니면 그냥 settlements API 생성 때 함께 처리하도록 할 수 있습니다.
            // 여기서는 기존에 fileController에 정의되었던 /api/files/upload API를 사용해 임시 ID를 받아옵니다.
            const response = await fetch(`${API_BASE}/files/upload`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` },
                body: formData
            });

            if (!response.ok) throw new Error('서버 업로드 에러');

            const data = await response.json();
            document.getElementById('uploadedAttachmentId').value = data.id;
            modalFileName.innerText = `S3 임시 백업 완료: ${file.name}`;
            writeConsole(`[FILE] S3 저장 성공. 메타데이터 임시 ID: ${data.id}`, 'success');
        } catch (err) {
            writeConsole(`[ERROR] 임시 파일 저장 실패: ${err.message}`, 'error');
            modalFileName.innerText = '업로드 실패. 다시 시도하세요.';
        }
    }
}

// 파일 다운로드
async function downloadFile(settlementId, fileName) {
    writeConsole(`[FILE] 정산 ID: ${settlementId} 증빙 파일 다운로드 요청: ${fileName}`, 'system');
    
    try {
        const fileToken = localStorage.getItem('jwtToken');
        const downloadUrl = `${API_BASE}/settlements/${settlementId}/attachments/download`;

        const response = await fetch(downloadUrl, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${fileToken}`
            }
        });

        if (!response.ok) throw new Error('파일 다운로드 실패.');

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
        writeConsole(`[FILE] 파일 전송 성공: ${fileName}`, 'success');

    } catch (err) {
        writeConsole(`[ERROR] 다운로드 실패: ${err.message}`, 'error');
        alert("다운로드 실패: " + err.message);
    }
}

// 14. 모달 토글 함수
function toggleRequestModal(show) {
    const modal = document.getElementById('requestModal');
    if (show) modal.classList.add('active');
    else modal.classList.remove('active');
}

function toggleDetailModal(show) {
    const modal = document.getElementById('detailModal');
    if (show) modal.classList.add('active');
    else modal.classList.remove('active');
}

// 15. 콘솔 제어
function writeConsole(message, type = 'system') {
    const consoleScreen = document.getElementById('consoleScreen');
    const line = document.createElement('div');
    line.classList.add('console-line', type);
    
    const timeStr = `[${new Date().toLocaleTimeString('ko-KR')}]`;
    line.innerText = `${timeStr} ${message}`;
    
    consoleScreen.appendChild(line);
    consoleScreen.scrollTop = consoleScreen.scrollHeight;
}

function clearConsole() {
    document.getElementById('consoleScreen').innerHTML = '';
}
