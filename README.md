# CampusMate — 대학생 과제·할 일 플래너

과목별 **과제·시험(마감 일정)** 과 **하루 할 일**을 한 목록에서 관리한다.
과제·시험은 D-day를 보여 주고, 3일 안에 마감하면 시작할 때 알려 준다.
어제까지 못 한 할 일은 시작할 때 오늘로 옮길지 물어본다.

- **1단계 콘솔 버전**: `campusmate.console.ConsoleApp`
- **2단계 Swing 버전**: `campusmate.swing.MainFrame`

두 버전은 같은 핵심 로직(`model`, `service`, `storage`)과 같은 저장 파일(`data/campusmate.txt`)을 쓴다.

## 패키지 구조

```
src/campusmate
├── model/
│   ├── Task           할 일 하나 (내용, 과목, 종류, 날짜, 완료 여부)
│   └── Category       종류 enum: 할 일 / 과제 / 시험
├── service/
│   └── TaskManager    핵심 로직: CRUD, 조건 검색, 마감 알림, 지난 할 일 옮기기, 통계
├── storage/
│   └── FileStore      텍스트 파일 저장/불러오기
├── console/           ── 1단계 ──
│   └── ConsoleApp     메뉴 입력/출력 (main)
└── swing/             ── 2단계 ──
    ├── MainFrame      메인 창 (main), 오늘 진행률, 시작 알림, 추가/수정 입력 창
    ├── TaskPanel      '할 일 목록' 탭: 검색 + 표 + 추가/수정/삭제
    ├── CalendarPanel  '달력' 탭
    └── StatsPanel     '통계' 탭: 과목별 진행률 바 + 최근 7일 차트
```

화면(ConsoleApp, Swing 패널)은 입력과 출력만 맡고, 실제 처리는 모두 `TaskManager`가 한다.
그래서 Swing 버전은 화면 클래스만 새로 만들고 로직은 그대로 재사용했다.

## 콘솔 메뉴

| 메뉴 | 기능 |
| --- | --- |
| (시작 시) | 마감 임박 알림 → 지난 할 일 오늘로 옮기기(y/n) → 오늘 할 일 |
| 1. 전체 목록 | 날짜순 전체 목록과 완료율 |
| 2. 날짜별 보기 | 특정 날짜의 할 일 |
| 3. 추가 / 4. 수정 / 5. 삭제 | 번호(#)로 선택. 수정은 Enter로 기존 값 유지 |
| 6. 완료 체크 | 완료 ↔ 미완료 |
| 7. 조건 검색 | 검색어(내용·과목) + 종류 + 상태, 원하는 조건만 입력 |
| 0. 저장 후 종료 | |

날짜 입력: `Enter`(오늘), `+1`(내일), `-1`(어제), `11/20`, `2026-11-20`

## 1단계 요구사항 대응

| 요구사항 | 구현 |
| --- | --- |
| 클래스 분리 (3~4개 이상) | `Task`, `Category`, `TaskManager`, `FileStore`, `ConsoleApp` (역할별로 패키지 분리) |
| 캡슐화 | `Task`의 모든 필드 `private` + getter/setter. setter에서 빈 내용, null 날짜 등을 검사 |
| CRUD + 조건 검색 | `add` / `getAll`·`getByDate` / `update` / `delete`, `search(검색어, 종류, 완료여부)` — 조건은 AND |
| 컬렉션 | **Map** `HashMap<Integer, Task>` ID → 할 일 / **List** 목록·검색 결과 (날짜순 정렬) / **Set** `TreeSet<String>` 과목 이름 (중복 없이 정렬) |
| 파일 입출력 | 시작 시 `FileStore.load()`, 종료(0번) 시 `save()`. 파일이 깨졌으면 덮어쓰지 않고 종료 |
| 예외 처리 | 잘못된 입력은 다시 묻거나 안내 후 메뉴로 복귀 |

## 2단계 요구사항 대응

| 요구사항 | 구현 |
| --- | --- |
| JFrame / JPanel / 레이아웃 | `MainFrame`(BorderLayout) + 탭별 JPanel, GridLayout(달력·입력 폼·통계), FlowLayout(버튼 줄) |
| JTable / 목록 뷰 | 할 일 표(`TaskPanel`), 달력 옆 그날 목록(JList) |
| 버튼 이벤트 | 추가/수정/삭제, 달 이동(◀ ▶), 달력 날짜 클릭 (`ActionListener`) |
| 체크박스 (완료 처리) | 표의 첫 열 체크박스 → `setValueAt`에서 완료 처리 |
| 키 입력 | 검색어 칸에 글자를 칠 때마다 바로 검색, 표에서 Delete 키로 삭제 (`KeyListener`) |
| 기타 리스너 | 표 더블클릭 수정(`MouseListener`), 창 닫을 때 저장(`WindowListener`) |
| 달력 뷰 | `CalendarPanel` — 모두 완료(연두), 못 한 일 있음(연빨강), 오늘(파란 테두리), 마감일(★) |
| 진행률 표시바 | 상단 오늘 진행률, 통계 탭의 전체·과목별 `JProgressBar` |
| 마감 임박 알림 | 시작 시 3일 이내 마감 과제·시험을 `JOptionPane`으로 알림 |
| 통계 차트 | 최근 7일 완료율 막대 차트를 `Graphics`로 직접 그림 |
| 팀 특화 기능 | 지난 할 일을 오늘로 옮기기, 과제·시험 D-day |
| 자동 저장 | 데이터가 바뀔 때마다 저장(`dataChanged()`), 창을 닫을 때 저장 |

## 실행 방법

**IntelliJ**: `ConsoleApp.java`(콘솔) 또는 `MainFrame.java`(Swing)를 열고 `main` 옆의 ▶ 실행.

**명령줄** (프로젝트 폴더에서):

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out campusmate.console.ConsoleApp   # 콘솔 버전
java -cp out campusmate.swing.MainFrame      # Swing 버전
```

저장 파일 형식 (한 줄 = 할 일 하나, 탭으로 구분): `번호  날짜  종류  완료여부  과목  내용`
