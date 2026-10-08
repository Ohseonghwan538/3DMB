package com.map.egis.dto;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter
public class CapsuleCreateRequest {
    private String userId;
    private Long capsuleId;
    private String title;
    private String memo;
    private String color;
    private Double longitude;
    private Double latitude;
    private Double altitude;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private List<ImageDto> images;

    @Getter 
    @Setter 
    public static class ImageDto {
        private String originalFilePath;
        private String compressedFilePath;
        private OffsetDateTime takenAt;

    }
}