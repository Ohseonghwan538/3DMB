import { createIcon, moveCameraTo, createPositionWithOffset } from "../utils/helperMethod.js";
import { billboardLayer } from "./layers/user/BillboardLayer.js";

const DEFAULT_TEXT_OPTIONS = {
    fontSize: 18,
    font: "sans-serif",
    fontColor: "#FFFFFF",
    backgroundColor: "rgba(0, 0, 0, 0.75)",
    outlineColor: "#000000",
    outlineWidth: 2
};

export const billboardRenderer = {
    clearAll() {
        if (billboardLayer.layer) {
            billboardLayer.layer.removeAll(); // 모든 오브젝트 제거
        }
    },

    renderBillboard({ imageId, imageUrl, position, memo, shouldMoveCamera = false }) {
        const objectKey = `Billboard_${imageId}`;
        const textObjectKey = `${objectKey}_TEXT`;

        // 아이콘 이름에 타임스탬프를 붙여 엔진 캐시 중복 방지
        const uniqueIconName = `Icon_${imageId}_${Date.now()}`;

        createIcon(uniqueIconName, imageUrl, (iconName) => {
            // 기존 빌보드 중복 제거
            if (billboardLayer.layer.getObjects(objectKey)) {
                billboardLayer.layer.removeAtKey(objectKey);
            }

            billboardLayer.createImageBillboard(objectKey, position, iconName);

            if (memo) {
                const textPosition = createPositionWithOffset(position, -18.0);
                this.renderTextBillboard(textObjectKey, textPosition, memo);
            }

            if (shouldMoveCamera) {
                moveCameraTo(position.longitude, position.latitude, position.altitude);
            }
        });
    },

    renderTextBillboard(textObjectKey, textPosition, text) {
        if (billboardLayer.layer && billboardLayer.layer.getObjects(textObjectKey)) {
            billboardLayer.layer.removeAtKey(textObjectKey);
        }

        billboardLayer.createTextBillboard(textObjectKey, textPosition, {
            ...DEFAULT_TEXT_OPTIONS,
            text
        });
    }
};
