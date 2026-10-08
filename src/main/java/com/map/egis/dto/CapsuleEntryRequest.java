package com.map.egis.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CapsuleEntryRequest {
    private String userId;
    private String memo;
    private String title;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private List<CapsuleCreateRequest.ImageDto> images;
}
