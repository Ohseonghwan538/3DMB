package com.map.egis.controller;

import com.map.egis.dto.UserDTO;
import com.map.egis.service.JwtService;
import com.map.egis.service.OidcClient;
import com.map.egis.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Controller
@Slf4j
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final OidcClient oidcClient;

    // 의존성 주입
    public UserController(UserService userService, JwtService jwtService, OidcClient oidcClient) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.oidcClient = oidcClient;
    }

    // 🏠 localhost:8080 접속 시 바로 /login으로 이동
    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    // 📄 로그인 페이지 요청 Handling
    @GetMapping("/login")
    public String loginPage() {
        return "forward:/src/components/login.html";
    }

    // 📄 메인 페이지 요청 Handling
    @GetMapping("/main")
    public String mainPage() {
        return "forward:/src/components/index.html";
    }

    // 회원가입 페이지 이동
    @GetMapping("/register")
    public String registerPage() {
        return "forward:/src/components/register.html";
    }

    // 회원가입 요청 처리
    @PostMapping("/register")
    public String register(UserDTO user) {
        boolean isSuccess = userService.register(user);

        if (isSuccess) {
            // ✅ 성공 시 로그인 페이지로 리다이렉트
            return "redirect:/login?registered=true";
        } else {
            // ❌ 실패(아이디 중복 등) 시 에러 파라미터와 함께 회원가입 페이지로 리다이렉트
            return "redirect:/register?error=true";
        }
    }

    // 📥 로그인 처리 (POST)
    @PostMapping("/login")
    public String login(@RequestParam("userId") String userId,
                        @RequestParam("password") String password,
                        HttpServletRequest request,
                        HttpServletResponse response) {

        UserDTO loginUser = userService.login(userId, password);

        if (loginUser != null) {
            JwtService.IssuedToken token = jwtService.issue(loginUser.getUserId(), loginUser.getRole());
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtService.COOKIE_NAME, token.value())
                    .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                    .maxAge(token.ttlSeconds()).build().toString());
            return "redirect:/main"; // 메인 페이지로 이동
        } else {
            // ❌ 로그인 실패: 다시 로그인 페이지로 이동
            return "redirect:/login?error=true";
        }
    }

    @GetMapping("/oauth2/authorization/oidc")
    public void oidcLogin(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        try {
            OidcClient.AuthorizationRequest authorization = oidcClient.authorizationRequest();
            var session = request.getSession(true);
            session.setAttribute("oidcState", authorization.state());
            session.setAttribute("oidcNonce", authorization.nonce());
            session.setAttribute("oidcVerifier", authorization.verifier());
            response.sendRedirect(authorization.location());
        } catch (RuntimeException configurationFailure) {
            log.warn("OIDC authorization could not be started: {}", configurationFailure.getMessage());
            response.sendRedirect("/login?ssoError=true");
        }
    }

    @GetMapping("/oauth2/callback")
    public String oidcCallback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session == null) return "redirect:/login?ssoError=true";
        Object expectedState = session.getAttribute("oidcState");
        Object nonce = session.getAttribute("oidcNonce");
        Object verifier = session.getAttribute("oidcVerifier");
        session.removeAttribute("oidcState");
        session.removeAttribute("oidcNonce");
        session.removeAttribute("oidcVerifier");
        if (error != null) {
            log.warn("OIDC provider returned an authorization error.");
            return "redirect:/login?ssoError=true";
        }
        if (code == null || !(expectedState instanceof String expected)
                || !(nonce instanceof String expectedNonce) || !(verifier instanceof String expectedVerifier)
                || state == null || !java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), state.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return "redirect:/login?ssoError=true";
        }
        try {
            OidcClient.Identity identity = oidcClient.exchangeAndVerify(code, expectedVerifier, expectedNonce);
            UserDTO user = userService.loginOidc(identity);
            JwtService.IssuedToken token = jwtService.issue(user.getUserId(), user.getRole());
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtService.COOKIE_NAME, token.value())
                    .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                    .maxAge(token.ttlSeconds()).build().toString());
            return "redirect:/main";
        } catch (RuntimeException authenticationFailure) {
            log.warn("OIDC callback authentication failed: {}", authenticationFailure.getMessage());
            return "redirect:/login?ssoError=true";
        }
    }

    // 로그인된 JWT 주체 정보 조회 API
    @GetMapping("/api/auth/me")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("authenticatedUserId");
        String userName = (String) request.getAttribute("authenticatedUserName");
        String role = (String) request.getAttribute("authenticatedUserRole");
        String csrfToken = (String) request.getAttribute("authenticatedCsrfToken");

        // 세션에 저장된 유저 정보가 없는 경우 401 Unauthorized 반환
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", userId);
        userInfo.put("userName", userName);
        userInfo.put("role", role);
        userInfo.put("csrfToken", csrfToken);

        return ResponseEntity.ok(userInfo);
    }

    // 🚪 로그아웃 처리
    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtService.COOKIE_NAME, "")
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/").maxAge(0).build().toString());
        return "redirect:/login";
    }
}
