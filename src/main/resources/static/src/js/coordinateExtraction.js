/**
 * 사진 파일 또는 Blob 객체로부터 GPS 메타데이터를 추출합니다.
 */
export async function extractGpsMetadataAsync(fileOrBlob) {
    if (!fileOrBlob) return null;

    try {
        // 전체 EXIF 객체를 추출하도록 옵션 변경 (strict 제한 해제)
        const gps = await exifr.parse(fileOrBlob, true);

        console.log("🔍 exifr 파싱 전체 결과:", gps);

        if (!gps || gps.latitude === undefined || gps.longitude === undefined) {
            console.warn("⚠️ exifr 파싱 실패: latitude/longitude 속성을 찾을 수 없습니다.");
            return null;
        }

        let alt = gps.GPSAltitude || gps.altitude || 0.0;
        const altRef = gps.GPSAltitudeRef;
        const isBelowSeaLevel = Array.isArray(altRef) ? altRef[0] === 1 : altRef === 1;
        if (isBelowSeaLevel) alt = -alt;

        return {
            lon: Number(gps.longitude),
            lat: Number(gps.latitude),
            alt: Number(alt)
        };

    } catch (error) {
        console.error(`❌ 추출 중 오류 발생: ${error.message}`);
        return null;
    }
}