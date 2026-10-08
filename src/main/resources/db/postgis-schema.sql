-- 1. PostGIS 확장 설치 (공간 정보용)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. 👤 사용자 테이블 생성 (참조 무결성을 위해 가장 먼저 생성)
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS oidc_issuer VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS oidc_subject VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(320);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_oidc_identity ON users(oidc_issuer, oidc_subject)
    WHERE oidc_issuer IS NOT NULL AND oidc_subject IS NOT NULL;

-- 3. 📦 캡슐(이벤트 그룹) 테이블
-- 하나의 캡슐(그룹)이 지도상의 하나의 위치(마커)와 메모를 갖도록 역할 재조정
CREATE TABLE IF NOT EXISTS capsules (
    capsule_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    
    -- 내용 (기존 이미지 테이블의 memo 이동 및 제목 추가)
    title VARCHAR(255),
    memo TEXT,
    
    -- 공간 정보 (PostGIS Point, 기존 이미지 테이블에서 이동)
    location GEOMETRY(Point, 4326),
    longitude NUMERIC(11, 8),
    latitude NUMERIC(10, 8),
    altitude NUMERIC(7, 2),
    
    -- 이벤트 기록 기간 (기존 요구사항 유지)
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 외래키 설정 (사용자 탈퇴 시 캡슐도 삭제되도록 CASCADE)
    CONSTRAINT fk_capsules_user FOREIGN KEY (user_id) 
        REFERENCES users (user_id) ON DELETE CASCADE
);

-- 캡슐 단위의 공간 정보 GiST(R-Tree) 인덱스 및 위경도 B-Tree 인덱스
CREATE INDEX IF NOT EXISTS idx_capsules_location ON capsules USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_capsules_lat_lng ON capsules (latitude, longitude);


-- 4. 🖼 캡슐 첨부 이미지 테이블
-- 위치와 메모는 캡슐로 넘기고, 여기서는 순수하게 파일 경로와 촬영 시간만 관리
CREATE TABLE IF NOT EXISTS capsule_images (
    image_id BIGSERIAL PRIMARY KEY,
    capsule_id BIGINT NOT NULL,
    
    original_file_path VARCHAR(500) NOT NULL,
    compressed_file_path VARCHAR(500) NOT NULL,
    
    taken_at TIMESTAMP WITH TIME ZONE NOT NULL,   -- 개별 사진 촬영 시간
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 외래키 설정 (캡슐 삭제 시 이미지 데이터도 삭제)
    CONSTRAINT fk_images_capsule FOREIGN KEY (capsule_id) 
        REFERENCES capsules (capsule_id) ON DELETE CASCADE
);

ALTER TABLE capsules ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE capsules ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE capsules ADD COLUMN IF NOT EXISTS memo_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE capsules ADD COLUMN IF NOT EXISTS memo_deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE capsules ADD COLUMN IF NOT EXISTS color CHAR(7) NOT NULL DEFAULT '#4263EB';
ALTER TABLE capsules ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS title VARCHAR(100);
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS poi_memo VARCHAR(2000);
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS longitude NUMERIC(11, 8);
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 8);
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS altitude NUMERIC(8, 2);
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS location GEOMETRY(PointZ, 4326);
ALTER TABLE capsule_images ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE capsule_images i
SET title = COALESCE(NULLIF(i.title, ''), NULLIF(c.title, ''), '나의 3D 추억'),
    poi_memo = COALESCE(i.poi_memo, c.memo),
    longitude = COALESCE(i.longitude, c.longitude),
    latitude = COALESCE(i.latitude, c.latitude),
    altitude = COALESCE(i.altitude, c.altitude, 0),
    location = COALESCE(i.location, CASE WHEN c.longitude IS NOT NULL AND c.latitude IS NOT NULL
        THEN ST_SetSRID(ST_MakePoint(c.longitude, c.latitude, COALESCE(c.altitude, 0)), 4326) ELSE NULL END)
FROM capsules c
WHERE i.capsule_id = c.capsule_id
  AND (i.title IS NULL OR (i.poi_memo IS NULL AND c.memo IS NOT NULL)
       OR (i.longitude IS NULL AND c.longitude IS NOT NULL)
       OR (i.latitude IS NULL AND c.latitude IS NOT NULL) OR i.altitude IS NULL
       OR (i.location IS NULL AND c.longitude IS NOT NULL AND c.latitude IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_capsule_images_location ON capsule_images USING GIST(location) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS poi_history (
    history_id BIGSERIAL PRIMARY KEY,
    poi_id BIGINT NOT NULL,
    capsule_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(2000),
    original_file_path VARCHAR(500) NOT NULL,
    compressed_file_path VARCHAR(500) NOT NULL,
    location GEOMETRY(PointZ, 4326),
    longitude NUMERIC(11, 8),
    latitude NUMERIC(10, 8),
    altitude NUMERIC(8, 2),
    poi_updated_at TIMESTAMP WITH TIME ZONE,
    changed_by VARCHAR(50) NOT NULL REFERENCES users(user_id),
    change_type VARCHAR(10) NOT NULL CHECK (change_type IN ('UPDATE', 'DELETE')),
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE poi_history ADD COLUMN IF NOT EXISTS poi_updated_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_poi_history_poi ON poi_history(poi_id, archived_at DESC);

CREATE TABLE IF NOT EXISTS poi_shares (
    share_id BIGSERIAL PRIMARY KEY,
    poi_id BIGINT NOT NULL REFERENCES capsule_images(image_id),
    owner_id VARCHAR(50) NOT NULL REFERENCES users(user_id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_poi_shares_poi ON poi_shares(poi_id) WHERE revoked_at IS NULL;

-- 캡슐 내에서 촬영 시간순 정렬을 위한 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_capsule_images_taken ON capsule_images (capsule_id, taken_at DESC);

CREATE TABLE IF NOT EXISTS admin_audit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    actor_id VARCHAR(50) NOT NULL REFERENCES users(user_id),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(100),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created ON admin_audit_log(created_at DESC);

CREATE TABLE IF NOT EXISTS admin_error_log (
    error_id BIGSERIAL PRIMARY KEY,
    error_code VARCHAR(30) NOT NULL,
    domain VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_error_created ON admin_error_log(created_at DESC);

CREATE TABLE IF NOT EXISTS capsule_reports (
    report_id BIGSERIAL PRIMARY KEY,
    capsule_id BIGINT NOT NULL REFERENCES capsules(capsule_id),
    reporter_id VARCHAR(50) NOT NULL REFERENCES users(user_id),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_capsule_reports_group_status ON capsule_reports(capsule_id, status);

CREATE TABLE IF NOT EXISTS admin_defect (
    defect_id VARCHAR(10) PRIMARY KEY,
    screen_element VARCHAR(100) NOT NULL,
    symptom VARCHAR(500) NOT NULL,
    related_errors VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HOLD',
    resolution VARCHAR(500) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO admin_defect (defect_id, screen_element, symptom, related_errors, status, resolution)
VALUES
 ('D-001', 'SCR-AUTH-001 · password', 'AUTH_002 CAPTCHA 입력 필드 작동 안 함', 'AUTH_002', 'HOLD', 'CAPTCHA 적용 or 미적용 결정'),
 ('D-002', 'SCR-MAIN-001 · memory-group', '선택한 묶음이 아닌 사용자 전체 묶음 로드', '-', 'HOLD', '추억 묶음별로 기록 로드'),
 ('D-003', 'SCR-MAIN-001 · btn-start-memory', '새 추억 저장 위치(지도)를 요구함', 'GROUP_001', 'HOLD', '이슈 기능 제거'),
 ('D-004', 'SCR-MAIN-001 · file-photo', 'EXIF 좌표 대신 선택 위치를 DB에 저장', 'UPLOAD_001', 'HOLD', '사진 좌표 추출 후 카메라 제어'),
 ('D-005', 'SCR-MAIN-001 · btn-save-memory', '카메라 고도가 높으면 마커 생성 안 됨', 'UPLOAD_003~005', 'HOLD', '업로드 이미지 좌표로 카메라 이동'),
 ('D-006', 'SCR-MAIN-001 · btn-save-memory', '기본 마커(NO.png) 표시, 새로고침 필요', 'UPLOAD_003~005', 'HOLD', '업로드 이미지로 마커 표시')
ON CONFLICT (defect_id) DO NOTHING;
