/**
 * GeoserverWmsLayer.js
 *
 * 역할: GeoServer WMS 레이어 생성 + 고도(LOD)에 따른 표출 전환.
 *  - 이벤트 리스너/타이머는 레이어마다 두지 않고, 공유 컨트롤러(WmsLodController) 하나가 전체를 관리한다.
 *  - 카메라를 움직이는 동안에는 "고도 한 번 읽어서 비교"만 한다. 레이어를 켜고 끌 때만 엔진을 건드린다.
 *  - 지금 고도에서 필요 없는 레이어는 아예 만들지 않는다(지연 생성) → 시작 시 타일 요청/메모리 절약.
 *  - 라벨 배치·폴리곤 스타일은 SLD(서버) 몫이라 여기서는 다루지 않는다.
 */

// 접속 주소는 한 곳에서만 관리한다. 배포 환경에서는 페이지에서 window.GEOSERVER_WMS_URL 로 덮어쓴다.
const GEOSERVER_WMS_URL =
    (typeof window !== "undefined" && window.GEOSERVER_WMS_URL) ||
    "http://localhost:8085/geoserver/map/wms?";

// LOD 컨트롤러 튜닝 값
const LOD = Object.freeze({
    TICK_MS: 200,          // 고도 확인 주기 (고도 읽기 1회는 매우 가벼움)
    ALT_EPSILON: 0.005,    // 마지막 판단 이후 고도 변화가 0.5% 미만이면 아무것도 하지 않음
    FAST_MOVE_RATIO: 0.4,  // 한 주기에 고도가 40% 넘게 변하면 "빠르게 이동 중"으로 보고 판단을 미룸
    MAX_DEFER_TICKS: 6,    // 계속 빠르게 움직여도 최대 6주기(≈1.2초)까지만 미룸
    HYSTERESIS: 0.05,      // 경계 고도 ±5% 구간에서는 상태를 바꾸지 않음 (경계에서 깜빡임 방지)
    HIDE_DELAY_MS: 350,    // 새 레이어를 먼저 켜고, 이전 레이어는 이만큼 뒤에 끈다 (빈 화면 방지)
});

function requestRender() {
    if (typeof Module !== "undefined" && typeof Module.XDRenderData === "function") {
        Module.XDRenderData();
    }
}

// =========================================================================
// 공유 LOD 컨트롤러 : 모든 WMS 레이어의 가시성을 한 곳에서 판단한다
// =========================================================================
class WmsLodController {
    constructor() {
        this.layers = new Set();
        this.timer = null;
        this.camera = null;     // 카메라 객체는 한 번만 얻어서 재사용 (매번 getViewCamera 호출 방지)
        this.prevAlt = NaN;     // 직전 주기의 고도 (이동 속도 계산용)
        this.evalAlt = NaN;     // 마지막으로 가시성을 판단한 고도
        this.deferred = 0;
        this.onVisibilityChange = () => {
            if (typeof document !== "undefined" && !document.hidden) this.tick(true);
        };
    }

    register(layer) {
        this.layers.add(layer);
        this.start();
    }

    unregister(layer) {
        this.layers.delete(layer);
        if (this.layers.size === 0) this.stop();
    }

    start() {
        if (this.timer) return;
        this.timer = setInterval(() => this.tick(false), LOD.TICK_MS);
        if (typeof document !== "undefined") {
            document.addEventListener("visibilitychange", this.onVisibilityChange);
        }
    }

    stop() {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
        if (typeof document !== "undefined") {
            document.removeEventListener("visibilitychange", this.onVisibilityChange);
        }
    }

    getAltitude() {
        try {
            if (!this.camera) this.camera = Module.getViewCamera();
            return this.camera ? this.camera.getAltitude() : NaN;
        } catch (e) {
            this.camera = null; // 엔진이 아직 준비 전이면 다음 주기에 다시 시도
            return NaN;
        }
    }

    /**
     * @param {boolean} force true 이면 변화량/이동 속도와 상관없이 즉시 판단 (시작, 탭 복귀, 수동 토글)
     */
    tick(force) {
        if (!force && typeof document !== "undefined" && document.hidden) return;

        const alt = this.getAltitude();
        if (!(alt >= 0)) return; // NaN/undefined 방어

        const prev = this.prevAlt;
        this.prevAlt = alt;

        if (!force && this.evalAlt >= 0) {
            // 1) 마지막 판단 이후 거의 변화가 없으면 아무 일도 하지 않는다 (가만히 있을 때 비용 0)
            if (Math.abs(alt - this.evalAlt) <= this.evalAlt * LOD.ALT_EPSILON) {
                this.deferred = 0;
                return;
            }
            // 2) 아직 빠르게 줌 이동 중이면 지나가는 중간 레이어를 만들지 않도록 잠깐 미룬다
            if (prev > 0 &&
                Math.abs(alt - prev) > prev * LOD.FAST_MOVE_RATIO &&
                this.deferred < LOD.MAX_DEFER_TICKS) {
                this.deferred++;
                return;
            }
        }

        this.deferred = 0;
        this.evalAlt = alt;
        this.apply(alt);
    }

    apply(alt) {
        const toShow = [];
        const toHide = [];

        for (const layer of this.layers) {
            const want = layer.wantsVisible(alt);
            if (want && !layer.isVisible) toShow.push(layer);
            else if (!want && layer.isVisible) toHide.push(layer);
        }
        if (toShow.length === 0 && toHide.length === 0) return;

        // 새 레이어를 먼저 켜고 이전 레이어를 끈다 → 전환 순간 빈 화면이 생기지 않는다
        toShow.forEach((l) => l.applyVisible(true));
        toHide.forEach((l) => l.applyVisible(false));

        requestRender(); // 상태가 바뀐 경우에만, 한 주기에 한 번
    }
}

const lodController = new WmsLodController();

// =========================================================================
// WMS 레이어
// =========================================================================
class GeoServerWmsLayer {
    constructor(layerId, options = {}) {
        this.layerId = layerId;
        this.layerList = null;
        this.layer = null;          // 엔진 쪽 WMS 레이어 (필요해질 때 생성)

        this.enabled = true;        // 사용자가 켜 둔 상태 (체크박스 등)
        this.isVisible = false;     // LOD 판단 기준이 되는 "논리적" 표출 상태
        this.nativeVisible = false; // 엔진에 실제로 적용된 상태 (지연 숨김 때문에 isVisible 과 잠깐 다를 수 있음)
        this.failed = false;        // 생성 실패 시 매 주기마다 재시도하지 않도록 막는다
        this.hideTimer = null;

        const defaultOptions = {
            tileSize: 256,
            crs: "EPSG:4326",
            minDistance: 0,                  // 표출 최소 고도 (m)
            maxDistance: 99999999,           // 표출 최대 고도 (m)
            hysteresis: LOD.HYSTERESIS,
            hideDelay: LOD.HIDE_DELAY_MS,
            minimumLevel: 0,                 // GeoServer 타일 요청 최소 레벨
            maximumLevel: 18,                // GeoServer 타일 요청 최대 레벨
            parameters: {
                tiled: "true",               // GeoServer 메타타일링 (라벨 중복 방지 핵심)
                transparent: "true",
                version: "1.1.1",
                service: "WMS",
                request: "GetMap",
                format: "image/png"          // 용량을 줄이려면 image/png8 을 시험해 볼 수 있다 (라벨 가장자리 품질 확인 필요)
            }
        };

        this.options = {
            ...defaultOptions,
            ...options,
            parameters: {
                ...defaultOptions.parameters,
                ...(options.parameters || {})
            }
        };
    }

    /** 컨트롤러에 등록한다. 레이어는 지금 고도에서 필요할 때 비로소 만들어진다. */
    attach(immediate = true) {
        this.failed = false;
        lodController.register(this);
        if (immediate) lodController.tick(true);
        return this;
    }

    detach() {
        clearTimeout(this.hideTimer);
        this.hideTimer = null;
        this.isVisible = false;
        if (this.layer && this.nativeVisible) {
            this.layer.setVisible(false);
            this.nativeVisible = false;
            requestRender();
        }
        lodController.unregister(this);
    }

    /** 기존 코드와의 호환: 예전에는 init() 이 레이어를 바로 만들었다. 이제는 attach() 와 같다. */
    init() {
        return this.attach(true);
    }

    /** 사용자 on/off. 꺼 둔 레이어는 고도가 맞아도 LOD 가 다시 켜지 않는다. */
    setVisible(visible) {
        this.enabled = Boolean(visible);
        lodController.tick(true);
    }

    refresh() {
        requestRender();
    }

    /** 기존 코드와의 호환 */
    checkVisibilityByCamera() {
        lodController.tick(true);
    }

    /**
     * 주어진 고도에서 이 레이어가 보여야 하는지 판단한다.
     * 현재 보이는 레이어는 범위를 hysteresis 만큼 넓게, 숨은 레이어는 좁게 본다.
     * 인접한 두 레이어가 같은 경계값을 쓰면 전환 고도가 정확히 일치해서 겹침/공백이 생기지 않는다.
     */
    wantsVisible(altitude) {
        if (!this.enabled || this.failed) return false;
        const { minDistance, maxDistance, hysteresis } = this.options;
        const k = this.isVisible ? hysteresis : -hysteresis;
        return altitude >= minDistance * (1 - k) && altitude < maxDistance * (1 + k);
    }

    applyVisible(visible) {
        if (visible) {
            this.isVisible = true;
            clearTimeout(this.hideTimer);
            this.hideTimer = null;

            if (!this.createNative()) {
                this.isVisible = false;
                return;
            }
            if (!this.nativeVisible) {
                this.layer.setVisible(true);
                this.nativeVisible = true;
            }
            return;
        }

        this.isVisible = false;
        if (!this.layer || !this.nativeVisible) return;

        const delay = this.options.hideDelay;
        if (delay > 0) {
            clearTimeout(this.hideTimer);
            this.hideTimer = setTimeout(() => {
                this.hideTimer = null;
                // 기다리는 사이 다시 보여야 하는 상태가 되었다면 숨기지 않는다
                if (!this.isVisible && this.layer && this.nativeVisible) {
                    this.layer.setVisible(false);
                    this.nativeVisible = false;
                    requestRender();
                }
            }, delay);
        } else {
            this.layer.setVisible(false);
            this.nativeVisible = false;
        }
    }

    createNative() {
        if (this.layer) return true;
        if (this.failed) return false;

        try {
            const layerList = new Module.JSLayerList(false);
            const layer = layerList.createWMSLayer(this.layerId);
            if (!layer) throw new Error("createWMSLayer 실패");

            layer.setBBoxOrder(true);

            // 이 클래스 전용 옵션(고도/전환 설정)은 엔진에 넘기지 않는다.
            // 가시거리는 JS 쪽(LOD 컨트롤러)에서만 제어한다 — 엔진 setMin/MaxDistance 와 이중으로 걸면 서로 충돌할 수 있다.
            const { minDistance, maxDistance, hysteresis, hideDelay, ...provider } = this.options;
            if (!layer.setWMSProvider(provider)) throw new Error("setWMSProvider 실패");

            layer.setWMSTransparent(true);

            this.layerList = layerList;
            this.layer = layer;
            console.log(`${this.layerId} WMS 레이어가 성공적으로 로드되었습니다.`);
            return true;
        } catch (e) {
            this.failed = true;
            console.error(`${this.layerId} WMS 레이어 생성에 실패했습니다.`, e);
            return false;
        }
    }
}

// =========================================================================
// 인스턴스 레이어 정의 (고도 단위: m)
// 인접 레이어는 같은 경계값을 공유한다 → 컨트롤러가 그 지점에서 한 번에 바톤을 넘긴다.
// =========================================================================

// 시도 레이어: 150km 이상 고도에서 표출
export const wmsLayerSIDO = new GeoServerWmsLayer("GEOSERVER_MAP_KOREA_SIDO", {
    url: GEOSERVER_WMS_URL,
    layer: "map:TL_SCCO_CTPRVN",
    minDistance: 150000.0,
    maxDistance: 5000000.0,
});

// 시군구 레이어: 35km ~ 150km 고도에서 표출
export const wmsLayerSIG = new GeoServerWmsLayer("GEOSERVER_MAP_KOREA_SIG", {
    url: GEOSERVER_WMS_URL,
    layer: "map:korea_sig_utf8",
    minDistance: 35000.0,
    maxDistance: 150000.0,
});

// 행정동 레이어: 0m ~ 35km 고도에서 표출
export const wmsLayerADM = new GeoServerWmsLayer("GEOSERVER_MAP_KOREA_ADM", {
    url: GEOSERVER_WMS_URL,
    layer: "map:korea_adm_utf8",
    minDistance: 0.0,
    maxDistance: 35000.0,
});

export const wmsLayers = [wmsLayerSIDO, wmsLayerSIG, wmsLayerADM];
export const wmsLodController = lodController;

/** 세 레이어를 한 번에 등록하고, 판단은 한 번만 수행한다 (엔진 파일에서 호출). */
export function startWmsLayers() {
    wmsLayers.forEach((l) => l.attach(false));
    lodController.tick(true);
}

export function stopWmsLayers() {
    wmsLayers.forEach((l) => l.detach());
}