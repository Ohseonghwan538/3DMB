class GeoServerWmsLayer {
    constructor(layerId, options = {}) {
        this.layerId = layerId;
        this.layerList = null;
        this.layer = null;
        this.isVisible = true;

        const defaultOptions = {
            tileSize: 256,
            crs: "EPSG:4326",
            minDistance: 0,         // 기본 가시거리 (최소)
            maxDistance: 99999999,  // 기본 가시거리 (최대)
            minimumLevel: 0,        // GeoServer 타일 요청을 위한 최소 레벨 기본값
            maximumLevel: 18,       // GeoServer 타일 요청을 위한 최대 레벨 기본값
            parameters: {
                tiled: "true",           // GeoServer MetaTiling 활성화 (라벨 중복 방지 핵심)
                transparent: "true",      // 지도 배경 투명화
                version: "1.1.1",
                service: "WMS",
                request: "GetMap",
                format: "image/png"
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

    init() {
        if (this.layer) return false;
        this.layerList = new Module.JSLayerList(false);
        this.layer = this.layerList.createWMSLayer(this.layerId);

        if (this.layer) {
            this.layer.setBBoxOrder(true);

            // XDWorld 기본 가시거리 설정 적용
            if (typeof this.layer.setMinDistance === "function") {
                this.layer.setMinDistance(this.options.minDistance);
                this.layer.setMaxDistance(this.options.maxDistance);
            }

            const isSuccess = this.layer.setWMSProvider(this.options);

            if (isSuccess) {
                this.layer.setWMSTransparent(true);
                this.bindCameraEvent();
                console.log(`${this.layerId} WMS 레이어가 성공적으로 로드되었습니다.`);
                
                // 초기 카메라 위치에 맞게 가시성 1회 체크
                this.checkVisibilityByCamera();
            } else {
                console.error(`${this.layerId} WMS Provider 설정에 실패했습니다.`);
            }
        }
    }

    setVisible(visible) {
        if (!this.layer) return;
        this.isVisible = Boolean(visible);
        this.layer.setVisible(this.isVisible);
        this.refresh();
    }

    refresh() {
        if (typeof Module !== "undefined" && typeof Module.XDRenderData === "function") {
            Module.XDRenderData(); 
        }
    }

    /**
     * 현재 카메라의 고도를 체크하여 레이어의 실시간 가시성을 제어합니다.
     */
    checkVisibilityByCamera() {
        if (!this.layer || !Module.getViewCamera) return;

        const camera = Module.getViewCamera();
        if (!camera) return;

        // [오류 수정] 공식 문서 명세에 맞춰 독립 메서드인 getAltitude()를 직접 호출합니다.
        const currentAltitude = camera.getAltitude(); 

        // 설정된 가시거리 범위를 벗어나면 레이어를 숨기고, 범위 내에 있으면 켭니다.
        if (currentAltitude >= this.options.minDistance && currentAltitude <= this.options.maxDistance) {
            if (!this.isVisible) this.setVisible(true);
        } else {
            if (this.isVisible) this.setVisible(false);
        }
    }

    bindCameraEvent() {
        if (!Module.canvas) return;

        let moveTimer = null;
        
        // 마우스 업(카메라 이동 완료) 시점에 체크
        Module.canvas.addEventListener("mouseup", () => {
            clearTimeout(moveTimer);
            moveTimer = setTimeout(() => {
                this.checkVisibilityByCamera();
                this.refresh();
            }, 150); 
        });

        // 마우스 휠(줌인/아웃) 시점에도 즉각 반응하도록 추가
        Module.canvas.addEventListener("mousewheel", () => {
            clearTimeout(moveTimer);
            moveTimer = setTimeout(() => {
                this.checkVisibilityByCamera();
                this.refresh();
            }, 200);
        });
    }
}

// =========================================================================
// 인스턴스 레이어 정의 (Distance 미터 단위 수치 적용)
// minimumLevel과 maximumLevel을 명시하여 localhost:8080 타일 요청 깨짐 현상 방지
// =========================================================================

// 시도 레이어: 대한민국 전도 고도 (약 150km 이상 고도에서만 표출)
export const wmsLayerSIDO = new GeoServerWmsLayer("GEOSERVER_MAP_KOREA_SIDO", {
    url: "http://localhost:8085/geoserver/map/wms?",
    layer: "map:TL_SCCO_CTPRVN",
    minimumLevel: 0,   
    maximumLevel: 18,
    minDistance: 150000.0,       // 150km 이하로 내려가면(줌인) 안 보임
    maxDistance: 5000000.0,      // 5,000km 이상 멀어지면 안 보임
});

// 시군구 레이어: 중간 고도 (35km ~ 150km 고도에서 표출)
export const wmsLayerSIG = new GeoServerWmsLayer("GEOSERVER_MAP_KOREA_SIG", {
    url: "http://localhost:8085/geoserver/map/wms?",
    layer: "map:korea_sig_utf8",
    minimumLevel: 0,   
    maximumLevel: 18,
    minDistance: 35000.0,        // 35km 이하로 내려가면 안 보임 (행정동에게 바톤 터치)
    maxDistance: 150000.0       // 150km 이상 멀어지면 안 보임 (시도에게 바톤 터치)
});

// 행정동 레이어: 상세 고도 (0m ~ 35km 고도에서 표출)
export const wmsLayerADM = new GeoServerWmsLayer("GEOSERVER_MAP_KOREA_ADM", {
    url: "http://localhost:8085/geoserver/map/wms?",
    layer: "map:korea_adm_utf8",
    minimumLevel: 0,   
    maximumLevel: 18,
    minDistance: 0.0,            // 끝까지 줌인해도 계속 보임
    maxDistance: 35000.0        // 35km 이상 멀어지면 안 보임
});
