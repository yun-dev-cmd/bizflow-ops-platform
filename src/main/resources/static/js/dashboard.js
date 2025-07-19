// 글로벌 설정 및 상태 정보
const API_BASE = '/api';
let systemTime = new Date('2026-06-30T10:00:00');

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
        fetchDashboardData();
        fetchSettlementData();
    });

    // 배치 기동 제어 이벤트 바인딩
    document.getElementById('btnTriggerMockSuccess').addEventListener('click', () => triggerJob('fileIntegrationJob', false));
    document.getElementById('btnTriggerReconSuccess').addEventListener('click', () => triggerJob('settlementVerificationJob', false));
    document.getElementById('btnTriggerReconFailed').addEventListener('click', () => triggerJob('settlementVerificationJob', true));
    
    // 콘솔 청소
    document.getElementById('btnClearConsole').addEventListener('click', clearConsole);

    // 폼 서브밋 바인딩
    document.getElementById('requestForm').addEventListener('submit', handleCreateSettlement);
    document.getElementById('assignForm').addEventListener('submit', handleAssignOperator);

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
        document.getElementById('displayRole').innerText = localStorage.getItem('userRole').replace('ROLE_', '');
        
        writeConsole('[SYSTEM] 플랫폼 인증 완료. 데이터를 로드합니다.', 'success');
        
        fetchDashboardData();
        fetchSettlementData();
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

// 3. 배치 실행 현황 조회
async function fetchDashboardData() {
    try {
        const response = await fetch(`${API_BASE}/batch/histories`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('배치 실행 이력 동기화 실패');
        }

        const histories = await response.json();
        renderBatchTable(histories);
    } catch (err) {
        writeConsole(`[ERROR] 배치 동기화 실패: ${err.message}`, 'error');
    }
}

function renderBatchTable(histories) {
    let failedCount = 0;
    const tbody = document.getElementById('historyTableBody');
    tbody.innerHTML = '';

    if (histories.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">배치 실행 기록이 없습니다.</td></tr>';
    } else {
        histories.forEach(job => {
            if (job.status === 'FAILED') failedCount++;

            const start = job.startTime ? new Date(job.startTime).toLocaleString('ko-KR') : '-';
            const end = job.endTime ? new Date(job.endTime) : null;
            let duration = '-';
            if (end && job.startTime) {
                const diffMs = end - new Date(job.startTime);
                duration = `${(diffMs / 1000).toFixed(1)}초`;
            }

            let statusBadge = '';
            if (job.status === 'SUCCESS') statusBadge = '<span class="badge badge-success"><i class="fa-solid fa-circle-check"></i> 성공</span>';
            else if (job.status === 'FAILED') statusBadge = '<span class="badge badge-danger"><i class="fa-solid fa-circle-xmark"></i> 실패</span>';
            else statusBadge = '<span class="badge badge-warning"><i class="fa-solid fa-spinner fa-spin"></i> 진행중</span>';

            let actionBtn = '-';
            if (job.status === 'FAILED') {
                actionBtn = `<button class="btn btn-mini btn-primary" onclick="retryJob(${job.id})"><i class="fa-solid fa-rotate-right"></i> 재처리</button>`;
            }

            let retryMark = job.retryCount > 0 ? `<span class="text-warning font-weight-bold">${job.retryCount}회</span>` : '0회';
            let retryHistory = job.isRetried ? `<span class="text-muted" style="font-size:0.75rem;">${job.retriedBy}</span>` : '-';

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${job.id}</td>
                <td><strong>${job.jobName}</strong></td>
                <td>${start}</td>
                <td>${duration}</td>
                <td>${statusBadge}</td>
                <td>${retryMark}</td>
                <td>${retryHistory}</td>
                <td>${actionBtn}</td>
            `;
            tbody.appendChild(tr);
        });
    }

    document.getElementById('batchFailedCount').innerText = failedCount;
}

// 4. 정산 요청 내역 동기화
async function fetchSettlementData() {
    try {
        const response = await fetch(`${API_BASE}/settlements`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('정산 정보 동기화 실패');
        }

        const list = await response.json();
        renderSettlements(list);
    } catch (err) {
        writeConsole(`[ERROR] 정산 정보 조회 실패: ${err.message}`, 'error');
    }
}

function renderSettlements(list) {
    let total = list.length;
    let approved = 0;
    let mismatch = 0;

    const tbody = document.getElementById('settlementTableBody');
    tbody.innerHTML = '';

    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="text-center">정산 요청 데이터가 없습니다.</td></tr>';
    } else {
        list.forEach(item => {
            if (item.status === 'APPROVED') approved++;
            if (item.reconciliationStatus === 'MISMATCHED') mismatch++;

            let statusBadge = '';
            if (item.status === 'REQUESTED') statusBadge = '<span class="badge badge-warning">요청됨</span>';
            else if (item.status === 'ASSIGNED') statusBadge = '<span class="badge badge-success" style="background:rgba(59,130,246,0.15); color:var(--primary);">배정완료</span>';
            else if (item.status === 'APPROVED') statusBadge = '<span class="badge badge-success">승인완료</span>';
            else if (item.status === 'REJECTED') statusBadge = '<span class="badge badge-danger">반려됨</span>';

            let fileLink = '-';
            if (item.originalFileName !== '-') {
                fileLink = `<a href="#" onclick="downloadFile(${item.id}, '${item.originalFileName}')" class="text-success" style="text-decoration:none;"><i class="fa-solid fa-file-pdf"></i> ${item.originalFileName}</a>`;
            }

            let reconBadge = '';
            if (item.reconciliationStatus === 'MATCHED') reconBadge = '<span class="badge badge-success">일치</span>';
            else if (item.reconciliationStatus === 'MISMATCHED') reconBadge = '<span class="badge badge-danger" style="animation: heartbeat 1.5s infinite;">불일치</span>';
            else reconBadge = '<span class="badge badge-warning" style="background:rgba(255,255,255,0.05); color:var(--text-secondary);">미검증</span>';

            let actionHtml = '-';
            const userRole = localStorage.getItem('userRole');
            const hasAuthority = userRole === 'ROLE_ADMIN' || userRole === 'ROLE_OPERATOR';

            if (hasAuthority) {
                if (item.status === 'REQUESTED') {
                    actionHtml = `<button class="btn btn-mini btn-primary" onclick="openAssignModal(${item.id})"><i class="fa-solid fa-user-plus"></i> 배정</button>`;
                } else if (item.status === 'ASSIGNED') {
                    actionHtml = `
                        <div style="display:flex; gap:4px;">
                            <button class="btn btn-mini btn-primary" onclick="processSettlement(${item.id}, 'approve')">승인</button>
                            <button class="btn btn-mini btn-logout" onclick="processSettlement(${item.id}, 'reject')" style="padding:4px 8px;">반려</button>
                        </div>
                    `;
                }
            }

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${item.id}</td>
                <td><strong>${item.title}</strong></td>
                <td>${item.amount.toLocaleString()}원</td>
                <td>${item.requesterName}</td>
                <td>${item.assigneeName}</td>
                <td>${statusBadge}</td>
                <td>${fileLink}</td>
                <td>${reconBadge}</td>
                <td>${item.externalAmount ? item.externalAmount.toLocaleString() + '원' : '-'}</td>
                <td>${actionHtml}</td>
            `;
            tbody.appendChild(tr);
        });
    }

    document.getElementById('settlementTotal').innerText = total;
    document.getElementById('settlementApproved').innerText = approved;
    document.getElementById('reconcileMismatch').innerText = mismatch;
}

// 5. 정산 처리 액션 (배정, 승인, 반려)
function openAssignModal(id) {
    document.getElementById('assignTargetId').value = id;
    toggleAssignModal(true);
}

async function handleAssignOperator(e) {
    e.preventDefault();
    const id = document.getElementById('assignTargetId').value;
    const assigneeUsername = document.getElementById('assigneeUser').value;

    writeConsole(`[PROCESS] ID ${id} 정산 건 담당자(${assigneeUsername}) 배정 처리 중...`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements/${id}/assign`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ assigneeUsername })
        });

        if (!response.ok) {
            throw new Error('담당자 배정에 실패했습니다.');
        }

        writeConsole(`[PROCESS] ID ${id} 정산 건 배정 완료.`, 'success');
        toggleAssignModal(false);
        fetchSettlementData();
    } catch (err) {
        writeConsole(`[ERROR] 배정 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

async function processSettlement(id, action) {
    const actionKorean = action === 'approve' ? '승인' : '반려';
    if (!confirm(`해당 건을 최종 ${actionKorean} 처리하시겠습니까?`)) return;

    writeConsole(`[PROCESS] ID ${id} 정산 건 ${actionKorean} 요청 송신 중...`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements/${id}/${action}`, {
            method: 'POST',
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error(`${actionKorean} 처리에 실패했습니다.`);
        }

        writeConsole(`[PROCESS] ID ${id} 정산 건 최종 ${actionKorean} 완료.`, 'success');
        fetchSettlementData();
    } catch (err) {
        writeConsole(`[ERROR] 처리 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

async function handleCreateSettlement(e) {
    e.preventDefault();
    const title = document.getElementById('reqTitle').value;
    const amount = parseInt(document.getElementById('reqAmount').value);
    const attachmentIdVal = document.getElementById('uploadedAttachmentId').value;
    const attachmentId = attachmentIdVal ? parseInt(attachmentIdVal) : null;

    writeConsole(`[PROCESS] 신규 정산 요청 등록 시작: ${title}`, 'system');

    try {
        const response = await fetch(`${API_BASE}/settlements`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ title, amount, attachmentId })
        });

        if (!response.ok) {
            throw new Error('정산 요청 등록에 실패했습니다.');
        }

        writeConsole('[PROCESS] 정산 요청 등록 완료.', 'success');
        toggleRequestModal(false);
        document.getElementById('requestForm').reset();
        document.getElementById('modalFileName').innerText = '선택된 파일 없음';
        document.getElementById('uploadedAttachmentId').value = '';
        
        fetchSettlementData();
    } catch (err) {
        writeConsole(`[ERROR] 등록 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 6. 배치 수동 기동 제어
async function triggerJob(jobName, mockFailure = false) {
    writeConsole(`[CONTROL] 원격 배치 실행 요청: ${jobName} (테스트 오류주입=${mockFailure})`, 'system');
    
    let url = `${API_BASE}/batch/trigger/${jobName}`;

    try {
        const response = await fetch(url + (mockFailure ? '?mockFailure=true' : ''), {
            method: 'POST',
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error(await response.text());
        }

        writeConsole('[CONTROL] 배치 기동 신호 전송 성공.', 'success');
        setTimeout(() => {
            fetchDashboardData();
            fetchSettlementData();
        }, 1500);
    } catch (err) {
        writeConsole(`[ERROR] 배치 구동 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

async function retryJob(historyId) {
    writeConsole(`[CONTROL] 실패 배치 재기동 처리 시작 (이력 ID: ${historyId})`, 'system');
    try {
        const response = await fetch(`${API_BASE}/batch/retry`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ historyId })
        });

        if (!response.ok) {
            throw new Error(await response.text());
        }

        writeConsole('[CONTROL] 배치 재기동 신호 전송 성공. 화면을 갱신합니다.', 'success');
        setTimeout(() => {
            fetchDashboardData();
            fetchSettlementData();
        }, 1500);
    } catch (err) {
        writeConsole(`[ERROR] 재기동 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 7. S3 파일 업로드 및 다운로드 연동
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
        writeConsole(`[FILE] 증빙 파일 S3 전송 시작: ${file.name}`, 'system');
        modalFileName.innerText = '업로드 중...';

        const formData = new FormData();
        formData.append('file', file);
        const token = localStorage.getItem('jwtToken');

        try {
            const response = await fetch(`${API_BASE}/files/upload`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` },
                body: formData
            });

            if (!response.ok) throw new Error('서버 업로드 응답 에러');

            const data = await response.json();
            document.getElementById('uploadedAttachmentId').value = data.id;
            modalFileName.innerText = `S3 저장 완료: ${file.name}`;
            writeConsole(`[FILE] 증빙 S3 저장 완료. ID: ${data.id}`, 'success');
        } catch (err) {
            writeConsole(`[ERROR] 증빙 업로드 실패: ${err.message}`, 'error');
            modalFileName.innerText = '업로드 실패. 다시 시도하세요.';
        }
    }
}

async function downloadFile(settlementId, fileName) {
    writeConsole(`[FILE] 증빙 자료 수신 중: ${fileName}`, 'system');
    
    try {
        const listResponse = await fetch(`${API_BASE}/settlements`, {
            method: 'GET',
            headers: getHeaders()
        });
        const list = await listResponse.json();
        const item = list.find(s => s.id === settlementId);
        
        if (!item || !item.fileUrl) {
            alert('다운로드할 파일이 없습니다.');
            return;
        }

        const fileToken = localStorage.getItem('jwtToken');
        let downloadUrl = item.fileUrl;

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
        writeConsole(`[FILE] 파일 수신 완료: ${fileName}`, 'success');

    } catch (err) {
        writeConsole(`[ERROR] 다운로드 실패: ${err.message}`, 'error');
        alert(err.message);
    }
}

// 8. 모달창 토글 함수
function toggleRequestModal(show) {
    const modal = document.getElementById('requestModal');
    if (show) modal.classList.add('active');
    else modal.classList.remove('active');
}

function toggleAssignModal(show) {
    const modal = document.getElementById('assignModal');
    if (show) modal.classList.add('active');
    else modal.classList.remove('active');
}

// 9. 콘솔 스크린 로그 함수
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
