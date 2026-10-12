# 전국 문화축제 지도 설정

축제 지도는 `/festivals`에서 로그인 없이 사용할 수 있습니다. `ADMIN` 또는 `SUPER_ADMIN` 계정으로 로그인한 뒤 관리자 콘솔의 **축제 CSV 적재** 메뉴(`/admin/festivals`)에서 표준데이터 CSV를 재적재할 수 있습니다. CSV 업로드와 이력 API는 이 두 역할만 사용할 수 있습니다.

## 데이터베이스 준비

DB 비밀번호는 저장소에 두지 않습니다. 로컬 실행 전에 PowerShell 환경변수 `DB_USER`, `DB_PASSWORD`를 설정하거나, 저장소에 올리지 않는 `application-local.yaml`에 `spring.datasource.username`과 `spring.datasource.password`를 지정하세요. `.gitignore`에서 이 로컬 설정 파일과 로그 디렉터리를 제외합니다. 환경변수를 쓸 때는 같은 PowerShell 세션에서 DB 계정을 설정한 후 `.\gradlew.bat bootRun`을 실행합니다.

애플리케이션이 연결하는 PostgreSQL 데이터베이스에서 축제 스키마와 여섯 가지 유형을 한 번 생성합니다.

```powershell
psql -h localhost -p 5432 -U postgres -d map_db -f src\main\resources\db\festival-schema.sql
```

애플리케이션의 DB 연결 정보는 `src\main\resources\application.yaml`의 기존 datasource 설정을 따릅니다. 스키마 파일은 재실행할 수 있도록 `IF NOT EXISTS`와 유형 코드 upsert를 사용합니다.

HTTP 압축과 일별 Logback 로그 회전도 애플리케이션 설정에 포함되어 있습니다. 기본 로그 경로는 `logs\egis.log`이며 `LOG_FILE`로 바꿀 수 있습니다.

## API

- 공개 조회: `GET /api/festivals`, `GET /api/festivals/{id}`, `GET /api/festival-types`, `GET /api/regions`
- 관리자 적재: `POST /admin/api/import` (`file`, `encoding=MS949|UTF-8`)
- 관리자 이력: `GET /admin/api/imports`

축제 CSV는 최대 5MB이며 표준데이터의 `축제명`, `축제시작일자`, `축제종료일자`, `위도`, `경도` 헤더가 필요합니다. 다른 필드는 선택 항목이고, 행별 오류는 적재 결과에 건너뜀으로 기록됩니다. 파일의 행 순서와 무관하게 헤더 이름으로 컬럼을 매핑합니다.

지도 마커는 지형에 가려지지 않도록 XDWorld HTML 지도 객체로 표시하며, 마커 클릭 시 축제 상세 정보를 엽니다. 초기 카메라 이동 중에는 전국 단위 클러스터를 우선 표시하고, 카메라 이동이 끝난 뒤 현재 고도에 맞춰 마커를 다시 계산합니다.
