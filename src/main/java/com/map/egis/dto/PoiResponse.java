package com.map.egis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PoiResponse {
    private Long poiId;
    private Long groupId;
    private Long capsuleId;
    private String groupName;
    private String groupColor;
    private String title;
    private String content;
    private Double longitude;
    private Double latitude;
    private Double altitude;
    private String originalFilePath;
    private String compressedFilePath;
    private String imageUrl;
    private String thumbUrl;
    private OffsetDateTime takenAt;
    private boolean shared;
}
