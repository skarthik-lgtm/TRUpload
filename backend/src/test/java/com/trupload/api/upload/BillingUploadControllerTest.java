package com.trupload.api.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.trupload.service.upload.BillingUploadService;

class BillingUploadControllerTest {

    private final BillingUploadService service = mock(BillingUploadService.class);
    private final BillingUploadController controller = new BillingUploadController(service);

    @Test
    void rejectsMissingAuthenticationWithoutCallingUploadService() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.upload(new UploadRequest(List.of()), null));

        assertEquals(401, exception.getStatusCode().value());
        verify(service, never()).upload(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesAdminIdentityAndRoleToUploadService() {
        Authentication authentication = authenticated("admin@example.com", "ROLE_ADMIN");
        UploadRequest request = new UploadRequest(List.of(new UploadSelection("CSTRAB", "0101T01")));
        UploadResponse expected = new UploadResponse(UUID.randomUUID(), 1, 0, List.of(), List.of());
        when(service.upload(request, "admin@example.com", "ADMIN")).thenReturn(expected);

        assertEquals(expected, controller.upload(request, authentication));
        verify(service).upload(request, "admin@example.com", "ADMIN");
    }

    @Test
    void passesUserIdentityAndRoleToUploadService() {
        Authentication authentication = authenticated("user@example.com", "ROLE_USER");
        UploadRequest request = new UploadRequest(List.of(new UploadSelection("CSTRAB", "0101T01")));
        UploadResponse expected = new UploadResponse(UUID.randomUUID(), 0, 1, List.of(), List.of());
        when(service.upload(request, "user@example.com", "USER")).thenReturn(expected);

        assertEquals(expected, controller.upload(request, authentication));
        verify(service).upload(request, "user@example.com", "USER");
    }

    private Authentication authenticated(String username, String authority) {
        return new UsernamePasswordAuthenticationToken(
                username, "unused", List.of(new SimpleGrantedAuthority(authority)));
    }
}
