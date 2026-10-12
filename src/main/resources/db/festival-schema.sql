CREATE TABLE IF NOT EXISTS festival_type (
    type_code    VARCHAR(20) PRIMARY KEY,
    type_name    VARCHAR(50) NOT NULL,
    marker_image VARCHAR(200) NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0
);

INSERT INTO festival_type (type_code, type_name, marker_image, sort_order) VALUES
    ('MUSIC', '음악', '/img/marker/MUSIC.png', 1),
    ('TRADITION', '전통·역사', '/img/marker/TRADITION.png', 2),
    ('FOOD', '음식', '/img/marker/FOOD.png', 3),
    ('NATURE', '자연·꽃', '/img/marker/NATURE.png', 4),
    ('ART', '예술·공연', '/img/marker/ART.png', 5),
    ('ETC', '기타', '/img/marker/ETC.png', 6)
ON CONFLICT (type_code) DO UPDATE SET
    type_name = EXCLUDED.type_name,
    marker_image = EXCLUDED.marker_image,
    sort_order = EXCLUDED.sort_order;

CREATE TABLE IF NOT EXISTS festival (
    festival_id   BIGSERIAL PRIMARY KEY,
    festival_name VARCHAR(200) NOT NULL,
    venue         VARCHAR(200) NOT NULL DEFAULT '',
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    content       TEXT,
    host_org      VARCHAR(200),
    mng_org       VARCHAR(200),
    road_addr     VARCHAR(300),
    lot_addr      VARCHAR(300),
    sido          VARCHAR(30),
    sigungu       VARCHAR(50),
    latitude      NUMERIC(10,7) NOT NULL,
    longitude     NUMERIC(10,7) NOT NULL,
    type_code     VARCHAR(20) NOT NULL REFERENCES festival_type(type_code),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_festival UNIQUE (festival_name, start_date, venue),
    CONSTRAINT ck_festival_period CHECK (end_date >= start_date),
    CONSTRAINT ck_festival_coord CHECK (latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132)
);

CREATE INDEX IF NOT EXISTS ix_festival_period ON festival (start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_festival_type ON festival (type_code);
CREATE INDEX IF NOT EXISTS ix_festival_sido ON festival (sido);

CREATE TABLE IF NOT EXISTS import_history (
    import_id    BIGSERIAL PRIMARY KEY,
    file_name    VARCHAR(300) NOT NULL,
    total_rows   INT NOT NULL,
    success_rows INT NOT NULL,
    skipped_rows INT NOT NULL,
    imported_at  TIMESTAMP NOT NULL DEFAULT now()
);
