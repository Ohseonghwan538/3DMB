/**
 * XDWorld ScreenToMapPointEX 반환 객체에서 안전하게 좌표 추출
 */
export function parseMapPoint(mapPoint) {
    if (!mapPoint) return null;

    // 버전에 따른 프로퍼티 대응 (x, X, longitude, Longitude)
    const lon = mapPoint.x ?? mapPoint.X ?? mapPoint.longitude ?? mapPoint.Longitude;
    const lat = mapPoint.y ?? mapPoint.Y ?? mapPoint.latitude ?? mapPoint.Latitude;
    const alt = mapPoint.z ?? mapPoint.Z ?? mapPoint.altitude ?? mapPoint.Altitude ?? 0;

    // 숫자 유효성 검증 (undefined, null, NaN 및 0,0 예외 제외)
    if (typeof lon !== 'number' || typeof lat !== 'number' || isNaN(lon) || isNaN(lat) || (lon === 0 && lat === 0)) {
        return null;
    }

    return { lon, lat, alt };
}

/**
 * 브라우저 캔버스 CSS 크기 대비 실제 렌더링 해상도 좌표 변환
 */
export function getCanvasScreenPoint(Module, canvas, clientX, clientY) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;

    const screenX = (clientX - rect.left) * scaleX;
    const screenY = (clientY - rect.top) * scaleY;

    return new Module.JSVector2D(screenX, screenY);
}