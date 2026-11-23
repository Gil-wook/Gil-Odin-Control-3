# GitHub 공개 저장소 만들기 (GitHub Desktop)

이 ZIP은 소스 저장소에 올릴 파일을 준비한 패키지입니다. 이 작업만으로 사용자의 GitHub 계정에 업로드되지는 않습니다.

## 1. GitHub에서 빈 저장소 생성
1. https://github.com/new 에 접속합니다.
2. Repository name: `Gil-Odin-Control-3`
3. Visibility: **Public**
4. README, .gitignore, License 자동 생성 옵션은 선택하지 않습니다. 이 패키지에 이미 포함되어 있습니다.
5. **Create repository**를 누릅니다.

## 2. GitHub Desktop으로 빈 저장소 clone
1. https://desktop.github.com/ 에서 GitHub Desktop을 설치하고 로그인합니다.
2. File → Clone repository를 선택합니다.
3. 방금 만든 `Gil-Odin-Control-3` 저장소를 선택하고 PC의 작업 폴더에 clone합니다.

## 3. 준비된 소스 복사
1. `Gil-Odin-Control-3-Public-v0.7.0.zip`을 별도 폴더에 압축 해제합니다.
2. 압축 해제된 폴더 **안의 모든 파일과 폴더**를 clone한 저장소 폴더로 복사합니다.
3. `GilOdinControl3-0.7.0-Studio-Source` 같은 상위 폴더가 저장소 안에 한 겹 더 생기지 않게 주의합니다.
4. GitHub Desktop의 Changes 탭에서 파일 목록을 확인합니다.

## 4. 첫 업로드
1. Summary에 `Initial public source import` 입력
2. **Commit to main** 클릭
3. **Push origin** 클릭

이제 GitHub 저장소 페이지를 새로고침하면 소스가 공개됩니다.

## 5. APK 배포는 빌드 검증 후
APK를 만들고 Odin 3 실기기에서 설치/기능을 확인한 뒤:
1. GitHub 저장소의 Releases → Draft a new release
2. Tag: 예) `v0.7.0`
3. APK와 변경 내역을 첨부
4. Publish release

현재 v0.7.0 스냅샷은 Android Gradle 빌드와 전체 실기기 검증이 끝난 안정판이 아닙니다. 검증 전에는 APK를 정식 Release로 표시하지 않는 것을 권장합니다.
