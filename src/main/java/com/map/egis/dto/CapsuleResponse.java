package com.map.egis.dto;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor 
public class CapsuleResponse {
    private Long capsuleId;
    private String userId;
    private String title;
    private String memo;
    private Double longitude;
    private Double latitude;
    private Double altitude;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private OffsetDateTime createdAt;
    private List<ImageResponse> images;

    @Getter
    @Setter
    @NoArgsConstructor 
    public static class ImageResponse {
        private Long imageId;
        private String originalFilePath;
        private String compressedFilePath;
        private OffsetDateTime takenAt;
    }
}