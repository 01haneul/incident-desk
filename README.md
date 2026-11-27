# Incident Desk

- 장애 이벤트를 접수하고 담당자의 확인, 담당자 전환,
  알림 재시도와 대응 이력을 관리하는 백엔드 서비스.


## 프로젝트 목표

- Ai와 함께 구현하되, 작성한 코드의 흐름과 설계 이유를 
  직접 설명하고 수정할 수 있는 상태를 목표로 한다

## 해결 하려는 문제

- 동일한 장애 알림이 반복되어 대응 상태를 파악하기 어렵다.
- 담당자가 확인하지 않은 장애가 방치될 수 있따.
- 알림 전송 실패와 대응 과정을 추적하기 어렵다.

## 구현한 기능

- 외부 장애 이벤트를 접수하고, 같은 이벤트를 재전송하면 기존 처리 결과를 반환한다.
- 같은 서비스에서 같은 장애가 반복되면 열려 있는 사건에 새 이벤트를 연결한다.
- 담당자를 배정·인계하고, 사건 상태를 `OPEN` → `ACKNOWLEDGED` → `RESOLVED` 순서로 변경한다.
- 담당자 배정·인계 알림을 전송하고, 전송에 실패하면 재시도한다.
- 사건 상세와 대응 이력을 조회한다. `afterId`를 지정하면 해당 이력 번호 이후의 변경만 반환한다.

## 검증 상태

로컬 서버에 실제 HTTP 요청을 보내 상태 변경과 담당자 인계 결과를 개발 DB와 대조했다.
같은 확인 요청을 두 번 보내도 이력은 한 건만 저장됐고, 거절된 인계 요청은 담당자와 이력을 그대로 유지했다.
알림은 첫 전송 실패 후 두 번째 시도에서 수신 성공과 DB의 `SENT` 상태를 확인했다.

전체 Gradle 테스트는 통과했다. 빈 MySQL 환경에서의 최초 설치는 아직 검증 전이다.


## 기술 문서

- [요구사항](docs/requirements.md)
- [API 계약](docs/api.md)
- [데이터 모델](docs/data-model.md)
- [검증 기록](docs/verification.md)
- [트러블슈팅](docs/troubleshooting.md)
- [보안 설계 및 검증 상태](docs/security.md)
- [이벤트 재전송 처리 설계와 검증](docs/idempotency.md)

## 로컬에서 처음 실행하기

Windows PowerShell, JDK 21, MySQL 8.0과 MySQL Workbench를 사용한다.
프로젝트에 포함된 Gradle Wrapper로 빌드한다.

### 저장소 내려받기

```powershell
git clone https://github.com/01haneul/incident-desk.git
cd incident-desk
java -version
```

`java -version`에서 Java 21을 확인한다.

### 빈 MySQL 환경 준비

Workbench에서 로컬 MySQL의 관리자 계정으로 접속한다.
아래 비밀번호 자리표시자를 로컬에서 사용할 값으로 바꾼 뒤 실행한다.

```sql
CREATE DATABASE incident_desk
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'incident_app'@'localhost'
    IDENTIFIED BY '<로컬에서 정한 비밀번호>';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON incident_desk.*
    TO 'incident_app'@'localhost';
```

이어서 `database` 폴더의 SQL 파일을 `001`부터 `007`까지 번호순으로 각각 한 번 실행한다.
`001`은 개발 테이블을 만들고, `002`는 테스트 DB와 테스트 테이블을 생성하고 앱 계정에 권한을 부여한다.
뒤의 파일들은 두 DB에 기능별 스키마 변경을 적용한다.

## 이벤트 접수 인증

POST /api/events 요청에는 X-API-Key 헤더가 필요하다.

실행에 필요한 환경 변수:

| 변수 | 용도 |
| --- | --- |
| DB_PASSWORD | incident_app 계정의 MySQL 비밀번호 |
| INGESTION_API_KEY | 이벤트 접수 인증용 비밀 키 |

두 환경 변수를 설정한 PowerShell에서 서버를 실행한다.

API 키 생성 예시:

```powershell
$apiKeyBytes = New-Object byte[] 32
$apiKeyGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$apiKeyGenerator.GetBytes($apiKeyBytes)
$env:INGESTION_API_KEY = [Convert]::ToBase64String($apiKeyBytes)
$apiKeyGenerator.Dispose()
Remove-Variable apiKeyBytes, apiKeyGenerator

### 서버 실행과 확인

API 키를 생성한 같은 PowerShell에서 앞서 정한 MySQL 비밀번호를 입력한다.

```powershell
$credential = Get-Credential -UserName 'incident_app' -Message '로컬 MySQL 비밀번호를 입력하세요'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
Remove-Variable credential
.\gradlew.bat bootRun
```

콘솔에 `Started IncidentDeskApplication`이 출력되면 서버 창을 열어 둔다.
다른 PowerShell에서 아래 명령을 실행해 HTTP `200`을 확인한다.

```powershell
curl.exe -sS -i http://127.0.0.1:8081/api/health
```

### 전체 테스트

서버를 실행한 PowerShell에서 `Ctrl+C`로 서버를 종료한 뒤 실행한다.
통합 테스트는 `incident_desk_test`를 사용한다.

```powershell
.\gradlew.bat test --rerun-tasks --console=plain
```

성공 기준은 `BUILD SUCCESSFUL`이다.