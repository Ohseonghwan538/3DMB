/* 아이콘 생성 함수 */
export function createIcon(_iconName, _url, _imageLoadedCallback) {
    var img = new Image();
    img.crossOrigin = "Anonymous"; // CORS 문제 방지
    img.onload = function() {
        var canvas = document.createElement('canvas');
        canvas.width = img.width;
        canvas.height = img.height;
        
        var ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0);
        
        var imageData = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
        
        if (Module.getSymbol().insertIcon(_iconName, imageData, canvas.width, canvas.height)) {          
            if (_imageLoadedCallback) {
                _imageLoadedCallback(_iconName);
            }
        }
    };
    img.src = _url;
}

/* 고도 Offset을 적용한 3D Vector3D 좌표 생성 헬퍼 함수 */
export function createPositionWithOffset(_basePosition, _altitudeOffset) {
    // _basePosition이 JSVector3D 객체이거나 일반 {longitude, latitude, altitude} 객체인 경우 모두 대응
    var lon = _basePosition.longitude || _basePosition.Longitude || _basePosition.x;
    var lat = _basePosition.latitude || _basePosition.Latitude || _basePosition.y;
    var alt = (_basePosition.altitude || _basePosition.Altitude || _basePosition.z) + _altitudeOffset;

    return new Module.JSVector3D(lon, lat, alt);
}

/* 카메라 이동 헬퍼 함수 */
export function moveCameraTo(lon, lat, alt) {
    if (typeof Module !== "undefined" && Module.getViewCamera) {
        // 지정된 좌표 위로 카메라 이동
        Module.getViewCamera().move(new Module.JSVector3D(lon, lat - 0.005, alt + 200.0), 20, 0, 0);
    }
}
