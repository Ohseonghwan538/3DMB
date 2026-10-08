package com.map.egis.service;

import com.map.egis.mapper.UserMapper;
import com.map.egis.mapper.AdminMapper;
import com.map.egis.dto.UserDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    // 생성자 주입 (DI)
    public UserService(UserMapper userMapper, AdminMapper adminMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // ➕ 회원가입 (비밀번호 암호화 후 DB 저장)
    public boolean register(UserDTO user) {
        if (user == null || isBlank(user.getUserId()) || isBlank(user.getPassword()) || isBlank(user.getName())
                || user.getUserId().length() < 3 || user.getUserId().length() > 40 || user.getPassword().length() < 8) {
            return false;
        }
        user.setUserId(user.getUserId().trim());
        user.setName(user.getName().trim());
        // 1. 아이디 중복 확인
        if (userMapper.countByUserId(user.getUserId()) > 0) {
            // 중복된 아이디가 존재할 경우 false 반환 (또는 커스텀 예외 발생 가능)
            return false; 
        }
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        userMapper.insertUser(user);
        return true;
    }

    // 🔑 로그인 검증 로직
    public UserDTO login(String userId, String rawPassword) {
        if (isBlank(userId) || isBlank(rawPassword)) {
            return null;
        }
        // 1. DB에서 사용자 조회
        UserDTO user = userMapper.findByUserId(userId);

        // 2. 사용자가 존재하지 않는 경우
        if (user == null) {
            adminMapper.insertError("AUTH_001", "AUTH", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return null;
        }

        if (!"ACTIVE".equals(user.getAccountStatus()) || user.getLockedAt() != null) {
            if (user.getLockedAt() != null) {
                adminMapper.insertError("AUTH_002", "AUTH", "연속 로그인 실패로 계정이 잠겼습니다.");
            }
            return null;
        }

        // 3. 비밀번호 일치 여부 확인 (평문 비밀번호 vs 암호화된 비밀번호)
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            userMapper.recordSuccessfulLogin(user.getUserId());
            return user; // 로그인 성공 시 사용자 객체 반환
        } else {
            userMapper.recordFailedLogin(user.getUserId());
            UserDTO updatedUser = userMapper.findByUserId(user.getUserId());
            if (updatedUser != null && updatedUser.getLockedAt() != null) {
                adminMapper.insertError("AUTH_002", "AUTH", "연속 로그인 5회 실패로 계정이 잠겼습니다.");
            } else {
                adminMapper.insertError("AUTH_001", "AUTH", "아이디 또는 비밀번호가 일치하지 않습니다.");
            }
            return null; // 비밀번호 불일치
        }
    }

    @Transactional
    public UserDTO loginOidc(OidcClient.Identity identity) {
        if (identity == null || !identity.emailVerified() || isBlank(identity.subject())
                || isBlank(identity.issuer()) || isBlank(identity.email())) {
            throw new IllegalArgumentException("검증된 OIDC 사용자 정보가 필요합니다.");
        }
        UserDTO user = userMapper.findByOidcIdentity(identity.issuer(), identity.subject());
        if (user == null) {
            int emailMatches = userMapper.countByEmail(identity.email());
            if (emailMatches > 1) {
                throw new IllegalArgumentException("이메일이 중복된 계정은 외부 계정과 자동으로 연결할 수 없습니다.");
            }
            user = emailMatches == 1 ? userMapper.findByEmail(identity.email()) : null;
            if (user != null) {
                if (user.getOidcIssuer() != null || user.getOidcSubject() != null
                        || userMapper.linkOidcIdentity(user.getUserId(), identity.issuer(),
                        identity.subject(), identity.email()) == 0) {
                    throw new IllegalArgumentException("해당 이메일은 다른 외부 계정과 이미 연결되어 있습니다.");
                }
                user = userMapper.findByOidcIdentity(identity.issuer(), identity.subject());
            } else {
                String userId = "oidc_" + UUID.randomUUID().toString().replace("-", "");
                String password = passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString());
                String name = isBlank(identity.name()) ? identity.email() : identity.name().trim();
                name = name.substring(0, Math.min(name.length(), 50));
                userMapper.insertOidcUser(userId, password, name, identity.issuer(),
                        identity.subject(), identity.email());
                user = userMapper.findByOidcIdentity(identity.issuer(), identity.subject());
            }
        }
        if (user == null || !"ACTIVE".equals(user.getAccountStatus()) || user.getLockedAt() != null) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다. 관리자에게 문의해 주세요.");
        }
        userMapper.recordSuccessfulLogin(user.getUserId());
        return user;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
