import { fetchFestivalTypes, fetchFestivals, fetchRegions } from "./festivalGuest.js";
import { FestivalMap } from "./map.js";

const searchForm = document.getElementById("search-form");
const typeContainer = document.getElementById("festival-types");
const regionSelect = document.getElementById("sido");
const resultCount = document.getElementById("result-count");
const resultMessage = document.getElementById("result-message");
const resultList = document.getElementById("result-list");
const mapStatus = document.getElementById("map-status");
const mapLoader = document.getElementById("map-loader");
let festivals = [];
let festivalMap;
let searchSequence = 0;

function showResultError(message, retry) {
  resultMessage.replaceChildren(document.createTextNode(message));
  if (retry) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "inline-retry";
    button.textContent = "다시 시도";
    button.addEventListener("click", retry, { once: true });
    resultMessage.append(button);
  }
}

function showEngineError(message) {
  mapStatus.textContent = message;
  showResultError(message, () => location.reload());
  document.getElementById("map-loader-message").textContent = message;
  document.getElementById("map-loader-retry").hidden = false;
  mapLoader.classList.remove("is-done");
}

document.getElementById("map-loader-retry").addEventListener("click", () => location.reload());

function setTypes(types) {
  for (const type of types) {
    const label = document.createElement("label");
    label.className = "type-chip";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.name = "types";
    checkbox.value = type.typeCode;
    const name = document.createElement("span");
    name.textContent = type.typeName;
    label.append(checkbox, name);
    typeContainer.append(label);
  }
}

function setRegions(regions) {
  for (const region of regions) {
    const option = document.createElement("option");
    option.value = region;
    option.textContent = region;
    regionSelect.append(option);
  }
}

function getFilters() {
  const formData = new FormData(searchForm);
  return {
    keyword: String(formData.get("keyword") || "").trim(),
    startDate: String(formData.get("startDate") || ""),
    endDate: String(formData.get("endDate") || ""),
    sido: String(formData.get("sido") || ""),
    types: formData.getAll("types").map(String)
  };
}

function renderResults() {
  resultCount.replaceChildren();
  const count = document.createTextNode(festivals.length.toLocaleString("ko-KR"));
  const suffix = document.createElement("span");
  suffix.textContent = "건";
  resultCount.append(count, suffix);
  resultList.replaceChildren();
  if (festivals.length === 0) {
    resultMessage.textContent = "조건에 맞는 축제가 없습니다. 검색 조건을 바꿔 보세요.";
    const empty = document.createElement("li");
    empty.className = "empty-results";
    empty.textContent = "검색 결과가 없습니다.";
    resultList.append(empty);
    return;
  }
  resultMessage.textContent = "축제를 선택하면 지도에서 위치와 상세 정보를 확인할 수 있습니다.";
  const fragment = document.createDocumentFragment();
  for (const festival of festivals) {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.type = "button";
    button.className = "result-item";
    const icon = document.createElement("span");
    icon.className = "result-icon";
    const image = document.createElement("img");
    image.src = festival.iconUrl;
    image.alt = "";
    image.loading = "lazy";
    icon.append(image);
    const copy = document.createElement("span");
    copy.className = "result-copy";
    const title = document.createElement("strong");
    title.textContent = festival.name;
    const date = document.createElement("span");
    date.textContent = `${festival.startDate} ~ ${festival.endDate}`;
    copy.append(title, date);
    const arrow = document.createElement("span");
    arrow.className = "result-arrow";
    arrow.setAttribute("aria-hidden", "true");
    arrow.textContent = "›";
    button.append(icon, copy, arrow);
    button.addEventListener("click", () => festivalMap?.selectFestival(festival.id));
    item.append(button);
    fragment.append(item);
  }
  resultList.append(fragment);
}

async function applyFilters() {
  const sequence = ++searchSequence;
  resultMessage.textContent = "축제를 검색하고 있습니다.";
  try {
    const response = await fetchFestivals(getFilters());
    if (sequence !== searchSequence) return;
    festivals = response.items;
    renderResults();
    festivalMap?.closeInfo();
    await festivalMap?.setFestivals(festivals);
  } catch (error) {
    if (sequence !== searchSequence) return;
    showResultError(error.message, () => void applyFilters());
    mapStatus.textContent = error.message;
  }
}

searchForm.addEventListener("submit", (event) => {
  event.preventDefault();
  void applyFilters();
});
searchForm.addEventListener("reset", () => setTimeout(() => void applyFilters(), 0));
document.getElementById("home-view").addEventListener("click", () => {
  festivalMap?.moveCamera(127.5, 36.2, 400_000);
});
document.getElementById("fullscreen").addEventListener("click", () => {
  if (document.fullscreenElement) void document.exitFullscreen?.();
  else void document.documentElement.requestFullscreen?.();
});

async function startMap() {
  try {
    const [types, regions, response] = await Promise.all([
      fetchFestivalTypes(), fetchRegions(), fetchFestivals()
    ]);
    setTypes(types);
    setRegions(regions);
    festivals = response.items;
    renderResults();
    festivalMap = new FestivalMap(window.Module, window.Module.canvas, mapStatus);
    festivalMap.moveCamera(127.5, 36.2, 400_000, true);
    await festivalMap.setFestivals(festivals);
    mapLoader.classList.add("is-done");
  } catch (error) {
    showResultError(error.message, () => void applyFilters());
    mapStatus.textContent = error.message;
    mapLoader.classList.add("is-done");
  }
}

const webglProbe = document.createElement("canvas");
const webgl2 = webglProbe.getContext("webgl2");
webgl2?.getExtension("WEBGL_lose_context")?.loseContext();
if (!webgl2) {
  showEngineError("WebGL 2를 지원하는 최신 Chrome 또는 Edge에서 이용해 주세요.");
} else {
  let engineLoaded = false;
  const engineTimeout = setTimeout(() => {
    if (!engineLoaded) showEngineError("3D 지도 엔진을 불러오지 못했습니다.");
  }, 30_000);
  const handleEngineReady = () => {
    engineLoaded = true;
    clearTimeout(engineTimeout);
    void startMap();
  };
  window.addEventListener("XDWorldLoaded", handleEngineReady, { once: true });
  if (window.XDWorldReady) {
    window.removeEventListener("XDWorldLoaded", handleEngineReady);
    handleEngineReady();
  }
  window.addEventListener("error", (event) => {
    const source = event.target?.src || "";
    if (source.includes("XDWorldEM") || source.includes("/js/module.js")) {
      clearTimeout(engineTimeout);
      showEngineError("3D 지도 파일을 불러오지 못했습니다.");
    }
  }, true);
  window.addEventListener("unhandledrejection", () => {
    if (!engineLoaded) {
      clearTimeout(engineTimeout);
      showEngineError("3D 지도 엔진 초기화에 실패했습니다.");
    }
  });
}
