# Gil Odin Control 3

**A unified control center for the AYN Odin 3.**  
AYN Odin 3용 독(Dock), 버튼 설정, 시스템 도구를 하나로 모으는 비공식 통합 프로젝트입니다.

[한국어](#한국어) · [English](#english)

> **Development status:** experimental / pre-release.  
> The source package is prepared for public collaboration, but this snapshot has **not yet passed a clean Android Gradle build and full Odin 3 device validation**. Do not treat it as a stable release.

## 한국어

### 프로젝트 소개

Gil Odin Control 3은 기존 Odin Dock의 독 기능과 OdinTools/Odin3Tools 계열의 기기 설정 기능을 하나의 앱에서 제공하는 것을 목표로 합니다.

### 현재 포함된 영역

- **Dock Studio:** 독 미리보기, 앱 슬롯, 크기·간격·위치·색상·테두리 등의 외형 설정
- **Dock service:** 접근성 서비스 기반 독 표시 및 앱 화면 상태 감지
- **Button Studio:** M1/M2/BACK/HOME 버튼 설정 UI 및 입력 정책 코드
- **Odin 3 Tools:** 성능/팬/컨트롤러/L2·R2 설정, 앱별 프로필, 외부 화면 관련 기능, 빠른 설정 타일 등 Odin3Tools 기반 코드
- **한국어 리소스:** 주요 설정 화면의 한국어 문자열

일부 기능은 제조사 PServer, 접근성 권한, Android 버전 및 Odin 펌웨어에 의존합니다. UI에 항목이 존재한다고 해서 해당 기능이 모든 펌웨어에서 정상 동작한다는 뜻은 아닙니다.

### 호환성 및 주의사항

- 대상 기기: AYN Odin 3
- Android: 프로젝트의 `minSdk` 및 `targetSdk` 설정 참고
- Odin3Tools 원본은 펌웨어 **1.0.0.151**에서 테스트된 것으로 안내되어 있습니다. 다른 펌웨어에서는 제조사 내부 API가 달라질 수 있습니다.
- 접근성 서비스는 독 표시, 화면 상태 감지 및 설정한 입력 동작에 사용됩니다. 앱이 요청하는 권한을 설치 전에 확인하세요.
- 버튼 재매핑, HOME 입력 제어, RetroArch 조합키 동작은 기기 펌웨어와 입력 경로에 따라 제한될 수 있습니다.
- 이 저장소의 현재 스냅샷은 **개발용 소스**입니다. APK 빌드와 Odin 3 실기기 전체 테스트가 완료되기 전까지 안정판으로 배포하지 않습니다.

### 빌드 방법 (Windows)

필요 환경:
- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Gradle 8.9 (Gradle Wrapper가 필요 시 다운로드)

가장 간단한 방법:

1. 저장소를 clone하거나 ZIP으로 받습니다.
2. Windows에서 `Build-Windows.cmd`를 실행합니다.
3. 빌드 로그는 `build-log.txt`에서 확인합니다.
4. 성공하면 프로젝트 루트에 `GilOdinControl3-0.7.0-studio-debug.apk`가 생성됩니다.

Android Studio를 사용한다면 프로젝트를 열고 Gradle 동기화 후 다음 작업을 실행할 수 있습니다.

```powershell
.\gradlew.bat :app:assembleDebug
```

첫 빌드에는 Gradle 및 Android SDK 구성요소 다운로드가 필요할 수 있습니다. 이 저장소에는 SDK나 개인 서명 키를 포함하지 않습니다.

### GitHub Actions

`.github/workflows/build.yml`은 JDK 17과 Android SDK 35 환경에서 Debug APK를 빌드하고 Actions artifact로 업로드하도록 구성되어 있습니다. CI 성공은 에뮬레이터/실기기 기능 검증을 의미하지 않습니다.

### 버그 제보

GitHub Issues에 다음 정보를 포함해 주세요.

- 앱 버전과 Odin 펌웨어 버전
- 재현 순서
- 기대한 동작과 실제 동작
- 진단 로그 (계정명, 기기 식별자 등 개인정보는 가린 뒤 첨부)

### 라이선스

이 저장소의 라이선스와 업스트림 고지는 [`LICENSE`](LICENSE) 및 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)를 확인하세요. OdinTools/Odin3Tools에서 유래한 부분의 원저작권 표시는 유지합니다. Gradle로 가져오는 외부 라이브러리는 각 라이브러리의 별도 라이선스를 따릅니다.

---

## English

### Overview

Gil Odin Control 3 is an unofficial unified control center for the AYN Odin 3. The goal is to combine a customizable floating dock, button configuration, and Odin device utilities in one application.

### Project areas

- **Dock Studio:** live preview, app slots, size, spacing, position, colors, borders, and visibility settings
- **Dock service:** accessibility-based dock display and foreground-window detection
- **Button Studio:** M1/M2/BACK/HOME configuration UI and input-policy code
- **Odin 3 Tools:** Odin3Tools-derived device controls such as performance/fan/controller/L2-R2 settings, per-app profiles, external-display handling, and Quick Settings tiles
- **Korean localization:** Korean strings for the main settings UI

Some functionality depends on the OEM PServer, accessibility permission, Android version, and Odin firmware. A visible setting does not guarantee that the feature works on every firmware.

### Compatibility and safety

- Target device: AYN Odin 3
- The upstream Odin3Tools project reports testing on firmware **1.0.0.151**. OEM APIs may change in later firmware.
- Accessibility access is used for dock display, window-state detection, and configured input behavior. Review permissions before enabling the service.
- Button remapping, HOME interception, and RetroArch hotkey behavior can be limited by the device firmware and input path.
- This repository snapshot is **development source**, not a stable release. A clean Android build and full device validation have not yet been completed for this snapshot.

### Build on Windows

Requirements:
- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Gradle 8.9 (the Gradle Wrapper downloads it if needed)

Run `Build-Windows.cmd`. Build output is copied to the repository root as `GilOdinControl3-0.7.0-studio-debug.apk`. See `build-log.txt` if the build fails.

With Android Studio, open the project and run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The first build may download Gradle and Android SDK components. No Android SDK or private signing key is included in this repository.

### License

See [`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). Upstream copyright notices are retained. Dependencies fetched by Gradle remain under their respective licenses.
