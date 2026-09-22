# Font Extractor

설치된 앱(갤럭시 스토어 / Play 스토어 글꼴 앱 등)의 APK 안에 들어 있는
TTF / OTF / TTC 글꼴 파일을 찾아 `Download/FontExtractor/<앱이름>/` 폴더로 추출하는 안드로이드 앱.

## 동작 방식

1. 설치된 모든 앱의 APK(base + split)를 zip으로 열어 글꼴 파일을 찾습니다.
   - 확장자 `.ttf .otf .ttc .otc`
   - `assets/`, `res/font/`, `res/raw/` 아래 파일은 확장자가 달라도 매직바이트로 판별
2. 글꼴이 있는 앱 목록을 보여줍니다. (글꼴 앱으로 보이는 것 우선, 검색·필터 지원)
3. 앱을 누르면 글꼴 목록이 뜨고, 각 글꼴을 **실제 글꼴로 미리보기** + TTF `name` 테이블에서 읽은 글꼴 이름을 보여줍니다.
4. 원하는 글꼴을 골라 **추출** → `Download/FontExtractor/<앱이름>/<글꼴이름>.ttf`
   개별 글꼴은 공유 버튼으로 바로 다른 앱에 보낼 수도 있습니다.

## 빌드

요구: JDK 17, Gradle 8.7 (또는 Android Studio Koala 이상)

```bash
# 최초 1회 (gradle wrapper jar 생성)
gradle wrapper --gradle-version 8.7

# 디버그 APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Android Studio에서는 폴더를 열고 그대로 Run 하면 됩니다.

GitHub에 올리면 `.github/workflows/build.yml` 이 자동으로 debug APK를 빌드해서
Actions → Artifacts 에 `FontExtractor-debug` 로 올려줍니다.

## 제한 사항

- 글꼴이 **APK 안에** 들어 있는 앱만 추출됩니다. (갤럭시 스토어 글꼴, 대부분의 Play 스토어 글꼴 팩)
  설치 후 서버에서 글꼴을 내려받아 앱 전용 저장소(`/data/data/...`)에 두는 앱은 루트 없이는 접근할 수 없습니다.
- 암호화된 글꼴 파일은 목록에 뜨지 않거나 미리보기가 안 될 수 있습니다.
- Android 11+ 에서 모든 앱을 열람하기 위해 `QUERY_ALL_PACKAGES` 권한을 사용합니다. (사이드로드용)
- 추출한 글꼴의 사용 범위는 해당 글꼴의 라이선스를 따릅니다.

## 구조

```
app/src/main/java/com/fontextractor/app/
  MainActivity.kt      설치 앱 검사 + 앱 목록
  FontListActivity.kt  글꼴 목록 / 미리보기 / 추출 / 공유
  FontScanner.kt       APK zip 스캔 (확장자 + 매직바이트)
  FontNameParser.kt    TTF/OTF/TTC name 테이블 파싱
  FontExporter.kt      MediaStore(Android 10+) / 레거시 저장
  Adapters.kt          RecyclerView 어댑터
  Models.kt            데이터 클래스
```
