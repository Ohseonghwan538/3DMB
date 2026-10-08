package com.map.egis.service;

import com.map.egis.dto.PoiResponse;
import com.map.egis.mapper.CapsuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PoiShareService {
    private final CapsuleMapper capsuleMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createShare(long poiId, String ownerId) {
        if (capsuleMapper.selectPoi(poiId, ownerId) == null) throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (capsuleMapper.insertShare(poiId, ownerId, hash(token)) == 0) throw new IllegalArgumentException("공유 링크를 만들 수 없습니다.");
        return token;
    }

    @Transactional
    public void revokeShares(long poiId, String ownerId) {
        if (capsuleMapper.selectPoi(poiId, ownerId) == null) throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
        capsuleMapper.revokeShares(poiId, ownerId);
    }

    @Transactional(readOnly = true)
    public PoiResponse sharedPoi(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{40,50}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공유 링크를 찾을 수 없습니다.");
        }
        PoiResponse response = capsuleMapper.selectSharedPoi(hash(token));
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공유 링크가 만료되었거나 취소되었습니다.");
        }
        response.setImageUrl("/api/shared/" + token + "/image");
        response.setThumbUrl(response.getImageUrl());
        response.setShared(true);
        return response;
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("공유 링크 해시를 생성하지 못했습니다.", unavailable);
        }
    }
}
