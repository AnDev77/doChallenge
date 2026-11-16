# 대표 종목 공통 목록과 관심 저장

2026-11-16 KST. 회원 관심 저장 초안 #16의 원격 커밋 aa01c34를 기준으로 새 분기를 만들었다. 새 목록 변경을 기존 #16 및 브리핑 #23에 섞지 않았다.

- 시작: 웹 최신 계획과 구조 검토를 확인하고 CAMT·SPCX·AVGO를 우선했다. 기존 관심 저장은 QQQ·SPY만 받고 교차 출처 허용 설정도 없었다.
- 변경: Java 공개 GET /api/v1/instruments와 관심 저장이 같은 카탈로그를 사용한다. 기존 5종목의 가시성과 기능 지원을 별도로 반환한다. 새 종목을 자동으로 개인 목록에 저장하지 않는다. 브리핑은 별도 통합이 완료될 때까지 꺼져 있다.
- 확인 코드: src/main/java/com/fitmeet/instruments/InstrumentCatalog.java, InstrumentController.java, src/main/java/com/fitmeet/watchlist/WatchlistService.java, src/main/java/com/fitmeet/auth/presentation/AuthRateLimitConfig.java.
- 검증: 인증·두 회원 분리와 반복 저장, 허용/거부 Origin, 3종목 추가, Docker MySQL 재기동 뒤 AVGO 보존을 시험에 포함했다. Java 21은 설치됐으나 Gradle 배포 파일 다운로드가 네트워크에서 차단되어 로컬 Java 전체 시험·JAR은 실행하지 못했다. diff·Python 구문 확인을 통과했다. Java/Redis/JAR/Compose는 원격 게이트에서 확인한다.
- 한계: 기존 4자리 티커 저장 계약과 V2 스키마를 유지한다. 미국 전체 종목 확장은 별도 고정 ID·마이그레이션·검색 구현이 필요하다. 동시 중복 PUT은 기존 DB 유일 제약이 막으며 성공 응답까지 보장하는 경쟁 시험은 이번 범위에 없다. 운영 설정·가격 공급·친구 모델·실제 배포는 변경하지 않았다.
