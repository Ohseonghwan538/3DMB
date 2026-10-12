# 소프트웨어요구사항명세서(SRS): 전국문화축제 지도 시각화 시스템

Oct 8, 2026 · @MR.OH

## 1. 개요

본 문서는 FRD의 기능 요구사항 8건(DAT 2, RND 1, UI 2, EVT 3)을 Spring Boot MVC, 바닐라 JavaScript, XDWorld 구조로 구현하기 위한 요구사항과 설계 기준을 정의한다.

### 1.1 목적

- FRD의 요구사항을 API, 데이터, 화면 모듈 단위로 구체화하여 추가 해석 없이 구현과 테스트가 가능하게 한다.
- 요구사항 ID를 FRD, SRS, API, 프론트엔드 모듈 순으로 추적할 수 있게 한다.

### 1.2 범위

| 구분 | 내용 |
| --- | --- |
| 포함 | 전국문화축제표준데이터 CSV 일괄 적재(관리자 페이지에서 업로드), 관리자 로그인, 적재 결과와 이력 조회, 축제 조회 API, `JSBillboard` 마커 표출, `JSHTMLObject` 정보창, 정보창 단일 유지, 프론트엔드 클러스터링, 검색/필터(축제명, 기간, 지역, 유형) |
| 제외 | 축제 개별 등록·수정·삭제 화면, 일반 방문자 로그인, 공공데이터 OpenAPI 자동 동기화, 모바일 네이티브 앱 |
| 개발 외 작업 | Imagen 3(Google AI Studio)로 유형별 마커 이미지 생성은 개발 전에 수행하는 제작 작업이며, 시스템은 완성된 정적 이미지만 매핑한다(FRD-DAT-02). |

### 1.3 용어 정의

| 용어 | 정의 |
| --- | --- |
| XDWorld | 3D 웹 지도 엔진(WebGL). 지도 위에 마커와 HTML 오버레이를 배치한다. |
| JSBillboard | 3D 좌표에 2D 이미지 마커를 렌더링하는 XDWorld 객체 |
| JSHTMLObject | HTML DOM 요소를 3D 좌표에 오버레이하는 XDWorld 객체 |
| Picking | 화면의 마우스 위치에서 3D 객체를 판별하는 동작 |
| 클러스터링 | 근접한 마커를 줌 레벨에 따라 하나의 묶음 마커로 합쳐 표시하는 처리 |
| 표준데이터 | 공공데이터포털의 전국문화축제표준데이터(CSV) |

### 1.4 참조 문서

- FRD.md: 기능요구사항정의서, 전국문화축제 지도 시각화 시스템
- 공공데이터포털: 전국문화축제표준데이터 (원본 CSV의 컬럼 명세 기준)
- XDWorld WebGL 매뉴얼(https://egiscorp.gitbook.io/xdworld-webgl-manual): JSBillboard, JSHTMLObject

## 2. 전체 설명

시스템은 브라우저에서 접속하는 단일 웹 애플리케이션이다. 서버는 CSV로 적재한 축제 데이터를 JSON API로 제공하고, 브라우저는 이를 받아 XDWorld 지도 위에 마커와 정보창으로 그린다.

### 2.1 사용자 분류

| 사용자 | 설명 | 사용 기능 |
| --- | --- | --- |
| 일반 방문자 | 로그인 없이 접속하는 지도 이용자 | 지도 탐색, 마커 클릭, 정보창 확인, 검색/필터 |
| 관리자 | 관리자 페이지에 로그인해 CSV를 올려 재적재하는 담당자 | 로그인, CSV 업로드 재적재, 적재 결과와 이력 확인 |

### 2.2 운영 및 개발 환경

언어, 프레임워크, 데이터베이스 버전은 11절에서 확정되었고, 비고에 가정이라고 적은 항목은 구현 시 확인한다.

| 항목 | 내용 | 비고 |
| --- | --- | --- |
| 운영체제 | Windows | 개발과 실행 환경 |
| IDE | VS Code | Extension Pack for Java, Spring Boot Extension Pack 사용 |
| 언어 | Java 17 | 확정 |
| 프레임워크 | Spring Boot 4.x (MVC) | 확정. 웹 스타터는 `spring-boot-starter-webmvc` |
| 빌드 도구 | Gradle | Wrapper(`gradlew.bat`) 사용. Boot 4는 Gradle 8.14 이상 또는 9.x 필요 |
| 데이터베이스 | PostgreSQL 15 이상 | 확정 |
| DB 연동 | MyBatis (`mybatis-spring-boot-starter` 4.0.x) | Boot 4 호환 버전, 구현 시 확인. JPA는 사용하지 않음 |
| 보안 | Spring Security | 관리자 페이지 보호용. 가정 |
| 프론트엔드 | 바닐라 JavaScript(ES 모듈), HTML, CSS | 빌드 도구와 프레임워크 없음 |
| 지도 엔진 | XDWorld WebGL | 엔진 파일은 `static/js/engine/`, 초기화 스크립트는 `static/js/module.js`(확정) |
| 클라이언트 브라우저 | 최신 Chrome, Edge | WebGL 2 지원 필요 |

### 2.3 제약사항

- 좌표계는 WGS84(위도, 경도)이며 XDWorld 좌표 변환 후 `JSBillboard`에 전달한다.
- 클러스터링과 가시 영역 최적화는 프론트엔드에서 처리한다(서버는 전체 목록과 필터 결과만 제공).
- 마커 이미지는 유형별 공용 이미지이며 축제별 개별 이미지는 사용하지 않는다.
- 일반 방문자용 `/api`는 조회 전용이다. 데이터를 쓰는 기능은 관리자 인증을 거치는 CSV 적재 하나뿐이다.

### 2.4 가정 및 의존성

- 데이터 규모는 2,000건 이하로 확정되었다. 이 규모에서는 전체 목록을 한 번에 내려받아도 응답 크기가 1MB 이내이다.
- 공공데이터포털 CSV는 EUC-KR(CP949)일 수 있으므로 적재 시 인코딩을 지정해서 읽는다.
- 대한민국 영역 밖의 좌표는 오류 데이터로 간주한다.
- 2,000건 CSV는 업로드 요청 안에서 수 초 이내에 처리할 수 있다고 보고 비동기 작업 큐를 두지 않는다.

## 3. 시스템 아키텍처

브라우저는 정적 파일과 JSON만 받아 XDWorld 지도를 그리고, 서버는 `/api` 조회와 정적 리소스를 제공하며, DB에 쓰는 것은 관리자 인증을 거친 CSV 적재뿐이다.

&#91;embedded content: 시스템 구성 · 브라우저, 서버, DB와 적재 경로\]

화살표는 호출 방향이며, 강조한 `ImportService`만 DB에 쓴다.

### 3.1 구성 요소와 책임

| 구성 요소 | 책임 | 요구사항 |
| --- | --- | --- |
| `api/` | `fetch`로 `/api` 호출, 오류와 타임아웃 처리 | SRS-DAT-01, SRC-01 |
| `state/` | 현재 필터 조건, 축제 목록, 선택된 축제 id를 보관하고 변경을 구독자에게 알림 | SRS-SRC-02, EVT-03 |
| `ui/` | 검색 패널, 결과 목록, 정보창 DOM 템플릿 | SRS-UI-01, SRC-01, SRC-02 |
| `map/` | XDWorld 초기화, 좌표 변환, `JSBillboard`, 클러스터링, Picking, `JSHTMLObject` | SRS-RND-01\~03, UI-02, EVT-01\~04 |
| Controller | 요청 파라미터 바인딩, JSON 응답, 공통 오류 응답 | SRS-DAT-02, EVT-02 |
| Service | 필터 조건 검증(날짜 순서, 유형 코드), DTO 변환 | SRS-SRC-01 |
| Mapper | MyBatis XML 동적 SQL로 조회 | SRS-SRC-01 |
| `SecurityConfig` | `/admin/**` 인증 요구, 폼 로그인, CSRF 보호. `/api`와 지도 화면은 공개 | SRS-ADM-01 |
| `AdminImportController`, `ImportService` | CSV 업로드 수신, 읽기, 검증, 유형 분류, upsert, 결과·이력 기록, 동시 적재 1건 제한 | SRS-DAT-01, DAT-03, ADM-02, ADM-03 |
| `admin/` 화면 | 로그인, CSV 업로드, 적재 결과와 이력 표시 | SRS-ADM-01\~03 |

### 3.2 주요 데이터 흐름

1. 접속: 브라우저가 `index.html`과 XDWorld 엔진 파일을 서버의 정적 리소스에서 받는다.
2. 초기화: `api/`가 `/api/festival-types`, `/api/regions`, `/api/festivals`를 호출하고 결과를 `state/`에 저장한다.
3. 마커 표시: `map/`이 `state/`의 목록으로 클러스터를 계산하고 가시 영역의 `JSBillboard`를 만든다.
4. 클릭: Picking으로 축제 id를 찾고 `/api/festivals/{id}`로 상세를 받아 `JSHTMLObject` 정보창을 연다.
5. 필터: 검색 패널 조건이 바뀌면 같은 목록 API를 조건과 함께 다시 호출하고 마커를 다시 구성한다.
6. 적재: 관리자가 `/admin`에서 로그인하고 CSV를 올리면 `ImportService`가 DB에 반영하고 결과를 돌려준다. 이후 일반 사용자가 지도를 새로 고치면 새 데이터가 보인다.

## 4. 데이터 설계

PostgreSQL에 축제 테이블 `festival`, 유형 테이블 `festival_type`, 적재 이력 테이블 `import_history` 3개를 둔다. 표준데이터에는 유형 컬럼이 없으므로 유형은 적재 시 키워드 규칙으로 부여한다(11절 확인 필요).

### 4.1 CSV 필드와 festival 컬럼 매핑

컬럼명은 표준데이터 명세 기준이며, 실제 CSV 헤더와 다르면 적재 설정의 매핑표만 수정한다.

| CSV 필드 | 컬럼 | 타입 | 필수 | 용도(FRD) |
| --- | --- | --- | --- | --- |
| 축제명 | `festival_name` | VARCHAR(200) | 예 | 정보창 타이틀, 검색 |
| 개최장소 | `venue` | VARCHAR(200) | 아니오(빈 문자열) | 정보창 장소 |
| 소재지도로명주소 | `road_addr` | VARCHAR(300) | 아니오 | 정보창 장소, 지역 파생 |
| 소재지지번주소 | `lot_addr` | VARCHAR(300) | 아니오 | 도로명주소 누락 시 지역 파생 |
| 축제시작일자 | `start_date` | DATE | 예 | 정보창 기간, 기간 필터 |
| 축제종료일자 | `end_date` | DATE | 예 | 정보창 기간, 기간 필터 |
| 축제내용 | `content` | TEXT | 아니오 | 정보창 상세내용 |
| 주최기관명 | `host_org` | VARCHAR(200) | 아니오 | 정보창 주최 |
| 주관기관명 | `mng_org` | VARCHAR(200) | 아니오 | 정보창 주관 |
| 위도 | `latitude` | NUMERIC(10,7) | 예 | 마커 좌표 |
| 경도 | `longitude` | NUMERIC(10,7) | 예 | 마커 좌표 |
| (파생) | `sido`, `sigungu` | VARCHAR | 아니오 | 지역 필터. 도로명주소 앞 두 토큰에서 추출 |
| (파생) | `type_code` | VARCHAR(20) | 예 | 유형 필터, 마커 이미지 선택 |

### 4.2 유형 분류 규칙

- `festival_type` 테이블이 유형 코드, 표시명, 마커 이미지 경로를 가진다. 마커 이미지는 `/img/marker/{type_code}.png` 정적 파일이다.
- 적재 시 `festival_name`과 `content`에서 키워드를 찾아 유형을 정한다. 여러 유형이 맞으면 규칙 목록의 앞선 유형을 쓰고, 맞는 것이 없으면 `ETC`로 한다.
- 확정 유형(11절 1번): `MUSIC` 음악, `TRADITION` 전통·역사, `FOOD` 음식, `NATURE` 자연·꽃, `ART` 예술·공연, `ETC` 기타.

### 4.3 CSV 적재 절차

관리자가 관리자 페이지에서 CSV 파일을 올리면 서버가 같은 요청 안에서 한 번에 처리한다. 데이터가 2,000건 이하이므로 비동기 작업 큐는 두지 않는다.

1. 관리자가 로그인한 뒤 CSV 파일(`.csv`, 최대 5MB)과 인코딩(기본 MS949, UTF-8 선택)을 골라 `POST /admin/api/import`로 올린다. 다른 적재가 진행 중이면 409로 거절한다.
2. 헤더 이름으로 컬럼을 찾아 한 행씩 읽는다(컬럼 순서에 의존하지 않는다). 필수 헤더가 없으면 400으로 거절한다.
3. 행별로 검증한다: 축제명 필수, 시작일과 종료일이 날짜로 해석되고 종료일이 시작일 이후, 위도 33\~39와 경도 124\~132 범위.
4. 유형과 시도/시군구를 파생한다.
5. `(festival_name, start_date, venue)` 기준으로 upsert 한다. 전체를 한 트랜잭션으로 묶어 DB 오류가 나면 이번 적재를 모두 되돌린다. 같은 파일을 다시 올려도 중복이 생기지 않으며, CSV에 없는 기존 축제는 삭제하지 않는다.
6. 검증에 실패한 행은 건너뛰고 행 번호와 사유를 로그에 남기며(FRD-DAT-01 예외 처리), 총계는 `import_history`에 저장한다. 응답에는 총계와 건너뛴 행(최대 100건)을 담아 관리자 화면에 보여준다.

### 4.4 DDL

```sql
CREATE TABLE festival_type (
    type_code    VARCHAR(20)  PRIMARY KEY,
    type_name    VARCHAR(50)  NOT NULL,
    marker_image VARCHAR(200) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0
);

CREATE TABLE festival (
    festival_id   BIGSERIAL     PRIMARY KEY,
    festival_name VARCHAR(200)  NOT NULL,
    venue         VARCHAR(200)  NOT NULL DEFAULT '',
    start_date    DATE          NOT NULL,
    end_date      DATE          NOT NULL,
    content       TEXT,
    host_org      VARCHAR(200),
    mng_org       VARCHAR(200),
    road_addr     VARCHAR(300),
    lot_addr      VARCHAR(300),
    sido          VARCHAR(30),
    sigungu       VARCHAR(50),
    latitude      NUMERIC(10,7) NOT NULL,
    longitude     NUMERIC(10,7) NOT NULL,
    type_code     VARCHAR(20)   NOT NULL REFERENCES festival_type(type_code),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uq_festival UNIQUE (festival_name, start_date, venue),
    CONSTRAINT ck_period   CHECK (end_date >= start_date),
    CONSTRAINT ck_coord    CHECK (latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132)
);

CREATE INDEX ix_festival_period ON festival (start_date, end_date);
CREATE INDEX ix_festival_type   ON festival (type_code);
CREATE INDEX ix_festival_sido   ON festival (sido);

CREATE TABLE import_history (
    import_id    BIGSERIAL    PRIMARY KEY,
    file_name    VARCHAR(300) NOT NULL,
    total_rows   INT          NOT NULL,
    success_rows INT          NOT NULL,
    skipped_rows INT          NOT NULL,
    imported_at  TIMESTAMP    NOT NULL DEFAULT now()
);
```

## 5. 상세 기능 요구사항

FRD 8건은 SRS 요구사항으로 1:1 대응시키고, FRD 5절 제약사항과 확정된 범위(검색/필터)에서 나온 항목은 `추가`로 표시한다. 인수 기준은 테스트 케이스의 합격 조건으로 쓴다.

### 5.1 데이터

| SRS ID | FRD ID | 요구사항 | 인수 기준 |
| --- | --- | --- | --- |
| SRS-DAT-01 | FRD-DAT-01 | CSV에서 축제명, 개최장소, 도로명·지번주소, 기간, 축제내용, 주최·주관기관, 위도, 경도를 읽어 DB에 적재하고 API가 JSON으로 제공한다. 좌표가 비었거나 숫자가 아니거나 범위(위도 33\~39, 경도 124\~132) 밖인 행은 적재하지 않고 로그에 남긴다. | 1) 정상 행은 모두 적재된다. 2) 좌표 오류 행은 건너뛰고 행 번호와 사유가 로그와 적재 응답에 남고 총계가 `import_history`에 기록된다. 3) 같은 CSV를 두 번 적재해도 행 수가 늘지 않는다. |
| SRS-DAT-02 | FRD-DAT-02 | 유형별 마커 이미지(Imagen 3로 제작한 정적 PNG)를 `festival_type.marker_image`에 매핑하고, API가 축제마다 `iconUrl`을 내려준다. | 1) 모든 유형 코드에 이미지 파일이 존재한다. 2) 목록 API의 모든 축제에 `iconUrl`이 있고 해당 URL이 200으로 응답한다. |
| SRS-DAT-03 | 추가 | 적재 시 키워드 규칙으로 `type_code`를 부여하고 시도/시군구를 주소에서 파생한다. | 1) 모든 행에 `type_code`가 있다(미해당은 `ETC`). 2) 도로명주소가 있는 행은 `sido`가 채워진다. |

### 5.2 렌더링

| SRS ID | FRD ID | 요구사항 | 인수 기준 |
| --- | --- | --- | --- |
| SRS-RND-01 | FRD-RND-01 | 축제의 위도/경도를 XDWorld 좌표로 변환하고 `iconUrl` 이미지를 적용한 `JSBillboard`를 만들어 지도에 추가한다. 마커는 축제 id와 연결된 조회용 맵에 보관한다. | 1) 적재된 모든 축제가 올바른 위치에 마커로 보인다. 2) 마커 객체로 축제 id를 역조회할 수 있다. |
| SRS-RND-02 | 추가 (FRD 5절) | 줌 레벨에 따라 근접 마커를 클러스터 마커(개수 표시)로 합치고, 줌인하면 개별 마커로 풀어준다. 처리는 프론트엔드에서 한다. | 1) 전국 보기에서 개별 마커가 아닌 클러스터가 표시된다. 2) 클러스터 클릭 또는 줌인으로 하위 마커가 보인다. 3) 클러스터 개수의 합과 개별 마커 수의 합이 가시 영역(여유 영역 포함) 안의 필터 결과 건수와 같다. |
| SRS-RND-03 | 추가 (FRD 5절) | 현재 카메라 영역 밖의 마커는 그리지 않는다. 카메라 이동 종료 시 가시 영역을 다시 계산한다. | 1) 영역 밖 축제는 객체가 지도에 없다. 2) 이동 후 영역 안으로 들어온 축제가 나타난다. |

### 5.3 정보창 UI

| SRS ID | FRD ID | 요구사항 | 인수 기준 |
| --- | --- | --- | --- |
| SRS-UI-01 | FRD-UI-01 | 정보창 HTML/CSS 템플릿은 타이틀(축제명), 장소(개최장소, 도로명주소), 기간(시작\~종료), 주최/주관, 상세내용을 보여준다. 서버 데이터는 `textContent`로 삽입해 HTML로 해석되지 않게 한다. | 1) 5개 항목이 표시된다. 2) 값이 없는 항목은 "정보 없음"으로 표시된다. 3) `<script>`가 포함된 축제명을 넣어도 실행되지 않는다. |
| SRS-UI-02 | FRD-UI-02 | 마커 위치 좌표에 연동된 `JSHTMLObject`에 정보창 템플릿을 담고 초기 가시성을 `false`로 만든다. 마커마다 미리 만들면 수천 개가 되므로 해당 마커를 처음 클릭할 때 생성하고 id별로 캐시한다. | 1) 생성 직후 정보창이 화면에 보이지 않는다. 2) 같은 마커를 다시 클릭해도 객체가 중복 생성되지 않는다. |

### 5.4 이벤트

| SRS ID | FRD ID | 요구사항 | 인수 기준 |
| --- | --- | --- | --- |
| SRS-EVT-01 | FRD-EVT-01 | 지도 클릭 이벤트에서 Picking으로 `JSBillboard`를 판별한다. 마커가 작아 클릭이 빗나가지 않도록 클릭 지점 주변 허용 반경(기본 10px)을 둔다. | 1) 마커 중심과 가장자리 클릭 모두 해당 마커가 선택된다. 2) 마커가 아닌 곳을 클릭하면 선택 동작이 없다. |
| SRS-EVT-02 | FRD-EVT-02 | 마커가 선택되면 해당 축제의 상세를 조회(캐시에 없을 때만)해 정보창에 채우고 `JSHTMLObject` 가시성을 `true`로 바꾼다. | 1) 클릭 후 1초 안에 해당 축제의 정보창이 마커 위치에 보인다. 2) 정보창 내용이 클릭한 축제와 일치한다. |
| SRS-EVT-03 | FRD-EVT-03 | 다른 마커를 클릭하면 이전에 열린 정보창의 가시성을 `false`로 바꾸어 화면에 정보창이 항상 1개 이하가 되게 한다. | 1) 마커 A, B를 차례로 클릭하면 B만 보인다. 2) 어떤 시점에도 가시 상태의 정보창은 1개 이하이다. |
| SRS-EVT-04 | 추가 | 정보창의 닫기(×) 버튼 또는 지도 빈 곳 클릭으로 정보창을 닫는다. | 1) 닫기 버튼 클릭 시 가시성이 `false`가 된다. 2) 빈 곳 클릭 시에도 닫힌다. |

### 5.5 검색과 필터

| SRS ID | FRD ID | 요구사항 | 인수 기준 |
| --- | --- | --- | --- |
| SRS-SRC-01 | 추가 | 검색 패널에서 축제명 키워드(부분 일치), 기간(시작일\~종료일과 겹치는 축제), 시도, 유형(복수 선택)을 조합해 조회한다. 조건은 모두 AND로 결합한다. | 1) 조건 조합 결과가 서버 쿼리 결과와 일치한다. 2) 조건을 모두 비우면 전체가 조회된다. |
| SRS-SRC-02 | 추가 | 필터 결과로 지도 마커를 다시 구성하고 결과 건수를 표시한다. 결과가 0건이면 안내 문구를 보여준다. 결과 목록에서 항목을 선택하면 지도가 해당 마커로 이동하고 정보창이 열린다. | 1) 필터 적용 후 조건에 맞는 마커만 보인다. 2) 건수 표시가 API 응답의 count 값과 같고, 전국 보기에서는 마커 수(클러스터 개수 포함)의 합과 같다. 3) 목록 선택 시 SRS-EVT-02와 같은 정보창이 열린다. |

### 5.6 관리자

| SRS ID | FRD ID | 요구사항 | 인수 기준 |
| --- | --- | --- | --- |
| SRS-ADM-01 | 추가 (결정 4번) | 관리자 페이지(`/admin`)와 `/admin/api`는 로그인한 관리자만 접근한다. 미인증 요청은 로그인 페이지로 보내고(API는 401), 일반 방문자 화면과 `/api` 조회는 로그인 없이 쓴다. | 1) 로그인하지 않고 `/admin`에 접근하면 로그인 페이지로 이동한다. 2) 틀린 계정은 거부되고 오류 문구가 보인다. 3) 로그아웃 후 `/admin/api` 호출은 401을 돌려준다. |
| SRS-ADM-02 | 추가 (결정 4번) | 관리자가 CSV 파일(`.csv`, 최대 5MB)과 인코딩(기본 MS949, UTF-8 선택)을 올리면 4.3 절차로 재적재한다. 적재는 한 번에 한 건만 처리한다. | 1) 정상 CSV 적재 시 총계가 응답된다. 2) 같은 파일을 다시 올려도 `festival` 행 수가 늘지 않는다. 3) 적재 후 지도 화면을 새로 고치면 새 데이터가 보인다. 4) `.csv`가 아니거나 5MB를 넘으면 400, 진행 중 재요청은 409를 돌려준다. 5) CSV에 없는 기존 축제는 그대로 남는다. |
| SRS-ADM-03 | 추가 (결정 4번) | 적재 결과(총 행, 성공, 건너뜀)와 건너뛴 행의 행 번호·사유(최대 100건)를 화면에 보이고, 최근 적재 이력 20건을 최신순으로 조회한다. | 1) 건너뛴 행마다 행 번호와 사유가 표시된다. 2) 이력 목록은 최신순이며 20건을 넘지 않는다. |

## 6. API 인터페이스 명세

REST API는 일반용 조회 전용 4개를 `/api` 아래에, 관리자용 API 2개와 로그인·로그아웃을 `/admin` 아래에 두고 JSON(UTF-8)으로 응답한다(6.5). 목록 API는 마커 표시에 필요한 최소 필드만 내려주고, 무거운 `content`는 상세 API에서만 내려준다.

### 6.1 엔드포인트

| 메서드 | 경로 | 설명 | 요구사항 |
| --- | --- | --- | --- |
| GET | `/api/festivals` | 축제 목록(마커용 경량 필드). 필터 조건 지원 | SRS-DAT-01, DAT-02, RND-01, SRC-01 |
| GET | `/api/festivals/{id}` | 축제 상세(정보창용 전체 필드) | SRS-EVT-02, UI-01 |
| GET | `/api/festival-types` | 유형 목록과 마커 이미지 경로 | SRS-DAT-02, SRC-01 |
| GET | `/api/regions` | 데이터에 존재하는 시도 목록 | SRS-SRC-01 |

### 6.2 GET /api/festivals 요청 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | string | 아니오 | 축제명 부분 일치(대소문자 무시) |
| `startDate` | yyyy-MM-dd | 아니오 | 이 날짜 이후에 끝나는 축제만(기간이 겹침) |
| `endDate` | yyyy-MM-dd | 아니오 | 이 날짜 이전에 시작하는 축제만(기간이 겹침) |
| `sido` | string | 아니오 | 시도명 정확 일치 |
| `types` | string | 아니오 | 쉼표로 구분한 유형 코드. 예: `MUSIC,FOOD` |

기간 조건은 `start_date <= :endDate AND end_date >= :startDate`이며, 한쪽만 주어지면 그쪽 조건만 적용한다.

### 6.3 응답 예시

목록 응답(`GET /api/festivals?types=MUSIC`):

```json
{
  "count": 1,
  "items": [
    {
      "id": 101,
      "name": "○○ 재즈페스티벌",
      "lat": 37.5665,
      "lng": 126.9780,
      "typeCode": "MUSIC",
      "iconUrl": "/img/marker/MUSIC.png",
      "startDate": "2026-10-10",
      "endDate": "2026-10-12"
    }
  ]
}
```

상세 응답(`GET /api/festivals/101`):

```json
{
  "id": 101,
  "name": "○○ 재즈페스티벌",
  "venue": "△△ 공원",
  "roadAddr": "서울특별시 ○○구 △△로 1",
  "startDate": "2026-10-10",
  "endDate": "2026-10-12",
  "content": "축제 소개 문구",
  "hostOrg": "○○구청",
  "mngOrg": "○○문화재단",
  "lat": 37.5665,
  "lng": 126.9780,
  "typeCode": "MUSIC",
  "typeName": "음악"
}
```

### 6.4 오류 처리와 캐시

| 상황 | HTTP | 본문 |
| --- | --- | --- |
| 날짜 형식 오류, `startDate`가 `endDate`보다 늦음, 알 수 없는 유형 코드 | 400 | `{"code":"INVALID_PARAMETER","message":"…"}` |
| 존재하지 않는 id | 404 | `{"code":"FESTIVAL_NOT_FOUND","message":"…"}` |
| 서버 내부 오류 | 500 | `{"code":"INTERNAL_ERROR","message":"…"}` (스택트레이스는 응답에 넣지 않는다) |

- 공통 오류는 `@RestControllerAdvice`로 처리한다.
- 데이터는 CSV 적재 때만 바뀌므로 `/api/festival-types`와 `/api/regions`는 `Cache-Control: max-age=3600`을 붙인다. 목록 API는 필터 조합이 많아 캐시 헤더를 붙이지 않는다.

### 6.5 관리자 API

관리자 API는 로그인한 세션에서만 호출할 수 있으며, 시스템에서 유일하게 데이터를 쓰는 요청을 포함한다.

| 메서드 | 경로 | 설명 | 요구사항 |
| --- | --- | --- | --- |
| POST | `/admin/login` | 폼 로그인(`username`, `password`). 성공 시 세션 쿠키 발급 | SRS-ADM-01 |
| POST | `/admin/logout` | 로그아웃 | SRS-ADM-01 |
| POST | `/admin/api/import` | CSV 업로드 재적재. `multipart/form-data`: `file`, `encoding`(`MS949` 또는 `UTF-8`) | SRS-ADM-02 |
| GET | `/admin/api/imports` | 최근 적재 이력 20건(최신순) | SRS-ADM-03 |

적재 응답 예시(`POST /admin/api/import`):

```json
{
  "fileName": "festival.csv",
  "totalRows": 1500,
  "successRows": 1492,
  "skippedRows": 8,
  "skipped": [
    { "row": 12, "reason": "위도 범위 오류" },
    { "row": 87, "reason": "종료일이 시작일보다 빠름" }
  ]
}
```

`skipped`는 최대 100건이다. 관리자 API의 오류는 다음과 같다.

| 상황 | HTTP | code |
| --- | --- | --- |
| 로그인하지 않고 `/admin/api` 호출 | 401 | `UNAUTHORIZED` |
| CSRF 토큰 누락 또는 불일치 | 403 | `CSRF_INVALID` |
| `.csv`가 아님, 5MB 초과, 필수 헤더 누락, 인코딩 해석 실패 | 400 | `INVALID_FILE` |
| 다른 적재가 진행 중 | 409 | `IMPORT_IN_PROGRESS` |
| 내부 오류(트랜잭션은 롤백) | 500 | `INTERNAL_ERROR` |

- CSRF 토큰은 `XSRF-TOKEN` 쿠키로 내려주고, 관리자 화면 JS가 `X-XSRF-TOKEN` 헤더로 보낸다.
- 일반용 `/api`에는 `GET`만 두어 NFR-SEC-04를 만족시킨다.

## 7. 프론트엔드 설계

프론트엔드는 `static/` 아래 ES 모듈로 나눈 바닐라 JavaScript이며, XDWorld 호출은 `map/` 모듈에만 두고 상태는 `state/store.js`가 중계한다.

### 7.1 모듈 구성

| 파일 | 역할 | 요구사항 |
| --- | --- | --- |
| `js/main.js` | 초기화 순서: 엔진 준비, 유형·시도·목록 병렬 조회, 첫 렌더링 | SRS-DAT-01 |
| `js/api/festivalApi.js` | `fetch` 래퍼. 목록, 상세, 유형, 시도 호출과 오류 변환 | SRS-DAT-01, SRC-01 |
| `js/state/store.js` | 필터 조건, 목록, 선택된 id를 보관하고 변경을 구독자에게 알리는 단순 pub/sub | SRS-SRC-02, EVT-03 |
| `js/map/engine.js` | XDWorld 생성, 위경도에서 엔진 좌표로 변환, 화면에서 지면 좌표로 변환, 카메라 이동 종료 이벤트 구독 | SRS-RND-01 |
| `js/map/markerLayer.js` | `JSBillboard` 생성·삭제, 축제 id와 객체의 조회 맵 | SRS-RND-01 |
| `js/map/cluster.js` | 격자 클러스터링 순수 함수(엔진에 의존하지 않아 단위 테스트 가능) | SRS-RND-02 |
| `js/map/viewport.js` | 카메라 가시 영역 계산과 여유 영역 확장 | SRS-RND-03 |
| `js/map/picking.js` | 클릭 이벤트 수신, 허용 반경 Picking | SRS-EVT-01 |
| `js/map/infoWindow.js` | `JSHTMLObject` 생성·캐시, 가시성 전환, 단일 정보창 유지 | SRS-UI-02, EVT-02\~04 |
| `js/ui/infoTemplate.js` | 정보창 DOM 생성(`textContent` 삽입) | SRS-UI-01 |
| `js/ui/searchPanel.js` | 키워드·기간·시도·유형 입력과 적용·초기화 | SRS-SRC-01 |
| `js/ui/resultList.js` | 결과 건수와 목록, 항목 선택 시 지도 이동 | SRS-SRC-02 |

### 7.2 XDWorld 사용 방침

- `JSBillboard`, `JSHTMLObject`, 객체 선택(Pick), 좌표 변환은 `map/engine.js`의 얇은 어댑터 함수(이 문서의 가칭: `createBillboard`, `createHtmlObject`, `pickAt`, `toEnginePoint`)로 감싼다.
- 엔진 파일은 `static/js/engine/`에 있고, 엔진 초기화 스크립트 `static/js/module.js`를 `index.html`이 먼저 로드한다. `map/engine.js`는 초기화가 끝난 엔진 객체를 받아 쓴다.
- 실제 함수명과 인자는 XDWorld WebGL 매뉴얼(https://egiscorp.gitbook.io/xdworld-webgl-manual)의 해당 모듈 문서를 먼저 학습한 뒤 확정한다. 다른 모듈은 엔진을 직접 호출하지 않으므로 어댑터만 고치면 된다.
- 마커와 정보창 객체는 하나의 레이어에 모아 필터 변경 시 일괄 삭제할 수 있게 한다.

### 7.3 마커 생성 (SRS-RND-01)

1. 목록의 각 축제에서 `lat`, `lng`를 어댓터로 엔진 좌표로 변환한다.
2. `iconUrl` 이미지를 쓰는 `JSBillboard`를 만들어 지도 레이어에 추가한다.
3. 같은 `iconUrl`은 이미지를 한 번만 로드해 재사용한다(유형당 1장).
4. `markerLayer`는 축제 id를 객체로, 객체를 축제 id로 찾는 양방향 맵을 유지한다.

### 7.4 클러스터링과 가시 영역 (SRS-RND-02, 03)

격자 방식이다. 카메라 고도로 셀 크기를 정하고, 같은 셀에 든 축제가 2건 이상이면 클러스터 마커 하나로 그린다. 기준값은 초기 가정이며 실제 데이터와 엔진의 고도 단위로 조정한다.

| 카메라 고도 | 셀 크기(도) | 동작 |
| --- | --- | --- |
| 400 km 이상 | 1.0 | 전국 보기, 광역 단위 묶음 |
| 100\~400 km | 0.5 | 광역·권역 보기 |
| 30\~100 km | 0.2 | 시·군 보기 |
| 10\~30 km | 0.05 | 인접 축제만 묶음 |
| 10 km 미만 | 해제 | 모든 마커를 개별 표시 |

- 클러스터 마커는 개수를 Canvas로 그린 이미지를 쓰고 개수 값별로 캐시한다. 클릭하면 해당 셀 중심으로 한 단계 줌인한다.
- 가시 영역은 화면 네 모서리를 지면 좌표로 바꾼 경위도 범위에 20% 여유를 더한 사각형이다. 지평선이 보여 변환에 실패하는 모서리가 있으면 그 방향은 제한 없이 포함한다.
- 재계산은 카메라 이동 종료와 필터 변경 시 200ms 디바운스로 한다. 마커에 키(`f:{축제 id}` 또는 `c:{셀 키}:{개수}`)를 붙여 같은 키는 재사용하고 사라진 것만 삭제, 새 것만 생성한다.

### 7.5 클릭과 정보창 (SRS-UI-02, EVT-01\~04)

클릭은 마커가 아니면 정보창을 닫고, 클러스터면 줌인하며, 개별 마커일 때만 정보창을 연다.

&#91;embedded content: 마커 클릭 처리 흐름 · 판별 3단계\]

- Picking: 클릭 지점에서 먼저 판별하고 실패하면 반경 10px 원 둘레의 8방향 점에서 차례로 시도한다(최대 9회). 여러 객체가 잡히면 클릭 지점에 가장 가까운 것을 고른다.
- `JSHTMLObject` 하나가 축제 하나에 대응하며 첫 클릭 때 만들어 id로 캐시한다. 생성 직후 가시성은 `false`이고 위치는 마커 좌표에 위쪽 픽셀 오프셋을 준다.
- 열린 정보창을 가리키는 값은 `state`의 선택 id 하나뿐이므로, 새 마커를 클릭하면 이전 id의 가시성을 먼저 `false`로 한 뒤 새 정보창을 `true`로 한다.
- 정보창 DOM: 제목과 닫기(×) 버튼, 장소(개최장소, 도로명주소), 기간, 주최·주관, 상세내용(최대 높이 160px, 넘치면 스크롤). 값이 없으면 "정보 없음"을 표시한다.
- 상세 조회가 실패하면 정보창에 오류 문구와 다시 시도 버튼을 보여준다(NFR-REL-01).

### 7.6 필터 적용 흐름 (SRS-SRC-01, 02)

1. 검색 패널에서 적용을 누르면 `store`에 조건을 저장하고 열린 정보창을 닫는다.
2. `festivalApi`가 조건을 쿼리 문자열로 붙여 목록을 다시 요청한다.
3. 응답으로 `store`의 목록이 바뀌면 `resultList`가 건수와 목록을 갱신하고 `map/`이 클러스터를 다시 계산한다.
4. 0건이면 마커를 모두 제거하고 안내 문구를 보여준다.
5. 결과 목록에서 항목을 고르면 클러스터가 해제되는 고도로 해당 축제 좌표로 카메라를 이동하고, 이동 종료 후 SRS-EVT-02와 같은 정보창을 연다.

### 7.7 관리자 화면 (SRS-ADM-01\~03)

관리자 화면은 지도 화면과 별도 페이지이며 XDWorld를 쓰지 않는다. 서버 문자열은 모두 `textContent`로 넣는다(NFR-SEC-02).

- 파일: `static/admin/login.html`, `static/admin/index.html`, `static/js/admin/adminApi.js`, `static/js/admin/importPage.js`.
- 로그인 페이지: 계정과 비밀번호를 `POST /admin/login`으로 보내고, 실패하면 오류 문구를 보여준다.
- 적재 영역: `.csv` 파일 선택, 인코딩 선택(MS949 기본, UTF-8), 적재 버튼. 요청이 진행되는 동안 버튼을 비활성화한다.
- 결과 영역: 총 행, 성공, 건너뜀과 건너뛴 행의 행 번호·사유 표.
- 이력 영역: `GET /admin/api/imports`로 받은 최근 20건(파일명, 총 행, 성공, 건너뜀, 시각).
- `adminApi.js`는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 실어 보내고, 401을 받으면 로그인 페이지로 이동한다.

## 8. 비기능 요구사항

성능 수치는 확정된 상한인 축제 2,000건 기준의 목표값이다.

| ID | 분류 | 요구사항 | 측정 기준 |
| --- | --- | --- | --- |
| NFR-PERF-01 | 성능 | 목록 API는 필터 조합과 관계없이 빠르게 응답한다. gzip 압축을 켠다. | 2,000건 기준 서버 응답 500ms 이내, 압축 후 전송량 1MB 이하 |
| NFR-PERF-02 | 성능 | 첫 화면에서 지도 로딩과 클러스터 표시가 끝난다. | 페이지 로드 후 3초 이내(캐시 없는 로컬 네트워크) |
| NFR-PERF-03 | 성능 | 클러스터링과 가시 영역 최적화로 동시에 존재하는 `JSBillboard` 수를 줄여 지도 조작이 끊기지 않게 한다. | 카메라 이동 중 30fps 이상, 동시 마커 객체 2,000개 이하 |
| NFR-SEC-01 | 보안 | MyBatis 매퍼는 `#{}` 바인딩만 쓴다. `${}`는 금지하며 정렬 컬럼이 필요하면 화이트리스트로 검증한다. | 코드 리뷰에서 `${}` 사용 0건 |
| NFR-SEC-02 | 보안 | 정보창에 넣는 모든 서버 데이터는 `textContent`로 삽입한다. 응답에 `Content-Security-Policy` 헤더를 붙인다. | 스크립트가 든 데이터가 실행되지 않음 |
| NFR-SEC-03 | 보안 | DB 접속 정보와 관리자 계정 정보는 환경변수 또는 저장소에 올리지 않는 `application-local.yml`에 둔다. | 저장소에 비밀번호 0건 |
| NFR-SEC-04 | 보안 | 데이터 변경은 관리자 인증을 거친 CSV 적재 하나로만 가능하며 일반용 /api에는 GET만 둔다. CSRF 보호를 켜고 관리자 비밀번호는 BCrypt 해시로 보관하며, 미로그인 접근은 401 또는 로그인 페이지로 보낸다. 업로드는 확장자와 5MB 상한을 검사한다. | `GET` 외 메서드가 `/api`에 없음 |
| NFR-COMP-01 | 호환성 | WebGL 2를 지원하는 최신 Chrome과 Edge를 지원한다. 미지원 브라우저는 안내 문구를 보여준다. | 두 브라우저에서 전체 시나리오 통과 |
| NFR-REL-01 | 안정성 | API 호출이 실패하거나 엔진 로딩이 실패하면 화면에 오류 안내와 다시 시도 버튼을 보여준다. 개별 행 오류가 적재 전체를 중단시키지 않는다. | 서버 중단 상태에서 안내가 표시됨, 불량 행 포함 CSV 적재 완료 |
| NFR-MNT-01 | 유지보수 | 백엔드는 Controller, Service, Mapper 계층을 분리하고 프론트엔드는 ES 모듈로 역할별 파일을 나눈다. XDWorld 호출은 한 모듈에 모은다. | 엔진 API 호출이 `map/` 모듈 밖에 없음 |
| NFR-LOG-01 | 로깅 | SLF4J와 Logback을 쓴다. 적재 결과와 API 오류를 기록하고 파일은 일 단위로 롤링한다. | 적재 로그에 총계와 건너뛴 행 사유가 있음 |
| NFR-TEST-01 | 시험성 | 서비스와 CSV 검증 로직은 JUnit 5 단위 테스트, 매퍼는 테스트용 PostgreSQL에서 통합 테스트한다. | `gradlew.bat test` 통과 |

## 9. 개발환경 및 프로젝트 구조

단일 Gradle 프로젝트 하나에 백엔드와 프론트엔드 정적 파일을 함께 두고, 프론트엔드는 `src/main/resources/static`에서 Spring Boot가 직접 서빙한다. 패키지 기준 이름 `kr.example.festivalmap`은 실제 조직 도메인으로 바꾼다.

### 9.1 디렉터리 구조

```text
festival-map/
├─ build.gradle
├─ settings.gradle
├─ gradlew.bat
└─ src/
   ├─ main/
   │  ├─ java/kr/example/festivalmap/
   │  │  ├─ FestivalMapApplication.java
   │  │  ├─ common/            ApiError, GlobalExceptionHandler
   │  │  ├─ config/            SecurityConfig (관리자 보호, CSRF)
   │  │  ├─ festival/
   │  │  │  ├─ controller/     FestivalController
   │  │  │  ├─ service/        FestivalService
   │  │  │  ├─ mapper/         FestivalMapper (인터페이스)
   │  │  │  └─ dto/            FestivalSearchCond, FestivalMarkerDto, FestivalDetailDto
   │  │  ├─ type/              FestivalTypeController, FestivalTypeMapper
   │  │  └─ importer/          AdminImportController, ImportService, ImportHistoryMapper,
   │  │                        FestivalCsvReader, RowValidator, TypeClassifier
   │  └─ resources/
   │     ├─ application.yml
   │     ├─ application-local.yml   (저장소 제외)
   │     ├─ db/schema.sql, db/data-types.sql
   │     ├─ mapper/FestivalMapper.xml, FestivalTypeMapper.xml, ImportHistoryMapper.xml
   │     └─ static/
   │        ├─ index.html           지도 화면
   │        ├─ admin/               login.html, index.html (관리자 화면)
   │        ├─ css/app.css
   │        ├─ img/marker/          MUSIC.png, FOOD.png, ...
   │        └─ js/
   │           ├─ engine/           XDWorld 엔진 파일
   │           ├─ module.js         엔진 초기화 스크립트
   │           ├─ main.js, api/, map/, ui/, state/
   │           └─ admin/            adminApi.js, importPage.js
   └─ test/java/kr/example/festivalmap/
```

### 9.2 build.gradle 의존성

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.x'      // 구현 시 4.x 최신 안정 버전으로 확정
    id 'io.spring.dependency-management' version '1.1.x'
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'   // Boot 4에서 바뀐 웹 스타터 이름
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.x' // Boot 4 호환 버전
    implementation 'org.apache.commons:commons-csv:1.x'
    runtimeOnly    'org.postgresql:postgresql'
    // Boot 4는 테스트 스타터도 모듈별로 나뉜다
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
}
```

### 9.3 application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/festival
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
server:
  compression:
    enabled: true
    mime-types: application/json
admin:
  username: ${ADMIN_USER}
  password-hash: ${ADMIN_PASSWORD_HASH}   # BCrypt 해시
```

### 9.4 MyBatis 목록 조회 매퍼 예시

모든 조건을 `#{}`로 바인딩하고 조건이 비면 해당 절을 생략한다(SRS-SRC-01, NFR-SEC-01).

```xml
<select id="findMarkers" resultType="FestivalMarkerDto">
  SELECT f.festival_id AS id, f.festival_name AS name,
         f.latitude AS lat, f.longitude AS lng,
         f.type_code, t.marker_image AS icon_url,
         f.start_date, f.end_date
    FROM festival f JOIN festival_type t ON t.type_code = f.type_code
  <where>
    <if test="keyword != null and keyword != ''">
      AND f.festival_name ILIKE '%' || #{keyword} || '%'
    </if>
    <if test="startDate != null"> AND f.end_date   &gt;= #{startDate} </if>
    <if test="endDate   != null"> AND f.start_date &lt;= #{endDate}   </if>
    <if test="sido != null and sido != ''"> AND f.sido = #{sido} </if>
    <if test="types != null and types.size() > 0">
      AND f.type_code IN
      <foreach collection="types" item="c" open="(" separator="," close=")">#{c}</foreach>
    </if>
  </where>
  ORDER BY f.start_date, f.festival_id
</select>
```

### 9.5 Windows 개발 시 유의점

- 실행은 `gradlew.bat bootRun`, 빌드는 `gradlew.bat build`를 쓴다. Spring Boot 4는 Java 17 이상과 Gradle 8.14 이상 또는 9.x가 필요하므로 `gradlew.bat --version`으로 Wrapper 버전을 먼저 확인한다.
- 한글 CSV는 CP949인 경우가 많으므로 읽을 때 `Charset.forName("MS949")`를 기본으로 하고 UTF-8이면 업로드 화면의 인코딩 선택으로 바꾼다.
- 콘솔 한글 로그가 깨지면 `chcp 65001`과 JVM 옵션 `-Dfile.encoding=UTF-8`을 쓴다.
- VS Code는 Extension Pack for Java와 Spring Boot Extension Pack을 설치하고, 프론트엔드 파일은 같은 창에서 편집한다. 정적 파일만 고칠 때는 `spring-boot-devtools`를 추가하면 재시작 없이 반영된다.

## 10. 요구사항 추적성 매트릭스

FRD 8건은 모두 SRS 요구사항, API, 프론트엔드 모듈에 대응한다. 모듈 파일명은 7절의 구조와 같다.

| FRD ID | SRS ID | API | 프론트엔드 모듈 | 백엔드/DB |
| --- | --- | --- | --- | --- |
| FRD-DAT-01 | SRS-DAT-01, SRS-DAT-03 | `GET /api/festivals`, `POST /admin/api/import` | `api/festivalApi.js`, `admin/importPage.js` | `AdminImportController`, `ImportService`, `FestivalCsvReader`, `RowValidator`, `TypeClassifier`, 테이블 `festival` |
| FRD-DAT-02 | SRS-DAT-02 | `GET /api/festival-types`, 목록 응답의 `iconUrl` | `map/markerLayer.js` | `festival_type.marker_image`, `static/img/marker/` |
| FRD-RND-01 | SRS-RND-01 | `GET /api/festivals` | `map/engine.js`, `map/markerLayer.js` | `FestivalMapper.findMarkers` |
| FRD-UI-01 | SRS-UI-01 | `GET /api/festivals/{id}` | `ui/infoTemplate.js` | `FestivalMapper.findById` |
| FRD-UI-02 | SRS-UI-02 | 없음 | `map/infoWindow.js` | 없음 |
| FRD-EVT-01 | SRS-EVT-01 | 없음 | `map/picking.js` | 없음 |
| FRD-EVT-02 | SRS-EVT-02 | `GET /api/festivals/{id}` | `map/picking.js`, `map/infoWindow.js`, `ui/infoTemplate.js` | `FestivalController.getDetail` |
| FRD-EVT-03 | SRS-EVT-03, SRS-EVT-04 | 없음 | `map/infoWindow.js`, `state/store.js` | 없음 |
| 추가 (FRD 5절) | SRS-RND-02, SRS-RND-03 | 없음 | `map/cluster.js`, `map/viewport.js` | 없음 |
| 추가 (검색/필터) | SRS-SRC-01, SRS-SRC-02 | `GET /api/festivals`(필터), `/api/festival-types`, `/api/regions` | `ui/searchPanel.js`, `ui/resultList.js`, `state/store.js` | `FestivalMapper.findMarkers` |
| 추가 (결정 4번, 관리자) | SRS-ADM-01, SRS-ADM-02, SRS-ADM-03 | `POST /admin/login`, `POST /admin/logout`, `POST /admin/api/import`, `GET /admin/api/imports` | `static/admin/*`, `admin/adminApi.js`, `admin/importPage.js` | `SecurityConfig`, `ImportService`, `ImportHistoryMapper`, 테이블 `import_history` |

## 11. 미결 사항 및 확인 필요 항목

결정 사항이 적힌 항목은 본문에 반영을 마쳤다. 9번과 10번은 결정 4번에서 새로 생긴 항목이며 현재 가정과 동일하게 확정되었다.

| # | 확인 항목 | 현재 가정 | 결정 사항 | 영향 절 |
| --- | --- | --- | --- | --- |
| 1 | 표준데이터에 축제 유형 컬럼이 없다. 유형 목록과 분류 방식을 확정해야 한다. | 6종(음악, 전통·역사, 음식, 자연·꽃, 예술·공연, 기타)을 키워드 규칙으로 분류. 마커 이미지 6장 제작 | `현재 가정` 내용과 동일 | 4.2, 5.1 |
| 2 | XDWorld 버전, 라이선스, 엔진 파일 배포 방식, `JSBillboard`/`JSHTMLObject` 생성·Picking·좌표 변환 API의 정확한 함수명 | 엔진 파일을 `static/lib/xdworld/`에 두고 서버에서 서빙. 함수명은 7절에서 개념 수준으로만 기술. 클러스터 개수 이미지를 Canvas로 만들어 JSBillboard에 쓸 수 있는지도 확인 | 엔진 파일은 `resources/static/js/engine/`에 존재하고, 엔진 초기화 스크립트(`resources/static/js/module.js`)를 이용하여 서버에 서빙. 함수명은 `https://egiscorp.gitbook.io/xdworld-webgl-manual`에 접속해 관련 모듈 문서를 학습한 이후 필요한 함수명 사용. | 7, 9 |
| 3 | Java, Spring Boot, PostgreSQL 버전 | Java 17, Spring Boot 3.x, PostgreSQL 15 이상 | Java 17, Spring Boot 4.x, PostgreSQL 15 이상 | 2.2, 9 |
| 4 | 실제 데이터 건수와 CSV 갱신 주기 | 3,000건 이하, 연 수회 재적재. 재적재 시 사라진 축제는 삭제하지 않음 | 2000건 이하 관리자가 관리자 페이지에서 재적재. 재적재 시 사라진 축제는 삭제하지 않음 | 1.2, 2, 3, 4.3, 5.6, 6.5, 7.7, 8 |
| 5 | 정보창 `JSHTMLObject`를 첫 클릭 시 생성·캐시해도 되는지(FRD-UI-02는 마커마다 준비하는 것으로 읽힘) | 첫 클릭 시 생성, 초기 가시성 `false` | `현재 가정` 내용과 동일 | 5.3 |
| 6 | 추가 요구 승인: 정보창 닫기(SRS-EVT-04), 필터 결과 목록과 목록 선택 시 지도 이동(SRS-SRC-02) | 포함 | 포함 | 5.4, 5.5 |
| 7 | 위도와 경도가 바뀐 행의 처리 | 범위를 벗어나면 제외하고 로그만 남김(교환 후 재검증은 하지 않음) | `현재 가정` 내용과 동일 | 4.3 |
| 8 | 배포 환경과 동시 접속자 수 | 로컬 또는 단일 서버, 동시 접속 수십 명 이하 | `현재 가정` 내용과 동일 | 8 |
| 9 | 관리자 인증 방식(결정 4번으로 추가): 계정 수, 계정 저장 위치, 로그인 방식 | Spring Security 폼 로그인, 관리자 계정 1개를 환경변수(`ADMIN_USER`, BCrypt 해시 `ADMIN_PASSWORD_HASH`)로 설정, 세션 쿠키 사용 | `현재 가정` 내용과 동일 | 2.2, 5.6, 6.5, 8, 9 |
| 10 | CSV 업로드 제한과 처리 방식(결정 4번으로 추가) | `.csv`만, 최대 5MB, 요청 안에서 동기 처리, 동시에 1건만 적재 | `현재 가정` 내용과 동일 | 4.3, 5.6, 6.5 |
