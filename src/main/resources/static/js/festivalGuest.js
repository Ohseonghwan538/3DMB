const REQUEST_TIMEOUT_MS = 10_000;

export async function requestJson(url, options = {}) {
  let response;
  try {
    response = await fetch(url, {
      ...options,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      headers: { Accept: "application/json", ...options.headers }
    });
  } catch (error) {
    if (error.name === "TimeoutError") throw new Error("요청 시간이 초과되었습니다. 다시 시도해 주세요.");
    throw new Error("서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.");
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message = body?.message || body?.error?.message || `요청에 실패했습니다 (${response.status}).`;
    throw new Error(message);
  }
  return body;
}

export function buildFestivalQuery(filters) {
  const params = new URLSearchParams();
  for (const key of ["keyword", "startDate", "endDate", "sido"]) {
    if (filters[key]) params.set(key, filters[key]);
  }
  if (filters.types?.length) params.set("types", filters.types.join(","));
  return params.toString();
}

export function fetchFestivals(filters = {}) {
  const query = buildFestivalQuery(filters);
  return requestJson(`/api/festivals${query ? `?${query}` : ""}`);
}

export const fetchFestivalDetail = (id) => requestJson(`/api/festivals/${encodeURIComponent(id)}`);
export const fetchFestivalTypes = () => requestJson("/api/festival-types");
export const fetchRegions = () => requestJson("/api/regions");
