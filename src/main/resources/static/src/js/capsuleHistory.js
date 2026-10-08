import { deleteGroup, fetchCapsulesByUser, updateGroup } from '../api/capsuleApi.js';

let groups = [];

async function loadHistory(selectedGroupId = null) {
    const select = document.getElementById('memory-group');
    const feedback = document.getElementById('memory-feedback');
    if (!select) return;
    const previous = selectedGroupId == null ? select.value : String(selectedGroupId);
    const knownGroupIds = new Set(groups.map((group) => Number(group.groupId)));
    try {
        groups = await fetchCapsulesByUser();
        if (Array.isArray(window.selectedGroupIds)) {
            groups.filter((group) => !knownGroupIds.has(Number(group.groupId)))
                .forEach((group) => window.selectedGroupIds.push(Number(group.groupId)));
        }
        window.memoryGroups = groups;
        select.replaceChildren(new Option(groups.length ? '추억 묶음을 선택하세요' : '먼저 묶음을 만들어 주세요', ''));
        groups.forEach((group) => {
            const option = new Option(`${group.name} (${group.poiCount || 0})`, group.groupId);
            option.style.color = group.color;
            select.add(option);
        });
        select.value = groups.some((group) => String(group.groupId) === previous) ? previous : '';
        renderGroups();
        updateSelectedGroups();
        if (select.value && document.getElementById('file-photo')?.files?.length) {
            document.getElementById('memory-group').dispatchEvent(new Event('change', { bubbles: true }));
        }
        if (feedback && groups.length === 0) feedback.textContent = '추억 묶음을 먼저 만들어 주세요.';
        if (feedback && groups.length > 0 && feedback.textContent === '추억 묶음을 먼저 만들어 주세요.') {
            feedback.textContent = `${groups.length}개의 추억 묶음을 불러왔습니다.`;
        }
        document.dispatchEvent(new CustomEvent('memoryGroupsLoaded', { detail: { groups } }));
    } catch (error) {
        if (feedback) feedback.textContent = error.message;
    }
}

function renderGroups() {
    const list = document.getElementById('memory-group-list');
    if (!list) return;
    list.replaceChildren();
    groups.forEach((group) => {
        const row = document.createElement('div');
        row.className = 'memory-group-row';
        const check = document.createElement('input');
        check.type = 'checkbox';
        check.checked = Array.isArray(window.selectedGroupIds)
            ? window.selectedGroupIds.includes(Number(group.groupId)) : true;
        check.value = group.groupId;
        check.setAttribute('aria-label', `${group.name} 지도 표시`);
        check.addEventListener('change', updateSelectedGroups);
        const swatch = document.createElement('span');
        swatch.className = 'memory-group-swatch';
        swatch.style.backgroundColor = group.color;
        const label = document.createElement('span');
        label.className = 'memory-group-name';
        label.textContent = `${group.name} (${group.poiCount || 0})`;
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'text-button memory-group-edit';
        edit.textContent = '편집';
        edit.addEventListener('click', () => editGroup(group));
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'text-button memory-group-delete';
        remove.textContent = '삭제';
        remove.addEventListener('click', () => removeGroup(group));
        row.append(check, swatch, label, edit, remove);
        list.append(row);
    });
}

function updateSelectedGroups() {
    const selected = [...document.querySelectorAll('#memory-group-list input[type="checkbox"]:checked')]
        .map((input) => Number(input.value));
    window.selectedGroupIds = selected;
    window.dispatchEvent(new CustomEvent('groupFilterChanged', { detail: { groupIds: selected } }));
}

async function editGroup(group) {
    const name = window.prompt('추억 묶음 이름을 입력해 주세요.', group.name);
    if (name === null) return;
    const color = window.prompt('묶음 색상을 #RRGGBB 형식으로 입력해 주세요.', group.color);
    if (color === null) return;
    if (!/^#[0-9A-Fa-f]{6}$/.test(color)) {
        const feedback = document.getElementById('memory-feedback');
        if (feedback) feedback.textContent = '색상은 #RRGGBB 형식이어야 합니다.';
        return;
    }
    try {
        await updateGroup(group.groupId, { name: name.trim(), color });
        await loadHistory(group.groupId);
        window.dispatchEvent(new CustomEvent('capsuleCreated'));
    } catch (error) {
        const feedback = document.getElementById('memory-feedback');
        if (feedback) feedback.textContent = error.message;
    }
}

async function removeGroup(group) {
    if (!window.confirm(`「${group.name}」 묶음을 삭제할까요? POI가 들어 있는 묶음은 삭제하기 전에 POI를 이동하거나 삭제해야 합니다.`)) return;
    try {
        await deleteGroup(group.groupId);
        await loadHistory();
        window.dispatchEvent(new CustomEvent('capsuleCreated'));
    } catch (error) {
        const feedback = document.getElementById('memory-feedback');
        if (feedback) feedback.textContent = error.message;
    }
}

document.addEventListener('sidebarLoaded', () => {
    document.getElementById('memory-group')?.addEventListener('change', (event) => {
        document.getElementById('memory-feedback').textContent = event.target.value
            ? '선택한 묶음에 사진과 기록을 저장할 수 있습니다.' : '';
    });
    document.getElementById('refresh-memories')?.addEventListener('click', () => loadHistory());
    loadHistory();
});

window.addEventListener('capsuleCreated', (event) => loadHistory(event.detail?.capsuleId));
