package com.map.egis.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoiWriteRequest {
    private Long capsuleId;
    private String title;
    private String content;
}
