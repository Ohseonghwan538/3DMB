// main.js
import { wmsLayerSIDO, wmsLayerSIG, wmsLayerADM } from "./layers/service/GeoserverWmsLayer.js";    
import { tileLayer } from "./layers/TileLayer.js";
import { renderCapsuleBillboard, setCapsuleMarkersVisible } from '../api/markerRenderer.js';
import { BoundsDetector } from '../utils/boundsDetector.js';
import { CapsuleFormHandler } from '../utils/capsuleForm.js';
import { DetailModal } from '../utils/detailModal.js';

let csrfToken = '';
/**
 * 🔑 세션 확인 및 localStorage 동기화
 */
async function initSession() {
    try {
        const response = await fetch('/api/auth/me');
        if (response.ok) {
            const user = await response.json();
            csrfToken = user.csrfToken || '';
            window.csrfToken = csrfToken;
            // 서버 세션의 실제 userId를 localStorage에 동기화
            localStorage.setItem('userId', user.userId);
            const adminLink = document.getElementById('admin-link');
            if (adminLink) adminLink.hidden = !['MODERATOR', 'ADMIN', 'SUPER_ADMIN'].includes(user.role);
            console.log('✅ 로그인 사용자 세션 확인:', user.userId);
        } else {
            localStorage.removeItem('userId');
            window.location.href = '/login';
            return false;
        }
    } catch (error) {
        console.error('세션 확인 실패:', error);
        window.location.href = '/login';
        return false;
    }
    return true;
}

// 애플리케이션 진입 시 세션 검증 실행
const sessionReady = initSession();

// 3D 엔진 로드 완료 후 실행
window.addEventListener("XDWorldLoaded", async function () {
    if (!(await sessionReady)) return;
    console.log("🚀 3D 엔진 초기화 완료");

    // 레이어 초기화
    wmsLayerSIDO.init();
    wmsLayerADM.init();
    wmsLayerSIG.init();
    tileLayer.init();

    document.getElementById("map-loader")?.classList.add("is-done");
    const status = document.getElementById("map-status-text");
    if (status) status.textContent = "3D 지도 연결됨";

    initApplication(window.Module);
});

window.addEventListener("toggleBuildingLayer", (event) => tileLayer.setVisible(event.detail.visible));

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("home-view")?.addEventListener("click", () => location.reload());
    document.getElementById("fullscreen")?.addEventListener("click", () => {
        document.fullscreenElement ? document.exitFullscreen?.() : document.documentElement.requestFullscreen?.();
    });
    document.getElementById("btn-logout")?.addEventListener("click", async () => {
        const response = await fetch('/logout', { method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken } });
        if (response.ok) location.href = '/login';
    });
});

/**
 * 애플리케이션 모듈 통합 초기화
 */
function initApplication(Module) {
    const canvas = document.querySelector('#map canvas') || document.getElementById('map');
    const moduleObj = window.Module || Module;
    window.Module = moduleObj;

    if (!canvas) {
        console.error("❌ Map Canvas 엘리먼트를 찾을 수 없습니다.");
        return;
    }

    const eventCanvas = moduleObj.canvas || canvas;
    try {
        DetailModal.initialize(moduleObj, eventCanvas);
    } catch (error) {
        console.error('POI HTML Overlay를 초기화하지 못했습니다.', error);
    }
    // 2. 가시 영역 캡슐 수신 콜백
    let markerRefreshId = 0;
    const handleCapsulesLoaded = (capsules) => {
        const refreshId = ++markerRefreshId;
        console.log(`📌 가시 영역 내 조회된 캡슐 개수: ${capsules.length}`);
        const visibleKeys = new Set(capsules.map((poi) => `poi_${poi.poiId}`));
        setCapsuleMarkersVisible(visibleKeys);
        window.renderedPoiKeys = visibleKeys;
        capsules.forEach((poi) => renderCapsuleBillboard(
            moduleObj,
            poi,
            () => refreshId === markerRefreshId,
            (selectedPoi, marker) => {
                try {
                    DetailModal.open(selectedPoi, marker);
                } catch (error) {
                    console.error('POI 정보창을 표시하지 못했습니다.', error);
                }
            }
        ));
        if (DetailModal.activePoi && !visibleKeys.has(`poi_${DetailModal.activePoi.poiId}`)) {
            DetailModal.close();
        }
    };

    // 3. 지도 영역 감지기 실행
    const boundsDetector = new BoundsDetector(moduleObj, canvas, handleCapsulesLoaded);
    window.boundsDetector = boundsDetector;
    boundsDetector.initEventListeners();

    // 4. Capsule Form Handler 객체 우선 생성
    const capsuleFormHandler = new CapsuleFormHandler();

    // 6. 새 캡슐 생성 이벤트 수신 시 지도 마커 즉시 재조회
    window.addEventListener('capsuleCreated', () => {
        boundsDetector.triggerBoundsSearch();
    });
    window.addEventListener('groupFilterChanged', () => boundsDetector.triggerBoundsSearch());
}
