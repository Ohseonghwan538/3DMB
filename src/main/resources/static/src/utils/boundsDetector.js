// js/map/boundsDetector.js

import { fetchPoisInBounds } from '../api/capsuleApi.js';
import { parseMapPoint } from './mapUtils.js';

export class BoundsDetector {
    /**
     * @param {Object} module XDWorld WASM Module 객체
     * @param {HTMLCanvasElement} canvas XDWorld 렌더링 캔버스
     * @param {Function} onCapsulesLoaded 캡슐 데이터 수신 시 실행할 콜백 (3D 마커 생성용)
     */
    constructor(module, canvas, onCapsulesLoaded) {
        this.Module = module;
        this.canvas = canvas;
        this.onCapsulesLoaded = onCapsulesLoaded;
        this.debounceTimer = null;
        this.debounceDelay = 300; // 카메라 정지 후 300ms 뒤 API 호출
        this.searchSequence = 0;
    }

    /**
     * XDWorld 화면 4개 모서리 지점으로부터 Bounding Box (minLng, minLat, maxLng, maxLat) 추출
     */
    getCurrentBounds() {
        const map = this.Module.getMap();
        if (!map) return null;

        const width = this.canvas.width;
        const height = this.canvas.height;

        // 캔버스 4개 코너 화면 좌표 (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
        const screenPoints = [
            new this.Module.JSVector2D(0, 0),
            new this.Module.JSVector2D(width, 0),
            new this.Module.JSVector2D(0, height),
            new this.Module.JSVector2D(width, height)
        ];

        let lons = [];
        let lats = [];

        const convertFn = map.ScreenToMapPointEX || map.screenToMapPointEX;
        if (typeof convertFn !== 'function') return null;

        screenPoints.forEach((sp) => {
            const rawPoint = convertFn.call(map, sp);
            const coords = parseMapPoint(rawPoint);

            if (coords) {
                lons.push(coords.lon);
                lats.push(coords.lat);
            }
        });

        // 지형 교차 지점이 없거나 카메라가 우주/하늘을 향할 때 예외 처리 (Fallback)
        if (lons.length === 0 || lats.length === 0) {
            const camera = this.Module.getViewCamera();
            if (camera) {
                const camPos = camera.getLocation();
                const camCoords = parseMapPoint(camPos) || (camPos ? { lon: camPos.x, lat: camPos.y } : null);

                if (camCoords && camCoords.lon && camCoords.lat) {
                    return {
                        minLng: camCoords.lon - 0.05,
                        minLat: camCoords.lat - 0.05,
                        maxLng: camCoords.lon + 0.05,
                        maxLat: camCoords.lat + 0.05
                    };
                }
            }
            return null;
        }

        return {
            minLng: Math.min(...lons),
            minLat: Math.min(...lats),
            maxLng: Math.max(...lons),
            maxLat: Math.max(...lats)
        };
    }

    /**
     * 디바운스 적용 API 조회 핸들러
     */
    triggerBoundsSearch() {
        const sequence = ++this.searchSequence;
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }

        this.debounceTimer = setTimeout(async () => {
            const bounds = this.getCurrentBounds();
            
            // 바운딩 박스를 계산할 수 없거나 무효한 경우 실행 중단
            if (!bounds) return;

            console.log('📐 감지된 영역 BoundingBox:', bounds);

            try {
                const pois = await fetchPoisInBounds(
                    bounds.minLng,
                    bounds.minLat,
                    bounds.maxLng,
                    bounds.maxLat,
                    window.selectedGroupIds || []
                );
                if (sequence !== this.searchSequence) return;
                if (typeof this.onCapsulesLoaded === 'function') this.onCapsulesLoaded(pois);
            } catch (error) {
                if (sequence === this.searchSequence) console.error('POI 영역 조회에 실패했습니다.', error);
            }
        }, this.debounceDelay);
    }

    /**
     * XDWorld 카메라 이동 감지 이벤트 등록
     */
    initEventListeners() {
        // 1. XDWorld 엔진 모듈 이벤트 바인딩
        if (typeof this.Module.cbMoveEnd === 'function') {
            this.Module.cbMoveEnd = () => this.triggerBoundsSearch();
        }

        // 2. DOM 캔버스 기반 사용자 조작(마우스 떼기, 휠 조작, 터치 종료) 이벤트 감지
        this.canvas.addEventListener('mouseup', () => this.triggerBoundsSearch());
        this.canvas.addEventListener('wheel', () => this.triggerBoundsSearch());
        this.canvas.addEventListener('touchend', () => this.triggerBoundsSearch());

        // 최초 로드 시 1회 즉시 실행
        this.triggerBoundsSearch();
    }
}
