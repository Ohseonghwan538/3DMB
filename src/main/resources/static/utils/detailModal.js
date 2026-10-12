import { removeCapsuleMarker, setCapsuleMarkerVisible } from '../api/markerRenderer.js';

export class DetailModal {
    static initialize(Module, canvas) {
        this.Module = Module;
        this.canvas = canvas;
        if (Module.ELT_POLYHEDRON == null) {
            throw new Error('XDWorld HTML overlay layer type is not available.');
        }
        this.container = document.getElementById('poi-overlay-container');
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.id = 'poi-overlay-container';
            document.body.appendChild(this.container);
        }
        this.element = document.getElementById('poi-html-overlay');
        if (!this.element) {
            this.element = document.createElement('section');
            this.element.id = 'poi-html-overlay';
            this.element.className = 'poi-overlay-card';
            this.element.hidden = true;
            this.container.appendChild(this.element);
        }
        const layers = new Module.JSLayerList(true);
        this.layer = layers.nameAtLayer?.('POI_DETAIL_OVERLAY')
            || layers.createLayer('POI_DETAIL_OVERLAY', Module.ELT_POLYHEDRON);
        if (!this.layer) throw new Error('POI HTML Overlay 레이어를 생성하지 못했습니다.');
        this.layer.setMaxDistance?.(2_000_000);
        if (!this.htmlObject) {
            this.htmlObject = Module.createHTMLObject('poi-html-overlay-object');
            const result = this.htmlObject.createbyJson({
                position: new Module.JSVector3D(0, 0, 0),
                container: this.container.id,
                canvas,
                element: this.element,
                verticalAlign: 'bottom',
                horizontalAlign: 'center'
            });
            if (result?.result !== 1) {
                this.htmlObject = null;
                throw new Error(`POI HTML Overlay 생성에 실패했습니다: ${result?.return || result?.name || 'unknown'}`);
            }
            this.layer.addObject(this.htmlObject, 0);
            Module.XDRenderData?.();
        }
    }

    static open(poi, marker) {
        if (!this.htmlObject) throw new Error('POI HTML Overlay가 초기화되지 않았습니다.');
        if (this.activeMarker && this.activeMarker !== marker && this.activePoi?.poiId != null) {
            setCapsuleMarkerVisible(`poi_${this.activePoi.poiId}`, window.renderedPoiKeys?.has(`poi_${this.activePoi.poiId}`) === true);
        }
        this.activePoi = poi;
        this.activeMarker = marker;
        window.openPoiId = poi.poiId;
        if (marker?.element) {
            marker.element.hidden = true;
            marker.refresh?.();
        }
        this.htmlObject.position = new this.Module.JSVector3D(
            Number(poi.longitude), Number(poi.latitude), Number(poi.displayAltitude || poi.altitude || 0) + 20
        );
        this.element.hidden = false;
        this.render(this.element, poi);
        this.htmlObject.refresh();
    }

    static close() {
        const poiId = this.activePoi?.poiId;
        this.element.hidden = true;
        this.htmlObject?.refresh();
        if (poiId != null && this.activeMarker?.element) {
            setCapsuleMarkerVisible(`poi_${poiId}`, window.renderedPoiKeys?.has(`poi_${poiId}`) === true);
        }
        this.activeMarker = null;
        this.activePoi = null;
        window.openPoiId = null;
    }

    static render(modal, poi, editing = false) {
        const escapeHtml = (value = '') => String(value).replace(/[&<>'"]/g, (char) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
        })[char]);
        const image = poi.images?.[0] || {};
        const isPoi = poi.poiId != null;
        const groupOptions = (window.memoryGroups || []).map((group) =>
            `<option value="${escapeHtml(group.groupId)}" ${String(group.groupId) === String(poi.groupId ?? poi.capsuleId) ? 'selected' : ''}>${escapeHtml(group.name)}</option>`
        ).join('');
        const color = /^#[0-9A-Fa-f]{6}$/.test(poi.groupColor || '') ? poi.groupColor : '#4263EB';
        modal.innerHTML = `
            <div class="poi-detail-content">
                <button class="modal-close" type="button" aria-label="상세 창 닫기">×</button>
                ${editing ? `
                    <form id="poi-edit-form">
                        <h2>POI 수정</h2>
                        <label>추억 묶음<select name="groupId" required>${groupOptions}</select></label>
                        <label>제목<input name="title" maxlength="100" required value="${escapeHtml(poi.title)}"></label>
                        <label>내용<textarea name="content" maxlength="2000">${escapeHtml(poi.content || poi.memo || '')}</textarea></label>
                        <label>사진 교체<input name="image" type="file" accept="image/jpeg,image/png"></label>
                        <p class="poi-coordinates">위치는 수정할 수 없습니다 · ${Number(poi.longitude).toFixed(6)}, ${Number(poi.latitude).toFixed(6)}</p>
                        <div class="poi-actions"><button class="primary-button" type="submit">저장</button><button class="secondary-button" type="button" data-action="cancel">취소</button></div>
                        <p class="poi-message" aria-live="polite"></p>
                    </form>` : `
                    <div class="poi-titlebar" style="background-color:${color}"><span>${escapeHtml(poi.title || poi.groupName)}</span></div>
                    <p class="capsule-date">📍 ${Number(poi.longitude).toFixed(6)}, ${Number(poi.latitude).toFixed(6)}</p>
                    <div class="carousel-container"><div class="slide active"><img src="${escapeHtml(image.originalFilePath || poi.imageUrl || '')}" alt="추억 사진"></div></div>
                    <div class="capsule-memo"><p>${escapeHtml(poi.content || poi.memo || '')}</p></div>
                    ${isPoi ? `<div class="poi-actions">
                        <button class="secondary-button" data-action="edit" type="button">수정</button>
                        <button class="secondary-button" data-action="history" type="button">이력</button>
                        <button class="secondary-button" data-action="share" type="button">${poi.shared ? '공유 링크 새로 만들기' : '공유하기'}</button>
                        ${poi.shared ? '<button class="secondary-button" data-action="revoke" type="button">공유 취소</button>' : ''}
                        <button class="danger-button" data-action="delete" type="button">삭제</button>
                    </div>
                    <div class="poi-message" aria-live="polite"></div>` : ''}`}
            </div>`;
        modal.querySelector('.modal-close')?.addEventListener('click', () => this.close());
        if (!editing && isPoi) this.bindPoiActions(modal, poi);
        if (editing) this.bindEditForm(modal, poi);
    }

    static bindPoiActions(modal, poi) {
        modal.querySelector('[data-action="edit"]')?.addEventListener('click', () => this.render(modal, poi, true));
        modal.querySelector('[data-action="delete"]')?.addEventListener('click', async () => {
            if (!window.confirm('이 POI를 삭제할까요? 삭제 후 지도에서 표시되지 않습니다.')) return;
            try {
                const response = await fetch(`/api/pois/${poi.poiId}`, {
                    method: 'DELETE', headers: { 'X-CSRF-TOKEN': window.csrfToken || '' }
                });
                if (!response.ok) throw new Error('POI를 삭제하지 못했습니다.');
                removeCapsuleMarker(poi.poiId);
                this.close();
                this.refreshMap();
            } catch (error) { this.message(modal, error.message); }
        });
        modal.querySelector('[data-action="history"]')?.addEventListener('click', async () => {
            try {
                const response = await fetch(`/api/pois/${poi.poiId}/history`);
                if (!response.ok) throw new Error('수정 이력을 불러오지 못했습니다.');
                const history = await response.json();
                const message = history.length ? history.map((entry) =>
                    `${new Date(entry.archivedAt).toLocaleString()} · ${entry.changeType} · ${entry.title}\n${entry.content || ''}`
                ).join('\n\n') : '아직 수정·삭제 이력이 없습니다.';
                window.alert(message);
            } catch (error) { this.message(modal, error.message); }
        });
        modal.querySelector('[data-action="share"]')?.addEventListener('click', async () => {
            try {
                const response = await fetch(`/api/pois/${poi.poiId}/share`, {
                    method: 'POST', headers: { 'X-CSRF-TOKEN': window.csrfToken || '' }
                });
                if (!response.ok) throw new Error('공유 링크를 만들지 못했습니다.');
                const result = await response.json();
                const message = modal.querySelector('.poi-message');
                if (message) {
                    const link = document.createElement('a');
                    link.href = result.url;
                    link.target = '_blank';
                    link.rel = 'noopener noreferrer';
                    link.textContent = result.url;
                    message.replaceChildren(document.createTextNode('공유 링크: '), link);
                }
                try { await navigator.clipboard.writeText(result.url); } catch { /* Link remains visible for manual copying. */ }
                poi.shared = true;
                modal.querySelector('[data-action="share"]').textContent = '공유 링크 새로 만들기';
                if (!modal.querySelector('[data-action="revoke"]')) {
                    const button = document.createElement('button');
                    button.className = 'secondary-button';
                    button.dataset.action = 'revoke';
                    button.type = 'button';
                    button.textContent = '공유 취소';
                    modal.querySelector('.poi-actions').insertBefore(button, modal.querySelector('[data-action="delete"]'));
                    button.addEventListener('click', () => this.revokeShare(modal, poi));
                }
            } catch (error) { this.message(modal, error.message); }
        });
        modal.querySelector('[data-action="revoke"]')?.addEventListener('click', () => this.revokeShare(modal, poi));
    }

    static async revokeShare(modal, poi) {
        try {
            const response = await fetch(`/api/pois/${poi.poiId}/share`, {
                method: 'DELETE', headers: { 'X-CSRF-TOKEN': window.csrfToken || '' }
            });
            if (!response.ok) throw new Error('공유 링크를 취소하지 못했습니다.');
            poi.shared = false;
            modal.querySelector('[data-action="revoke"]')?.remove();
            modal.querySelector('[data-action="share"]').textContent = '공유하기';
            this.message(modal, '공유 링크를 취소했습니다.');
        } catch (error) { this.message(modal, error.message); }
    }

    static bindEditForm(modal, poi) {
        modal.querySelector('[data-action="cancel"]')?.addEventListener('click', () => this.render(modal, poi));
        modal.querySelector('#poi-edit-form')?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const form = event.currentTarget;
            const data = new FormData(form);
            if (!data.get('image')?.size) data.delete('image');
            try {
                const response = await fetch(`/api/pois/${poi.poiId}`, {
                    method: 'PUT', headers: { 'X-CSRF-TOKEN': window.csrfToken || '' }, body: data
                });
                if (!response.ok) {
                    const body = await response.json().catch(() => ({}));
                    throw new Error(body.detail || body.message || 'POI를 수정하지 못했습니다.');
                }
                const updated = await response.json();
                const normalized = {
                    ...poi,
                    ...updated,
                    groupId: updated.groupId,
                    capsuleId: updated.capsuleId,
                    memo: updated.content,
                    images: [{ originalFilePath: updated.imageUrl, compressedFilePath: updated.thumbUrl }]
                };
                this.render(modal, normalized);
                this.refreshMap();
            } catch (error) { this.message(modal, error.message); }
        });
    }

    static message(modal, text) {
        const element = modal.querySelector('.poi-message');
        if (element) element.textContent = text;
    }

    static refreshMap() {
        window.dispatchEvent(new CustomEvent('capsuleCreated'));
    }
}
