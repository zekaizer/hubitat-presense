# Hubitat Development Guide

## Quick Start

1. **코드 검증**: `./utilities/deployment-helper.sh validate`
2. **배포 준비**: `./utilities/deployment-helper.sh prepare`
3. **백업 생성**: `./utilities/deployment-helper.sh backup`

## 개발 환경 구성

### 필수 도구

1. **Groovy 설치** (코드 검증용):
   ```bash
   brew install groovy
   ```

2. **VS Code 확장** (권장):
   - Groovy Language Support
   - Hubitat Package Manager

### 디렉토리 구조

```
hubitat-presense/
├── drivers/                 # 드라이버 코드
├── apps/                   # 앱 코드
├── utilities/              # 개발 도구
├── deployment/             # 배포 준비된 파일
└── backups/               # 백업 파일
```

## 개발 워크플로우

### 1. 코드 작성

- `drivers/` 디렉토리에 드라이버 코드 작성
- `apps/` 디렉토리에 앱 코드 작성
- Groovy 문법 준수
- Hubitat 규칙 따르기

### 2. 코드 검증

```bash
./utilities/deployment-helper.sh validate
```

### 3. 배포 준비

```bash
./utilities/deployment-helper.sh prepare
```

### 4. Hubitat 허브에 배포

1. Hubitat 웹 인터페이스 접속
2. "Developer Tools" > "Drivers Code" 이동
3. 각 드라이버 파일을 "New Driver"로 생성
4. "Developer Tools" > "Apps Code" 이동
5. 각 앱 파일을 "New App"으로 생성

## 주요 개발 패턴

### 드라이버 개발

```groovy
metadata {
    definition (name: "Driver Name", namespace: "custom", author: "Author") {
        capability "Capability Name"
        
        attribute "customAttribute", "string"
        command "customCommand"
    }
    
    preferences {
        input "setting", "type", title: "Title", required: true
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    // 초기화 코드
}
```

### 앱 개발

```groovy
definition(
    name: "App Name",
    namespace: "custom",
    author: "Author",
    description: "Description",
    category: "Convenience"
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Title", install: true, uninstall: true) {
        section("Section") {
            input "setting", "type", title: "Title"
        }
    }
}
```

## 디버깅 가이드

### 로깅

```groovy
def logDebug(msg) {
    if (enableDebug) log.debug msg
}

def logInfo(msg) {
    log.info msg
}

def logWarn(msg) {
    log.warn msg
}

def logError(msg) {
    log.error msg
}
```

### 상태 관리

```groovy
// 상태 저장
state.myVariable = "value"

// 상태 읽기
def value = state.myVariable

// 상태 초기화
state.clear()
```

### 이벤트 처리

```groovy
// 이벤트 발생
sendEvent(name: "attribute", value: "value")

// 이벤트 구독
subscribe(device, "attribute", handlerMethod)

// 이벤트 핸들러
def handlerMethod(evt) {
    log.info "Event: ${evt.name} = ${evt.value}"
}
```

## 테스트 전략

### 1. 단계별 테스트

1. **드라이버 테스트**: 개별 드라이버 기능 확인
2. **앱 테스트**: 앱 설정 및 기본 동작 확인
3. **통합 테스트**: 전체 시스템 동작 확인

### 2. 실제 환경 테스트

1. 실제 WiFi 환경에서 테스트
2. GPS 앱과 연동 테스트
3. MQTT 브로커와 연동 테스트

### 3. 엣지 케이스 테스트

1. 네트워크 연결 불안정 상황
2. 동시 다발적 이벤트 처리
3. 시간 초과 상황 처리

## 배포 체크리스트

- [ ] 코드 검증 완료
- [ ] 로깅 레벨 적절히 설정
- [ ] 민감한 정보 제거
- [ ] 백업 생성
- [ ] 단계별 테스트 완료
- [ ] 문서 업데이트
- [ ] 배포 노트 작성

## 문제 해결

### 일반적인 문제

1. **구문 오류**: `groovy -c filename.groovy`로 확인
2. **런타임 오류**: Hubitat 로그에서 확인
3. **상태 문제**: `state.clear()`로 초기화
4. **이벤트 문제**: 구독 및 핸들러 확인

### 성능 최적화

1. **불필요한 로깅 제거**
2. **상태 변수 최적화**
3. **스케줄링 최적화**
4. **이벤트 처리 최적화**

## 유용한 명령어

```bash
# 코드 검증
./utilities/deployment-helper.sh validate

# 배포 준비
./utilities/deployment-helper.sh prepare

# 백업 생성
./utilities/deployment-helper.sh backup

# 파일 구조 확인
tree -I 'node_modules|.git'

# 로그 파일 실시간 모니터링 (Hubitat 허브에서)
# 웹 인터페이스 > Logs 페이지에서 확인
```