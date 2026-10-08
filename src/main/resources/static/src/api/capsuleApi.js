/**
 * 현재 지도 화면 바운딩 박스 영역 내 캡슐 목록 조회
 * @param {number} minLng 남서쪽 경도
 * @param {number} minLat 남서쪽 위도
 * @param {number} maxLng 북동쪽 경도
 * @param {number} maxLat 북동쪽 위도
 * @returns {Promise<Array>} 캡슐 및 이미지 데이터 목록
 */
export async function fetchCapsulesInBounds(userId, minLng, minLat, maxLng, maxLat) {
    try {
        const params = new URLSearchParams({
            minLng: minLng.toFixed(6),
            minLat: minLat.toFixed(6),
            maxLng: maxLng.toFixed(6),
            maxLat: maxLat.toFixed(6),
            userId
        });

        const response = await fetch(`/api/capsules/bounds?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('캡슐 영역 데이터 조회 실패:', error);
        return [];
    }
}

export async function fetchPoisInBounds(minLng, minLat, maxLng, maxLat, groupIds = []) {
    if (groupIds.length === 0) return [];
    const params = new URLSearchParams({
        minLng: minLng.toFixed(6),
        minLat: minLat.toFixed(6),
        maxLng: maxLng.toFixed(6),
        maxLat: maxLat.toFixed(6)
    });
    groupIds.forEach((groupId) => params.append('groupIds', String(groupId)));
    const response = await fetch(`/api/pois/bbox?${params}`, { headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error(`POI 영역을 불러오지 못했습니다. (${response.status})`);
    return response.json();
}

/**
 * 신규 캡슐 생성 요청
 * @param {Object} capsuleData 백엔드 DTO 매핑 객체
 * @returns {Promise<Object>} 생성된 캡슐 응답 객체
 */
export async function createCapsule(capsuleData) {
    try {
        const response = await fetch('/api/capsules', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': window.csrfToken || ''
            },
            body: JSON.stringify(capsuleData)
        });

        if (!response.ok) {
            const errorMsg = await response.text();
            throw new Error(`Capsule creation failed (${response.status}): ${errorMsg}`);
        }

        return await response.json();
    } catch (error) {
        console.error('캡슐 등록 실패:', error);
        throw error;
    }
}

export async function fetchCapsulesByUser(userId) {
    const response = await fetch('/api/groups', { headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error(`추억 목록을 불러오지 못했습니다. (${response.status})`);
    return response.json();
}

export async function createGroup(values) {
    const response = await fetch('/api/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-TOKEN': window.csrfToken || '' },
        body: JSON.stringify(values)
    });
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.detail || body.message || '추억 묶음을 만들지 못했습니다.');
    }
    return response.json();
}

export async function createPoi(entry) {
    const response = await fetch('/api/pois', {
        method: 'POST',
        headers: { Accept: 'application/json', 'X-CSRF-TOKEN': window.csrfToken || '' },
        body: entry
    });
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || body.detail || '사진과 기록을 저장하지 못했습니다.');
    }
    return response.json();
}

export async function updateGroup(groupId, values) {
    const response = await fetch(`/api/groups/${groupId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-TOKEN': window.csrfToken || '' },
        body: JSON.stringify(values)
    });
    if (!response.ok) throw new Error('추억 묶음을 수정하지 못했습니다.');
    return response.json();
}

export async function deleteGroup(groupId) {
    const response = await fetch(`/api/groups/${groupId}`, {
        method: 'DELETE', headers: { Accept: 'application/json', 'X-CSRF-TOKEN': window.csrfToken || '' }
    });
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || body.detail || '추억 묶음을 삭제하지 못했습니다.');
    }
    return response.json();
}
