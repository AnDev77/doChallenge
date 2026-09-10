# 02 Gradle Javac Windows Sandbox

## 1. 발생한 문제

Redis 최근 메시지 캐시 작업 중 다음 문제가 발생했다.

```text
compileJava
  - src/main/java 컴파일
  - 한 번 성공하면 build/classes/java/main 아래에 .class 파일이 생성됨

compileTestJava
  - src/test/java 컴파일
  - 원래는 build/classes/java/main의 메인 .class 파일을 classpath로 읽어야 함
  - 하지만 테스트 컴파일에서 메인 클래스를 찾지 못하는 오류가 발생함
```

대표 오류:

```text
error: package com.fitmeet.chat.domain does not exist
error: cannot find symbol
```

처음 보면 테스트 코드의 import 오류처럼 보인다.
하지만 실제로는 `ChatRoom.class`, `ChatMessage.class` 같은 메인 컴파일 결과물이
`build/classes/java/main`에 존재했다.

## 2. javac의 역할

`javac`는 Java 소스 파일을 JVM이 실행할 수 있는 바이트코드로 바꾸는 컴파일러다.

```text
ChatService.java
  -> javac
  -> ChatService.class
  -> JVM이 실행 가능한 바이트코드
```

Spring Boot 애플리케이션도 결국 Java 애플리케이션이다.
따라서 실행 전에 Gradle이 `javac`를 호출해서 다음 작업을 수행한다.

```text
src/main/java
  -> compileJava
  -> build/classes/java/main

src/test/java
  -> compileTestJava
  -> build/classes/java/test
```

테스트 코드는 메인 코드를 import해서 사용하므로,
`compileTestJava`는 `build/classes/java/main`을 classpath로 볼 수 있어야 한다.

## 3. JVM 관점

JVM은 `.class` 바이트코드를 실행하는 런타임이다.
Gradle도 Java로 만들어진 프로그램이기 때문에 Gradle 자체도 JVM 위에서 실행된다.

```text
PowerShell
  -> gradlew.bat
  -> Gradle JVM 실행
  -> Gradle Daemon JVM 실행
  -> javac 호출
  -> test JVM 실행
```

이번 환경에서는 Gradle Daemon 또는 test worker JVM이 내부 통신용 socket을 열다가
간헐적으로 다음 오류도 발생했다.

```text
java.net.SocketException: Bad address: listen
```

이 오류는 Spring Controller, Service, Bean 등록 문제가 아니다.
애플리케이션이 뜨기도 전에 Gradle/JVM 프로세스가 로컬 통신용 socket을 열면서 실패한 것이다.

## 4. Spring 관점

이번 문제는 Spring 문제로 보지 않는다.

이유:

```text
1. compileJava/compileTestJava 단계에서 발생했다.
2. 아직 Spring ApplicationContext가 뜨지 않았다.
3. Controller, Service, Repository Bean 생성 전이다.
4. Tomcat 포트 바인딩 전이거나, Gradle/test worker 내부 socket 단계였다.
```

Spring 문제가 되는 경우는 보통 다음 단계다.

```text
bootRun
  -> SpringApplication.run(...)
  -> ApplicationContext 생성
  -> Bean 등록
  -> Tomcat 시작
  -> HTTP/WebSocket 요청 처리
```

하지만 이번 문제는 그 전 단계에서 발생했다.

## 5. 확인한 내용

실제로 메인 클래스 파일은 존재했다.

```text
build/classes/java/main/com/fitmeet/chat/domain/ChatRoom.class
build/classes/java/main/com/fitmeet/chat/domain/ChatMessage.class
build/classes/java/main/com/fitmeet/common/exception/BaseException.class
```

`javap`로도 class 파일을 읽을 수 있었다.

```text
javap -classpath build/classes/java/main com.fitmeet.chat.domain.ChatRoomRepository
```

하지만 같은 경로를 `javac -cp build/classes/java/main`에 주면
일부 환경에서 class를 찾지 못했다.

반면 메인 `.java` 파일을 명시적으로 함께 넘기면 컴파일이 됐다.

```text
javac Probe.java ChatRoomRepository.java ChatRoom.java ...
```

따라서 원인은 코드 import 문제가 아니라,
현재 Windows sandbox/JDK 조합에서 javac가 디렉터리 classpath의 메인 output을 안정적으로 해석하지 못한 문제로 판단했다.

## 6. 적용한 해결책

`compileTestJava`가 테스트 소스만 컴파일하면서 `build/classes/java/main`을 classpath로 읽는 기본 흐름이 불안정했다.
그래서 테스트 컴파일 시 메인 소스도 함께 source로 제공하도록 보정했다.

```gradle
tasks.named('compileTestJava') {
    source sourceSets.main.java
}
```

이 설정의 의미:

```text
compileTestJava
  - src/test/java만 컴파일하는 것이 아니라
  - src/main/java도 테스트 컴파일 입력으로 함께 제공한다.
  - 따라서 javac가 main output 디렉터리를 못 읽어도 main source를 직접 보고 타입을 해석할 수 있다.
```

## 7. 장단점

장점:

```text
1. 현재 Windows sandbox 환경에서 compileTestJava가 통과한다.
2. 테스트가 메인 클래스를 못 찾는 문제를 우회한다.
3. 로컬 학습/개발 흐름을 끊지 않는다.
```

단점:

```text
1. 표준 Gradle 설정은 아니다.
2. 테스트 컴파일 때 메인 소스도 다시 다루므로 시간이 늘어날 수 있다.
3. 일반적인 로컬/CI 환경에서는 필요 없을 가능성이 있다.
```

따라서 이 설정은 "현재 개발 환경에서 테스트를 진행하기 위한 보정"으로 이해한다.
나중에 GitHub Actions나 일반 로컬 터미널에서 동일 문제가 없다면 제거 후보가 될 수 있다.

## 8. 최종 결과

```text
compileTestJava
  - 성공

test
  - 최종 재실행 성공

남은 주의점
  - Gradle Daemon 또는 test worker에서 Bad address: listen이 간헐적으로 발생할 수 있다.
  - 같은 명령을 재실행하면 통과하는 경우가 있었다.
  - CI 환경에서는 재현되지 않을 수 있다.
```

## 9. 정리

이번 문제는 Spring 애플리케이션 코드 문제가 아니라 빌드 단계 문제다.

```text
javac
  - .java를 .class로 바꾸는 컴파일러

JVM
  - .class를 실행하는 런타임
  - Gradle, test worker, Spring Boot app 모두 JVM 위에서 실행된다.

Gradle
  - javac를 호출하고 테스트 JVM을 실행하는 빌드 도구

Spring
  - 빌드가 끝난 뒤 애플리케이션 실행 단계에서 등장한다.
```

이번 해결은 `compileTestJava`가 메인 타입을 안정적으로 해석하도록
메인 소스를 테스트 컴파일 입력에도 포함시키는 방식이다.
