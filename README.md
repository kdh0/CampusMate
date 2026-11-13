# CampusMate — 대학생 하루 플래너

과제·시험 같은 **마감 일정**을 등록하고, 그것을 이루기 위한 **하루 할 일**을 날짜별로 적어 체크하는 플래너.
전날 끝내지 못한 일은 다음 실행 때 오늘로 옮길지 물어보고, 기록에는 "못함/미룸"으로 남는다.

```
[마감 일정] 자료구조 · 연결리스트 과제 (11/20 마감, D-7)
   ├─ 11/11 [V] 노드 클래스 작성
   ├─ 11/12 [>] 삽입 구현        ← 못 해서 다음 날로 미룸
   └─ 11/13 [ ] 삽입 구현 (미룬 일 1회)
```

- **1단계 콘솔 버전**: `campusmate.console.ConsoleMain`
- **2단계 Swing 버전**: `campusmate.swing.SwingMain`

두 버전 모두 같은 핵심 로직(`model`, `service`, `storage`)과 같은 저장 파일을 사용한다.
(동시에 켜 두면 나중에 저장한 쪽으로 덮어써지므로 하나씩 실행할 것)

## 메뉴

| 메뉴 | 기능 |
| --- | --- |
| (시작 시) | 지난 날 못 한 일 처리: 모두 오늘로 / 하나씩 선택 / 못함으로 기록 → 오늘 할 일·다가오는 마감 표시 |
| 1. 오늘 할 일 체크 | 번호를 입력하면 완료 ↔ 미완료 |
| 2. 할 일 추가 | 날짜를 고르고 여러 개를 이어서 입력. 마감 일정이나 과목에 연결 가능 |
| 3. 할 일 수정/삭제 | 날짜 → 번호 선택 후 수정·삭제·완료 변경 (지난 기록도 수정 가능) |
| 4. 날짜별 계획 보기 | 특정 날짜의 할 일과 그날 마감 일정 |
| 5. 전체 기록 | 날짜별 달성률, 완료/못함/미룸 표시, 이번 주 달성률, 연속 달성 일수 |
| 6. 조건 검색 | 검색어 → 해당 과목 → 기간 → 상태(예정/완료/못함) |
| 7. 마감 일정 관리 | 과제/시험/기타 등록·수정·완료·삭제, D-day, 연결된 할 일 진행률 |
| 8. 과목 관리 | 과목 등록·이름 수정·삭제 |
| 0. 저장 후 종료 | |

날짜 입력은 `Enter`(오늘), `+1`(내일), `-1`(어제), `오늘/내일/어제`, `11/20`, `2026-11-20` 모두 가능.

## 패키지 구조

```
src/campusmate
├── model/                 데이터 (화면과 무관)
│   ├── Course                 과목
│   ├── Deadline               마감 일정 (과제/시험)
│   ├── DeadlineType           마감 일정 종류 enum
│   ├── TodoItem               하루 할 일
│   └── TodoStatus             할 일 상태 enum (예정/완료/못함/미룸)
├── service/
│   └── PlannerService         핵심 로직: CRUD, 검색, 미루기, 기록/통계
├── storage/
│   └── FileRepository         파일 저장/불러오기
├── console/               ── 1단계 ──
│   ├── ConsoleMain            실행 진입점
│   └── ConsoleUI              메뉴 입력/출력
└── swing/                 ── 2단계 ──
    ├── SwingMain              실행 진입점
    ├── MainFrame              메인 창 (탭 6개, 상단 오늘 진행률, 메뉴, 시작 시 알림)
    ├── DayPanel               하루 계획 탭 (날짜 이동, 할 일 표, 빠른 추가)
    ├── TodoTableModel         할 일 표 모델 (완료 체크박스, 상태별 색)
    ├── TodoDialog             할 일 수정 창
    ├── OverdueDialog          시작 시 '못 한 일' 처리 창 (체크박스로 옮길 항목 선택)
    ├── CalendarPanel          달력 탭 (날짜별 달성 색 표시, 마감일 표시)
    ├── StatsPanel             기록·통계 탭 (요약 카드, JProgressBar, 차트)
    ├── BarChartPanel          Graphics로 직접 그리는 막대 차트
    ├── SearchPanel            검색 탭
    ├── DeadlinePanel          마감 일정 탭
    ├── DeadlineDialog         마감 일정 추가/수정 창
    ├── CoursePanel            과목 탭
    ├── LinkChoice             '연결' 콤보박스 항목 (없음 / 마감 일정 / 과목)
    └── SwingUtil              공통 색상, 날짜 해석 등
```

화면(ConsoleUI, Swing 패널들)은 입력과 출력만 맡고, 실제 처리는 모두 `PlannerService`가 한다.
그래서 Swing 버전은 화면 클래스만 새로 만들고 로직은 그대로 재사용했다.

## 1단계 요구사항 대응

| 요구사항 | 구현 |
| --- | --- |
| 클래스 분리 (3~4개 이상) | model 5개, service 1개, storage 1개, console 2개 |
| 캡슐화 | 모든 필드 `private`. `update()`는 값을 모두 검사한 뒤 한꺼번에 변경. 목록은 복사본을 돌려줌 |
| CRUD | 할 일: `addTodo` / `getTodos` / `updateTodo` / `deleteTodo`. 마감 일정·과목도 각각 CRUD |
| 조건 검색 | `PlannerService.searchTodos(검색어, 과목, 시작일, 종료일, 상태)` — 조건은 AND |
| 컬렉션 | **Map** `TreeMap<LocalDate, List<TodoItem>>` 날짜 → 그날 할 일 (날짜순 자동 정렬), `LinkedHashMap<Integer, Course>` 과목 ID → 과목 / **List** 하루 할 일 목록, 마감 일정 목록 / **Set** 모두 달성한 날짜(`getAchievedDays`, 연속 달성 계산), 불러올 때 ID 중복 검사 |
| 파일 입출력 | 시작 시 `FileRepository.load()`, 변경할 때마다와 종료(0번) 시 `save()` |
| 예외 처리 | 잘못된 입력은 다시 묻거나 안내 후 메뉴 복귀. 손상된/예전 버전 파일은 덮어쓰지 않고 종료 |

## 2단계 요구사항 대응

| 요구사항 | 구현 |
| --- | --- |
| JFrame / JPanel / 레이아웃 | `MainFrame`(BorderLayout) + 탭별 JPanel, GridLayout(달력·카드·입력 폼), FlowLayout(버튼 줄) |
| JTable / 목록 뷰 | 할 일 표(`TodoTableModel`), 마감 일정 표, 과목·달력 옆 JList |
| 버튼 이벤트 | 추가/수정/삭제, 날짜 이동(◀ ▶ 오늘), 달 이동 등 `ActionListener` |
| 체크박스 (완료 처리) | 할 일 표·마감 일정 표의 첫 열 체크박스, 못 한 일 처리 창의 JCheckBox |
| 키 입력 | 입력칸 Enter로 할 일 추가, 검색어 입력 즉시 검색(`KeyListener`), 표에서 Delete/Enter, 대화상자 Enter/ESC, Ctrl+S 저장, Ctrl+T 오늘로 |
| 마우스 | 표 더블클릭 수정, 달력 날짜 클릭/더블클릭 (`MouseListener`) |
| 달력 뷰 | `CalendarPanel` — 모두 완료(연두), 못 한 일 있음(연빨강), 오늘(파란 테두리), 마감일(▶) |
| 진행률 표시바 | 상단 오늘 진행률, 하루 계획 탭 달성률, 마감 일정별·과목별 `JProgressBar` |
| 마감 임박 알림 | 시작 시 3일 이내 마감을 `JOptionPane`으로 알림 (메뉴에서 다시 보기) |
| 통계 차트 | `BarChartPanel` — 최근 14일 달성률을 `Graphics`로 직접 그림 |
| 팀 특화 기능 | 시작 시 못 한 일을 체크박스로 골라 오늘로 옮기기, 연속 달성 일수, 마감 일정 ↔ 할 일 연결 |
| 자동 저장 | 변경 즉시 저장, 창을 닫을 때 저장(`WindowListener`) |

## 실행 방법

**IntelliJ**: `ConsoleMain.java`(콘솔) 또는 `SwingMain.java`(Swing)를 열고 `main` 옆의 ▶ 실행.

**명령줄** (프로젝트 폴더에서):

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out campusmate.console.ConsoleMain   # 콘솔 버전
java -cp out campusmate.swing.SwingMain       # Swing 버전
```

데이터는 `data/campusmate.properties`에 저장된다. 다른 파일을 쓰려면 실행 인자로 경로를 넘긴다.
