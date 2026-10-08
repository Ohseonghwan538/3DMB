package com.map.egis.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor 
public class CapsuleSearchRequest {
    private Double minLongitude; // 남서쪽 경도 (West)
    private Double minLatitude;  // 남서쪽 위도 (South)
    private Double maxLongitude; // 북동쪽 경도 (East)
    private Double maxLatitude;  // 북동쪽 위도 (North)
    private String userId;

}
