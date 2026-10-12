// 건물 타일 레이어 생성 파일 
class TileLayerManager {
    constructor() {
        this.layer = null;
        this.layerName = "facility_build";
        this.visible = true;
    }

    /**
     * XDServer 건물 3D 타일 레이어 초기화
     */
    init() {
        if (typeof Module === "undefined" || !Module.getTileLayerList) {
            console.error("XDWorld Module이 아직 로드되지 않았습니다.");
            return;
        }

        // 이미 생성되어 있다면 중복 생성 방지
        if (this.layer) return;

        // VWorld 3D 건물 타일 레이어 생성 및 옵션 설정
        this.layer = Module.getTileLayerList().createXDServerLayer({
            url: "https://xdworld.vworld.kr",
            servername: "XDServer3d",
            name: this.layerName,
            type: 9, // 3D 시설물/건물 타일 타입
            minLevel: 0,
            maxLevel: 15
        });

        // LOD(가시 범위) 설정: 최소 1.5m, 최대 5km 
        Module.setVisibleRange("facility_build", 1.5, 5000.0);

        if (this.layer) {
            console.log(`🏢 타일 레이어 [${this.layerName}] 생성 완료`);
            this.refresh();
        }
    }

    refresh() {
        if (typeof Module.XDRenderData === "function") {
            Module.XDRenderData();
        }
    }

    setVisible(visible) {
        this.visible = Boolean(visible);
        if (this.layer && typeof this.layer.setVisible === "function") {
            this.layer.setVisible(this.visible);
            this.refresh();
        }
        return this.visible;
    }

    // toggleLayer(forcedVisible) {
    //     if (!this.layer || typeof this.layer.setVisible !== "function") {
    //         return this.visible;
    //     }

    //     // 외부 인자가 전달되면 그 값 사용, 없으면 자체 상태 반전
    //     this.visible = (typeof forcedVisible === "boolean") ? forcedVisible : !this.visible;

    //     // 2. XDWorld C++ 엔진의 setVisible 호출
    //     this.layer.setVisible(this.visible);
    //     this.refresh();

    //     return this.visible; // 3. 변경된 상태를 main.js로 반환!
    // }

}

export const tileLayer = new TileLayerManager();
