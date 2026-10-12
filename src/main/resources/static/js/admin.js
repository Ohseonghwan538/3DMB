const roleOrder = { USER: 0, MODERATOR: 1, ADMIN: 2, SUPER_ADMIN: 3 };
const state = { role: 'USER', userId: '', csrf: '', view: 'dashboard', userPage: 1, userLocked: false, contentPage: 1, contentTab: 'groups' };
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short', timeStyle: 'short'
  }).format(date);
}

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const unit = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / (1024 ** unit)).toFixed(unit ? 1 : 0)} ${units[unit]}`;
}

function toast(message, isError = false) {
  const element = $('#admin-toast');
  element.textContent = message;
  element.classList.toggle('is-error', isError);
  element.classList.add('is-visible');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => element.classList.remove('is-visible'), 2800);
}

async function api(url, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (options.method && !['GET', 'HEAD'].includes(options.method.toUpperCase())) headers.set('X-CSRF-TOKEN', state.csrf);
  const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
  if (!response.ok) {
    let message = `요청에 실패했습니다. (${response.status})`;
    try {
      const result = await response.json();
      message = result.detail || result.message || result.error?.message || message;
    } catch { /* Non-JSON server responses keep the HTTP status message. */ }
    if (response.status === 401) location.href = '/login';
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function confirmAction(title, message, accept = '확인') {
  const dialog = $('#confirm-dialog');
  $('#confirm-title').textContent = title;
  $('#confirm-message').textContent = message;
  $('#confirm-accept').textContent = accept;
  dialog.showModal();
  return new Promise((resolve) => dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), { once: true }));
}

function badge(value, labels = {}) {
  const colors = {
    ACTIVE: 'green', PUBLIC: 'green', DONE: 'green', NORMAL: 'green',
    SUSPENDED: 'orange', IN_PROGRESS: 'orange', HOLD: 'orange',
    WITHDRAWN: 'gray', PRIVATE: 'gray', DELETED: 'red', LOCKED: 'red', MISSING: 'red'
  };
  return `<span class="badge ${colors[value] || 'gray'}">${escapeHtml(labels[value] || value || '—')}</span>`;
}

function setView(view) {
  state.view = view;
  $$('.admin-view').forEach((element) => element.classList.toggle('is-visible', element.id === `view-${view}`));
  $$('.nav-item').forEach((element) => element.classList.toggle('is-active', element.dataset.view === view));
  $('#page-title').textContent = ({
    dashboard: '에러 로그 · 결함 추적',
    users: '회원 · 계정 관리',
    content: '추억 그룹 · 게시물 · 스토리지'
  })[view];
  if (view === 'users') loadUsers().catch((error) => toast(error.message, true));
  if (view === 'content') loadContent().catch((error) => toast(error.message, true));
  if (view === 'dashboard') loadDashboard().catch((error) => toast(error.message, true));
}

async function loadDashboard() {
  const days = Number($('#error-days').value);
  const [summary, errors, defects] = await Promise.all([
    api(`/api/admin/dashboard/summary?days=${days}`),
    api(`/api/admin/dashboard/errors?days=${days}`),
    api('/api/admin/dashboard/defects')
  ]);
  const summaryCards = [
    ['24시간 에러 발생', summary.errors24h ?? 0, '조회 기간 전체의 에러 코드 집계'],
    ['미해결 결함', summary.openDefects ?? 0, '보류 또는 진행 중'],
    ['조치 중 / 완료', `${summary.inProgress ?? 0} / ${summary.completed ?? 0}`, '결함대장 기준'],
    ['최다 발생 도메인', summary.topDomain || '-', `${days === 1 ? '최근 24시간' : `최근 ${days}일`} 기준`]
  ];
  $('#dashboard-summary').innerHTML = summaryCards.map(([label, value, hint]) =>
    `<article class="summary-card"><div class="label">${escapeHtml(label)}</div><div class="value">${escapeHtml(value)}</div><div class="hint">${escapeHtml(hint)}</div></article>`
  ).join('');
  renderErrorChart(errors);
  renderDefectStats(defects);
  renderDefects(defects);
}

function renderErrorChart(errors) {
  const grouped = new Map();
  errors.forEach((entry) => {
    if (!grouped.has(entry.domain)) grouped.set(entry.domain, []);
    grouped.get(entry.domain).push(entry);
  });
  const max = Math.max(1, ...errors.map((entry) => Number(entry.count) || 0));
  if (!errors.length) {
    $('#error-chart').innerHTML = '<div class="empty-row">선택한 기간에 기록된 에러가 없습니다.</div>';
    return;
  }
  $('#error-chart').innerHTML = [...grouped.entries()].map(([domain, rows]) => {
    const total = rows.reduce((sum, row) => sum + Number(row.count), 0);
    const bars = rows.map((row) => `<div class="error-bar-row">
      <button class="error-code" data-error-code="${escapeHtml(row.code)}">${escapeHtml(row.code)}</button>
      <div class="bar-track"><div class="bar-fill" style="width:${Math.max(2, Number(row.count) / max * 100)}%"></div></div>
      <span class="error-count">${Number(row.count)}</span>
    </div>`).join('');
    return `<section class="error-domain"><div class="domain-title">${escapeHtml(domain)} · ${total}건</div>${bars}</section>`;
  }).join('');
  $$('.error-code').forEach((button) => button.addEventListener('click', async () => {
    try {
      const detail = await api(`/api/admin/dashboard/errors/${encodeURIComponent(button.dataset.errorCode)}?days=${$('#error-days').value}`);
      $('#confirm-title').textContent = `${button.dataset.errorCode} 에러 로그`;
      $('#confirm-message').textContent = detail.length
        ? detail.map((item) => `${formatDate(item.createdAt)} · ${item.domain}\n${item.message}`).join('\n\n')
        : '선택한 기간에 발생한 로그가 없습니다.';
      $('#confirm-accept').textContent = '닫기';
      $('#confirm-accept').value = 'cancel';
      $('#confirm-dialog').showModal();
    } catch (error) { toast(error.message, true); }
  }));
}

function renderDefectStats(defects) {
  const counts = { HOLD: 0, IN_PROGRESS: 0, DONE: 0 };
  defects.forEach((defect) => { if (counts[defect.status] !== undefined) counts[defect.status]++; });
  const total = defects.length || 1;
  const colors = { HOLD: '#e5a53c', IN_PROGRESS: '#6084d3', DONE: '#51a77c' };
  $('#defect-status-chart').innerHTML = `<div class="stacked-bar">${
    Object.entries(counts).map(([status, count]) =>
      `<span title="${status}: ${count}" style="width:${count / total * 100}%;background:${colors[status]}"></span>`).join('')
  }</div>`;
  $('#defect-legend').innerHTML = [
    ['HOLD', '보류'], ['IN_PROGRESS', '진행 중'], ['DONE', '완료']
  ].map(([key, label]) => `<span><i class="legend-dot" style="background:${colors[key]}"></i>${label} ${counts[key]}</span>`).join('');
  $('#error-defect-links').innerHTML = '<strong>에러 코드 ↔ 결함</strong><span>AUTH_002 → D-001 · GROUP_001 → D-003</span><span>UPLOAD_001 → D-004 · UPLOAD_003~005 → D-005, D-006</span>';
}

function renderDefects(defects) {
  const canUpdate = roleOrder[state.role] >= roleOrder.ADMIN;
  const labels = { HOLD: '보류', IN_PROGRESS: '진행 중', DONE: '완료' };
  $('#defects-table').innerHTML = defects.length ? defects.map((defect) => `<tr>
    <td><strong>${escapeHtml(defect.id)}</strong></td><td>${escapeHtml(defect.element)}</td>
    <td>${escapeHtml(defect.symptom)}</td><td>${escapeHtml(defect.relatedErrors)}</td>
    <td>${escapeHtml(defect.resolution)}</td><td>${canUpdate
      ? `<select class="defect-status" data-defect="${escapeHtml(defect.id)}" aria-label="${escapeHtml(defect.id)} 상태">${Object.entries(labels).map(([value, label]) => `<option value="${value}" ${defect.status === value ? 'selected' : ''}>${label}</option>`).join('')}</select>`
      : badge(defect.status, labels)}</td>
  </tr>`).join('') : '<tr><td class="empty-row" colspan="6">결함 데이터가 없습니다.</td></tr>';
  $$('.defect-status').forEach((select) => select.addEventListener('change', async () => {
    const before = defects.find((defect) => defect.id === select.dataset.defect)?.status;
    if (!await confirmAction('결함 상태 변경', `${select.dataset.defect} 상태를 변경할까요?`, '변경')) {
      select.value = before;
      return;
    }
    try {
      await api(`/api/admin/dashboard/defects/${encodeURIComponent(select.dataset.defect)}`, {
        method: 'PATCH', body: JSON.stringify({ status: select.value })
      });
      toast('결함 상태를 변경했습니다.');
      loadDashboard();
    } catch (error) { select.value = before; toast(error.message, true); }
  }));
}

let users = [];
function userFilterQuery() {
  const form = new FormData($('#user-filter'));
  const params = new URLSearchParams({ page: String(state.userPage) });
  if (state.userLocked) params.set('locked', 'true');
  for (const key of ['q', 'status', 'role']) if (form.get(key)) params.set(key, form.get(key));
  return params;
}

async function loadUsers() {
  const [summary, rows] = await Promise.all([
    api('/api/admin/users/summary'),
    api(`/api/admin/users?${userFilterQuery()}`)
  ]);
  users = rows;
  $('#user-summary').innerHTML = [
    ['전체 회원', summary.total, '', false], ['정상', summary.active, 'ACTIVE', false],
    ['일시정지', summary.suspended, 'SUSPENDED', false], ['탈퇴', summary.withdrawn, 'WITHDRAWN', false],
    ['계정 잠금', summary.locked, '', true]
  ].map(([label, value, status, locked]) => `<button class="summary-card" type="button" data-status="${status}" data-locked="${locked}"><div class="label">${label}</div><div class="value">${Number(value) || 0}</div></button>`).join('');
  $$('#user-summary .summary-card').forEach((card) => card.addEventListener('click', () => {
    $('#user-filter [name="status"]').value = card.dataset.status;
    $('#user-filter [name="role"]').value = '';
    $('#user-filter [name="q"]').value = '';
    state.userLocked = card.dataset.locked === 'true';
    state.userPage = 1;
    loadUsers().catch((error) => toast(error.message, true));
  }));
  const statusLabels = { ACTIVE: '정상', SUSPENDED: '일시정지', WITHDRAWN: '탈퇴' };
  $('#users-table').innerHTML = rows.length ? rows.map((user) => `<tr>
    <td><button class="error-code select-user" data-user="${escapeHtml(user.userId)}"><strong>${escapeHtml(user.userId)}</strong></button></td>
    <td>${escapeHtml(user.name)}</td><td>${badge(user.status, statusLabels)}</td><td>${badge(user.role)}</td>
    <td>${formatDate(user.lastLoginAt)}</td><td>${user.lockedAt ? badge('LOCKED', { LOCKED: `${user.failedLoginCount} · 잠금` }) : Number(user.failedLoginCount || 0)}</td>
    <td><button class="small-button select-user" data-user="${escapeHtml(user.userId)}">상세</button>
      ${user.lockedAt ? `<button class="small-button unlock-user" data-user="${escapeHtml(user.userId)}">잠금 해제</button>` : ''}</td>
  </tr>`).join('') : '<tr><td class="empty-row" colspan="7">조건에 맞는 회원이 없습니다.</td></tr>';
  $('#users-page').textContent = String(state.userPage);
  $('#users-prev').disabled = state.userPage <= 1;
  $('#users-next').disabled = rows.length < 20;
  $$('.select-user').forEach((button) => button.addEventListener('click', () => showUser(button.dataset.user)));
  $$('.unlock-user').forEach((button) => button.addEventListener('click', () => unlockUser(button.dataset.user)));
}

async function showUser(userId) {
  const user = await api(`/api/admin/users/${encodeURIComponent(userId)}`);
  const isSelf = user.userId === state.userId;
  const canAssignRole = state.role === 'SUPER_ADMIN' && !isSelf;
  const statusLabels = { ACTIVE: '정상', SUSPENDED: '일시정지', WITHDRAWN: '탈퇴' };
  $('#user-detail').innerHTML = `<h3>${escapeHtml(user.name)}</h3><div class="detail-id">${escapeHtml(user.userId)} · ${escapeHtml(user.role)}</div>
    ${user.lockedAt ? `<div class="lock-notice"><strong>AUTH_002 · 계정 잠금</strong><br>연속 로그인 ${Number(user.failedLoginCount)}회 실패<br>잠금 시각: ${formatDate(user.lockedAt)}</div>` : ''}
    <label class="detail-field">계정 상태<select id="detail-status" ${isSelf ? 'disabled' : ''}>${Object.entries(statusLabels).map(([value, label]) => `<option value="${value}" ${user.status === value ? 'selected' : ''}>${label}</option>`).join('')}</select></label>
    <label class="detail-field">관리자 권한<select id="detail-role" ${canAssignRole ? '' : 'disabled'}>${['USER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN'].map((role) => `<option ${user.role === role ? 'selected' : ''}>${role}</option>`).join('')}</select></label>
    ${!canAssignRole ? '<p class="hint">권한 변경은 SUPER_ADMIN만 할 수 있습니다.</p>' : ''}
    ${isSelf ? '<p class="hint">본인의 상태와 권한은 변경할 수 없습니다.</p>' : ''}
    <div class="detail-actions">
      ${user.lockedAt ? `<button class="secondary-button" id="detail-unlock">계정 잠금 해제</button>` : ''}
      <button class="primary-button" id="save-user">변경 사항 저장</button>
    </div>`;
  $('#detail-unlock')?.addEventListener('click', () => unlockUser(user.userId));
  $('#save-user').addEventListener('click', async () => {
    const status = $('#detail-status').value;
    const role = $('#detail-role').value;
    if (status === user.status && role === user.role) return toast('변경된 항목이 없습니다.');
    if (!await confirmAction('회원 정보 변경', `${user.userId} 회원의 상태 또는 권한을 변경할까요?`, '변경')) return;
    try {
      await api(`/api/admin/users/${encodeURIComponent(user.userId)}`, {
        method: 'PATCH', body: JSON.stringify({ status, role })
      });
      toast('회원 정보를 저장했습니다.');
      await loadUsers();
      await showUser(user.userId);
    } catch (error) { toast(error.message, true); }
  });
}

async function unlockUser(userId) {
  if (!await confirmAction('계정 잠금 해제', `${userId}의 로그인 실패 횟수를 0으로 초기화할까요?`, '잠금 해제')) return;
  try {
    await api(`/api/admin/users/${encodeURIComponent(userId)}/unlock`, { method: 'POST' });
    toast('계정 잠금을 해제했습니다.');
    await loadUsers();
    await showUser(userId);
  } catch (error) { toast(error.message, true); }
}

function contentQuery() {
  const params = new URLSearchParams({ tab: state.contentTab, page: String(state.contentPage) });
  const query = new FormData($('#content-filter')).get('q');
  if (query) params.set('q', query);
  return params;
}

async function loadContent() {
  const [summary, rows, storage] = await Promise.all([
    api('/api/admin/content/summary'),
    api(`/api/admin/content?${contentQuery()}`),
    api('/api/admin/storage')
  ]);
  const originalBytes = Number(storage.originalBytes || 0);
  const compressedBytes = Number(storage.compressedBytes || 0);
  const capacity = 1024 ** 3;
  const originalPercent = Math.min(100, originalBytes / capacity * 100);
  const compressedPercent = Math.min(100, compressedBytes / capacity * 100);
  $('#storage-summary').innerHTML = [
    ['storage/original', formatBytes(originalBytes), `${originalPercent.toFixed(1)}% 사용`, originalPercent >= 80],
    ['storage/compressed', formatBytes(compressedBytes), `${compressedPercent.toFixed(1)}% 사용`, compressedPercent >= 80],
    ['전체 추억 그룹', Number(summary.groupCount || 0), '삭제되지 않은 그룹'],
    ['신고 · 검토 대기', Number(summary.reviewCount || 0), '비공개 검토 대상']
  ].map(([label, value, hint, warning]) => `<article class="summary-card ${warning ? 'warning' : ''}"><div class="label">${escapeHtml(label)}</div><div class="value">${escapeHtml(value)}</div><div class="hint">${escapeHtml(hint || '')}</div></article>`).join('');
  renderContentRows(rows);
  renderStorage(storage);
}

function renderContentRows(rows) {
  const headers = {
    groups: '<tr><th>그룹명</th><th>소유자</th><th>사진 / 메모</th><th>상태</th><th>신고</th><th>작업</th></tr>',
    photos: '<tr><th>사진</th><th>그룹</th><th>소유자</th><th>상태</th><th>등록 일시</th><th>작업</th></tr>',
    memos: '<tr><th>그룹명</th><th>소유자</th><th>메모 미리보기</th><th>상태</th><th>등록 일시</th><th>작업</th></tr>'
  };
  $('#content-head').innerHTML = headers[state.contentTab];
  $('#content-table').innerHTML = rows.length ? rows.map((row) => {
    if (state.contentTab === 'groups') return `<tr>
      <td><button class="error-code content-detail" data-id="${row.groupId}" data-type="group"><strong>${escapeHtml(row.name)}</strong></button></td>
      <td>${escapeHtml(row.owner)}</td><td>${Number(row.photoCount)} / ${Number(row.memoCount)}</td><td>${badge(row.status, { PUBLIC: '공개', PRIVATE: '비공개' })}</td>
      <td>${Number(row.reportCount) ? badge('DELETED', { DELETED: row.reportCount }) : '0'}</td>
      <td>${row.status === 'PUBLIC' ? `<button class="small-button visibility-action" data-type="group" data-id="${row.groupId}" data-value="PRIVATE">비공개</button>` : `<button class="small-button visibility-action" data-type="group" data-id="${row.groupId}" data-value="PUBLIC">공개</button>`}
        <button class="small-button danger delete-content" data-type="group" data-id="${row.groupId}">삭제</button></td></tr>`;
    if (state.contentTab === 'photos') return `<tr>
      <td><button class="content-detail" data-type="photo" data-id="${row.imageId}" type="button"><img class="thumb" src="${escapeHtml(row.compressedPath || row.originalPath)}" alt="업로드 사진"></button></td><td>${escapeHtml(row.groupName)}</td><td>${escapeHtml(row.owner)}</td>
      <td>${badge(row.status, { PUBLIC: '공개', PRIVATE: '비공개' })}</td><td>${formatDate(row.createdAt)}</td>
      <td>${row.status === 'PUBLIC' ? `<button class="small-button visibility-action" data-type="photo" data-id="${row.imageId}" data-value="PRIVATE">비공개</button>` : `<button class="small-button visibility-action" data-type="photo" data-id="${row.imageId}" data-value="PUBLIC">공개</button>`}
        <button class="small-button danger delete-content" data-type="photo" data-id="${row.imageId}">삭제</button></td></tr>`;
    return `<tr><td>${escapeHtml(row.groupName)}</td><td>${escapeHtml(row.owner)}</td><td class="memo-preview" title="${escapeHtml(row.memo)}">${escapeHtml(row.memo)}</td><td>${badge(row.status, { PUBLIC: '공개', PRIVATE: '비공개' })}</td><td>${formatDate(row.createdAt)}</td><td>
      <button class="small-button content-detail" data-type="memo" data-id="${row.groupId}">상세</button>
      ${row.status === 'PUBLIC' ? `<button class="small-button visibility-action" data-type="memo" data-id="${row.groupId}" data-value="PRIVATE">비공개</button>` : `<button class="small-button visibility-action" data-type="memo" data-id="${row.groupId}" data-value="PUBLIC">공개</button>`}
      <button class="small-button danger delete-content" data-type="memo" data-id="${row.groupId}">삭제</button></td></tr>`;
  }).join('') : `<tr><td class="empty-row" colspan="6">표시할 콘텐츠가 없습니다.</td></tr>`;
  $('#content-page').textContent = String(state.contentPage);
  $('#content-prev').disabled = state.contentPage <= 1;
  $('#content-next').disabled = rows.length < 20;
  $$('.visibility-action').forEach((button) => button.addEventListener('click', async () => {
    const type = button.dataset.type;
    const label = type === 'group' ? '그룹' : type === 'photo' ? '사진' : '메모';
    if (!await confirmAction(`${label} 공개 상태 변경`,
      `${label}를 ${button.dataset.value === 'PRIVATE' ? '비공개' : '공개'}로 전환할까요?`, '변경')) return;
    try {
      await api(`/api/admin/${type === 'group' ? 'groups' : type === 'photo' ? 'photos' : 'memos'}/${button.dataset.id}/visibility`, {
        method: 'PATCH', body: JSON.stringify({ visibility: button.dataset.value })
      });
      toast('공개 상태를 변경했습니다.');
      loadContent();
    } catch (error) { toast(error.message, true); }
  }));
  $$('.delete-content').forEach((button) => button.addEventListener('click', async () => {
    const type = button.dataset.type;
    const group = type === 'group';
    const photo = type === 'photo';
    const message = group
      ? '그룹을 삭제 표시하고 하위 사진과 메모를 숨깁니다. 파일은 정리 대상이 되며, 저장소 정리 작업 전까지 보존됩니다.'
      : photo ? '사진을 삭제 표시합니다. 파일은 정리 대상이 되며, 저장소 정리 작업 전까지 보존됩니다.'
        : '메모를 삭제 표시합니다. 내용은 운영 이력 확인을 위해 DB에 보존됩니다.';
    if (!await confirmAction(group ? '추억 그룹 삭제' : '사진 삭제', message, '삭제')) return;
    try {
      await api(`/api/admin/${group ? 'groups' : photo ? 'photos' : 'memos'}/${button.dataset.id}`, { method: 'DELETE' });
      toast('삭제 처리했습니다.');
      loadContent();
    } catch (error) { toast(error.message, true); }
  }));
  $$('.content-detail').forEach((button) => button.addEventListener('click', () => {
    const row = rows.find((item) => String(item.groupId ?? item.imageId) === button.dataset.id);
    if (!row) return;
    const detail = $('#content-detail');
    detail.replaceChildren();
    if (state.contentTab === 'photos' && row.compressedPath) {
      const image = document.createElement('img');
      image.src = row.compressedPath;
      image.alt = '선택한 사진';
      detail.append(image);
    }
    const text = document.createElement('span');
    text.textContent = state.contentTab === 'groups'
      ? `${row.name}\n소유자: ${row.owner}\n사진 ${row.photoCount}장 · 메모 ${row.memoCount}건\n신고 ${row.reportCount}건 · ${row.status}\n${row.reportReasons || ''}`
      : `${row.groupName || ''}\n소유자: ${row.owner}\n${row.status || ''}\n\n${row.memo || row.originalPath || ''}`;
    detail.append(text);
  }));
}

function renderStorage(storage) {
  const capacity = 1024 ** 3;
  const items = [
    ['원본 저장소', Number(storage.originalBytes || 0)],
    ['압축 저장소', Number(storage.compressedBytes || 0)]
  ];
  $('#storage-progress').innerHTML = items.map(([label, bytes]) => {
    const percent = Math.min(100, bytes / capacity * 100);
    return `<div class="storage-progress-item"><header><span>${label}</span><span>${formatBytes(bytes)} · ${percent.toFixed(1)}%</span></header>
      <div class="bar-track"><div class="bar-fill" style="width:${percent}%;background:${percent >= 80 ? '#d4932d' : '#6489d9'}"></div></div></div>`;
  }).join('') + `<p class="hint">기준 용량 1 GB · 경고 기준 80% (운영 설정 전 임시 기준)</p>`;
  const files = [...(storage.files || []), ...(storage.orphans || [])];
  $('#storage-file-count').textContent = `${files.length}건`;
  $('#storage-files').innerHTML = files.length ? files.slice(0, 12).map((file) =>
    `<div class="storage-file-row"><span title="${escapeHtml(file.path)}">${escapeHtml(file.path)}</span><span>${badge(file.status === 'OK' ? 'ACTIVE' : file.status === 'MISSING' ? 'MISSING' : 'DELETED', { ACTIVE: '정상', MISSING: '누락', DELETED: '고아 파일' })}</span></div>`
  ).join('') : '<div class="empty-row">파일이 없습니다.</div>';
  $('#cleanup-button').disabled = Number(storage.orphanCount || 0) === 0;
  $('#recompress-button').disabled = Number(storage.missingCompressed || 0) === 0;
}

async function handleStorageAction(action) {
  if (action === 'recompress') {
    if (!await confirmAction('압축 이미지 재생성', '압축본이 없고 원본이 존재하는 파일을 재압축할까요?', '실행')) return;
    try {
      const result = await api('/api/admin/storage/recompress', { method: 'POST' });
      toast(`압축본 ${result.rebuilt}개를 재생성했습니다.`);
      loadContent();
    } catch (error) { toast(error.message, true); }
    return;
  }
  if (!await confirmAction('고아 파일 정리', 'DB와 연결되지 않은 파일을 영구 삭제합니다. 이 작업은 되돌릴 수 없습니다.', '영구 삭제')) return;
  try {
    const result = await api('/api/admin/storage/orphans', { method: 'DELETE' });
    toast(`고아 파일 ${result.deleted}개를 정리했습니다.`);
    loadContent();
  } catch (error) { toast(error.message, true); }
}

async function initialize() {
  const user = await api('/api/auth/me');
  state.role = user.role || 'USER';
  state.userId = user.userId;
  state.csrf = user.csrfToken || '';
  $('#admin-user-name').textContent = user.userName || user.userId;
  $('#admin-role').textContent = state.role;
  if (roleOrder[state.role] < roleOrder.MODERATOR) {
    location.replace('/main');
    return;
  }
  if (roleOrder[state.role] < roleOrder.ADMIN) {
    $('[data-view="dashboard"]').hidden = true;
    $('[data-view="users"]').hidden = true;
    setView('content');
  } else {
    if (state.role !== 'SUPER_ADMIN') toast('권한 변경은 SUPER_ADMIN만 가능합니다.');
    setView('dashboard');
  }

  $$('.nav-item').forEach((button) => button.addEventListener('click', () => setView(button.dataset.view)));
  $('#error-days').addEventListener('change', () => loadDashboard().catch((error) => toast(error.message, true)));
  $('#user-filter').addEventListener('submit', (event) => { event.preventDefault(); state.userPage = 1; state.userLocked = false; loadUsers().catch((error) => toast(error.message, true)); });
  $('#users-prev').addEventListener('click', () => { state.userPage--; loadUsers().catch((error) => toast(error.message, true)); });
  $('#users-next').addEventListener('click', () => { state.userPage++; loadUsers().catch((error) => toast(error.message, true)); });
  $('#content-filter').addEventListener('submit', (event) => { event.preventDefault(); state.contentPage = 1; loadContent().catch((error) => toast(error.message, true)); });
  $('#content-prev').addEventListener('click', () => { state.contentPage--; loadContent().catch((error) => toast(error.message, true)); });
  $('#content-next').addEventListener('click', () => { state.contentPage++; loadContent().catch((error) => toast(error.message, true)); });
  $$('.tab-button').forEach((button) => button.addEventListener('click', () => {
    state.contentTab = button.dataset.tab;
    state.contentPage = 1;
    $$('.tab-button').forEach((tab) => tab.classList.toggle('is-active', tab === button));
    loadContent().catch((error) => toast(error.message, true));
  }));
  $('#recompress-button').addEventListener('click', () => handleStorageAction('recompress'));
  $('#cleanup-button').addEventListener('click', () => handleStorageAction('cleanup'));
  $('#logout-button').addEventListener('click', async () => {
    try {
      await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': state.csrf } });
      location.href = '/login';
    } catch { toast('로그아웃하지 못했습니다.', true); }
  });
}

initialize().catch((error) => {
  toast(error.message || '관리자 화면을 초기화하지 못했습니다.', true);
  if (error.message.includes('401')) location.replace('/login');
});
