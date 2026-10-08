# 소프트웨어 요구사항 명세서 (SRS)

**프로젝트:** 3D 지도 커스텀 POI (사진·기록 POI) 구현
**기준 문서:** 기능요구사항정의서(FRD) 및 협의 내용
**문서 상태:** 초안 v0.2 (11장 `결정 사항` 반영, 신규 미결 사항은 11.2 참조)
**작성일:** 2026-10-06

### 문서 이력
| 버전 | 일자 | 내용 |
| --- | --- | --- |
| v0.1 | 2026-10-06 | 최초 작성 |
| v0.2 | 2026-10-06 | 11장 Q1~Q12 결정 사항 반영. FR-06 변경, FR-12~14 추가, 인증·소유자 단위 접근, 사진 GPS 기반 위치, 소프트 삭제·이력 테이블 반영 |

> 표기 규칙
> - **[결정 Qn]** 11장 `결정 사항`으로 확정된 사항 (Q 번호는 11.1 참조)
> - **[확정]** 그 외 사용자 협의로 결정된 사항
> - **[가정]** 협의되지 않아 임시로 둔 값. 11.2의 신규 미결 사항 또는 11.3에서 확인한다
> - **[검증]** XDWorld 매뉴얼에서 API 존재는 확인했으나 실제 동작은 PoC에서 확인해야 하는 항목

---

## 1. 개요

### 1.1 목적
FRD에 정의된 커스텀 POI 기능을 Spring Boot MVC 백엔드, 바닐라 JS 프론트엔드, PostgreSQL/PostGIS, XDWorld WebGL 엔진 조합으로 구현하기 위한 소프트웨어 요구사항과 설계 기준을 정의한다.

### 1.2 범위
| 구분 | 내용 |
| --- | --- |
| 포함 | 로그인·권한(소유자 단위 CRUD), 사진 GPS 기반 POI 등록, POI 조회·수정·삭제(소프트 삭제), 추억 묶음 선택 및 묶음별 색상, 이미지 업로드/썸네일/제공, 지도 마커 표출, 클릭 시 HTML 정보창, 카메라 이동 시 영역 조회, 수정·삭제 이력 보관 |
| 제외 | 다중 이미지(POI당 사진 1장), 타 사용자와의 POI 공유 [가정], 이력 조회·복원 화면 [가정], 댓글, 검색/필터, 모바일 전용 UI |

### 1.3 FRD 대비 변경·추가 사항
| 구분 | 내용 |
| --- | --- |
| 추가 | **POI 생성**: `사진과 기록 저장` 버튼으로 POI 생성 (FR-08) [확정] |
| 추가 | **POI 수정/삭제**: 생성된 POI를 지도에서 클릭 후 수정·삭제 (FR-09, FR-10) [확정] |
| 변경 | **위치는 사진의 GPS 메타데이터에서 추출**한다. 지도 클릭으로 위치를 고르지 않는다 (FR-08) [결정 Q2, Q8] |
| 변경 | **전체 목록 조회 폐기.** PostGIS 공간 인덱스로 보이는 영역만 조회하는 방식만 사용한다 (FR-06, FR-07) [결정 Q3] |
| 추가 | **로그인·권한**: CRUD는 권한이 있는 사용자 단위로 동작한다 (FR-13) [결정 Q4] |
| 변경 | **삭제는 소프트 삭제**(삭제 표시)이다 (FR-10) [결정 Q5] |
| 추가 | **추억 묶음**: 묶음 선택 영역을 두고 묶음별 색상을 정보창 타이틀바에 적용한다 (FR-12, FR-03) [결정 Q11] |
| 추가 | **변경 이력 백업**: 수정 시 이전 내용을 백업 테이블에 저장한 뒤 메인 테이블을 갱신한다 (FR-14) [결정 Q12] |
| 구체화 | 이미지는 서버 파일로 저장하고 DB에는 경로만 저장한다. 인증을 거쳐서만 제공한다 (FR-11) |
| 구체화 | 정보창은 HTML Overlay(`JSHTMLObject`)로 구현한다 (FR-03, FR-05) [확정] |

### 1.4 용어
| 용어 | 설명 |
| --- | --- |
| POI | Point of Interest. 이 문서에서는 사진 1장·제목·내용·좌표·추억 묶음을 가진 지도 지점 |
| 마커 | 지도에 작은 이미지로 표출되는 POI (XDWorld `JSPoint`) |
| 정보창 | 마커 클릭 시 표출되는 확장 UI (XDWorld `JSHTMLObject`에 담는 HTML 요소) |
| 추억 묶음 | 사용자가 POI를 분류하는 단위. 이름과 색상을 가진다 |
| BBox | 화면에 보이는 영역의 경위도 사각형 (minLon, minLat, maxLon, maxLat) |
| EXIF GPS | 사진 파일에 기록된 위도·경도·고도 메타데이터 |
| 소프트 삭제 | 행을 지우지 않고 `deleted_at`에 삭제 시각을 기록하는 방식 |
| 이력 테이블 | 수정·삭제 직전 상태를 보관하는 백업 테이블 `tb_poi_history` |
| SRID 4326 | WGS84 경위도 좌표계. XDWorld `JSVector3D`는 (경도, 위도, 고도[m]) 순서 |

### 1.5 참조 문서
- 기능요구사항정의서(FRD)
- [XDWorld WebGL Manual](https://egiscorp.gitbook.io/xdworld-webgl-manual): 시작하기, POI 생성하기, JSPoint, JSHTMLObject, JSLayer, JSObject3D, JSMap, JSCamera, EVENT

---

## 2. 전체 설명

### 2.1 개발·운영 환경
| 항목 | 값 | 비고 |
| --- | --- | --- |
| OS | Windows | [확정] |
| IDE | VS Code | [확정] Extension Pack for Java, Spring Boot Extension Pack 권장 |
| 언어 / 프레임워크 | Java, Spring Boot, Spring MVC | [확정] **버전은 `build.gradle`에 기재한 값을 기준**으로 한다 [결정 Q9] (참고: Java 17, Boot 3.x) |
| 빌드 | Gradle | [확정] |
| DB | PostgreSQL + PostGIS | [확정] 공간 인덱스 요구로 PostGIS 필요 |
| DB 연동 | MyBatis | [확정] `mybatis-spring-boot-starter` (Boot 3.x용 3.0.x) |
| 인증·인가 | Spring Security (세션 로그인) | [가정] 방식은 N2 확인 |
| 프론트엔드 | Vanilla JS (ES Modules), HTML, CSS | [확정] 번들러 없음 |
| 3D 엔진 | XDWorld WebGL, **로컬 호스팅** | [결정 Q1] |
| 브라우저 | 최신 Chrome / Edge | [가정] WebGL2 필요 |

### 2.2 사용자
| 사용자 | 설명 |
| --- | --- |
| 로그인 사용자 | 본인 소유 POI와 추억 묶음을 조회·등록·수정·삭제한다 [결정 Q4]. 타인의 POI는 조회할 수 없다 [가정: 비공개, N2] |
| 관리자 | 역할 구분 여부와 권한은 미정 [가정, N2] |

### 2.3 제약 조건
- XDWorld는 전역 객체명 `Module`을 사용해야 하며, `Module.postRun`에 초기화 함수를 지정해야 한다.
- 엔진 파일(`XDWorldEM.js`, `XDWorldEM.wasm`, `XDWorldWorker.js`, `XDWorldWorker.wasm`)은 `static/xdworld/`에 두고, 초기화 스크립트는 `static/js/module.js`로 둔다 [결정 Q1 해석, N1]. `.wasm`은 `application/wasm` MIME으로 제공되어야 한다.
- 지도 위에 UI를 겹칠 때 canvas가 UI 아래에 오도록 z-index를 조정해야 한다.
- `JSObject3D.setProperty`는 같은 이름으로 재설정할 수 없으므로, 변경 가능한 POI 데이터는 객체 속성이 아니라 프론트엔드 상태 저장소에 둔다.
- 위치는 사진의 EXIF GPS에서 얻으므로 GPS가 없는 사진은 위치를 정할 수 없다 (FR-08, N3). 일반적으로 JPEG에는 GPS가 있고 PNG에는 없는 경우가 많다.

### 2.4 가정 및 의존성
11장 참조.

---

## 3. 시스템 구조

### 3.1 논리 구성
```
[Browser]
  login.html / index.html + Vanilla JS modules
    ├─ XDWorld Engine (Module, canvas, JSHTMLObject)
    ├─ EXIF 판독 (사진 선택 시 GPS 미리보기)
    └─ fetch() ──▶ REST API (/api/**) + 세션 쿠키 + CSRF 토큰
                      │
[Spring Boot MVC]     ▼
  SecurityFilterChain (인증·CSRF)
  Controller ─▶ Service ─▶ Mapper(MyBatis) ─▶ PostgreSQL + PostGIS
                  ├────▶ GpsExtractService (metadata-extractor)
                  ├────▶ FileStorageService ─▶ 로컬 디스크(정적 리소스 밖)
                  └────▶ ThumbnailService
```

### 3.2 백엔드 구조 (패키지 루트는 `build.gradle`의 `group` 기준, 아래는 임시값 [결정 Q9])
```
src/main/java/com/example/poimap
├─ PoiMapApplication.java
├─ config
│   ├─ SecurityConfig.java         // 인증/인가, CSRF, 401 JSON 응답
│   └─ UploadProperties.java       // app.upload.* 바인딩
├─ common
│   ├─ ApiResponse.java
│   ├─ ErrorCode.java
│   ├─ BusinessException.java
│   └─ GlobalExceptionHandler.java // @RestControllerAdvice
├─ auth
│   ├─ controller/AuthController.java   // GET /api/me
│   ├─ service/AppUserDetailsService.java
│   ├─ mapper/UserMapper.java
│   └─ domain/User.java
├─ group
│   ├─ controller/MemoryGroupController.java
│   ├─ service/MemoryGroupService.java
│   ├─ mapper/MemoryGroupMapper.java
│   └─ dto/ (GroupResponse, GroupCreateRequest)
└─ poi
    ├─ controller/PoiController.java
    ├─ controller/PoiImageController.java   // 이미지·썸네일 (소유자 확인 후 제공)
    ├─ service/PoiService.java              // 트랜잭션: 이력 백업 + 갱신
    ├─ service/GpsExtractService.java
    ├─ service/FileStorageService.java
    ├─ service/ThumbnailService.java
    ├─ mapper/PoiMapper.java
    ├─ mapper/PoiHistoryMapper.java
    ├─ domain/Poi.java
    └─ dto/ (PoiSummaryResponse, PoiDetailResponse, PoiCreateRequest, PoiUpdateRequest, BBoxRequest)
src/main/resources
├─ application.yml
├─ mapper/{auth,group,poi}/*.xml
└─ static/ (프론트엔드, 3.3 참조)
```

### 3.3 프론트엔드 구조 (바닐라, `src/main/resources/static`)
```
static
├─ login.html                      // 로그인 화면
├─ index.html                      // 지도 화면 (인증 필요)
├─ css/poi.css                     // 정보창, 좌측 패널 스타일
├─ xdworld/                        // 엔진 파일 로컬 호스팅 [결정 Q1]
└─ js
    ├─ module.js                   // Module 선언, postRun, 엔진 로드 (비-모듈 스크립트) [결정 Q1]
    ├─ main.js                     // 앱 진입점 (type="module")
    ├─ config.js                   // 레이어명, 디바운스 시간, 한도 등 상수
    ├─ store.js                    // POI·묶음 상태 저장소, 선택 상태
    ├─ lib/exifr.lite.umd.js       // EXIF GPS 판독 라이브러리 (로컬 파일) [가정]
    ├─ api/http.js                 // fetch 래퍼: CSRF 헤더 부착, 401 시 로그인 이동
    ├─ api/poiApi.js
    ├─ api/groupApi.js
    ├─ map/mapView.js              // 카메라 이동 종료 감지, BBox 계산
    ├─ map/poiLayer.js             // JSPoint 생성/삭제/표시, 아이콘 생성
    ├─ map/mapEvents.js            // 객체 선택, 카메라 이벤트 등록
    ├─ ui/popup.js                 // JSHTMLObject 정보창 (보기/수정 모드, 묶음 색 적용)
    ├─ ui/registerPanel.js         // 좌측 패널: 사진과 기록 저장 폼
    ├─ ui/groupSelector.js         // 좌측 패널: 추억 묶음 선택 영역
    └─ util/gpsReader.js           // 사진에서 GPS 추출
```
- `module.js`는 엔진이 요구하는 전역 `Module`을 선언하므로 일반 `<script>`로 로드하고, `postRun` 시점에 `main.js`를 동적 import 한다.

### 3.4 레이어·객체 규칙
| 항목 | 값 |
| --- | --- |
| 레이어 | `POI_LAYER` (`Module.ELT_3DPOINT`), `setSelectable(true)` |
| 객체 키 | `POI_{poiId}` (예: `POI_17`), 등록 미리보기용 임시 마커는 `POI_TEMP` |
| 선택 이벤트 | `Fire_EventSelectedObject`의 `layerName`, `objKey`로 poiId 역산 |
| 정보창 | `JSHTMLObject` 단일 인스턴스 재사용 (위치·내용·타이틀바 색만 교체) |

---

## 4. 기능 요구사항

### 4.1 요구사항 목록
| ID | 기능명 | FRD 매핑 / 근거 | 우선순위 |
| --- | --- | --- | --- |
| FR-01 | 기본 POI(썸네일 마커) 렌더링 | FRD FR-01 | 필수 |
| FR-02 | POI 클릭 이벤트 처리 | FRD FR-02 | 필수 |
| FR-03 | 상세 정보창 표출 (묶음 색 타이틀바) | FRD FR-03, Q11 | 필수 |
| FR-04 | 정보창 닫기 및 마커 복귀 | FRD FR-04 | 필수 |
| FR-05 | 지도 조작 시 정보창 위치 동기화 | FRD FR-05 | 필수 |
| FR-06 | 초기 표출 영역 POI 조회 | 신규, **v0.1의 전체 조회에서 변경** (Q3) | 필수 |
| FR-07 | 카메라 이동 시 화면 영역 POI 조회 | 신규 (Q3) | 필수 |
| FR-08 | POI 생성 (`사진과 기록 저장`, 사진 GPS 위치) | 신규 (Q2, Q6, Q8) | 필수 |
| FR-09 | POI 수정 | FRD 수정 버튼 구체화 (Q10, Q12) | 필수 |
| FR-10 | POI 삭제 (소프트 삭제) | 신규 (Q5) | 필수 |
| FR-11 | 이미지 업로드·정규화·썸네일·제공 | 신규 (Q6) | 필수 |
| FR-12 | 추억 묶음 선택·색상 | 신규 (Q11) | 필수 |
| FR-13 | 로그인·권한 (소유자 단위 CRUD) | 신규 (Q4) | 필수 |
| FR-14 | 수정·삭제 이력 보관 | 신규 (Q12) | 필수 |

### 4.2 상세 요구사항

#### FR-01 기본 POI(썸네일 마커) 렌더링
- 시스템은 POI의 좌표(경도, 위도, 고도)에 `JSPoint`를 생성해 `POI_LAYER`에 추가한다.
- 초기 상태에서는 작은 이미지 아이콘만 표출하고 텍스트(`setText`)는 설정하지 않는다.
- 아이콘 이미지는 서버가 생성한 썸네일(`thumbUrl`)을 로드해 canvas에 그린 뒤 `getImageData`로 `poi.setImage(data, w, h)`에 적용한다. 썸네일은 인증이 필요한 같은 출처 API이므로 세션 쿠키가 함께 전송되고 canvas 오염(CORS) 문제가 없다.
- 이미지 로드에 실패하면 기본 아이콘(canvas로 그린 단색 핀)으로 대체한다.
- 썸네일 크기는 64×64 px [가정].
- 표시 고도는 저장된 고도와 해당 위치의 지형 고도(`getTerrHeight`) 중 큰 값을 사용해 마커가 지형에 묻히지 않게 한다 [가정, N8]. **[검증]** PoC에서 EXIF 고도와 지형 고도의 차이를 확인한다.

#### FR-02 POI 클릭 이벤트 처리
- 시스템은 canvas(`Module.canvas`)에 `Fire_EventSelectedObject` 리스너를 등록한다.
- 이벤트 인자 `layerName`이 `POI_LAYER`일 때만 처리하고, `objKey`에서 poiId를 추출한다.
- poiId에 해당하는 상세 데이터를 `GET /api/pois/{poiId}`로 조회한다 (캐시에 상세가 있으면 생략. 수정·삭제 시 캐시를 무효화한다).
- 정보창이 이미 열려 있으면 먼저 닫고(FR-04), 수정 중이면 확인창을 띄운다.

#### FR-03 상세 정보창 표출
- 클릭된 POI의 마커는 `setVisible(false)`로 숨기고, 같은 좌표에 정보창을 표출한다 (마커를 가리거나 대체).
- 정보창은 `JSHTMLObject`에 HTML 요소를 담아 표출한다. 위치는 POI 좌표이며 말풍선 꼬리가 좌표를 가리키도록 정렬 옵션을 설정한다.
- **보기 모드 구성**
  1. 타이틀바: POI 제목, 우측 상단 닫기(X) 버튼. **배경색은 POI가 속한 추억 묶음의 색상**이다 [결정 Q11]
  2. 확장 이미지 (`GET /api/pois/{poiId}/image`, 최대 폭 기준 축소)
  3. 내용 텍스트 (흰색)
  4. 버튼: `수정`, `삭제`
- 타이틀바 색은 서버가 검증한 `#RRGGBB` 값만 CSS 속성(`style.backgroundColor`)으로 적용한다.
- 모든 사용자 입력 텍스트는 `textContent`로 삽입한다 (XSS 방지, `innerHTML` 금지).
- 정보창은 동시에 1개만 열린다 [결정 Q10].

#### FR-04 정보창 닫기 및 상태 복귀
- 닫기(X) 클릭 시 정보창 요소를 숨기고(`display:none`) 해당 POI 마커를 `setVisible(true)`로 복원한다.
- 수정 모드에서 닫으면 변경 내용이 있을 때 확인창을 띄운다.

#### FR-05 지도 조작 시 위치 동기화
- 정보창이 열린 상태에서 이동·확대/축소·회전 시 정보창이 POI 좌표를 따라가야 한다.
- 1차 구현: `JSHTMLObject`의 좌표 기반 오버레이 기능에 위임한다. **[검증]** 카메라 조작 중 위치가 갱신되는지 PoC 확인.
- 대체 구현(1차가 불충분할 경우): `Module.getMap().MapToScreenPointEX(position)`으로 화면 좌표를 구해 `requestAnimationFrame`에서 요소 위치를 갱신한다.
- POI가 화면 밖으로 나가면 정보창은 열린 상태를 유지하되 화면 밖에서는 보이지 않아도 된다 [가정].

#### FR-06 초기 표출 영역 POI 조회 [변경, 결정 Q3]
- 전체 목록 조회 API는 두지 않는다.
- 엔진 초기화와 초기 카메라 설정이 끝나면 현재 화면 BBox를 계산해 `GET /api/pois/bbox`를 **명시적으로 1회 호출**하고, 응답으로 마커를 생성한다. (초기에는 카메라 이동 종료 이벤트가 발생하지 않을 수 있으므로 별도 호출이 필요하다.)
- 초기 카메라 위치는 N5에서 정한다.
- **[검증]** 지형이 로드되기 전에는 `ScreenToMapPointEX`가 `null`을 줄 수 있으므로, 폴백 BBox(FR-07)를 사용하거나 첫 `Fire_EventCameraMoveEnd` 이후로 지연한다.

#### FR-07 카메라 이동 시 화면 영역 POI 조회 [결정 Q3]
- `Fire_EventCameraMoveEnd` 수신 후 300 ms 디바운스를 거쳐 현재 화면 BBox를 계산하고 `GET /api/pois/bbox`를 호출한다.
- **BBox 계산:** 화면 네 모서리와 중심을 `Module.getMap().ScreenToMapPointEX`로 변환해 min/max 경위도를 구한다. **[검증]** 인자 형식(JSVector2D 또는 x, y)과 하늘 방향 모서리에서 `null` 반환 여부를 확인한다.
- **폴백:** 변환 결과가 `null`인 모서리가 있으면 이벤트의 카메라 위치와 고도를 이용해 반경 `max(고도 × 2, 500m)` 정사각형 BBox를 사용한다 [가정].
- 서버는 세션의 로그인 사용자 **본인의 삭제되지 않은 POI**만 BBox 조건으로 조회한다. 요청에 소유자 값을 받지 않는다 [결정 Q4].
- 응답 처리: 마커가 없는 POI는 생성하고, 현재 BBox에서 벗어난 마커는 `removeAtKey`로 제거해 렌더링 객체 수를 제한한다. 정보창이 열린 POI는 BBox 밖이어도 유지한다.
- BBox가 직전 요청과 사실상 같으면(변화 0.0001° 미만) 요청을 생략한다.
- 이전 요청이 끝나기 전 새 요청이 발생하면 이전 요청은 `AbortController`로 취소한다.
- 서버 응답 건수는 최대 1,000건으로 제한한다. 사용자별 POI가 1,000건 미만으로 예상되므로 사실상 전체가 한 번에 내려올 수 있다 [결정 Q7, 한도값은 가정].

#### FR-08 POI 생성 (`사진과 기록 저장`) [결정 Q2, Q6, Q8]
- 화면 **좌측 패널**에 다음을 둔다: 추억 묶음 선택 영역(FR-12), 사진 선택, 제목, 내용, `사진과 기록 저장` 버튼.
- `사진과 기록 저장` 버튼을 한 번 사용할 때마다 **사진 1장**으로 POI 1개를 만든다. 사진은 필수이며 JPEG/PNG, 최대 5 MB이다.
- **사진 선택 시(클라이언트):** 브라우저에서 EXIF GPS(위도, 경도, 고도)를 읽어 다음을 수행한다.
  - 임시 마커(`POI_TEMP`)를 해당 위치에 가시화하고 카메라를 그 위치로 이동한다 (`getViewCamera().moveLonLatAlt` 등) [가정].
  - 패널에 좌표와 고도를 읽기 전용으로 표시한다.
  - GPS가 없으면 안내 문구를 표시하고 저장 버튼을 비활성화한다 [가정, N3].
- **저장 시(서버):** 클라이언트가 읽은 좌표는 신뢰하지 않는다. 서버가 업로드된 원본에서 EXIF GPS를 **직접 다시 추출**해 좌표를 결정한다.
  - GPS가 없으면 `GPS_NOT_FOUND`(422)로 거부한다 [가정, N3].
  - 고도는 EXIF `GPSAltitude`를 사용하며, `GPSAltitudeRef`가 1이면 음수로 처리한다. 고도가 없으면 0으로 저장한다 [결정 Q8, 폴백은 가정].
  - GPS 추출은 이미지 재인코딩(FR-11)보다 **먼저** 수행한다 (재인코딩 시 메타데이터가 제거되므로).
- **입력 검증:** 추억 묶음 필수(본인 소유), 제목 필수 1~100자, 내용 0~2000자, 사진 필수.
- 버튼 클릭 시 `POST /api/pois` (multipart)를 호출하고, 성공 응답으로 임시 마커를 정식 마커로 교체한다. 실패 시 오류 메시지를 표시하고 입력값은 유지한다.
- 저장 중에는 버튼을 비활성화해 중복 전송을 막는다.

#### FR-09 POI 수정 [결정 Q10, Q12]
- 정보창 `수정` 클릭 시 정보창이 **수정 모드**로 전환된다: 추억 묶음 선택, 제목 입력, 내용 입력, 사진 교체(선택), `저장`, `취소` 버튼. 사진은 필수 속성이므로 삭제할 수는 없고 교체만 가능하다.
- `저장` 시 `PUT /api/pois/{poiId}` (multipart)를 호출한다.
- **좌표는 변경하지 않는다** [결정 Q10]. 사진을 교체해도 새 사진의 GPS는 사용하지 않고 기존 좌표를 유지한다.
- 서버는 한 트랜잭션에서 다음 순서로 처리한다 (FR-14).
  1. 대상 행을 소유자 조건으로 `SELECT ... FOR UPDATE`
  2. 현재 상태를 `tb_poi_history`에 저장
  3. 메인 테이블 `tb_poi`를 새 내용으로 갱신
- 동시에 수정이 겹치면 **마지막 저장이 메인 테이블에 남고**, 이전 내용은 모두 이력 테이블에 보관된다 [결정 Q12].
- 성공 시 store와 마커 썸네일을 갱신하고 보기 모드로 돌아간다. 사진이 바뀌면 `removeAtKey` 후 재생성한다 (`setImage` 재호출 동작이 확인되지 않아 재생성 방식 채택).
- 사진을 교체해도 **이전 파일은 삭제하지 않는다**. 이력 테이블이 경로를 참조하기 때문이다 (FR-14, N6).

#### FR-10 POI 삭제 (소프트 삭제) [결정 Q5]
- 정보창 `삭제` 클릭 시 확인창을 띄우고 승인하면 `DELETE /api/pois/{poiId}`를 호출한다.
- 서버는 행을 지우지 않고 `deleted_at = now()`로 표시한다. 삭제 직전 상태를 이력 테이블에 `change_type = 'DELETE'`로 저장한다 [가정, N6].
- 삭제된 POI는 모든 조회(영역 조회, 상세, 이미지)에서 제외된다.
- 이미지 파일은 삭제하지 않는다 [가정, N6].
- 성공 시 정보창을 닫고 마커를 `removeAtKey`로 제거하며 store에서 삭제한다.

#### FR-11 이미지 업로드·정규화·썸네일·제공 [결정 Q6]
- 허용 형식 JPEG, PNG, 최대 5 MB. POI당 1장.
- 서버는 파일의 실제 내용을 이미지 디코더로 읽어 이미지 여부를 검증한다 (확장자·Content-Type만 신뢰하지 않음). JDK 기본 
- 처리 순서: 검증 → **GPS 추출(FR-08)** → EXIF 회전 방향 적용 → 긴 변 1280 px 이하로 축소 → JPEG(품질 0.85)로 재인코딩하여 저장 [가정, N7].
  - 재인코딩으로 EXIF(촬영 위치 포함)가 저장 파일에서 제거되어, 이미지를 내려받아도 위치가 노출되지 않는다.
  - 알파 채널이 있는 이미지는 흰 배경으로 합성한다.
- 썸네일은 64×64 px(중앙 크롭) PNG로 생성한다 [가정].
- 저장 파일명은 UUID로 새로 만들고 사용자 파일명은 사용하지 않는다. 경로는 `yyyy/MM/{uuid}.jpg`, `yyyy/MM/{uuid}_thumb.png` 형태의 **상대 경로**로 DB에 저장한다.
- 저장소는 정적 리소스 경로 밖에 둔다. 이미지는 `GET /api/pois/{poiId}/image`, `GET /api/pois/{poiId}/thumb`로만 제공하며, 로그인한 소유자이고 삭제되지 않은 POI일 때만 응답한다. `Cache-Control: private`을 설정한다.
- 썸네일 URL에는 `?v={updatedAt epoch}`를 붙여 사진 교체 후 캐시를 무효화한다.

#### FR-12 추억 묶음 선택·색상 [결정 Q11]
- 좌측 패널에 `추억 묶음 선택` 영역을 두고, 로그인 사용자의 묶음 목록(이름, 색상 견본)을 보여준다 (`GET /api/groups`).
- POI 생성·수정 시 묶음 하나를 반드시 선택한다.
- **묶음별 색상**이 정보창 타이틀바 색이 된다 (FR-03). 타이틀 텍스트는 사용자가 입력한 제목이다.
- 묶음 생성(이름, 색상)을 패널에서 할 수 있다 (`POST /api/groups`). 색상은 `#RRGGBB` 형식이며, 지정하지 않으면 서버가 기본 팔레트에서 순서대로 배정한다 [가정, N4].
- 묶음의 수정·삭제, 기본 묶음, 묶음 기준 지도 필터는 이번 범위에서 제외한다 [가정, N4].

#### FR-13 로그인·권한 [결정 Q4]
- 로그인 화면(`login.html`)과 로그아웃을 제공한다. 로그인하지 않은 사용자는 지도 화면과 모든 `/api/**`에 접근할 수 없다 (`/api/**`는 401 JSON, 화면은 로그인으로 이동).
- 모든 POI·묶음 조회·수정·삭제는 **세션의 사용자 ID를 소유자로 조건에 포함**해 처리한다. 소유자 ID를 요청 값으로 받지 않는다.
- 타인 소유 자원에 접근하면 존재 여부를 노출하지 않도록 `POI_NOT_FOUND`(404)로 응답한다 [가정].
- 세션 쿠키는 `HttpOnly`, `SameSite=Lax`로 설정한다. 비밀번호는 BCrypt로 저장한다 [가정].
- 상태를 변경하는 요청(POST/PUT/DELETE)은 CSRF 토큰 헤더(`X-CSRF-TOKEN`)를 요구한다. `api/http.js`가 토큰을 자동 부착한다 [가정].
- 로그인 방식(세션/토큰), 계정 생성 방법, 역할(USER/ADMIN)과 관리자 권한은 N2에서 확정한다.

#### FR-14 수정·삭제 이력 보관 [결정 Q12]
- 수정 시 이전 내용을 `tb_poi_history`에 저장한 **다음** 메인 테이블을 갱신한다. 두 작업은 같은 트랜잭션이며 하나라도 실패하면 모두 롤백한다.
- 이력에는 제목, 내용, 묶음, 이미지·썸네일 경로, 좌표, 해당 시점의 `updated_at`, 변경 유형(`UPDATE`/`DELETE`), 변경자, 보관 시각을 저장한다.
- 이력을 참조하는 이미지 파일은 삭제하지 않는다.
- 이력 조회·복원 화면과 보존 기간은 이번 범위에서 제외한다 [가정, N6].

### 4.3 정보창·마커 상태 전이
| 현재 상태 | 이벤트 | 다음 상태 | 동작 |
| --- | --- | --- | --- |
| MARKER | 마커 클릭 | POPUP_VIEW | 마커 숨김, 정보창 표출 (묶음 색 타이틀바) |
| POPUP_VIEW | 수정 클릭 | POPUP_EDIT | 입력 폼 표시 |
| POPUP_EDIT | 저장 성공 | POPUP_VIEW | 데이터·마커 갱신 |
| POPUP_EDIT | 취소 | POPUP_VIEW | 원래 값 복원 |
| POPUP_VIEW/EDIT | 닫기(X) | MARKER | 정보창 숨김, 마커 복원 |
| POPUP_VIEW | 삭제 승인 | (없음) | 서버 소프트 삭제, 마커 제거 |
| POPUP_* | 다른 마커 클릭 | POPUP_VIEW(새 POI) | 현재 정보창 닫고 새로 표출 |

---

## 5. 외부 인터페이스

### 5.1 사용자 인터페이스
| 화면 요소 | 설명 |
| --- | --- |
| 로그인 화면 | 아이디·비밀번호 입력, 실패 메시지 |
| 지도 영역 | `#map` 전체 화면. XDWorld가 canvas와 HTMLObject 컨테이너를 내부에 추가 |
| 좌측 패널 | 고정 패널(z-index가 canvas보다 높음). 위에서부터 `추억 묶음 선택` 영역, 사진 선택(미리보기, 추출된 좌표·고도 표시), 제목, 내용, `사진과 기록 저장` 버튼. 로그아웃 버튼 |
| 정보창(보기) | 둥근 말풍선, **묶음 색 타이틀바**, 이미지, 흰색 텍스트, 반투명 어두운 내용 배경, 아래쪽 꼬리 |
| 정보창(수정) | 보기와 같은 외형, 묶음 선택·제목 input·내용 textarea·사진 교체 |
| 알림 | 저장/삭제 결과를 화면 상단 토스트로 표시 |

### 5.2 REST API

공통 응답 형식:
```json
{ "success": true, "data": { }, "error": null }
{ "success": false, "data": null, "error": { "code": "POI_NOT_FOUND", "message": "..." } }
```
`/api/**`는 모두 로그인이 필요하며, 상태 변경 요청은 CSRF 토큰 헤더가 필요하다. 아래 모든 POI·묶음 API는 세션 사용자의 소유 자원만 대상으로 한다.

| 기능 | 메서드 / URL | 요청 | 응답(data) |
| --- | --- | --- | --- |
| 로그인 (FR-13) | `POST /login` | form: `username, password` | 200 `{success:true}` / 실패 401 [가정] |
| 로그아웃 | `POST /logout` | 없음 | 200 |
| 내 정보 | `GET /api/me` | 없음 | `{userId, username, role}` |
| 묶음 목록 (FR-12) | `GET /api/groups` | 없음 | `[{groupId, name, color}]` |
| 묶음 생성 (FR-12) | `POST /api/groups` | JSON: `name, color(선택)` | `{groupId, name, color}` (201) |
| 영역 조회 (FR-06, 07) | `GET /api/pois/bbox` | query: `minLon, minLat, maxLon, maxLat, limit(기본 1000)` | `[{poiId, groupId, lon, lat, alt, thumbUrl}]` |
| 상세 조회 (FR-02) | `GET /api/pois/{poiId}` | path | `{poiId, groupId, groupName, groupColor, title, content, lon, lat, alt, imageUrl, thumbUrl, createdAt, updatedAt}` |
| 생성 (FR-08) | `POST /api/pois` | multipart: `groupId, title, content, image(필수)` | 상세 응답 (201). 좌표·고도는 서버가 사진에서 추출 |
| 수정 (FR-09) | `PUT /api/pois/{poiId}` | multipart: `groupId, title, content, image(선택)` | 상세 응답 |
| 삭제 (FR-10) | `DELETE /api/pois/{poiId}` | path | `null` (200) |
| 이미지 (FR-11) | `GET /api/pois/{poiId}/image` | path | `image/jpeg` |
| 썸네일 (FR-11) | `GET /api/pois/{poiId}/thumb` | path, `v` | `image/png` |

v0.1의 `GET /api/pois`(전체 조회)와 `GET /uploads/**`(정적 이미지)는 폐기했다.

오류 코드:
| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 로그인 필요 또는 로그인 실패 |
| `FORBIDDEN` | 403 | CSRF 토큰 불일치 등 권한 없음 |
| `INVALID_PARAM` | 400 | 필수값 누락, 길이/범위 초과, BBox 역전, 색상 형식 오류 |
| `INVALID_IMAGE` | 400 | 이미지로 디코딩 불가, 허용 형식 아님 |
| `GPS_NOT_FOUND` | 422 | 사진에 GPS 위치 정보가 없음 (FR-08) |
| `FILE_TOO_LARGE` | 413 | 용량 초과 |
| `GROUP_NOT_FOUND` | 404 | 묶음이 없거나 본인 소유가 아님 |
| `POI_NOT_FOUND` | 404 | 대상 POI가 없거나 삭제됨, 또는 본인 소유가 아님 |
| `INTERNAL_ERROR` | 500 | 처리 중 서버 오류 (상세 메시지는 로그에만 기록) |

> `PUT` 요청에서 multipart 파싱이 동작하는지 구현 초기에 확인한다. 문제가 있으면 `POST /api/pois/{poiId}/update`로 대체한다.

### 5.3 XDWorld 엔진 인터페이스
| 용도 | API | 상태 |
| --- | --- | --- |
| 엔진 시작 | `Module = {locateFile, postRun:[init]}`, `Module.initialize({container, terrain:{dem,image}, worker, defaultKey})` | 매뉴얼 확인 (키·URL은 N1) |
| 레이어 생성 | `new Module.JSLayerList(true).createLayer("POI_LAYER", Module.ELT_3DPOINT)` | 매뉴얼 확인 |
| 선택 가능 설정 | `layer.setSelectable(true)`, `object.setSelectable(true)`, `object.setPickable(true)` | 매뉴얼 확인 / **[검증]** 클릭 인식 |
| POI 생성 | `Module.createPoint(key)`, `poi.setPosition(new Module.JSVector3D(lon, lat, alt))`, `poi.setImage(rgbaData, w, h)` | 매뉴얼 확인 |
| 레이어 추가/삭제 | `layer.addObject(poi, 0)`, `layer.removeAtKey(key)`, `layer.keyAtObject(key)`, `layer.removeAll()` | 매뉴얼 확인 |
| 마커 표시/숨김 | `object.setVisible(bool)` | 매뉴얼 확인 |
| 클릭 이벤트 | `canvas.addEventListener("Fire_EventSelectedObject", e => e.layerName, e.objKey)` | 매뉴얼 확인 / **[검증]** JSPoint 발생 조건 |
| 카메라 이동 종료 | `Fire_EventCameraMoveEnd(longitude, latitude, altitude)` | 매뉴얼 확인 |
| 카메라 이동 | `Module.getViewCamera().moveLonLatAlt(x, y, z, type)`, `moveLonLatAltOval(...)` | 매뉴얼 확인 |
| 좌표 변환 | `Module.getMap().ScreenToMapPointEX`, `MapToScreenPointEX` | 매뉴얼 확인 / **[검증]** 인자 형식 |
| 지형 고도 | `Module.getMap().getTerrHeight(lon, lat)` | 매뉴얼 확인 (실패 시 0) |
| 정보창 | `Module.createHTMLObject(id).createbyJson({position, container, canvas:Module.canvas, element, verticalAlign, horizontalAlign, moveChange})`, `refresh()` | 매뉴얼 확인 / **[검증]** 위치 갱신·제거 |
| POI 크기/페이드 | `layer.setLayerStyle({poi:{scaleable, fadeable}})` | 매뉴얼 확인 (필요 시 사용) |

v0.1의 위치 선택용 이벤트(`Fire_EventClickPosition`, `Fire_MouseUp`)는 위치를 사진 GPS로 정하므로 사용하지 않는다.

**PoC 검증 체크리스트** (구현 1단계에서 먼저 확인)
1. `JSHTMLObject`가 카메라 이동·회전·확대/축소 중 좌표를 따라가는가 (FR-05)
2. `JSHTMLObject`의 `position` 변경 후 `refresh()`로 위치 이동이 되는가, 인스턴스 제거 방법은 무엇인가
3. 사용자 레이어의 `JSPoint` 클릭 시 `Fire_EventSelectedObject`가 발생하는가 (`setSelectable` 필요 여부)
4. `ScreenToMapPointEX` 인자 형식, 하늘 방향 `null` 처리, 엔진 준비 직후(지형 로드 전) 반환값 (FR-06)
5. 알파 채널이 있는 PNG 썸네일이 `setImage`에서 투명하게 표현되는가
6. 사진 EXIF 고도와 `getTerrHeight` 값의 차이, 마커가 지형에 묻히는지 여부 (FR-01, N8)
7. `object.setVisible(false)`를 JSPoint에 적용했을 때 선택/피킹도 비활성화되는가
8. 로컬 호스팅한 엔진의 `.wasm`이 Spring Boot에서 정상 제공되는가, 로그인 전 허용할 정적 경로 범위

---

## 6. 데이터 요구사항

### 6.1 테이블
```sql
CREATE EXTENSION IF NOT EXISTS postgis;

-- 사용자 (기존 계정 체계가 있으면 대체, N2)
CREATE TABLE tb_user (
    user_id        BIGSERIAL PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 추억 묶음
CREATE TABLE tb_memory_group (
    group_id    BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT      NOT NULL REFERENCES tb_user(user_id),
    name        VARCHAR(50) NOT NULL,
    color       CHAR(7)     NOT NULL,               -- '#RRGGBB'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, name)
);

-- POI (메인)
CREATE TABLE tb_poi (
    poi_id      BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES tb_user(user_id),
    group_id    BIGINT       NOT NULL REFERENCES tb_memory_group(group_id),
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(2000),
    image_path  VARCHAR(300) NOT NULL,              -- 원본(정규화) 상대 경로
    thumb_path  VARCHAR(300) NOT NULL,              -- 썸네일 상대 경로
    geom        geometry(PointZ, 4326) NOT NULL,    -- (경도, 위도, 고도) 사진 GPS
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ                          -- NULL이면 활성, 값이 있으면 소프트 삭제
);

-- 삭제되지 않은 행만 대상으로 하는 부분 인덱스
CREATE INDEX idx_tb_poi_geom_active  ON tb_poi USING GIST (geom)    WHERE deleted_at IS NULL;
CREATE INDEX idx_tb_poi_owner_active ON tb_poi (owner_id)           WHERE deleted_at IS NULL;

-- POI 이력 (수정·삭제 직전 상태 백업)
CREATE TABLE tb_poi_history (
    hist_id         BIGSERIAL PRIMARY KEY,
    poi_id          BIGINT       NOT NULL,
    owner_id        BIGINT       NOT NULL,
    group_id        BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    content         VARCHAR(2000),
    image_path      VARCHAR(300) NOT NULL,
    thumb_path      VARCHAR(300) NOT NULL,
    geom            geometry(PointZ, 4326) NOT NULL,
    poi_updated_at  TIMESTAMPTZ  NOT NULL,           -- 백업된 시점 버전의 updated_at
    change_type     VARCHAR(10)  NOT NULL,           -- 'UPDATE' | 'DELETE'
    changed_by      BIGINT       NOT NULL,
    archived_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tb_poi_history_poi ON tb_poi_history (poi_id, archived_at DESC);
```
- 좌표는 `geom` 한 곳에만 저장하고 응답 시 `ST_X/ST_Y/ST_Z`로 추출한다 (중복 컬럼 불일치 방지).
- 부분 인덱스는 쿼리에 `deleted_at IS NULL` 조건이 있을 때만 사용되므로 모든 조회 쿼리에 이 조건을 넣는다.
- GiST 기본 연산자는 2D 기준이며 BBox 조회(`&&`)에 사용된다.

### 6.2 MyBatis 매퍼 (`PoiMapper.xml` 핵심)
`#{ownerId}`는 항상 서비스가 세션 사용자에서 채우며 요청 파라미터에서 받지 않는다.
```xml
<select id="selectByBbox" resultType="com.example.poimap.poi.dto.PoiSummaryResponse">
  SELECT p.poi_id, p.group_id,
         ST_X(p.geom) AS lon, ST_Y(p.geom) AS lat, ST_Z(p.geom) AS alt,
         p.updated_at
    FROM tb_poi p
   WHERE p.owner_id = #{ownerId}
     AND p.deleted_at IS NULL
     AND p.geom &amp;&amp; ST_MakeEnvelope(#{minLon}, #{minLat}, #{maxLon}, #{maxLat}, 4326)
   ORDER BY p.poi_id
   LIMIT #{limit}
</select>

<select id="selectDetail" resultType="com.example.poimap.poi.dto.PoiDetailResponse">
  SELECT p.poi_id, p.group_id, g.name AS group_name, g.color AS group_color,
         p.title, p.content,
         ST_X(p.geom) AS lon, ST_Y(p.geom) AS lat, ST_Z(p.geom) AS alt,
         p.created_at, p.updated_at
    FROM tb_poi p
    JOIN tb_memory_group g ON g.group_id = p.group_id
   WHERE p.poi_id = #{poiId}
     AND p.owner_id = #{ownerId}
     AND p.deleted_at IS NULL
</select>

<!-- 수정·삭제 시 동시성 직렬화 -->
<select id="selectForUpdate" resultType="com.example.poimap.poi.domain.Poi">
  SELECT poi_id, owner_id, group_id, title, content, image_path, thumb_path, updated_at
    FROM tb_poi
   WHERE poi_id = #{poiId} AND owner_id = #{ownerId} AND deleted_at IS NULL
   FOR UPDATE
</select>

<insert id="insert" useGeneratedKeys="true" keyProperty="poiId" keyColumn="poi_id">
  INSERT INTO tb_poi (owner_id, group_id, title, content, image_path, thumb_path, geom)
  VALUES (#{ownerId}, #{groupId}, #{title}, #{content}, #{imagePath}, #{thumbPath},
          ST_SetSRID(ST_MakePoint(#{lon}, #{lat}, #{alt}), 4326))
</insert>

<!-- 좌표는 갱신하지 않는다 -->
<update id="update">
  UPDATE tb_poi
     SET group_id = #{groupId}, title = #{title}, content = #{content},
         image_path = #{imagePath}, thumb_path = #{thumbPath},
         updated_at = now()
   WHERE poi_id = #{poiId} AND owner_id = #{ownerId} AND deleted_at IS NULL
</update>

<update id="softDelete">
  UPDATE tb_poi
     SET deleted_at = now(), updated_at = now()
   WHERE poi_id = #{poiId} AND owner_id = #{ownerId} AND deleted_at IS NULL
</update>
```
`PoiHistoryMapper.xml`
```xml
<insert id="insertFromPoi">
  INSERT INTO tb_poi_history
         (poi_id, owner_id, group_id, title, content, image_path, thumb_path, geom,
          poi_updated_at, change_type, changed_by)
  SELECT poi_id, owner_id, group_id, title, content, image_path, thumb_path, geom,
         updated_at, #{changeType}, #{changedBy}
    FROM tb_poi
   WHERE poi_id = #{poiId} AND owner_id = #{ownerId}
</insert>
```
`PoiService.update()` / `delete()`는 `@Transactional`로 `selectForUpdate` → `insertFromPoi` → `update`(또는 `softDelete`) 순서로 호출한다. `selectForUpdate`가 없는 행이면 `POI_NOT_FOUND`를 던진다.

- `mybatis.configuration.map-underscore-to-camel-case: true`
- 응답 DTO에서 `thumbUrl`은 `/api/pois/{poiId}/thumb?v={updatedAt epoch}`로 조립한다 (서비스 계층).
- 날짜변경선을 넘는 BBox(`minLon > maxLon`)는 국내 서비스 범위상 지원하지 않고 `INVALID_PARAM`으로 처리한다 [가정].
- `MemoryGroupMapper`: `selectByOwner`, `selectOwned(groupId, ownerId)`(POI 생성·수정 시 소유 검증), `insert`.

### 6.3 파일 저장소
| 항목 | 값 |
| --- | --- |
| 업로드 루트 | `app.upload.dir` (Windows 예: `C:/poi-data/uploads`). **정적 리소스 경로 밖** [가정] |
| 하위 구조 | `yyyy/MM/{uuid}.jpg`, `yyyy/MM/{uuid}_thumb.png` |
| 제공 방식 | 컨트롤러가 소유자·삭제 여부를 확인한 뒤 스트리밍 (`/uploads/**` 정적 매핑 없음) |
| 정합성 | DB 저장 성공 후 파일 확정. DB 실패 시 이번 요청에서 새로 저장한 파일 삭제(보상 처리) |
| 보관 | 교체·삭제된 이전 파일은 이력 테이블이 참조하므로 삭제하지 않음 (정리 정책은 N6) |

### 6.4 설정 예시
`application.yml`
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/poimap
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
server:
  servlet:
    session:
      cookie:
        http-only: true
        same-site: lax
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
app:
  upload:
    dir: C:/poi-data/uploads
```

`build.gradle` 의존성 (참고용. **버전은 `build.gradle`에 기재한 값이 기준** [결정 Q9])
```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3'
    implementation 'com.drewnoakes:metadata-extractor:2.19.0'                 // EXIF GPS 추출
    implementation 'net.coobird:thumbnailator:0.4.20'                         // 축소·썸네일·회전 보정
    runtimeOnly   'org.postgresql:postgresql'
    compileOnly   'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```
DB 계정 정보는 환경변수로 주입하고 저장소에 커밋하지 않는다.

---

## 7. 주요 처리 흐름

### 7.1 로그인 및 지도 로드
1. 미인증 사용자가 `index.html` 요청 → 로그인 화면으로 이동 → 로그인 성공 시 세션 발급.
2. `index.html`이 `module.js`를 로드하고 로컬 엔진 파일을 요청한다.
3. `Module.postRun`의 `init()`에서 `Module.initialize(...)` 실행 후 `main.js`를 로드한다.
4. `POI_LAYER` 생성, 이벤트 리스너(선택, 카메라 이동 종료) 등록, `GET /api/groups`로 묶음 목록 로드.
5. 초기 카메라 설정 후 BBox 계산 → `GET /api/pois/bbox` → 마커 생성.

### 7.2 마커 클릭 → 정보창
1. `Fire_EventSelectedObject` 수신 → poiId 추출.
2. store에 상세가 없으면 `GET /api/pois/{id}`.
3. 마커 `setVisible(false)`, 정보창 내용 채움, 타이틀바에 묶음 색 적용, `JSHTMLObject` 위치를 POI 좌표로 설정, 표출.

### 7.3 POI 생성
1. 묶음 선택, 사진 선택 → 브라우저가 EXIF GPS 판독 → 임시 마커 표시, 카메라 이동, 좌표 표시. GPS 없으면 저장 비활성화.
2. 제목·내용 입력 → `사진과 기록 저장` 클릭.
3. `POST /api/pois`: 서버가 이미지 검증 → **원본에서 GPS 추출** → 회전 보정·축소·JPEG 재인코딩·썸네일 → 파일 저장 → DB INSERT → 상세 응답.
4. 임시 마커 제거, 정식 마커 생성, store 추가, 패널 초기화.

### 7.4 카메라 이동
1. `Fire_EventCameraMoveEnd` → 300 ms 디바운스 → BBox 계산.
2. `GET /api/pois/bbox` → 신규 마커 생성, 영역 밖 마커 제거 (열린 정보창의 POI 제외).

### 7.5 POI 수정
1. `수정` → 입력 → `저장` → `PUT /api/pois/{id}`.
2. 서버 트랜잭션: 행 잠금 → 이력 저장(`UPDATE`) → 메인 갱신. 사진 교체 시 새 파일 저장 (이전 파일 유지).
3. 응답으로 store·마커·정보창 갱신.

### 7.6 POI 삭제
1. `삭제` → 확인 → `DELETE /api/pois/{id}`.
2. 서버 트랜잭션: 행 잠금 → 이력 저장(`DELETE`) → `deleted_at` 설정.
3. 마커 제거, 정보창 닫기.

---

## 8. 비기능 요구사항

| 구분 | 요구사항 |
| --- | --- |
| 성능 | 사용자별 POI는 1,000건 미만 [결정 Q7]. 영역 조회 p95 300 ms 이내 (전체 사용자 합계 10만 건 기준 [가정, 총 사용자 수는 미정]) |
| 성능 | 카메라 이동 중 요청 폭주 방지: 디바운스 300 ms, 직전 요청 취소 |
| 성능 | 응답 최대 1,000건, 렌더링 객체는 화면 영역 중심으로 제한 |
| 성능 | 이미지 업로드 처리(검증·재인코딩·썸네일) 5 MB 기준 3초 이내 [가정] |
| 보안 | 인증 필수, 모든 쿼리에 소유자 조건 포함. 타인 자원은 404로 응답 (IDOR 방지) |
| 보안 | CSRF 토큰 검증, 세션 쿠키 `HttpOnly`·`SameSite=Lax`, 비밀번호 BCrypt |
| 보안 | 입력 텍스트는 `textContent`로만 출력 (XSS). 묶음 색은 `#RRGGBB` 검증 후 적용 |
| 보안 | 이미지는 내용 디코딩으로 검증, 파일명은 UUID, 경로 조작(`../`) 차단, 정적 경로 밖 저장, 인증 후에만 제공 |
| 보안 | 재인코딩으로 저장 이미지의 EXIF(위치 포함) 제거 |
| 보안 | SQL은 MyBatis `#{}` 바인딩만 사용 (`${}` 금지) |
| 신뢰성 | 수정·삭제는 이력 저장과 메인 갱신을 한 트랜잭션으로 처리. 파일 저장과 DB 반영의 불일치 시 보상 처리 |
| 신뢰성 | 오류는 `GlobalExceptionHandler`가 공통 형식으로 응답 |
| 유지보수 | 계층 분리(Controller/Service/Mapper), 프론트는 모듈 단위 분리, 상수는 `config.js`로 집중 |
| 호환성 | 최신 Chrome/Edge, WebGL2 지원 환경 |
| 로깅 | 요청 ID, 예외 스택은 서버 로그에만 기록하고 클라이언트에는 코드·메시지만 전달 |

---

## 9. 인수 기준 (테스트 시나리오)

| ID | 대상 | 시나리오 | 기대 결과 |
| --- | --- | --- | --- |
| AC-01 | FR-01 | 로그인 후 지도 로드 | 본인의 POI가 작은 이미지 아이콘으로만 표시된다 (텍스트·팝업 없음) |
| AC-02 | FR-02/03 | 마커 클릭 | 마커가 숨겨지고 제목 타이틀바·이미지·텍스트·수정/삭제 버튼이 있는 정보창이 표시된다 |
| AC-03 | FR-03/12 | 서로 다른 묶음의 POI를 각각 클릭 | 타이틀바 색이 각 POI가 속한 묶음의 색으로 표시된다 |
| AC-04 | FR-04 | X 클릭 | 정보창이 사라지고 마커가 다시 표시된다 |
| AC-05 | FR-05 | 정보창 열고 이동/줌/회전 | 정보창이 POI 좌표를 계속 따라간다 |
| AC-06 | FR-06 | 지도 로드 시 네트워크 확인 | 전체 목록 API 호출은 없고, 초기 `GET /api/pois/bbox`가 1회 호출된다 |
| AC-07 | FR-07 | 카메라 이동 후 멈춤 | 멈춘 뒤 약 300 ms 후 `GET /api/pois/bbox`가 호출되고, 연속 이동 중에는 호출되지 않는다 |
| AC-08 | FR-07 | 이동 후 영역 밖 POI | 영역 밖 마커는 제거되고, 열린 정보창의 POI는 유지된다 |
| AC-09 | FR-08 | GPS가 있는 JPEG 선택 | 임시 마커가 사진 위치에 표시되고 좌표·고도가 패널에 나타난다 |
| AC-10 | FR-08 | 묶음·제목·내용 입력 후 `사진과 기록 저장` | POI가 DB에 저장되고(좌표는 서버가 추출한 값) 해당 위치에 마커가 생성된다 |
| AC-11 | FR-08 | GPS가 없는 사진 업로드 | 저장 버튼이 비활성화되고, API 직접 호출 시 `GPS_NOT_FOUND`(422)로 거부된다 |
| AC-12 | FR-08 | 클라이언트가 임의 좌표를 보내는 요청 | 요청의 좌표는 무시되고 사진 GPS가 저장된다 |
| AC-13 | FR-08 | 제목 없이 저장 | 저장되지 않고 오류 메시지가 표시되며 입력값이 유지된다 |
| AC-14 | FR-09 | 수정 → 제목/내용/묶음 변경 → 저장 | 정보창이 갱신되고 새로고침 후에도 유지되며, 이전 내용이 `tb_poi_history`에 `UPDATE`로 1행 생긴다 |
| AC-15 | FR-09 | 사진 교체 후 저장 | 좌표는 변하지 않고, 이전 이미지 파일은 디스크에 남아 있다 |
| AC-16 | FR-09 | 수정 중 취소 | 원래 값이 유지되고 이력이 생기지 않는다 |
| AC-17 | FR-09/14 | 같은 POI를 두 세션에서 연속 저장 | 마지막 저장 내용이 `tb_poi`에 남고, 이전 내용 두 건이 이력에 순서대로 보관된다 |
| AC-18 | FR-10 | 삭제 → 확인 | `tb_poi.deleted_at`이 설정되고 마커가 사라지며, 이력에 `DELETE`가 기록되고 이미지 파일은 남는다. 새로고침 후에도 나타나지 않는다 |
| AC-19 | FR-11 | 이미지가 아닌 파일 업로드 | `INVALID_IMAGE`(400)로 거부된다 |
| AC-20 | FR-11 | 5 MB 초과 파일 업로드 | `FILE_TOO_LARGE`(413)로 거부된다 |
| AC-21 | FR-11 | 저장된 이미지를 내려받아 EXIF 확인 | GPS 등 메타데이터가 없다 |
| AC-22 | FR-13 | 로그인 없이 `/api/pois/bbox` 호출 | 401이 반환되고 화면은 로그인으로 이동한다 |
| AC-23 | FR-13 | 사용자 A가 사용자 B의 poiId로 조회·수정·삭제·이미지 요청 | 모두 `POI_NOT_FOUND`(404)이며 데이터가 변하지 않는다 |
| AC-24 | FR-13 | 사용자 A의 영역 조회 | B의 POI는 포함되지 않는다 |
| AC-25 | FR-13 | CSRF 토큰 없이 POST/PUT/DELETE | `FORBIDDEN`(403)으로 거부된다 |
| AC-26 | 보안 | 제목에 `<script>` 입력 | 문자 그대로 표시되고 실행되지 않는다 |
| AC-27 | 성능 | 사용자당 1,000건 데이터에서 영역 조회 | 8장 기준을 만족한다 |

---

## 10. 구현 순서 제안

1. **PoC** (5.3 체크리스트): 로컬 엔진 구동, JSPoint 클릭, JSHTMLObject 추종, 좌표 변환, EXIF 고도 비교
2. **DB·인증 골격**: PostGIS 확장, 테이블 4종, Spring Security 로그인·CSRF, 사용자/묶음 API
3. **POI 백엔드**: 영역 조회·상세, 생성(GPS 추출·이미지 정규화·썸네일), 이미지 제공 컨트롤러
4. **프론트 조회 흐름**: 로그인 → 초기 영역 조회 → 마커 → 클릭 정보창(묶음 색) → 닫기 (FR-01~06, 12)
5. **카메라 연동 조회**: BBox 계산, 디바운스, 마커 증감 (FR-07)
6. **좌측 패널 등록 흐름**: 묶음 선택, 사진 GPS 미리보기, 저장 (FR-08)
7. **수정·삭제·이력**: 트랜잭션, 이력 테이블, 소프트 삭제 (FR-09, 10, 14)
8. **예외·성능·보안 점검 및 인수 시험** (9장)

---

## 11. 가정 및 결정 사항

### 11.1 결정 사항 반영 현황

아래는 사용자가 작성한 `결정 사항`과 v0.2에서의 반영 내용이다. (답변 = 결정 사항)

| # | 질문 | 현재 가정 | 결정 사항 | 영향 범위 | 반영 내용 (v0.2) |
| --- | --- | --- | --- | --- | --- |
| Q1 | XDWorld 엔진 파일을 CDN으로 쓸지 로컬에 둘지, API 키(`defaultKey`)와 지형 DEM/영상 서비스 URL은 무엇인가? | 미정 (module.js에 플레이스홀더) | 로컬 module.js 사용 | 2.3, 3.3, 7.1 | 엔진을 `static/xdworld/`에 로컬 호스팅, 초기화 스크립트를 `static/js/module.js`로 명명. **API 키·지형 URL은 미정 → N1** |
| Q2 | `사진과 기록 저장` 버튼 UI는 이미 제시된 화면이 있는가? 좌측 패널인가, 지도 클릭 후 뜨는 팝업 폼인가? | 좌측 패널 + 지도 클릭으로 위치 선택 | 좌측 패널에서 사진 GPS 정보를 추출하여 위치 가시화 | FR-08, 5.1 | 지도 클릭 위치 선택 폐기. 사진 선택 시 EXIF GPS로 임시 마커 표시(FR-08), 서버가 원본에서 재추출. **GPS 없는 사진 처리 → N3** |
| Q3 | 최초 전체 조회와 이후 영역 조회의 역할 구분이 4.2 FR-07 설명(초기 화면 확보 / 이후 동기화 + 렌더링 제한)과 같은가? 전체 건수가 많다면 초기 조회 응답을 요약으로 제한하는 방식이 맞는가? | 요약만 전체 조회, 상세는 클릭 시 조회 | POI 데이터 규모와 조회 방식은 PostGIS geometry와 공간 인덱스로 보이는 영역만 조회하는 방법만 사용 | FR-06, FR-07, 8장 | 전체 조회 API·매퍼 폐기. FR-06은 초기 표출 영역을 영역 조회로 1회 호출하는 것으로 변경. **초기 카메라 위치 → N5** |
| Q4 | 로그인/권한이 필요한가? (작성자별 수정·삭제 제한 등) | 인증 없음, 누구나 수정/삭제 | 로그인·권한 기능이 존재하고, CRUD는 권한이 있는 사용자 단위로 작동 | 전체 보안, DB 컬럼 | FR-13 추가. `tb_user`, `owner_id`, 소유자 조건 쿼리, 타인 자원 404, CSRF, 이미지 인증 제공. **세부 방식 → N2** |
| Q5 | 삭제는 영구 삭제인가, 삭제 표시(soft delete)인가? | 영구 삭제 + 파일 삭제 | 삭제 표시 | FR-10, 6장 | `deleted_at` 컬럼, 부분 인덱스, 모든 조회에 `deleted_at IS NULL`. 이미지 파일은 삭제하지 않음 |
| Q6 | 사진은 필수인가? 1 POI당 1장인가? 허용 형식/최대 용량은? | 선택, 1장, JPEG/PNG, 5 MB | `사진과 기록 저장` 버튼 사용할 때마다 1장씩, JPEG/PNG, 5 MB | FR-08, FR-11 | 사진 필수(위치를 사진에서 얻으므로), 사진 삭제 불가·교체만 가능. |
| Q7 | 예상 POI 건수와 동시 사용자 수는? | 1,000건 ~ 10만 건, 소수 사용자 | 사용자별 POI 수는 1000건 미만 예상 | 8장 성능 목표, 응답 한도 | 응답 한도 1,000건, 성능 목표를 사용자별 기준으로 조정. 전체 사용자 수는 미정 |
| Q8 | POI 고도는 어떻게 정할까? (지형 고도 / 사용자 입력 / 0) | 클릭 지점 또는 지형 고도, 실패 시 0 | 사진의 메타데이터(GPS)를 이용해 설정 | FR-08 | EXIF `GPSAltitude`/`GPSAltitudeRef` 사용, 없으면 0. 표시 시 지형 고도보다 낮으면 보정 (**N8**) |
| Q9 | Java, Spring Boot, PostgreSQL, PostGIS 버전과 기본 패키지명이 정해져 있는가? | Java 17, Boot 3.x, `com.example.poimap` | build.gradle에 작성 | 2.1, 3.2, 6.4 | 버전·group은 `build.gradle`을 기준으로 한다고 명시. 6.4 의존성은 참고용. PostgreSQL/PostGIS 버전은 미기재 → N9 |
| Q10 | 정보창은 동시에 여러 개 열 수 있어야 하는가? 수정 시 좌표 이동도 필요한가? | 1개만, 좌표 변경 없음 | 1개만, 좌표 변경 없음 | FR-03, FR-09 | [가정]에서 [결정]으로 승격. 사진 교체 시에도 좌표 유지로 구체화 |
| Q11 | 타이틀바 색(보라색)은 고정인가? 타이틀 텍스트는 사용자가 입력한 제목인가? (FRD 예: "영화") | 고정 색, 제목 필드 표시 | `추억 묶음 선택` 영역에 있는 추억 별로 색 설정, 제목 필드 표시 | FR-03 | FR-12 추가, `tb_memory_group.color`, 타이틀바 색 적용. **묶음 관리 범위·색 지정 방식 → N4** |
| Q12 | 동시 수정 충돌은 마지막 저장 우선으로 충분한가? | 마지막 저장 우선 | 이전 내용은 백업 테이블에 저장한 이후 마지막 저장을 메인 테이블에 저장 | FR-09 | FR-14 추가, `tb_poi_history`, 한 트랜잭션 + `FOR UPDATE`. **이력 정책 → N6** |

### 11.2 신규 미결 사항

결정 사항을 반영하면서 새로 생긴 질문이다. `결정 사항` 열에 답을 적어 주시면 해당 절을 수정한다.

| # | 질문 | 현재 가정 | 결정 사항 | 영향 범위 |
| --- | --- | --- | --- | --- |
| N1 | Q1 관련: `defaultKey`(API 키), 지형 DEM·영상 서비스 URL은 무엇인가? `module.js`는 엔진 초기화 스크립트를 뜻하는 것이 맞는가? 엔진 파일 버전은? | 엔진은 `static/xdworld/`, `module.js`는 초기화 스크립트, 키·URL은 플레이스홀더 | `현재 가정`과 동일 | 2.3, 3.3, 7.1 |
| N2 | 인증 범위: 기존 계정/SSO와 연동하는가, 이번에 새로 구현하는가? 회원가입 화면이 필요한가? 로그인 방식(세션/JWT)? 역할 구분(USER/ADMIN)과 관리자 권한은? POI는 소유자 본인만 볼 수 있는가(공유 없음)? | 신규 구현, 세션 로그인, 계정은 사전 등록(가입 화면 없음), 역할은 USER 하나, POI는 본인만 조회 | login.html에서 받아온 정보로 JWT 방식으로 기존 계정/SSO와 연동, 역할 구분(USER/ADMIN) 존재하고 관리자(ADMIN)는 admin-screen-spec.md 명세서대로 구현, POI는 소유자가 `공유하기` 버튼을 누르면 공유 가능 | FR-13, 6.1, 8장 |
| N3 | GPS 없는 사진 처리: 저장을 거부하는가, 지도 클릭으로 위치를 지정하게 하는가? (PNG와 일부 캡처·메신저 전달 사진은 GPS가 없는 경우가 많다) | 저장 거부 + 안내 문구 | 지도 클릭으로 위치를 지정하게 함 | FR-08, 2.3 |
| N4 | 추억 묶음 관리 범위: 묶음 생성·이름/색 수정·삭제 중 어디까지? 색은 사용자가 직접 고르는가, 팔레트 자동 배정인가? 기본 묶음이 필요한가? 묶음 선택이 지도 표시 필터 역할도 하는가? | 생성만 지원, 색 미지정 시 팔레트 자동 배정, 기본 묶음 없음, 필터 없음 | 묶음 생성·이름/색 수정·삭제 지원, 색은 사용자가 직접 고름, 기본 묶음은 없고 추억 묶음이 존재해야 `사진과 기록 저장` 기능 사용 가능, 선택한 추억 묶음만 지도에 표시하는 필터 적용 | FR-12, 6.1 |
| N5 | 로그인 직후 초기 카메라 위치는? (고정 좌표 / 마지막 본 위치 / 내 POI 전체 범위) | 고정 좌표(미정) | 내 POI 전체 범위 | FR-06, 7.1 |
| N6 | 이력·삭제 데이터 정책: 이력 조회·복원 화면이 필요한가? 삭제도 이력에 남기는가? 이력·삭제된 POI의 이미지 파일을 언제 정리하는가? | 삭제도 이력 기록, 조회·복원 화면 없음, 파일 정리 안 함 | `현재 가정`과 동일 | FR-09, 10, 14, 6.3 |
| N7 | 저장 이미지를 JPEG로 재인코딩(알파 제거, EXIF 제거, 긴 변 1280 px)해도 되는가? 원본 보관이 필요한가? | 재인코딩, 원본 미보관 | 저장 이미지를 WebP 형태로 재인코딩하여 압축을 진행하여 저장하고, 지도 위 POI는 WebP 형태로 보여주고, 정보창(HTML Overlay)은 원본 이미지 JPEG/PNG 형태로 표시| FR-11 |
| N8 | EXIF 고도가 없거나 지형 고도보다 낮을 때 표시 방식은? | 표시 고도 = max(저장 고도, 지형 고도), 고도 없으면 0 저장 | `현재 가정`과 동일 | FR-01, FR-08 |
| N9 | PostgreSQL / PostGIS 버전, 개발 DB 접속 정보 관리 방식(환경변수)은? | 최신 안정 버전, 환경변수 | `현재 가정`과 동일 | 2.1, 6.4 |

### 11.3 반영 과정에서 추가한 세부 가정
위 질문에 포함되지 않았지만 설계상 필요해 둔 값이며, 이견이 있으면 알려 주시기 바란다.
- 타인 소유 자원 접근은 403이 아닌 404로 응답한다.
- 이미지·썸네일은 정적 매핑이 아니라 인증된 컨트롤러로 제공한다 (CRUD가 사용자 단위이므로 조회도 소유자 단위).
- 사진을 교체할 때 이전 파일을 삭제하지 않는다 (이력이 참조).
- 클라이언트가 읽은 EXIF 좌표는 미리보기용이며, 저장 좌표는 서버가 원본에서 다시 추출한다.
- 프론트엔드 EXIF 판독은 로컬에 둔 `exifr` 라이브러리를 사용한다.
- 영역 조회 한도는 1,000건, 업로드 처리 목표는 3초이다.
