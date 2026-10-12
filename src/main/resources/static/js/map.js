import { cellSizeForAltitude, clusterFestivals } from "./cluster.js";
import { createInfoElement, renderInfo, renderInfoError } from "./infoTemplate.js";
import { fetchFestivalDetail } from "./festivalGuest.js";

const INITIAL_CAMERA_ALTITUDE = 400_000;
const INFO_LAYER_NAME = "FESTIVAL_INFO";
const MARKER_LAYER_NAME = "FESTIVAL_MARKERS";

function pointCoordinate(point, key, alternative) {
  const value = point?.[key] ?? point?.[key.toUpperCase()] ?? point?.[alternative];
  return Number.isFinite(Number(value)) ? Number(value) : null;
}

export class FestivalMap {
  constructor(Module, canvas, statusElement) {
    this.Module = Module;
    this.canvas = canvas;
    this.statusElement = statusElement;
    this.map = Module.getMap();
    this.markerLayer = this.getLayer(MARKER_LAYER_NAME);
    this.infoLayer = this.getLayer(INFO_LAYER_NAME);
    this.markerLayer?.setMaxDistance?.(20_000_000);
    this.infoLayer?.setMaxDistance?.(20_000_000);
    this.markers = new Map();
    this.infoWindows = new Map();
    this.details = new Map();
    this.festivals = [];
    this.selectedId = null;
    this.renderSequence = 0;
    this.refreshTimer = null;
    this.initializingView = true;
    this.initialViewTimer = null;
    if (!this.map || !this.markerLayer || !this.infoLayer) {
      throw new Error("XDWorld 축제 레이어를 초기화하지 못했습니다.");
    }
    this.bindMapEvents();
  }

  getLayer(name) {
    const layers = new this.Module.JSLayerList(true);
    const existing = layers.nameAtLayer?.(name);
    if (existing) return existing;
    if (this.Module.ELT_POLYHEDRON == null) return null;
    return layers.createLayer(name, this.Module.ELT_POLYHEDRON);
  }

  bindMapEvents() {
    this.canvas.addEventListener("click", () => this.closeInfo());
    this.canvas.addEventListener("mouseup", () => this.scheduleRefresh());
    this.canvas.addEventListener("wheel", () => this.scheduleRefresh());
    this.canvas.addEventListener("touchend", () => this.scheduleRefresh());
    window.addEventListener("resize", () => this.scheduleRefresh());
    this.Module.cbMoveEnd = () => {
      this.initializingView = false;
      clearTimeout(this.initialViewTimer);
      this.infoWindows.get(this.selectedId)?.object.refresh?.();
      this.scheduleRefresh();
      this.Module.XDRenderData?.();
    };
  }

  async setFestivals(festivals) {
    this.festivals = festivals;
    await this.refresh();
  }

  scheduleRefresh() {
    clearTimeout(this.refreshTimer);
    this.refreshTimer = setTimeout(() => void this.refresh(), 200);
  }

  async refresh() {
    const sequence = ++this.renderSequence;
    const viewport = this.getViewportBounds();
    const visible = viewport
      ? this.festivals.filter((festival) => this.isInside(festival, viewport))
      : this.festivals;
    const altitude = this.cameraAltitude();
    const clusteringAltitude = this.initializingView
      ? Math.max(altitude, INITIAL_CAMERA_ALTITUDE)
      : altitude;
    const displays = clusterFestivals(visible, cellSizeForAltitude(clusteringAltitude));
    try {
      if (sequence !== this.renderSequence) return;
      this.draw(displays);
      this.statusElement.textContent = `${visible.length.toLocaleString("ko-KR")}개 축제 · ${clusteringAltitude >= 10_000 ? "클러스터 보기" : "개별 축제 보기"}`;
    } catch (error) {
      this.statusElement.textContent = error.message;
      throw error;
    }
  }

  draw(displays) {
    const nextKeys = new Set(displays.map((item) => item.key));
    for (const [key, marker] of this.markers) {
      if (!nextKeys.has(key)) {
        this.markerLayer.removeAtKey?.(marker.objectId);
        marker.element.remove();
        this.markers.delete(key);
      }
    }
    for (const item of displays) {
      const latitude = item.kind === "festival" ? item.festival.lat : item.lat;
      const longitude = item.kind === "festival" ? item.festival.lng : item.lng;
      const position = new this.Module.JSVector3D(
        longitude, latitude, this.terrainHeight(longitude, latitude) + 2
      );
      let marker = this.markers.get(item.key);
      if (!marker) {
        marker = this.createMarker(item, position);
        this.markerLayer.addObject(marker.object, 0);
        this.markers.set(item.key, marker);
      }
      marker.item = item;
      marker.object.position = position;
      marker.object.refresh?.();
    }
    this.Module.XDRenderData?.();
  }

  createMarker(item, position) {
    const isCluster = item.kind === "cluster";
    const objectId = `festival-${item.key.replaceAll(":", "-")}`;
    const object = this.Module.createHTMLObject(objectId);
    if (!object) throw new Error("축제 마커 객체를 생성하지 못했습니다.");
    const element = document.createElement("button");
    element.type = "button";
    element.className = `festival-map-marker${isCluster ? " festival-map-marker--cluster" : ""}`;
    element.setAttribute(
      "aria-label",
      isCluster ? `축제 ${item.count}개가 모인 지역, 확대 보기` : `${item.festival.name} 정보 보기`
    );
    if (isCluster) {
      element.textContent = item.count.toLocaleString("ko-KR");
    } else {
      const image = document.createElement("img");
      image.alt = "";
      image.draggable = false;
      image.loading = "lazy";
      image.src = item.festival.iconUrl;
      element.append(image);
    }
    element.addEventListener("pointerdown", (event) => event.stopPropagation());
    element.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (isCluster) {
        this.closeInfo();
        this.zoomToCluster(item);
      } else {
        void this.openInfo(item.festival.id);
      }
    });
    const container = document.getElementById("festival-marker-container");
    container.append(element);
    const result = object.createbyJson({
      position,
      container: container.id,
      canvas: this.canvas,
      element,
      verticalAlign: "bottom",
      horizontalAlign: "center"
    });
    if (result?.result !== 1) {
      element.remove();
      throw new Error(`축제 마커를 지도에 배치하지 못했습니다: ${result?.return || "XDWorld 오류"}`);
    }
    return { item, objectId, object, element };
  }

  terrainHeight(longitude, latitude) {
    const fastHeight = Number(this.map.getTerrHeightFast?.(longitude, latitude));
    if (Number.isFinite(fastHeight)) return fastHeight;
    const terrainHeight = Number(this.map.getTerrHeight?.(longitude, latitude));
    return Number.isFinite(terrainHeight) ? terrainHeight : 0;
  }

  cameraAltitude() {
    const location = this.Module.getViewCamera?.()?.getLocation?.();
    return Math.abs(pointCoordinate(location, "z", "altitude") ?? 1_000_000);
  }

  getViewportBounds() {
    const width = this.canvas.width;
    const height = this.canvas.height;
    const points = [
      new this.Module.JSVector2D(0, 0),
      new this.Module.JSVector2D(width, 0),
      new this.Module.JSVector2D(0, height),
      new this.Module.JSVector2D(width, height)
    ];
    const coordinates = points.map((point) => {
      const location = this.map.ScreenToMapPointEX?.(point);
      const lng = pointCoordinate(location, "x", "longitude");
      const lat = pointCoordinate(location, "y", "latitude");
      return lng === null || lat === null ? null : { lng, lat };
    });
    if (coordinates.some((point) => point === null)) return null;
    const longitudes = coordinates.map((point) => point.lng);
    const latitudes = coordinates.map((point) => point.lat);
    const lngMargin = (Math.max(...longitudes) - Math.min(...longitudes)) * 0.2;
    const latMargin = (Math.max(...latitudes) - Math.min(...latitudes)) * 0.2;
    return {
      minLng: Math.min(...longitudes) - lngMargin,
      maxLng: Math.max(...longitudes) + lngMargin,
      minLat: Math.min(...latitudes) - latMargin,
      maxLat: Math.max(...latitudes) + latMargin
    };
  }

  isInside(festival, bounds) {
    return festival.lng >= bounds.minLng && festival.lng <= bounds.maxLng
      && festival.lat >= bounds.minLat && festival.lat <= bounds.maxLat;
  }

  zoomToCluster(cluster) {
    const currentAltitude = this.cameraAltitude();
    const targetAltitude = Math.max(8_000, currentAltitude / 2);

    // 고도(m)에 비례하여 동적 위도 오프셋 계산 (위도 1° ≈ 111,000m)
    // targetAltitude 높이에서 화면 상단 영역에 위치하도록 비율(0.25) 적용
    const latOffset = (targetAltitude / 111_000) * 0.25;

    this.moveCamera(cluster.lng, cluster.lat - latOffset, targetAltitude);
  }

  moveCamera(longitude, latitude, altitude, initialView = false) {
    this.initializingView = initialView;
    clearTimeout(this.initialViewTimer);
    this.Module.getViewCamera()?.move(
      new this.Module.JSVector3D(longitude, latitude, altitude),
      70, 0, 10
    );
    if (initialView) {
      this.initialViewTimer = setTimeout(() => {
        if (!this.initializingView) return;
        this.initializingView = false;
        this.scheduleRefresh();
      }, 5_000);
    }
    this.scheduleRefresh();
  }

  async selectFestival(id) {
    const festival = this.festivals.find((item) => String(item.id) === String(id));
    if (!festival) return;
    this.moveCamera(festival.lng, festival.lat - 0.05, Math.min(this.cameraAltitude(), 20_000));
    await this.openInfo(festival.id);
  }

  async openInfo(id) {
    this.closeInfo();
    this.selectedId = String(id);
    const overlay = this.getInfoWindow(id);
    const marker = this.festivals.find((item) => String(item.id) === String(id));
    renderInfo(overlay.element, marker);
    overlay.element.card.hidden = false;
    overlay.object.setVisible?.(true);
    overlay.object.refresh?.();
    this.Module.XDRenderData?.();
    try {
      let detail = this.details.get(String(id));
      if (!detail) {
        detail = await fetchFestivalDetail(id);
        this.details.set(String(id), detail);
      }
      if (this.selectedId !== String(id)) return;
      renderInfo(overlay.element, detail);
      overlay.element.card.hidden = false;
      overlay.object.setVisible?.(true);
      overlay.object.refresh?.();
    } catch (error) {
      if (this.selectedId !== String(id)) return;
      renderInfoError(overlay.element, error.message, () => {
        this.details.delete(String(id));
        void this.openInfo(id);
      });
      overlay.element.card.hidden = false;
      overlay.object.setVisible?.(true);
      overlay.object.refresh?.();
    }
  }

  getInfoWindow(id) {
    const key = String(id);
    if (this.infoWindows.has(key)) return this.infoWindows.get(key);
    const festival = this.festivals.find((item) => String(item.id) === key);
    const element = createInfoElement();
    element.close.addEventListener("click", () => this.closeInfo());
    const container = document.getElementById("festival-info-container");
    container.append(element.card);
    const objectId = `festival-info-${key}`;
    const object = this.Module.createHTMLObject(objectId);
    if (!object) throw new Error("축제 정보창 객체를 생성하지 못했습니다.");
    const result = object.createbyJson({
      position: new this.Module.JSVector3D(festival.lng, festival.lat, this.terrainHeight(festival.lng, festival.lat) + 30),
      container: container.id,
      canvas: this.canvas,
      element: element.card,
      verticalAlign: "bottom",
      horizontalAlign: "center"
    });
    if (result?.result !== 1) {
      element.card.remove();
      throw new Error(`축제 정보창을 준비하지 못했습니다: ${result?.return || "XDWorld 오류"}`);
    }
    object.setVisible?.(false);
    this.infoLayer.addObject(object, 0);
    const record = { element, object, objectId };
    this.infoWindows.set(key, record);
    return record;
  }

  closeInfo() {
    if (this.selectedId === null) return;
    const previous = this.infoWindows.get(this.selectedId);
    if (previous) {
      previous.element.card.hidden = true;
      previous.object.setVisible?.(false);
      previous.object.refresh?.();
    }
    this.selectedId = null;
    this.Module.XDRenderData?.();
  }
}
