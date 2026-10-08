import { createGroup, createPoi } from '../api/capsuleApi.js';
import { renderCapsuleBillboard, setCapsuleMarkerVisible } from '../api/markerRenderer.js';

export class CapsuleFormHandler {
    constructor() {
        this.gpsAvailable = false;
        this.previewUrl = null;
        this.previewLocation = null;
        this.previewRequestId = 0;
        this.initEvents();
    }

    /**
     * DOM 이벤트 리스너 바인딩
     */
    initEvents() {
        // sidebar.html의 #memory-upload 폼 제출 감지 (이벤트 위임)
        document.addEventListener('submit', async (e) => {
            if (e.target && e.target.id === 'memory-upload') {
                await this.handleSubmit(e);
            }
        });
        window.addEventListener('createMemoryGroup', (event) => this.createMemoryCapsule(event.detail));
        document.addEventListener('change', (event) => {
            if (event.target?.id === 'file-photo') this.previewGps(event.target.files?.[0]);
            if (event.target?.id === 'memory-group' && this.previewLocation) {
                this.showPreviewMarker(this.previewLocation, this.previewRequestId, false);
            }
        });
    }

    /**
     * 폼 제출 처리 및 API 호출
     */
    async handleSubmit(e) {
        e.preventDefault(); // 기본 URL 이동(GET) 방지

        const userId = localStorage.getItem('userId');
        const capsuleId = document.getElementById('memory-group')?.value;
        const memo = document.getElementById('memory-memo')?.value || '';
        const title = document.getElementById('memory-title')?.value || '';
        const fileInput = document.getElementById('file-photo');
        const selectedFile = fileInput?.files[0];

        try {
            if (!userId) throw new Error('사용자 정보를 찾지 못했습니다. 다시 로그인해 주세요.');
            if (!capsuleId) throw new Error('사진을 저장할 추억 묶음을 먼저 선택해 주세요.');
            if (!title.trim()) throw new Error('제목을 입력해 주세요.');
            if (!selectedFile) throw new Error('사진을 선택해 주세요.');
            if (!this.gpsAvailable) throw new Error('GPS 위치 정보가 있는 사진을 선택해 주세요.');
            const button = document.getElementById('btn-save-memory');
            button.disabled = true;
            const payload = new FormData();
            payload.set('groupId', capsuleId);
            payload.set('title', title.trim());
            payload.set('content', memo);
            payload.set('image', selectedFile);
            await createPoi(payload);
            e.target.reset();
            this.clearGpsPreview();
            const gpsStatus = document.getElementById('memory-gps-status');
            if (gpsStatus) gpsStatus.textContent = '사진의 GPS 위치를 확인하면 저장할 수 있습니다.';
            const feedback = document.getElementById('memory-feedback');
            if (feedback) feedback.textContent = '사진과 기록을 저장했습니다.';
            window.dispatchEvent(new CustomEvent('capsuleCreated', { detail: { capsuleId: Number(capsuleId) } }));
        } catch (err) {
            const feedback = document.getElementById('memory-feedback');
            if (feedback) feedback.textContent = err.message;
        } finally {
            const button = document.getElementById('btn-save-memory');
            if (button) button.disabled = !this.gpsAvailable;
        }
    }

    async previewGps(file) {
        const requestId = ++this.previewRequestId;
        this.gpsAvailable = false;
        this.removePreviewMarker();
        if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
        this.previewUrl = null;
        const button = document.getElementById('btn-save-memory');
        const status = document.getElementById('memory-gps-status');
        if (button) button.disabled = true;
        if (!file) {
            if (status) status.textContent = '사진의 GPS 위치를 확인하면 저장할 수 있습니다.';
            return;
        }
        if (status) status.textContent = '사진의 GPS 위치를 확인하고 있습니다.';
        try {
            const payload = new FormData();
            payload.set('image', file);
            const response = await fetch('/api/pois/gps', {
                method: 'POST',
                headers: { Accept: 'application/json', 'X-CSRF-TOKEN': window.csrfToken || '' },
                body: payload
            });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.detail || body.message || '사진 GPS 정보를 읽지 못했습니다.');
            }
            const location = await response.json();
            if (requestId !== this.previewRequestId) return;
            if (!Number.isFinite(location.latitude) || !Number.isFinite(location.longitude)) {
                if (status) status.textContent = '이 사진에는 GPS 정보가 없습니다. 위치 정보가 있는 원본 사진을 선택해 주세요.';
                return;
            }
            this.gpsAvailable = true;
            this.previewLocation = location;
            this.previewUrl = URL.createObjectURL(file);
            if (button) button.disabled = false;
            if (status) status.textContent = `사진 위치: ${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)} · 고도 ${Number(location.altitude || 0).toFixed(1)}m`;
            this.showPreviewMarker(location, requestId, true);
        } catch (error) {
            if (requestId === this.previewRequestId && status) status.textContent = error.message;
        }
    }

    showPreviewMarker(location, requestId, focusCamera) {
        if (!window.Module || !this.previewUrl) return;
        const groupId = document.getElementById('memory-group')?.value;
        const group = (window.memoryGroups || []).find((item) => String(item.groupId) === String(groupId));
        renderCapsuleBillboard(window.Module, {
            capsuleId: 'upload-preview',
            latitude: location.latitude,
            longitude: location.longitude,
            altitude: location.altitude || 0,
            groupColor: group?.color || '#4263EB',
            thumbUrl: this.previewUrl,
            isPreview: true
        }, () => requestId === this.previewRequestId && this.gpsAvailable);
        if (focusCamera) {
            const camera = window.Module.getViewCamera?.();
            if (camera) {
                camera.move(new window.Module.JSVector3D(
                    location.longitude, location.latitude - 0.005,
                    Math.max(Number(location.altitude || 0) + 200, 200)
                ), 20, 0, 0);
            }
        }
    }

    clearGpsPreview() {
        this.previewRequestId++;
        this.gpsAvailable = false;
        this.previewLocation = null;
        if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
        this.previewUrl = null;
        this.removePreviewMarker();
    }

    removePreviewMarker() {
        if (!window.Module) return;
        setCapsuleMarkerVisible('capsule_upload-preview', false);
    }

    async createMemoryCapsule({ title, color }) {
        if (!title) return;
        const userId = localStorage.getItem('userId');
        try {
            if (!userId) throw new Error('사용자 정보를 찾지 못했습니다. 다시 로그인해 주세요.');
            const result = await createGroup({ name: title.trim(), color });
            window.dispatchEvent(new CustomEvent('capsuleCreated', { detail: { capsuleId: result.groupId } }));
            const feedback = document.getElementById('memory-feedback');
            if (feedback) feedback.textContent = '새 추억 묶음을 만들었습니다.';
        } catch (error) {
            const feedback = document.getElementById('memory-feedback');
            if (feedback) feedback.textContent = `새 추억 묶음 생성에 실패했습니다: ${error.message}`;
        }
    }

}
