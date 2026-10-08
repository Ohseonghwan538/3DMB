package com.map.egis.service;

import com.map.egis.config.StorageProperties;
import com.map.egis.mapper.AdminMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminServiceTests {
    @Test
    void onlySuperAdminCanAssignRoles() {
        AdminMapper mapper = mock(AdminMapper.class);
        when(mapper.selectUser("member")).thenReturn(Map.of("status", "ACTIVE", "role", "USER"));
        AdminService service = new AdminService(mapper, new StorageProperties());

        assertThrows(SecurityException.class, () -> service.updateUser("operator", "ADMIN", "member", null, "MODERATOR"));
        verify(mapper, never()).updateRole(anyString(), anyString());
    }

    @Test
    void administratorsCannotChangeTheirOwnStatusOrRole() {
        AdminMapper mapper = mock(AdminMapper.class);
        when(mapper.selectUser("operator")).thenReturn(Map.of("status", "ACTIVE", "role", "SUPER_ADMIN"));
        AdminService service = new AdminService(mapper, new StorageProperties());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateUser("operator", "SUPER_ADMIN", "operator", "SUSPENDED", null));
        verify(mapper, never()).updateAccountStatus(anyString(), anyString());
        verify(mapper, never()).updateRole(anyString(), anyString());
    }
}
