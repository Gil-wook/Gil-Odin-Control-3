# 기여 안내 / Contributing

## 한국어

기능 제안, 버그 수정, 번역, 문서 개선을 환영합니다.

### 변경 전
1. 관련 GitHub Issue가 있는지 확인하거나 새 Issue를 작성합니다.
2. 큰 기능은 먼저 설계 방향을 논의합니다.
3. Odin 3 펌웨어 버전과 재현 조건을 기록합니다.

### Pull Request
- 한 PR에는 한 가지 목적의 변경을 담아 주세요.
- 변경한 기능, 영향 범위, 테스트한 항목을 설명해 주세요.
- 빌드하지 못했거나 실기기에서 확인하지 못한 부분은 명확히 표시해 주세요.
- 개인 로그, 기기 식별자, 계정 정보, 서명 키를 커밋하지 마세요.
- 업스트림 코드의 저작권 및 라이선스 고지를 제거하지 마세요.

### 로컬 확인
Windows에서는 `Build-Windows.cmd`를 실행합니다. 가능한 경우 Android Studio에서 `:app:assembleDebug`와 관련 테스트도 실행해 주세요.

## English

Contributions are welcome: bug fixes, feature proposals, translations, and documentation.

- Open or find an Issue before large changes.
- Keep each pull request focused.
- Describe the change, affected behavior, and tests performed.
- Clearly disclose anything not built or tested on a physical Odin 3.
- Never commit private logs, device identifiers, account data, or signing keys.
- Preserve upstream copyright and license notices.
