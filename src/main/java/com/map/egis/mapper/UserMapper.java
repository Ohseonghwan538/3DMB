package com.map.egis.mapper;

import com.map.egis.dto.UserDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    // 🔍 아이디로 회원 정보 조회 (로그인 시 사용)
    UserDTO findByUserId(@Param("userId") String userId);

    // ➕ 회원가입 (테스트 데이터 삽입용)
    int insertUser(UserDTO user);

    // 🔍 아이디 중복 확인 (존재하면 1, 없으면 0 반환)
    int countByUserId(@Param("userId") String userId);

    int recordFailedLogin(@Param("userId") String userId);

    int recordSuccessfulLogin(@Param("userId") String userId);

    UserDTO findAccessProfile(@Param("userId") String userId);

    UserDTO findByOidcIdentity(@Param("issuer") String issuer, @Param("subject") String subject);

    UserDTO findByEmail(@Param("email") String email);

    int countByEmail(@Param("email") String email);

    int linkOidcIdentity(@Param("userId") String userId, @Param("issuer") String issuer,
                         @Param("subject") String subject, @Param("email") String email);

    int insertOidcUser(@Param("userId") String userId, @Param("password") String password,
                       @Param("name") String name, @Param("issuer") String issuer,
                       @Param("subject") String subject, @Param("email") String email);
}
