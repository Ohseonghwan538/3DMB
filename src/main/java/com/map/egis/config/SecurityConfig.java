package com.map.egis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 🔒 BCrypt 알고리즘을 사용해 비밀번호를 단방향 암호화
        return new BCryptPasswordEncoder();
    }
}
