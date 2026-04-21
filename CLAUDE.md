# 프로젝트: 부자될거지 (poor-to-rich)

Spring Boot 3.4.2, Java 21 기반의 4계층 아키텍처를 따르는 개인 가계부 및 채팅 REST API 백엔드입니다.

## 코드 스타일 및 규칙

- **4계층 아키텍처 엄수:** 모든 도메인은 반드시 `Controller → Facade → Service → Repository`의 단방향 흐름을 가져야 합니다.
- **응답 포맷 통일:** 모든 API 엔드포인트는 예외 없이 `BaseResponse` 또는 `DataResponse<T>` 래퍼 클래스로 반환해야 합니다.
- **응답 메시지:** 응답 상태 코드 및 메시지는 하드코딩하지 말고 Enum 기반의 상수를 사용하세요.
- **예외 처리:** 개별 try-catch 대신 `/global`의 `GlobalExceptionHandler`를 통해 중앙 집중식으로 처리하세요.

## 명령어

- `./gradlew bootRun`: 로컬 개발 서버 실행
- `./gradlew build`: 프로젝트 빌드
- `./gradlew test`: 전체 테스트 실행 (H2 인메모리 DB 환경)
- `docker-compose up --build -d`: 전체 인프라(MariaDB, Redis, Prometheus, Grafana) 백그라운드 실행

## 아키텍처 (주요 패키지)

- `/user` & `/auth` & `/admin` & `/security` : 회원가입, JWT 인증, 카카오 OAuth2
- `/accountbook` & `/expense` & `/income` & `/iteration` : 가계부 데이터 CRUD
- `/transactions` & `/chart` : 가계부 분석 및 차트
- `/websocket`: WebSocket/STOMP 실시간 채팅
- `/chat` & `/chatnotice` & `/like` & `/tag` & `photo` & `/report` & `/ranking` : 채팅방 CRUD 및 채팅 기능
- `/global`: 공통 예외 처리, 공통 응답 객체, 유틸리티 클래스

## 중요 사항

- `.env` 파일과 `/sql` 패키지 하위 파일은 절대 커밋하지 마세요.
- 테스트 코드는 `BaseSecurityTest`를 상속하여 JWT/OAuth2 Mocking을 활용하세요.
- **작업 지침:** 새로운 API 엔드포인트 생성 규칙, 테스트 코드 작성 템플릿, 상세 프롬프트 등 구체적인 작업 스킬 및 매뉴얼은 `/.claude/skills.md`를 읽고 지시를 따르세요.
- **언어: 사용자와의 모든 대화와 코드 설명은 반드시 한국어(Korean)로 대답하세요.**
- 작업 종료 이후: 파일의 수정 사항을 간략히 요약해 주세요. 또한, 새롭게 생성되거나 변경된 API 엔드포인트는 HTTP 메서드, URL 경로, 간단한 설명을 포함하여 표(Table) 형식으로 출력해 주세요.