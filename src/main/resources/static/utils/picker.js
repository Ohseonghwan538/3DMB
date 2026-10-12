import { parseMapPoint, getCanvasScreenPoint } from './mapUtils.js';

export class MapPicker {
    constructor(module, canvas, onPointPicked) {
        this.Module = module;
        this.canvas = canvas;
        this.onPointPicked = onPointPicked;
        this.isPickingMode = false;
        this.initPickEvents();
    }

    setPickingMode(enable) {
        this.isPickingMode = enable;
        this.canvas.style.cursor = enable ? 'crosshair' : 'default';
    }

    initPickEvents() {
        this.canvas.addEventListener('click', (e) => {
            if (!this.isPickingMode) return;

            const map = this.Module.getMap();
            if (!map) return;

            const convertFn = map.ScreenToMapPointEX || map.screenToMapPointEX;
            if (typeof convertFn !== 'function') return;

            // 해상도 보정된 화면 좌표 생성
            const screenPoint = getCanvasScreenPoint(this.Module, this.canvas, e.clientX, e.clientY);
            const rawPoint = convertFn.call(map, screenPoint);

            // 안전한 좌표 추출
            const coords = parseMapPoint(rawPoint);

            if (coords) {
                const pickedLocation = {
                    longitude: Number(coords.lon.toFixed(6)),
                    latitude: Number(coords.lat.toFixed(6)),
                    altitude: Number(coords.alt.toFixed(2))
                };

                console.log('📍 정밀 지형 좌표 피킹 완료:', pickedLocation);

                if (typeof this.onPointPicked === 'function') {
                    this.onPointPicked(pickedLocation);
                }

                this.setPickingMode(false);
            } else {
                console.warn('⚠️ 지형 교차 지점을 찾지 못했습니다. (하늘 또는 맵 외곽 클릭)');
            }
        });
    }
}