package com.map.egis.dto;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {
    private String userId;
    private String password;
    private String name;
    private OffsetDateTime createdAt;
    private String role;
    private String accountStatus;
    private Integer failedLoginCount;
    private OffsetDateTime lockedAt;
    private OffsetDateTime lastLoginAt;
    private String oidcIssuer;
    private String oidcSubject;
    private String email;
}