const markers = new Map();
let markerObjectSequence = 0;

function setMarkerElementVisible(element, visible) {
    if (!(element instanceof HTMLElement)) return;
    element.hidden = !visible;
    if (visible) element.style.removeProperty('display');
    else element.style.setProperty('display', 'none', 'important');
}

function updateMarkerOffsets() {
    const visible = [...markers.entries()]
        .filter(([, record]) => !record.capsule.isPreview)
        .sort(([keyA], [keyB]) => keyA.localeCompare(keyB));
    const locations = new Map();
    for (const [key, record] of visible) {
        const locationKey = `${record.longitude.toFixed(7)},${record.latitude.toFixed(7)}`;
        const locationMarkers = locations.get(locationKey) || [];
        locationMarkers.push([key, record]);
        locations.set(locationKey, locationMarkers);
    }
    for (const locationMarkers of locations.values()) {
        locationMarkers.forEach(([, record], index) => {
            record.element.style.marginLeft = `${(index - (locationMarkers.length - 1) / 2) * 50}px`;
        });
    }
}

export function renderCapsuleBillboard(Module, capsule, isStillVisible = () => true, onSelect = null) {
    const latitude = Number(capsule.latitude ?? capsule.lat);
    const longitude = Number(capsule.longitude ?? capsule.lon);
    if (!Module || !Number.isFinite(latitude) || !Number.isFinite(longitude)) return;

    const key = capsule.poiId != null ? `poi_${capsule.poiId}` : `capsule_${capsule.capsuleId}`;
    const layers = new Module.JSLayerList(true);
    let layer = layers.nameAtLayer?.('CAPSULES');
    if (!layer) {
        if (Module.ELT_POLYHEDRON == null) {
            console.error('XDWorld HTML overlay layer type is not available.');
            return;
        }
        layer = layers.createLayer('CAPSULES', Module.ELT_POLYHEDRON);
    }
    layer.setMaxDistance?.(2_000_000);

    if (!isStillVisible()) return;

    let container = document.getElementById('poi-overlay-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'poi-overlay-container';
        document.body.appendChild(container);
    }

    const map = Module.getMap?.();
    let terrainHeight = Number(map?.getTerrHeightFast?.(longitude, latitude));
    if (!Number.isFinite(terrainHeight)) terrainHeight = Number(map?.getTerrHeight?.(longitude, latitude));
    const altitude = Number(capsule.altitude);
    const displayAltitude = (Number.isFinite(terrainHeight) ? terrainHeight : Number.isFinite(altitude) ? altitude : 0) + 2;

    const capsuleData = capsule.poiId != null ? {
        ...capsule,
        latitude,
        longitude,
        displayAltitude,
        groupId: capsule.groupId ?? capsule.capsuleId,
        startedAt: capsule.takenAt || new Date().toISOString(),
        memo: capsule.content || '',
        images: [{
            imageId: capsule.poiId,
            originalFilePath: capsule.imageUrl || `/api/pois/${capsule.poiId}/image`,
            compressedFilePath: capsule.thumbUrl || `/api/pois/${capsule.poiId}/thumb`
        }]
    } : { ...capsule, latitude, longitude, displayAltitude };

    let record = markers.get(key);
    let marker = record?.marker || layer.searchObject?.(key);
    if (!record && marker) {
        const element = marker.element;
        if (element instanceof HTMLElement) {
            record = { marker, element, objectId: key };
            markers.set(key, record);
        } else {
            console.error(`Existing POI HTML marker ${key} has no HTML element; reload the map to recreate it.`);
            return;
        }
    }

    if (!record) {
        const markerElement = document.createElement('button');
        markerElement.type = 'button';
        markerElement.className = 'poi-map-marker';
        markerElement.dataset.poiKey = key;
        markerElement.setAttribute('aria-label', `${capsule.title || '추억'} 정보 보기`);
        const image = document.createElement('img');
        image.alt = '';
        image.draggable = false;
        markerElement.appendChild(image);

        record = { markerElement, marker: null };
        markerElement.addEventListener('pointerdown', (event) => event.stopPropagation());
        markerElement.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            if (!record.capsule.isPreview && record.isStillVisible()) {
                record.onSelect?.(record.capsuleData, record.marker);
            }
        });
        const objectId = `poi-marker-${++markerObjectSequence}-${key}`;
        record.objectId = objectId;
        record.marker = Module.createHTMLObject(objectId);
        if (!record.marker) {
            console.error(`Could not create POI HTML marker ${objectId}`);
            return;
        }
        const result = record.marker.createbyJson({
            position: new Module.JSVector3D(longitude, latitude, displayAltitude),
            container: container.id,
            canvas: Module.canvas,
            element: markerElement,
            verticalAlign: 'bottom',
            horizontalAlign: 'center'
        });
        if (result?.result !== 1) {
            markerElement.remove();
            console.error(`Could not position POI HTML marker ${objectId}`, result);
            return;
        }
        record.element = markerElement;
        layer.addObject(record.marker, 0);
        markers.set(key, record);
    }

    const markerElement = record.element;
    record.Module = Module;
    markerElement.dataset.poiKey = key;
    const groupColor = /^#[0-9A-Fa-f]{6}$/.test(capsule.groupColor || '') ? capsule.groupColor : '#4263EB';
    markerElement.style.setProperty('--poi-marker-color', groupColor);
    markerElement.setAttribute('aria-label', `${capsule.title || '추억'} 정보 보기`);
    const image = markerElement.querySelector('img');
    const thumbUrl = capsule.thumbUrl || capsule.images?.[0]?.compressedFilePath
        || capsule.compressedFilePath || '/src/components/NO.png';
    if (image && image.dataset.src !== thumbUrl) {
        image.onerror = () => {
            if (image.src.endsWith('/src/components/NO.png')) return;
            console.warn(`캡슐 이미지가 없어 기본 썸네일로 대체합니다: ${thumbUrl}`);
            image.src = '/src/components/NO.png';
        };
        image.dataset.src = thumbUrl;
        image.src = thumbUrl;
    }
    record.capsule = capsule;
    record.longitude = longitude;
    record.latitude = latitude;
    record.capsuleData = capsuleData;
    record.isStillVisible = isStillVisible;
    record.onSelect = onSelect;
    record.marker.capsuleData = capsuleData;
    record.marker.position = new Module.JSVector3D(longitude, latitude, displayAltitude);
    setMarkerElementVisible(markerElement, true);
    record.marker.refresh?.();
    updateMarkerOffsets();
    Module.XDRenderData?.();
}

export function setCapsuleMarkersVisible(visibleKeys) {
    for (const [key, record] of markers) {
        if (record.capsule.isPreview) continue;
        setMarkerElementVisible(record.element, visibleKeys.has(key));
        record.marker.refresh?.();
    }
    const container = document.getElementById('poi-overlay-container');
    for (const element of container?.querySelectorAll('[data-poi-key]') || []) {
        if (!element.dataset.poiKey.startsWith('poi_')) continue;
        setMarkerElementVisible(element, visibleKeys.has(element.dataset.poiKey));
    }
    for (const element of container?.querySelectorAll('[id^="poi_"]') || []) {
        if (element.id === 'poi-html-overlay-object') continue;
        setMarkerElementVisible(element, visibleKeys.has(element.id));
    }
    updateMarkerOffsets();
}

export function removeCapsuleMarker(poiId) {
    const key = `poi_${poiId}`;
    window.renderedPoiKeys?.delete(key);
    const record = markers.get(key);
    const container = document.getElementById('poi-overlay-container');
    for (const element of container?.querySelectorAll('[data-poi-key]') || []) {
        if (element.dataset.poiKey === key) setMarkerElementVisible(element, false);
    }
    const legacyElement = document.getElementById(key);
    if (legacyElement && legacyElement !== document.getElementById('poi-html-overlay-object')) {
        setMarkerElementVisible(legacyElement, false);
    }
    if (record) {
        const layer = new record.Module.JSLayerList(true).nameAtLayer('CAPSULES');
        layer?.removeAtKey?.(record.objectId);
        markers.delete(key);
        record.Module.XDRenderData?.();
    }
    updateMarkerOffsets();
}

export function setCapsuleMarkerVisible(key, visible) {
    const record = markers.get(key);
    if (!record) return;
    setMarkerElementVisible(record.element, visible);
    record.marker.refresh?.();
    updateMarkerOffsets();
}
