# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

이 프로젝트는 Hubitat Elevation 홈 오토메이션 플랫폼을 위한 포괄적인 WiFi/GPS 하이브리드 재실 감지 시스템입니다. WiFi 연결과 GPS 지오펜싱을 결합하여 정확한 재실 감지를 제공하며, 오탐지를 최소화하는 정교한 로직을 구현합니다.

## 시스템 구조

시스템은 4개의 주요 컴포넌트로 구성됩니다:

1. **개별 재실 장치 드라이버** (`WiFi GPS Hybrid Presence`)
   - 개별 장치의 재실 감지를 위해 WiFi와 GPS 데이터를 결합
   - WiFi는 도착 감지 트리거 (빠른 반응)
   - 출발 확인은 GPS+WiFi 조합 필요 (오탐 방지)
   - 세밀한 조정을 위한 구성 가능한 타임아웃과 지연

2. **Anyone 재실 장치** (`Anyone Presence Override`)
   - 모든 개별 장치를 하나의 "누군가 집에 있음" 상태로 통합
   - 특수 상황을 위한 수동 오버라이드 기능
   - 대시보드 스위치 제어 지원
   - 오버라이드를 위한 타임아웃 메커니즘 포함

3. **지연 외출 재실 장치** (`Delayed Away Presence`)
   - "외출" 상태 확인 전 구성 가능한 지연 구현
   - 임시 연결 문제로 인한 오탐 방지
   - 대기 상태 및 취소 기능 제공

4. **부모 앱** (`WiFi GPS Presence Manager Enhanced`)
   - 모든 재실 장치 관리
   - 개별 장치와 통합 재실 간 조정
   - WiFi 감지를 위한 MQTT 통합 처리
   - GPS 이벤트를 위한 웹훅 엔드포인트 제공
   - 알림 및 시스템 상태 관리

## 주요 기능

- **하이브리드 감지**: 빠른 도착은 WiFi, 신뢰성 있는 출발은 GPS+WiFi
- **오탐 방지**: 우발적인 상태 변경을 방지하는 정교한 로직
- **수동 오버라이드**: 필요시 재실 상태를 수동으로 설정하는 기능
- **지연 외출**: 모든 사람이 떠났음을 확인하기 전 구성 가능한 지연
- **외부 통합**: 외부 시스템을 위한 MQTT 및 웹훅 지원
- **포괄적 로깅**: 문제 해결을 위한 디버그 및 정보 로깅

## 개발 환경

이 프로젝트는 Hubitat Elevation 플랫폼 개발을 위해 Groovy를 사용합니다. 코드는 일반적으로 전통적인 빌드 도구가 아닌 Hubitat 웹 인터페이스를 통해 배포됩니다.

## 파일 구조

```
hubitat-presense/
├── initail-design.md    # 모든 드라이버 및 앱 코드가 포함된 완전한 시스템 구현
└── CLAUDE.md           # 이 파일
```

## 코드 구성

모든 구현 코드는 현재 `initail-design.md`에 다음 섹션으로 구성된 단일 설계 문서로 포함되어 있습니다:

1. 개별 재실 장치 드라이버 (6-259줄)
2. 수동 오버라이드가 있는 Anyone 재실 장치 (261-466줄)
3. 지연 외출 재실 드라이버 (468-592줄)
4. 부모 앱 - 재실 관리자 (594-993줄)

## 통합 포인트

- **MQTT 토픽**: WiFi 라우터 상태 업데이트 구독
  - `AsusAC68U/status/+/lastseen/epoch`
  - `UnifiU6Pro/status/+/lastseen/epoch`
- **웹훅 엔드포인트**: GPS 지오펜싱 이벤트
  - `/gps/[deviceId]/enter`
  - `/gps/[deviceId]/exit`
- **Hubitat 통합**: 표준 Hubitat 기능 및 이벤트 사용

## 구성 설정

각 컴포넌트는 광범위한 기본 설정을 가집니다:
- WiFi 및 GPS 타임아웃 값
- 도착 및 출발 지연 설정
- 디버그 로깅 제어
- 알림 기본 설정
- 장치 식별 (MAC 주소, GPS ID)

## 배포 참고사항

1. Hubitat의 Driver Code 섹션에 모든 드라이버 코드 설치
2. Apps Code 섹션에 부모 앱 설치
3. 앱 인스턴스 생성 및 개별 장치 구성
4. WiFi 감지를 사용하는 경우 MQTT 브로커 통합 설정
5. 지오펜싱을 위한 GPS 앱 웹훅 구성
6. 홈 오토메이션 통합을 위해 Rule Machine과 함께 테스트

## 개발 환경 및 도구

### 디렉토리 구조
```
hubitat-presense/
├── drivers/                 # 드라이버 코드 (.groovy)
├── apps/                   # 앱 코드 (.groovy)
├── utilities/              # 개발 도구 및 유틸리티
├── deployment/             # 배포 준비된 파일
└── backups/               # 백업 파일
```

### 개발 도구
- `utilities/deployment-helper.sh` - 코드 검증 및 배포 준비
- `utilities/development-guide.md` - 개발 가이드
- `utilities/test-suite.groovy` - 테스트 스위트
- `utilities/debug-helper.groovy` - 디버깅 도구

### 주요 명령어
- 코드 검증: `./utilities/deployment-helper.sh validate`
- 배포 준비: `./utilities/deployment-helper.sh prepare`
- 백업 생성: `./utilities/deployment-helper.sh backup`

## 개발 지침

- Hubitat Groovy 규칙 준수
- 적절한 capability 선언 사용
- 디버그/정보 레벨로 포괄적 로깅 구현
- 연결 문제에 대한 엣지 케이스 처리
- 속성과 이벤트를 통한 명확한 사용자 피드백 제공
- 실제 하드웨어로 배포 전 테스트

## 참고 문서

- [Hubitat 개발자 문서](https://docs2.hubitat.com/en/developer) - Hubitat 플랫폼 개발을 위한 공식 가이드

## 언어 설정

- 설정 페이지는 영어를 사용한다.