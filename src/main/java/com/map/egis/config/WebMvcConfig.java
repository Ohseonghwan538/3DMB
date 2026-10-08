package com.map.egis.config;

import com.map.egis.dto.UserDTO;
import com.map.egis.mapper.UserMapper;
import com.map.egis.service.JwtService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("wasm", MediaType.parseMediaType("application/wasm"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String uri = request.getRequestURI();
                boolean adminPage = uri.equals("/admin") || uri.equals("/src/components/admin.html");
                boolean appPage = uri.equals("/main") || uri.equals("/src/components/index.html");
                boolean publicShareApi = uri.startsWith("/api/shared/");
                boolean protectedApi = (uri.startsWith("/api/") && !publicShareApi)
                        || uri.startsWith("/images/") || uri.equals("/logout");
                if (!adminPage && !appPage && !protectedApi) return true;
                String token = accessToken(request);
                JwtService.Claims claims = jwtService.verify(token).orElse(null);
                UserDTO profile = claims == null ? null : userMapper.findAccessProfile(claims.userId());
                if (profile == null || !"ACTIVE".equals(profile.getAccountStatus())) {
                    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtService.COOKIE_NAME, "")
                            .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/").maxAge(0).build().toString());
                    if (adminPage || appPage) {
                        response.sendRedirect("/login");
                    } else {
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}}");
                    }
                    return false;
                }
                request.setAttribute("authenticatedUserId", claims.userId());
                request.setAttribute("authenticatedUserRole", profile.getRole());
                request.setAttribute("authenticatedUserName", profile.getName());
                request.setAttribute("authenticatedCsrfToken", claims.csrfToken());
                if (adminPage && !"ADMIN".equals(profile.getRole()) && !"SUPER_ADMIN".equals(profile.getRole())
                        && !"MODERATOR".equals(profile.getRole())) {
                    response.sendRedirect("/main");
                    return false;
                }
                return true;
            }
        });
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String method = request.getMethod();
                if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                        || "/login".equals(request.getRequestURI()) || "/register".equals(request.getRequestURI())) return true;
                Object expected = request.getAttribute("authenticatedCsrfToken");
                String submitted = request.getHeader("X-CSRF-TOKEN");
                if (expected instanceof String token && submitted != null
                        && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8))) return true;
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"요청 검증 토큰이 올바르지 않습니다.\"}}");
                return false;
            }
        });
    }

    private String accessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) return authorization.substring(7);
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (JwtService.COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}