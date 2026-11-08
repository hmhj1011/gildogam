# 길도감

**나의 자동차와 함께 달린 길마다 그날의 날씨와 시간, 함께 탄 사람이 카드로 남는 여정의 도감.**

터널, 다리, 고개, 해안길을 끝까지 달리면 차가 직접 통과를 확인하고 카드를 남깁니다. 같은 다리도 비 오는 날, 눈 오는 날, 밤에 건너면 서로 다른 에디션이 되고, 한 번의 운행은 그날의 카드와 함께 탄 사람이 묶인 여정 페이지가 됩니다.

현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect (Android Automotive) 앱

---

## 어떻게 동작하나

| 순간 | 차가 알아채는 것 | 길도감이 하는 일 |
|---|---|---|
| 출발 전 | 내비 목적지 | "이번 여정에서 만날 수 있는 카드 5장, 그중 5장은 아직 없는 카드예요" |
| 출발 | 기어 D | 오늘의 여정 페이지 시작 |
| 구간 접근 | 차량 위치 | "곧 대관령 구간이에요" (음성만) |
| 구간 통과 | 입구 → 출구 통과, 그동안 주행 유지 | "대관령, 빗길·야경 에디션 획득!" |
| 터널 안 | GPS 끊김 | 입구·출구 위치만으로 판정 |
| 휴게소 | 휴게소 안에서 주차(P) | 쉬어 가면 휴게소 카드 |
| 비·눈 | 와이퍼, 바깥 온도 | 빗길·눈길 에디션 |
| 밤·새벽 | 그 장소의 일몰·일출 계산 | 야경·새벽 에디션 |
| 막히는 길 | 낮은 속도가 계속됨 | 정체 에디션 |
| 내비 회전 안내 직전 | 다음 회전까지 300m 이내 | 알림을 미룸 (내비 안내 우선) |
| 도착 후 주차 | 기어 P + 도착 | 여정 페이지 저장, 도감 열림 |
| 같은 길을 세 번째 | 출발지·도착지 반복 | "내 길" 카드 탄생 |

운전자가 해야 하는 조작은 없습니다. 주행 중에는 음성 알림만 하고, 여정 도감은 차를 세운 동안에만 열립니다. 속도나 통과 시간은 기록하거나 보상하지 않습니다.

---

## 공모전 요건 대응

| 요건 | 이 저장소 |
|---|---|
| Android API 28 이상 | `minSdk = 28` |
| Gradle 8.0 이상 | Gradle 8.10.2 wrapper, AGP 8.8.2 |
| Java 또는 Kotlin | Kotlin (일부 SDK 어댑터는 Java) |
| Pleos Playground SDK 활용 | 아래 5개 SDK |
| APK 위치와 이름 | `apk/<팀명>_<앱이름>_release.apk` (태그를 올리면 자동 생성·커밋) |

### 사용한 Pleos Connect SDK

| SDK | 버전 | 쓰는 곳 | 파일 |
|---|---|---|---|
| FusedLocation | 1.1.3 | 구간 입구·출구 판정 | `car/PleosLocation.kt` |
| Vehicle | 2.3.2 | 앞유리 와이퍼 → 빗길·눈길 에디션 | `car/PleosWiper.java` |
| NaviHelper | 3.0.5 | 목적지(여정 예고), 남은 시간(도착 판단), 회전 안내 구간 | `car/PleosNavi.kt` |
| TextToSpeech | 3.3.1 | 구간 접근·획득·여정 요약 알림 | `voice/PleosSpeaker.kt` |
| SpeechToText | 3.3.1 | 함께 탄 사람 등록, 카드 한마디 | `voice/PleosStt.java` |

기어·속도·바깥 온도는 Pleos가 따르는 Android Automotive 표준 차량 속성(`CarPropertyManager`)으로 읽습니다 (`car/CarPropertySignals.kt`). 바깥 온도를 읽을 수 없는 차에서는 눈길 에디션만 빠집니다. Pleos TTS·STT를 쓸 수 없는 기기에서는 Android 기본 음성으로 자동 대체되고, 둘 다 없으면 자막과 입력창으로 동작합니다.

---

## 구조

```
core/   여정 엔진 (순수 Kotlin, Android 의존 없음, 단위 테스트)
  GildogamEngine   차·내비·음성 이벤트 → 구간 판정, 에디션, 여정 페이지, 내 길, 알림 타이밍
  Geo, Sun         거리 계산, 일출·일몰 계산 (NOAA 일출 방정식)
  Catalog          시연용 카드 9장 (4개 노선)과 가상 주행 경로
  Model            카드, 에디션, 여정, 도감 데이터
app/    Android 앱
  car/    Pleos Vehicle·NaviHelper·FusedLocation, Android Automotive 차량 속성
  voice/  Pleos TTS·STT (+ 대체 구현)
  dex/    Controller (엔진과 SDK 연결, 가상 주행), DexStore (기기 안 JSON 저장)
  ui/     주행 화면, 여정 도감(여정 페이지·카드 도감), 시연 콘솔
```

### 구간 판정 규칙
1. 구간은 입구와 출구, 두 개의 원(반경 300m)으로 정의합니다. 양방향 모두 인정합니다.
2. 한쪽 원을 지나 반대쪽 원에 도착하면 통과입니다. 원래 원으로 되돌아오면 처음부터 다시 셉니다.
3. 구간 길이에 맞는 시간(시속 8km 기준 + 30분) 안에 도착하지 못하면 무효입니다.
4. 사이의 위치는 보지 않으므로 터널에서 GPS가 끊겨도 판정됩니다.

---

## 빌드와 실행

### 요구 사항
- Android Studio (최신), JDK 17
- Pleos Connect Emulator v2.1.0 (API 34)
  - SDK Manager → SDK Update Sites에 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` 추가 (Mac은 `crp-arm64-sys-img.xml`)
  - AVD: Automotive, "Pleos Connect v2.1.0", RAM 4GB, Cold boot

```bash
./gradlew :core:test          # 여정 엔진 단위 테스트
./gradlew :app:assembleDebug  # 디버그 APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

실행 후 마이크, 위치, 차량 속도, 바깥 온도 권한을 허용해 주세요. 거부해도 시연 콘솔로 모든 기능을 확인할 수 있습니다.

---

## 1분 시연 시나리오

화면 오른쪽 아래 **시연 콘솔**을 엽니다. 가상 주행은 실제 SDK 어댑터와 똑같은 이벤트(목적지, 기어, 위치, 남은 시간)를 시간에 맞춰 보냅니다.

1. **함께 탄 사람** → "엄마 아빠 지우" (말하거나 입력)
2. 가상 주행 → **서울 → 강릉 경포해변**: 카드 5장 예고 → 강원도 진입, 횡성휴게소(정차), 둔내터널, 대관령, 경포 해안길 획득 → 도착 후 여정 페이지 저장 (약 2분 20초)
3. **여정 도감** → 여정 페이지 "강릉 경포해변 가는 길" → 카드에 **한마디 남기기**
4. 시연 콘솔 → **밤 10시**, **와이퍼 켬** → 같은 길을 다시 가상 주행: 빗길·야경 에디션
5. **바깥 -2°C** + 와이퍼 → 눈길 에디션
6. 주행 중 **회전 안내 구간** → 획득 알림이 미뤄지는 것 확인 → **안내 구간 끝**
7. **집 → 회사 (출근)** 을 세 번 가상 주행 → 세 번째에 "내 길: 회사 가는 길" 카드 탄생

**도감 비우기**로 처음 상태로 되돌릴 수 있습니다.

---

## 릴리스와 APK 제출

1. `gradle.properties`의 `TEAM_NAME`을 팀명으로 바꿉니다 (예: `TEAM_NAME=TeamRoad`). 파일 이름은 `apk/TeamRoad_Gildogam_release.apk`가 됩니다. GitHub에서 파일 이름이 깨지지 않도록 영문을 권장합니다.
2. `version.properties`의 버전과 `CHANGELOG.md`를 함께 올립니다.
3. 같은 버전으로 태그를 푸시합니다.
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
4. Actions의 **Release** 워크플로가 테스트 → 릴리스 APK 빌드 → Releases 게시 → `apk/` 폴더에 APK 커밋까지 합니다.

서명 키(선택): 저장소 Settings → Secrets에 `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`를 넣으면 그 키로 서명합니다. 없으면 디버그 키로 서명해 그대로 설치할 수 있습니다.

비공개 저장소라면 심사 계정을 Collaborator로 초대해 주세요.

---

## 현재 한계와 다음 단계

- **카드 데이터는 시연용 9장**이고 좌표는 근사치입니다. 실제 주행 전에 OpenStreetMap의 터널·다리 구간 형상과 한국도로공사 공공데이터(도로터널정보, 휴게시설 현황)로 교체해야 합니다.
- **여정 예고**는 목적지 이름이 노선의 목적지 목록에 들어 있는지로 판단합니다. 다음 단계는 NaviHelper의 경로 정보로 실제 경로 위의 구간을 계산하는 것입니다.
- **바깥 온도 속성**이 Pleos Connect 에뮬레이터에서 읽히는지는 확인이 필요합니다.
- **도감은 기기 안에만 저장**됩니다. 위치 기록은 외부로 보내지 않고, 음성은 인식된 문장만 남깁니다.
