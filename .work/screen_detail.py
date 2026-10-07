"""Version 1.1 screen-level requirements appended to the unified document."""

SCREENS = [
dict(id='SC01', title='첫 실행 안내', refs='FR02~04·09 / AT01·04',
 entry='첫 설치 후 첫 실행. Settings의 Gesture guide는 같은 안내를 다시 열되 마지막 닫기는 Settings로 복귀한다.',
 rows=[
 ('01 상단 고정','Sign Korean · Skip','단계 1/3. Skip은 완료로 기록하고 SC02 이동. 튜토리얼 재진입 시 Close로 교체.'),
 ('02 본문','Learn from real signs','1단계 간판 사진+영어 뜻 보기 예시. 사진에는 Example 표시. 실제 학습 상태를 변경하지 않음.'),
 ('03 본문','Left: I know / Right: Learn again','2단계 양방향 그림과 텍스트. 방향을 그대로 표시하며 필수 스와이프 연습은 요구하지 않음.'),
 ('04 본문','Down: Reviews','3단계 아래 화살표, 해당 가게 리뷰와 여행 계획 연결 설명. 위치 권한 요청 없음.'),
 ('05 하단 고정','Back · Next · Get started','1단계 Back 숨김, 2~3단계 활성. Next는 다음 단계, 마지막 Get started는 SC02. 단계 점 표시.')],
 states=[('기본','1→2→3 순서로 진행. 현재 단계와 그림의 접근성 설명 제공.'),('재진입','온보딩 완료 여부를 바꾸지 않고 Close로 SC10 복귀.'),('중단','미완료 단계 번호를 저장. 재실행 시 해당 단계부터, Skip 이후 다시 강제 노출하지 않음.')],
 exit='Get started 또는 Skip 뒤 Explore. OS 뒤로가기는 2~3단계에서 이전 단계, 1단계에서 앱 종료.'),
dict(id='SC02', title='랜드마크 탐색', refs='FR01·07·17 / AT01·03',
 entry='온보딩 완료, Explore 탭, 다른 화면의 Explore로 돌아가기.',
 rows=[
 ('01 상단 고정','Explore · Settings','Settings 아이콘은 SC10. 언어·지역 선택 드롭다운은 없음. 서울 출시 범위를 제목 아래 Seoul로 표시.'),
 ('02 검색','Search landmarks','앱 등록 랜드마크 영문·한국어 이름 부분 일치, 앞뒤 공백 제거. 입력 300ms 뒤 로컬 필터. ×는 검색어 초기화.'),
 ('03 재개 배너','Resume practice','미완료 학습이 있을 때만 출처 이름과 판정 n/10 표시. 누르면 저장된 SC04. 다른 학습 시작 시 SC20 C01.'),
 ('04 목록 스크롤','Landmark tile','썸네일, 영어 이름·한국어 병기, 활성 카드 수, Known x/y. 최근 known 카드 수이며 정답률 아님. 선택 SC03.'),
 ('05 하단 고정','Explore · Saved · Plan','현재 Explore 강조. SC09·SC11 이동. 탭 전환은 검색어·스크롤·진행 세션을 보존.')],
 states=[('로딩','목록 skeleton. 재개 배너는 로컬 데이터가 있으면 먼저 표시.'),('검색 0건','No landmarks found · Clear search.'),('콘텐츠 없음','No landmarks available · Retry. 내려받은 목록 있으면 우선 표시.'),('통신 실패','캐시 있으면 Offline banner와 마지막 목록, 없으면 Couldn’t load landmarks · Retry.')],
 exit='랜드마크 선택 SC03. 루트 OS 뒤로가기는 앱 종료. 이 화면에서 위치 권한 요청 없음.'),
dict(id='SC03', title='랜드마크 상세와 학습 시작', refs='FR01·05·13·18 / AT01·02·07·11',
 entry='SC02 랜드마크 선택, SC13 일정의 랜드마크 선택, SC14 등록 랜드마크 행.',
 rows=[
 ('01 상단 고정','Back · Landmark name','입력 landmark_id. 제목 영어 이름, 본문 한국어 이름 병기. 하단 탭 숨김.'),
 ('02 본문 스크롤','대표 이미지 · About','소개·주소·좌표 출처. 지도 이동은 이 랜드마크 자체를 목적지로 사용.'),
 ('03 상태 요약','New x · Learn again y · Known z','활성 카드의 전역 현재 상태 합계. 합계=활성 카드 수. New=unseen.'),
 ('04 모드 선택','All cards · Review','All cards 기본. Review는 learning 수가 0이면 비활성, No cards to review 표시.'),
 ('05 목록','Places and signs','연결 장소의 영어 이름, 대표 간판 썸네일, 카드 수. 행 선택 SC07. 목록은 장소 단위로 중복 제거.'),
 ('06 하단 고정','Start practice · Map · Add to plan','Start practice는 선택 모드 후보 1개 이상일 때 SC04. Map→SC18 single, Add to plan→SC15. 새 세션 전 미완료 세션은 C01.')],
 states=[('기본','기기 콘텐츠 버전의 활성 장소·카드 사용.'),('카드 0개','No study cards · Start practice 비활성. 좌표 유효하면 Map·Add to plan 활성.'),('비활성 랜드마크','Landmark unavailable · 학습/추가 금지, Back 제공.'),('오프라인','저장된 사진·텍스트 사용. 없는 자료는 재시도 안내; 가능한 카드만 선택적으로 몰래 제외하지 않음.')],
 exit='Back은 진입 화면과 스크롤로 복귀. 리뷰는 개별 장소 SC07에서만 제공.'),
dict(id='SC04', title='간판 카드 학습', refs='FR02~07·09·12·21 / AT01~04·09·13',
 entry='SC03 Start practice, SC07 Practice this sign, SC13 Study this day, SC02 Resume practice. source_type=landmark/card/trip_day.',
 rows=[
 ('01 상단 고정','Close · 출처명 · Undo','Close는 SC20 C02. Undo는 취소 가능한 마지막 판정 있을 때 활성; 취소 즉시 이전 카드·뜻 공개 상태 복원.'),
 ('02 진행 표시','Decisions n/10','n은 확정 판정 수. 같은 카드 재출제도 1회 판정으로 집계. Review 또는 All cards 모드를 작은 라벨로 표시.'),
 ('03 카드 이미지','간판 사진 · Bookmark','이미지 4:3, 원본 fit-contain, 핵심 영역 테두리. 사진 단일 탭 SC05. 북마크는 상단 우측 별도 버튼이며 사진 제스처 영역에서 제외.'),
 ('04 카드 텍스트','한국어 원문 · Show meaning','원문 최대 2줄 뒤 확대 또는 줄바꿈. 뜻 영역은 처음 숨김, 버튼 탭 시 영어 뜻·설명 공개. 뜻 공개는 판정 아님.'),
 ('05 판정 버튼','← I know · Learn again →','좌측=known, 우측=learning. 사진 영역 좌우 스와이프와 동일. 저장 성공 후 다음 카드; 실패는 현재 유지.'),
 ('06 보조 동작','Reviews · Map · Add to plan','Reviews=사진 아래 스와이프와 동일 SC08. Map→SC18 실제 가게 목적지, Add→SC15 가게 선택. 세 동작은 판정 없이 현재 세션 보존.')],
 states=[('뜻 숨김/공개','공개 후 Show meaning 대신 영어 뜻. 다음 카드 초기 숨김. 보조 화면 복귀 시 같은 공개 상태.'),('판정 저장 중','중복 터치 차단, Undo·판정 비활성. 300ms 이상이면 저장 표시.'),('이미지 실패','Image unavailable · Retry. 판정 비활성. Close는 가능. 이미지 확보 뒤 동일 카드 진행.'),('세션 종료','10회 또는 큐 소진이면 SC06. 마지막 판정 Undo는 SC06에서 제공.'),('장소 불일치','Place unavailable. Reviews·Map·Add 비활성, 이미 유효하게 확보한 카드 학습은 유지.')],
 exit='보조 화면 닫기는 같은 card_id·뜻 공개·큐로 복귀. Close에서 Keep for later 또는 End session 선택. OS 뒤로가기도 동일.'),
dict(id='SC05', title='간판 사진 확대', refs='FR02 / NFR04 / AT09·10',
 entry='SC04 이미지 탭 또는 SC07 대표 사진 확대 버튼. 입력 card_id와 복귀 주소.',
 rows=[
 ('01 상단 고정','Close · Sign photo','×와 OS Back 모두 진입 화면으로 복귀. 하단 탭 없음.'),
 ('02 전체 영역','확대 가능한 원본 이미지','초기 fit-contain. 핀치와 두 번 탭으로 1~4배 확대, 확대 중 드래그로 이미지 이동. 학습 스와이프 인식 없음.'),
 ('03 하단 설명','한국어 원문 · Photo credit','저작권 출처와 원문. 불필요한 한국어 번역은 추가하지 않음.'),
 ('04 보조 버튼','Reset zoom','확대 상태에서 활성, 초기 배율로 복귀. 스크린리더에는 텍스트 원문·사진 설명 제공.')],
 states=[('로딩','다운로드 중 spinner; Close는 항상 활성.'),('기본/확대','이미지와 텍스트 유지. 검은 여백은 원본 손실 없이 허용.'),('실패','Couldn’t load photo · Retry · Close.')],
 exit='학습·저장·세션 상태 불변. 확대 배율은 닫을 때 초기화.'),
dict(id='SC06', title='학습 세션 결과', refs='FR06·08·21 / AT02·03·13',
 entry='SC04 10번째 판정 또는 큐 소진. 수동 End session도 완료 원인을 구별하여 이 화면 표시.',
 rows=[
 ('01 상단','Practice summary','출처명과 Session complete 또는 Session ended. 하루 일정 학습이면 날짜 병기.'),
 ('02 본문','판정 횟수와 고유 카드 수','I know decisions, Learn again decisions, Unique signs studied를 숫자로 표시. 자기평가 안내 1문장.'),
 ('03 복습 요약','Signs to review x','현재 전역 learning 중 해당 출처에 속한 고유 카드 수. 판정 횟수와 구별.'),
 ('04 주요 버튼','Review again · Practice all','Review again은 x>0일 때 새 Review 세션. Practice all은 해당 출처 활성 카드가 있으면 새 All 세션.'),
 ('05 보조 버튼','Undo last decision · Done','Undo는 마지막 확정 판정 존재 시 SC04 복구. Done은 시작 화면 SC03/SC07/SC13. 복귀 주소 사라지면 해당 루트.')],
 states=[('완료','큐 소진 여부와 관계없이 실제 판정 합계 표시.'),('0회 종료','No signs studied yet. Review again은 learning 후보가 있는 경우만 활성.'),('출처 삭제','Source unavailable. 새 세션 버튼 비활성, Done은 Explore 또는 Plan.')],
 exit='새 세션 시작 또는 Done 뒤 이전 세션 Undo 불가. 결과 화면을 닫아도 카드별 상태 유지.'),
dict(id='SC07', title='간판 장소와 카드 상세', refs='FR02·09·12·13·18 / AT04·06·07·11',
 entry='SC09 간판 행, SC03 장소 행, SC13 가게 일정 행, SC14 장소 정보 버튼. 입력 place_id와 선택 card_id.',
 rows=[
 ('01 상단 고정','Back · Place name','실제 가게 영어 이름·한국어 이름. 주소가 일치하는 대상임을 본문 표시. 하단 탭 숨김.'),
 ('02 본문 스크롤','간판 사진 · 원문 · 영어 뜻','탐색용 상세에서는 영어 뜻 공개. 사진 확대 SC05. 이 화면에서 좌우 학습 스와이프 없음.'),
 ('03 카드 선택','Signs at this place','같은 가게에 여러 카드가 있으면 원문 칩 또는 목록. 선택 시 card_id·사진·뜻 교체. 1개이면 선택기 숨김.'),
 ('04 저장 버튼','Save sign / Saved','현재 card_id만 북마크. 저장 해제는 다른 카드나 일정에 영향 없음.'),
 ('05 장소 동작','Reviews · Map · Add to plan','SC08·SC18 single·SC15 이동. 목적지는 현재 place_id, 카드가 달라도 같은 가게임.'),
 ('06 하단 고정','Practice this sign','현재 카드 1개를 초기 큐로 새 세션. 기본 All. 결과와 상태는 전역 공유. 다른 세션 있으면 C01.')],
 states=[('기본','유효한 사진·한국어·영어 뜻·주소 표시.'),('카드 0개','No study cards. 카드 선택·사진·뜻 영역 숨김, Practice·Save 비활성. 유효 장소의 Reviews·Map·Add to plan은 활성.'),('장소 연결 없음','Place unavailable. 장소 동작 비활성, 유효 카드 학습과 해제는 가능.'),('카드 비활성','Sign unavailable. 학습/사진/저장 추가 금지. 이미 저장했다면 Remove saved sign만 제공.'),('오프라인','캐시 상세 표시, 리뷰/경로 진입 시 각 화면 offline 처리.')],
 exit='Back은 호출 화면의 선택 행·스크롤 유지. 리뷰·지도에서 복귀해도 선택 카드 유지.'),
dict(id='SC08', title='영어 장소 리뷰', refs='FR09~11 / AT04·05',
 entry='SC04 아래 스와이프/Reviews 또는 SC07 Reviews. 입력 place_id. 랜드마크 전체 리뷰로 대체하지 않음.',
 rows=[
 ('01 상단 고정','Close · Reviews','전체 화면. ×/OS Back은 진입 화면. Learn 세션을 잠그지 않고 보관.'),
 ('02 장소 헤더','장소 영어 이름 · 주소','현재 가게 이름과 주소, 공급자 표시. 평점·리뷰 수는 공급자가 제공한 경우에만 표시.'),
 ('03 목록 안내','Selected reviews from [provider]','제공받은 일부 리뷰임을 표시. 정렬·개수는 공급자 응답 기준, 최신순·전체보기 UI 만들지 않음.'),
 ('04 리뷰 목록 스크롤','Review row','작성자, 제공 시각, 평점, 영어 텍스트. 누락 시각/평점은 해당 항목 숨김. 긴 글 4줄 후 Read more/Show less.'),
 ('05 번역·원문','Translated · Show original','영어 번역일 때만 Translated, Show original 클릭 시 같은 행 아래 원문 펼침. 원문 없음이면 원문 링크로 제공.'),
 ('06 출처 동작','View on [provider]','허용된 원문 URL 외부 열기. 앱 복귀 시 목록 스크롤·펼침 상태 보존. 출처 표시를 목록과 분리해 지우지 않음.')],
 states=[('로딩','장소 헤더 먼저 표시하고 리뷰 skeleton.'),('0건','No reviews yet · 허용된 원문 장소 링크만 표시.'),('영어 없음','English reviews unavailable · View original reviews. 임의 번역 생성 없음.'),('오프라인/실패','Reviews need an internet connection 또는 Couldn’t load reviews · Retry. Close 항상 활성.'),('장소 식별 실패','Place unavailable. 원래 카드 유지, 다른 장소 리뷰 자동 선택 없음.')],
 exit='닫기는 동일 카드·뜻·큐 또는 동일 장소 상세로 복귀. 리뷰 목록 스와이프는 학습 판정 없음.'),
dict(id='SC09', title='저장한 간판', refs='FR12·18 / AT06·11',
 entry='Saved 하단 탭. 학습에서 Bookmark 직후에도 사용자는 직접 이 탭으로 이동한다.',
 rows=[
 ('01 상단 고정','Saved · Settings','전체 저장 카드 수. Settings→SC10.'),
 ('02 필터','All landmarks · All states / New / Learn again / Known','랜드마크 필터와 상태 필터는 AND. 기본 All landmarks·All states. unavailable 카드는 전체 상태 목록에 유지.'),
 ('03 목록 스크롤','Saved sign row','사진, 한국어 단어, 영어 뜻, 가게·랜드마크명, 학습 상태. 최신 저장순, 같은 시각은 card_id순. 행 선택 SC07.'),
 ('04 행 보조 버튼','Unsave · Add to plan','Unsave는 저장 해제 후 10초 Undo 알림. 스크린리더 사용 중에는 닫기 전까지 Undo 유지. Add to plan은 place_id로 SC15. 행 클릭과 별도 영역.'),
 ('05 하단 고정','Explore · Saved · Plan','Saved 활성. 필터·스크롤은 탭 전환에 보존.')],
 states=[('저장 0개','Save signs you want to revisit · Explore signs→SC02.'),('필터 0건','No saved signs match · Clear filters는 All landmarks·All states로 초기화.'),('콘텐츠 삭제','Unavailable 행. Unsave만 활성. 상태별 필터에서는 제외, 전체 필터에서는 보임.'),('오프라인','로컬 목록 정상. 캐시 없는 사진은 대체 썸네일, 원문과 해제는 사용 가능.')],
 exit='상세·일정 추가 시 복귀 Saved 필터/스크롤 유지. 북마크 해제는 학습·여행 일정 불변.'),
dict(id='SC10', title='설정과 기록 관리', refs='FR16 / NFR05 / AT09·10',
 entry='SC02·SC09·SC11 루트 상단 Settings. 하단 탭 숨김.',
 rows=[
 ('01 상단 고정','Back · Settings','복귀 루트 저장. 프로필·로그인·언어 변경 UI 없음.'),
 ('02 도움말','Gesture guide','SC01 재진입 모드. 언어 English는 읽기 전용 표시.'),
 ('03 위치 상태','Location access','Allowed / Not allowed / Not asked를 표시. Open device settings로 OS 설정 이동. 이 행 클릭만으로 위치 수집 안 함.'),
 ('04 데이터','Reset learning progress','SC20 C06 확인. known·learning·세션만 초기화, 저장 간판·여행 계획 유지.'),
 ('05 데이터','Clear saved signs','SC20 C07 확인. 북마크만 삭제, 학습·일정 유지.'),
 ('06 안내','Data on this device · Privacy · Credits','기기 내 기록·재설치 복구 제한 설명. Privacy와 Credits는 이 화면 안 펼침 텍스트. 위치 전송·외부 출처 조건과 이미지 출처를 제공.'),
 ('07 하단 정보','Version 1.0','앱 버전은 앱 빌드 값이며 본 문서 버전 1.1과 구분. 계정 설정·결제 버튼 없음.')],
 states=[('기본','실제 OS 권한 상태 표시, 복귀 시 다시 조회.'),('삭제 처리 중','해당 파괴 동작 비활성, 완료 알림.'),('저장 실패','Couldn’t reset data. Try again. 기존 데이터 유지, 성공 알림 없음.')],
 exit='Back은 호출 루트. 여행 삭제는 Plan 상세 메뉴에서 개별 여행 단위로 처리.'),
]

SCREENS += [
dict(id='SC11', title='여행 계획 목록', refs='FR17·22 / AT11·14',
 entry='Plan 하단 탭. 로그인 없이 기기 내 여러 여행 목록 제공.',
 rows=[
 ('01 상단 고정','Plan · Settings','Settings→SC10. 여행 총수 표시.'),
 ('02 생성 버튼','Create trip','SC12 create 모드. 상단 우측 + 아이콘에도 같은 이름 제공.'),
 ('03 목록 스크롤','Trip row','여행 제목, 기간, 일수, 등록 장소 수. 시작일 내림차순·id 보조정렬. 날짜가 지나도 자동 삭제 없음. 선택 SC13 마지막 선택 날짜.'),
 ('04 보관 안내','Saved on this device','재설치·기기 변경 복구 미지원 안내. 원격 저장 아이콘을 넣지 않음.'),
 ('05 하단 고정','Explore · Saved · Plan','Plan 활성. 목록 스크롤은 탭 이동 후 유지.')],
 states=[('여행 0개','Plan your days in Korea · Create trip.'),('기본','로컬 여행 목록 즉시 표시. 과거 여행도 편집 가능.'),('오프라인','여행 생성·열기·편집 가능. 이동 시간은 재조회 필요 상태로 구분.'),('읽기 오류','Couldn’t load your trips · Retry. 빈 목록으로 조용히 대체하지 않음.')],
 exit='SC13 Back은 Plan으로 복귀. OS Back은 루트에서 앱 종료.'),
dict(id='SC12', title='여행 생성과 정보 편집', refs='FR17·19·22 / AT11·14',
 entry='SC11 Create trip, SC15 Create trip, SC13 메뉴 Edit trip. create/edit와 호출 주소 저장.',
 rows=[
 ('01 상단 고정','Cancel · Create trip / Edit trip','Back·Cancel은 변경 없으면 복귀, 변경 있으면 SC20 C03. 여행 삭제는 이 폼에 넣지 않음.'),
 ('02 입력','Trip name','필수 trim 1~40자. 기본 빈 값, 힌트 My Seoul trip. 초과 입력은 막고 문자 수 n/40 표시.'),
 ('03 날짜','Start date · End date','OS 달력. 생성 기본 시작=오늘 한국 날짜, 종료=같은 날. 과거 날짜도 허용. 양끝 포함 1~14일.'),
 ('04 설명','Korea time (GMT+9) · Up to 14 days','시간대 변경 선택기 없음. 여행 날짜 변경은 동일한 달력 날짜의 일정 유지.'),
 ('05 하단 고정','Create trip / Save changes','필드 유효·저장 중 아님일 때 활성. create 성공→SC13 첫 날짜, SC15에서 호출이면 새 여행 선택한 시트로 복귀. edit→SC13.'),
 ('06 기간 변경 확인','일정이 범위 밖인 날짜 목록','edit에서 범위 축소 시 날짜·장소 수를 C05에 제시. 삭제 확인 전 저장 금지; 취소 후 범위 밖 날짜를 사용자가 기존 일정에서 옮길 수 있음.')],
 states=[('빈 이름','Enter a trip name. Save 비활성.'),('날짜 역전/초과','End date must be on or after start date. / Trips can be up to 14 days. 필드 아래 표시.'),('변경 없음','edit에서는 Save 비활성. Cancel 즉시 복귀.'),('저장 중/실패','버튼 중복 차단. 실패면 폼 입력 유지와 Retry. 생성 idempotency 키로 재시도 중복 여행 방지.')],
 exit='기간 변경은 기존 달력 날짜와 매칭한다. 새로운 날짜에는 빈 TripDay 생성. 범위 밖 일정은 C05 확인한 경우만 삭제.'),
dict(id='SC13', title='날짜별 여행 계획', refs='FR18~22 / AT11~14',
 entry='SC11 여행 선택 또는 SC12 여행 생성. trip_id와 선택 day_id를 사용. 최초는 시작일, 재진입은 마지막 선택 날짜.',
 rows=[
 ('01 상단 고정','Back · Trip name · More','More는 Edit trip→SC12, Delete trip→C04. 전체 삭제는 해당 여행만.'),
 ('02 날짜 선택','Day 1 · Wed, Oct 7','가로 스크롤 날짜 칩. 선택 날짜 강조. 전환은 그 날짜 일정·출발 설정·경로 상태 표시.'),
 ('03 출발 요약','09:00 · Start at first stop · Edit','Edit→SC17. 좌표 출발지가 지정되면 이름 표시. Korea time 공통 라벨.'),
 ('04 일정 목록 스크롤','번호 · 장소 · 도착/출발 · 체류','행당 장소 영어/한국어, Arrival·Leave·Stay n min, 학습 카드 수. 장소 이름 탭은 SC03 또는 SC07. ⋮→SC16. 이동 구간 사이 Walking n min 표시.'),
 ('05 순서 편집','Reorder','눌러 편집모드. 드래그 핸들+Up/Down 제공, 끝 행 방향 비활성. Done은 모드 종료. 각 이동 저장 성공 후 반영하고 영향 구간 재계산.'),
 ('06 목록 추가','Add place','SC14로 현재 day_id 전달. 날짜당 10개이면 비활성 · Maximum 10 places per day.'),
 ('07 하루 요약','Travel · Stay · Total · Finish','모든 구간 유효할 때 계산. 첫 장소 출발이면 First-stop travel not included 표시. 일부 실패는 Route incomplete와 — 표시.'),
 ('08 하단 고정','View day route · Study this day','장소≥1이고 좌표 유효하면 SC18 day. 카드≥1이면 모드 시트 All cards / Review 후 SC04. Review는 learning0이면 비활성. 순서편집 중 두 버튼 비활성.')],
 states=[('날짜 0곳','No places planned for this day · Add place. 09:00 설정 표시, 지도·학습 비활성.'),('경로 계산 중','영향 구간 spinner, 해당 이후 시각—. 이전 합계는 현재 결과처럼 표시하지 않음.'),('부분 실패/오프라인','구간 Route unavailable · Retry route. 전체 시간 미완료. 일정 편집·학습은 가능.'),('목적지 무효','Unavailable stop. 지도·시각 계산 중단, ⋮ 삭제 허용, 임의 장소 대체 없음.'),('저장 실패','기존 순서 유지. Couldn’t save changes. Try again. 편집모드 유지.')],
 exit='날짜·스크롤·선택 여행 보존. Back→SC11. 새로운 카드 학습은 C01로 미완료 세션 교체 확인.'),
dict(id='SC14', title='일정에 넣을 등록 장소 선택', refs='FR18·19 / AT11',
 entry='SC13 Add place. trip_id·day_id 전달. 외부 숙소·임의 장소 목적지 검색은 제공하지 않음.',
 rows=[
 ('01 상단 고정','Back · Add place · 날짜','현재 여행/날짜를 함께 표시. 날짜 변경은 이 화면에서 하지 않음.'),
 ('02 범주 필터','Landmarks · Sign places · Saved','기본 Landmarks. Sign places는 등록 가게. Saved는 북마크 카드의 유효한 place_id를 묶어 장소 단위 표시.'),
 ('03 검색','Search registered places','현재 범주 영어·한국어 이름 부분 일치, 300ms 로컬 필터. 외부 공급자 검색 없음.'),
 ('04 목록 스크롤','Place row · Add','썸네일, 장소명, 소속 랜드마크, 연결 카드 수. 정보 아이콘→SC03/SC07. Add는 이 날짜에 즉시30분 체류로 추가하고 SC13 복귀.'),
 ('05 중복 표시','Already added','동일 destination_type+destination_id가 당일 있으면 Add 비활성. 랜드마크와 그 주변 가게는 다른 목적지이며 둘 다 등록 가능.'),
 ('06 상한 안내','Maximum 10 places per day','호출 이후에도 실저장 직전 개수·중복 재검증. 상한이면 추가 불가.')],
 states=[('검색 0건','No registered places found · Clear search.'),('Saved 0건','No saved places · Landmarks 탭 또는 Back.'),('오프라인','내려받은 등록 목록 사용. 신규 콘텐츠 없음 안내.'),('저장 실패','추가 목록 유지, 실패 알림. 재시도 시 중복검사로 동일 방문점 두 개 생성 금지.')],
 exit='추가 성공 SC13 해당 날짜·추가 행으로 스크롤. Back은 변경 없이 SC13. 정보 화면 닫기는 동일 필터로 복귀.'),
dict(id='SC15', title='관심 장소를 여행에 추가', refs='FR18 / AT11',
 entry='SC03/SC04/SC07/SC09 Add to plan. 대상 destination_type·id와 호출 주소. 아래에서 올라오는 시트.',
 rows=[
 ('01 시트 헤더','Add to plan · Close','추가할 랜드마크/가게 이름과 주소. 사진 카드가 여러개여도 대상은 같은 가게.'),
 ('02 여행 선택','Choose trip','기존 여행 리스트. 마지막 사용 여행 기본, 없으면 미선택. 여행 변경 시 날짜는 해당 여행 시작일로 초기화.'),
 ('03 날짜 선택','Choose day','선택 여행 날짜 칩. 날짜·방문 장소 수 표시. 기본 여행 첫 날짜. 다른 여행 날짜를 참조할 수 없음.'),
 ('04 신규 생성','Create trip','SC12 create. 생성 후 시트 복귀해 새 여행과 첫 날짜 선택. 취소 후 원래 시트 유지.'),
 ('05 확인 버튼','Add place','여행·날짜 선택 및 중복/상한 통과 시 활성. 성공 시 시트 닫고 Added to [trip], [date] 알림 · View day→SC13.'),
 ('06 부가 안내','Stay: 30 min','기본 체류30분, 상세 편집은 SC16. 북마크와 독립적이므로 저장 없이도 일정 추가 가능.')],
 states=[('여행 없음','No trips yet · Create trip. Add place 비활성.'),('당일 중복','Already in this day · View day. Add 비활성.'),('날짜 상한','This day already has 10 places. 다른 날짜 선택 허용.'),('처리 중/실패','저장 중 닫기/추가 비활성. 실패는 선택 유지, 성공 전 호출 화면 그대로.')],
 exit='취소·시트 바깥·OS Back은 추가 없이 원래 화면. 학습에서 열었으면 현재 카드와 뜻 상태 유지. View day는 학습을 보관하고 이동.'),
dict(id='SC16', title='방문 장소 편집', refs='FR19·22 / AT11·14',
 entry='SC13 일정 행 ⋮. trip_id·day_id·stop_id 전달. 입력 변경은 적용 전 임시값.',
 rows=[
 ('01 헤더','Edit stop · Close','장소 이름과 현재 날짜. 바깥 탭/Back은 변경 있으면 C03.'),
 ('02 입력','Stay minutes','숫자 키보드, 필수 정수0~480. 현재값 기본. 단위min 고정. 0은 도착 후 즉시 다음 이동.'),
 ('03 날짜 선택','Move to day','같은 여행 날짜 목록. 현재 날짜 기본. 이동 날짜의 같은 목적지 중복/10곳 여부를 표시.'),
 ('04 설명','Place order','같은 날짜 유지면 순서 유지. 다른 날짜로 이동은 대상 날짜 마지막에 추가. 순서 조정은 SC13 Reorder.'),
 ('05 파괴 동작','Remove from day','C08 확인. 해당 stop만 제거, 북마크·학습 상태·다른 날짜 방문점 유지.'),
 ('06 확인','Apply changes','값 유효·변경 있음·이동 제한 통과 시 활성. 날짜 이동과 체류 저장은 한 트랜잭션. 성공시 이동 날짜의 SC13으로 복귀.')],
 states=[('범위 오류','Enter a whole number from 0 to 480. Apply 비활성.'),('중복/상한','Place already added to this day. / Maximum 10 places per day. 다른 날짜 선택 가능.'),('저장 실패','원래 stop 유지, 임시값 보존, 재시도. 성공 전 날짜나 순서 이동 없음.'),('무효 목적지','Unavailable stop. 체류·이동·Apply 금지, Remove 활성.')],
 exit='Apply 후 영향받는 출발 날짜·도착 날짜 경로 버전 증가. Remove 후 원래 날짜로 복귀, 목록 번호 재정렬.'),
dict(id='SC17', title='하루 출발 설정', refs='FR14·19·20 / AT08·12',
 entry='SC13 출발 요약 Edit. day_id·기존start_time·origin 전달. 하단 시트.',
 rows=[
 ('01 헤더','Day start · Close','현재 날짜, Korea time (GMT+9). 변경 시 C03 적용.'),
 ('02 시간','Start time','OS24시간 picker. 00:00~23:59, 기본09:00. 시작시각은 첫 구간 출발 또는 첫 장소 도착 기준.'),
 ('03 출발 방식','Start at first stop / Choose a start point','첫 장소 출발 기본. 선택 좌표 있으면 이름/주소와 Change→SC19. 첫 장소 출발은 origin=null.'),
 ('04 위치 선택','Use current location','SC20 C09→OS권한 후 SC19의 선택확정 흐름. 새 위치 자동 수집은 클릭시 1회. 실제 좌표/시각 스냅샷 저장.'),
 ('05 설명','Travel to the first stop is not included','첫 장소 출발일 때만 노출. 좌표 지정이면 첫 구간 포함. 수단 Walking 읽기 전용.'),
 ('06 확인','Apply','유효 변경 있음·좌표 선택 완료 때 활성. 성공 시 SC13에 시간/출발지 저장, 경로·시각 재계산.')],
 states=[('처음/비어있는 하루','09:00·첫장소 출발. 장소 없어도 설정 저장 가능.'),('위치 요청 중/실패','Getting location… 10초 제한. 실패 Location unavailable · Choose a start point. 가짜 좌표 지정 안 함.'),('해외 좌표','출시 국내경로 범위 밖이면 Choose a start point in Korea. Apply 비활성.'),('저장 실패','이전 설정 유지. 임시값과 재시도 제공.')],
 exit='SC19에서 선택한 값은 시트 임시값이며 Apply 전 저장하지 않음. 취소는 원래 출발 설정 보존.'),
dict(id='SC18', title='단일 경로와 하루 경로 지도', refs='FR13~15·20 / AT07·08·12',
 entry='single: SC03 랜드마크/SC04·SC07 가게 Map. day: SC13 View day route. 호출 모드는 고정이며 모드 전환 탭은 넣지 않음.',
 rows=[
 ('01 상단 고정','Back · Route / Day route','single은 실제 목적지명. day는 여행명·날짜. 하단 탭 없음.'),
 ('02 출발·도착','From · To · Walking','single From 기본=소속 랜드마크, 목적지가 랜드마크이면 미선택. single From 탭→SC19, To 읽기 전용. day는 저장된 출발·번호순 목적지, From 탭→SC13→SC17.'),
 ('03 지도 영역','출발/목적 마커 · 경로선','single 한 구간 경로, day 연속 구간과1~n 번호마커. 초기fit bounds. 핀치·드래그는 지도 조작, 학습 판정 없음. 전체 경로 보기 버튼 제공.'),
 ('04 출처','Map data / Routing attribution','공급자 필수표기와 계산 시각. 영어 지도 라벨은 실제 지원 검증 후 반영, 한국어 장소명은 보존 가능.'),
 ('05 하단 패널','Distance · Walking time · Updated','single 실제 경로 거리/예상시간. day Travel/Stay/Total/Finish와 구간 목록. 구간 행 탭은 그 경로선 강조.'),
 ('06 경로 동작','Refresh route · Open external map','재조회는 현재 좌표·최신일정 기준. 외부지도 single 목적지, day는 선택 구간 전달. 하나의 외부링크로 전체 일정 전송 보장 안 함.'),
 ('07 day 안내','Edit day start / Edit plan','출발 설정 수정은 SC13→SC17로 이동. 지도에서 목적지 순서를 직접 변경하지 않음.')],
 states=[('출발 미선택','Choose a start point. 목적지 마커만, 시간 —, SC19 버튼 활성.'),('출발=도착','같은 좌표는 0m·0min, 경로 호출 생략. 첫 장소 출발·1곳 일정은 이동 0과 체류 시간만 합계.'),('계산 중/실패','spinner 후 Route unavailable · Retry. 실패 구간은 —, 이후 시각·전체 합계 미완료.'),('오프라인/오래된 값','허용 캐시이며 같은 좌표·revision이면 Last calculated… 표시. 변경됐으면 값 —와 재조회 필요 안내.'),('지도 타일 실패','Couldn’t load map. 텍스트 결과는 유지, 지도 Retry. 외부 지도 대안과 앱 내부 P0 충족을 구별.')],
 exit='Back은 호출자와 세션 보존. single 임시 출발 설정은 현재 지도 동안만 유지, 여행 설정을 변경하지 않음. day 결과는 최신revision일 때만 일정에 반영.'),
dict(id='SC19', title='출발지 검색과 선택', refs='FR14 / AT08',
 entry='SC17 Choose a start point 또는 SC18 single From. 선택좌표를 호출 화면의 임시값으로 반환.',
 rows=[
 ('01 상단 고정','Back · Choose a start point','검색 목적은 출발지 선택뿐. 방문 목적지 Add to plan 버튼 없음.'),
 ('02 현재위치','Use current location','C09→OS권한, 성공한 국내 좌표를 확인 후보로 표시. 요청마다 새 위치1회. 위치 제공 시각 표시.'),
 ('03 등록 목록','Registered landmarks and places','기본 등록 랜드마크·가게 목록, 로컬 영어/한국어 이름 검색. 오프라인에서도 선택 가능.'),
 ('04 외부 검색','Search a start point in Korea','검색어2자 이상 Search 명시적 제출. 승인 공급자의 국내 위치 검색 결과만, 이름·주소·좌표 제공. 계정/지도 공급자에 따라 호출비 제한.'),
 ('05 선택 확인','Selected start point · Use this point','결과 탭은 이름·주소·지도마커 미리보기. 국내 유효좌표가 있는 경우만 Use 활성. 단순 텍스트로 좌표 없는 결과 반환 금지.'),
 ('06 예외안내','Choose a registered place instead','외부검색 미지원/오프라인/0건이면 등록목록으로 돌아갈 방법 제공. 임의숙소 등록 UI 없음.')],
 states=[('기본','등록 목록과 현재위치. 최근 위치 자동조회 안 함.'),('검색짧음/0건','Enter at least 2 characters. / No start points found.'),('오프라인/검색실패','Search needs an internet connection. / Couldn’t search. Retry. 등록목록 유지.'),('권한거부/영구거부','Location access is off. 등록지점 선택과 Open device settings 제공.'),('좌표범위오류','This start point is outside the supported area. Use 비활성.')],
 exit='Use는 호출 SC17 임시출발값 또는 SC18 단일출발값에 반환. Back은 변경 없이 복귀. 목적지 콘텐츠 DB에는 새 장소를 저장하지 않음.'),
dict(id='SC20', title='공통 확인과 권한 다이얼로그', refs='FR06·07·16~19 / AT03·08·09·11',
 entry='각 화면이 조건 충족 시 여는 모달. C코드는 인스턴스 종류이며 독립 네비게이션 화면 아님.',
 rows=[
 ('C01 세션 교체','Start new practice?','You have an unfinished session. 기존 판정 유지, 미완료 큐만 종료. Start new / Cancel. Start new는 새 출처세션 생성, Cancel은 호출자 유지.'),
 ('C02 학습 닫기','Leave practice?','Keep for later / End session / Cancel. 보관→출처 상세+Resume 가능, 종료→SC06. 둘 다 이미 확정된 판정 유지.'),
 ('C03 폼 이탈','Discard changes?','Discard / Keep editing. 폼변경 폐기 또는 유지. destructive 바깥탭/Back은 Keep editing과 동일. 자동저장순서 편집에는 적용 안 함.'),
 ('C04 여행 삭제','Delete [trip name]?','This deletes this trip and its days. Saved signs and learning progress stay. Delete trip / Cancel. 성공SC11.'),
 ('C05 기간 축소','Remove days outside the new dates?','범위밖 날짜·각 장소수 목록. Delete listed days and save / Keep editing. 이동을 원하면 취소해 SC13에서 먼저 옮기도록 안내. 확인 전 삭제 없음.'),
 ('C06 학습 초기화','Reset learning progress?','학습상태·진행 세션 삭제, 북마크·여행 유지. Reset / Cancel. 성공SC10, Resume배너 제거.'),
 ('C07 북마크 초기화','Clear saved signs?','북마크만 삭제. Clear / Cancel. 학습·여행계획 유지.'),
 ('C08 방문점 삭제','Remove [place] from this day?','이 날짜 방문점만 삭제. Remove / Cancel. 성공SC13 해당날짜, 영향구간 재계산.'),
 ('C09 위치 설명','Use your location?','현재 위치를 선택하고 권한 요청이 필요할 때만 설명. 경로 출발점에 1회 사용, 계획에 Apply하면 기기 내 저장. Continue→OS 권한, Not now→수동 선택. 이미 허용이면 재확인 없이 조회.')],
 states=[('기본','모달 focus 고정, 배경 터치 차단. 위험 동작은 텍스트로 대상 명시.'),('처리 중','동일 확정 중복 차단. 저장 트랜잭션이 끝날 때까지 Cancel 비활성.'),('실패','모달 유지, Retry/Cancel. 일부 삭제를 전체 성공으로 표시하지 않음.'),('OS 권한 후','허용=조회, 거부=수동 출발, 영구 거부=설정 링크. 위치 서비스 꺼짐은 권한 거부와 구별.')],
 exit='성공·취소 후 호출 화면으로 돌아간다. 저장·삭제 성공한 경우에만 목록을 갱신한다.'),
]

def append_screens(doc, page, h, p, table):
    append_common(doc,page,h,p,table)
    for index, screen in enumerate(SCREENS, start=15):
        page(f'{index} {screen["id"]} {screen["title"]}')
        p('연결 요구사항 및 검수  '+screen['refs'])
        h('진입과 목적')
        p(screen['entry'])
        h('화면 위에서 아래로 배치')
        table(['영역','영어 표기와 데이터','동작과 표시 조건'],screen['rows'])
        h('상태별 화면')
        table(['상태','표시와 처리'],screen['states'])
        h('복귀와 보존')
        p(screen['exit'])
    append_checks(doc,page,h,p,table)

def append_common(doc,page,h,p,table):
    page('12 화면 설계를 위한 제품 결정')
    p('버전 1.1은 앞선 제품 요구사항을 화면 단위로 구체화한다. 화면 제작자는 SC01~SC20과 공통 상태·입력 규칙을 기준으로 정상·오류·빈 상태를 설계한다. 사용자 확정 조건은 유지하고, 아래 UI 선택은 합리적 설계 결정으로 명시한다. 충돌 시 버전 1.1의 상세 규칙이 앞선 요약보다 우선한다.')
    table(['항목','확정된 기준 또는 설계 결정'],[
      ('여행 장소 사용자 확정','여행 일정의 방문 목적지는 앱에 등록된 간판 장소와 랜드마크만 허용한다. 숙소 등 외부 검색 장소를 목적지로 추가하는 버튼은 없다. 출발지는 별도이며 현재 위치·등록 장소·수동 검색 좌표를 사용할 수 있다.'),
      ('탐색 구조 설계 결정','하단 탭은 Explore / Saved / Plan 3개. 탐색·저장·여행 루트에만 표시. 상세·학습·리뷰·지도·설정은 탭 숨김. 초기 홈 Explore, 위치나 여행 날짜에 따라 홈을 자동 바꾸지 않음.'),
      ('계정과 여행 설계 결정','로그인 없이 여러 여행을 기기에 저장. 여행 1개 최대 14일, 날짜당 방문 장소 10개. 사용자 등록 장소·사용자 리뷰·예약은 초기 범위 제외.'),
      ('범위와 수단 설계 결정','처음부터 도보 경로만 UI 제공. 대중교통은 검증 전 버튼 숨김. AI 일정 생성·순서 최적화·방문 완료 체크는 제공하지 않음. 장소 순서는 사용자가 편집.'),
      ('리뷰와 학습 설계 결정','리뷰는 해당 가게 영어 방문 리뷰, 전체 화면. 카드별 사진·한국어·영어 뜻 제공. 탐색 상세는 뜻 공개, Learn만 뜻 초기 숨김. 저장 카드 선택 자체는 학습 판정이 아님.'),
      ('UI 제작 기준','Android 세로 390×844dp 기준안, 실제 360~430dp 대응. Safe area는 OS 값. 최소 터치48dp, 좌우16dp 여백, 요소 간8/16/24dp. 헤더56dp·하단탭64dp 기준. 키보드/확대 시 본문 스크롤 허용.')])
    h('화면 설계 산출물 기준')
    p('SC 화면 ID, 요소 순서, 영어 문구, 데이터 바인딩, 버튼 조건, 이동 대상, 상태별 변형을 그대로 화면 설계서에 옮길 수 있어야 한다. 색상·아이콘·일러스트는 디자이너가 결정하되 방향 의미와 버튼 라벨은 유지한다. 각 화면은 최소 기본 상태와 해당 상태표의 예외 상태를 함께 제작한다.')

    page('13 화면 목록과 연결 관계')
    table(['화면 ID','화면 및 타입','주요 진입과 이동'],[
      ('SC01','첫 실행 안내 · 전체 화면','처음 실행→SC01→SC02. SC10에서 재진입.'),
      ('SC02','Explore · 루트 탭','SC03 랜드마크, SC04 재개, SC10 설정.'),
      ('SC03','랜드마크 상세 · 전체 화면','SC04 학습, SC07 간판 장소, SC15 일정 추가, SC18 지도.'),
      ('SC04','Learn · 집중 화면','SC05 사진, SC08 리뷰, SC15 추가, SC18 지도, SC06 결과.'),
      ('SC05','사진 확대 · 전체 화면','SC04/SC07→확대→진입 화면.'),
      ('SC06','학습 결과 · 전체 화면','SC04 Undo/새 세션, 또는 출처 상세 복귀.'),
      ('SC07','가게 및 카드 상세 · 전체 화면','SC04 개별 학습, SC05·08·15·18 보조.'),
      ('SC08','영어 리뷰 · 전체 화면','SC04/SC07→리뷰→원래 화면.'),
      ('SC09','Saved · 루트 탭','SC07 상세, SC15 일정 추가, SC10 설정.'),
      ('SC10','Settings · 전체 화면','SC01 안내, SC20 초기화 확인, 원래 루트 복귀.'),
      ('SC11','Plan 여행 목록 · 루트 탭','SC12 여행 생성, SC13 여행 상세, SC10 설정.'),
      ('SC12','여행 생성 및 정보 편집 · 폼','저장→SC13, 취소→진입 화면.'),
      ('SC13','날짜별 여행 계획 · 전체 화면','SC14 추가, SC16 장소 편집, SC17 출발 설정, SC18 지도, SC04 일정 학습.'),
      ('SC14','일정 장소 선택 · 전체 화면','등록 랜드마크/가게 선택→SC13.'),
      ('SC15','여행과 날짜 선택 · 하단 시트','현재 장소를 선택 여행 날짜에 저장. 없으면 SC12 생성 후 시트 복귀.'),
      ('SC16','방문 장소 편집 · 하단 시트','체류 시간·날짜·순서 조정, 삭제→SC13.'),
      ('SC17','하루 출발 설정 · 하단 시트','출발 시각·출발지 변경. 출발지 선택→SC19.'),
      ('SC18','경로 지도 · 전체 화면','단일 장소 경로 또는 일정 하루 경로. 출발지→SC19.'),
      ('SC19','출발지 검색과 선택 · 전체 화면','등록 장소/현재 위치/좌표 검색→SC17/SC18.'),
      ('SC20','공통 확인과 권한 · 모달 모음','세션 교체·폼 이탈·삭제·위치 권한 등. 별도 탭 아님.')])
    p('상세 화면의 Back은 스택 한 단계 복귀한다. 보조 화면은 호출자와 선택 항목을 함께 저장한다. 루트 탭 전환은 각 루트의 필터·스크롤을 보존한다. 외부 지도/리뷰 페이지 복귀도 원래 화면을 유지한다. 앱 재실행은 SC02에 저장된 세션 Resume 배너를 보여주고 보조 화면을 자동 재개하지 않는다.')

    page('14 공통 화면 상태와 조작 규칙')
    table(['규칙 ID','화면 설계와 구현 기준'],[
      ('UX01 스크롤과 스와이프','학습 판정 스와이프는 SC04 사진 영역에서 시작한 터치에만 적용한다. 가로 이동≥사진 너비25%, 가로≥세로1.3배면 좌우 판정. 아래 이동≥사진 높이20%, 세로≥가로1.3배면 리뷰. 버튼·본문·확대사진·리뷰·지도는 제외. 사진 밖 세로 스크롤은 일반 스크롤.'),
      ('UX02 저장 시점','학습 판정·북마크·일정 순서 변경은 저장 성공 뒤 확정. 폼 SC12·16·17은 Save/Apply로 확정. 편집 중 Back·Cancel은 미저장 변경을 폐기할지 C03 확인. 실패 시 기존 데이터 유지하고 같은 화면 재시도.'),
      ('UX03 처리 상태','외부 응답 대기 즉시 skeleton/spinner, 8초 종료 뒤 Retry. 저장 중 같은 동작 버튼 비활성. 화면 복귀·빠른 입력에 중복 생성/중복 판정 금지. Disabled에는 비활성 이유 텍스트 제공.'),
      ('UX04 부분 데이터','값 없음은 — 또는 항목 숨김, 0과 구별. 경로 구간 하나라도 실패하면 전체 시간 미완료. 장소 무효 시 명시적 Unavailable. 이전 일정 버전 응답을 새 일정 결과로 표시하지 않음.'),
      ('UX05 기본 카피','실패 Couldn’t save changes. Try again. / 오프라인 You’re offline. / 성공 Saved. / 날짜 표기 Wed, Oct 7 · 시간24시간제09:00 · 거리1000m 미만m, 이상km 소수1자리 · 소요시간1h 20min.'),
      ('UX06 접근성','48dp 터치, 본문16sp 기준, 제목20~24sp. 200% 확대 시 본문 스크롤·고정 버튼 충돌 방지. 사진 영역 판정 외 버튼 대체 동작 보장. 활성/오류/판정은 텍스트와 아이콘 병기.'),
      ('UX07 입력과 선택','저장 전 문자열 trim. 필수 미입력과 유효성은 필드 바로 아래 영어로 표시. 오류 위치로 스크롤하고 스크린리더 알림. 숫자는 정수, 날짜·시간은 OS picker. 취소 가능한 시트 바깥 탭도 Back과 동일.'),
      ('UX08 위치와 외부 링크','현재 위치를 사용자가 선택할 때만 위치 설명→OS 권한. 거부해도 수동 출발 가능. 외부 링크는 허용 URL만, 앱 미설치면 브라우저. 실제 위치를 임의의 기본 좌표로 대체하지 않음.')])
    h('학습 상태와 표시 데이터')
    p('unseen→New, learning→Learn again, known→Known으로 표시한다. 랜드마크 Known x/y는 현재 상태의 고유 카드 수이며 세션 Decisions n/10과 다르다. 초기 카드 큐는 각 대상 카드 1회씩 생성하되 오른쪽 판정은 재출제 규칙으로 뒤에 다시 넣는다. 따라서 1회 판정 수와 고유 카드 수는 다를 수 있다.')
    p('시각은 모두 Asia/Seoul. 초기 사진은 fit-contain이며 잘라낸 썸네일은 목록에만 허용한다. 데이터 미확보 예시는 “Example”로 구분한다. UI 문구의 [name], [date], [provider]는 실제 데이터 자리이며 대괄호를 그대로 제품에 표시하지 않는다.')

def append_checks(doc,page,h,p,table):
    page('35 입력 데이터와 저장 계약')
    table(['입력 또는 표시값','검증과 기본값','화면 연결'],[
      ('trip.title','trim 후 1~40 Unicode 문자. 빈 값·초과 불가. 같은 이름의 여행은 허용하며 id로 구분.','SC12·13'),
      ('trip.start_date / end_date','YYYY-MM-DD, 한국 달력 날짜. 양끝 포함 1~14일, 종료일≥시작일. 과거 날짜 허용. 기본 오늘/오늘.','SC12'),
      ('day.start_time','HH:mm, 00:00~23:59. 기본 09:00. 날짜와 결합해 계산.','SC13·17'),
      ('stop.dwell_minutes','필수 0~480 정수. 기본 30. 빈 값은 0으로 자동 변환하지 않음.','SC16'),
      ('stop.destination','landmark 또는 place 등록 id. 같은 날짜의 type+id 중복 금지, 최대 10곳. 같은 실제 목적지를 두 type으로 중복 등록하지 않음.','SC14·15·16'),
      ('origin','null=첫 장소 출발. 좌표 선택 시 name, address, lat, lng, source, selected_at. 유효 좌표·국내 지원 범위 확인. 출발지 검색은 목적지 등록과 별개.','SC17~19'),
      ('검색','공백 trim·대소문자 구분 없음·한국어/영문 이름 부분 일치. 최대 80자. 외부 출발지 검색은 2자 이상 입력 후 Search.','SC02·14·19'),
      ('표시명과 이미지','영어 이름 우선·한국어 병기. 영문 없으면 한국어 보존. 대표 이미지·설명·주소·credits를 콘텐츠에 포함. 학습 사진 없으면 판정 불가.','SC02~08'),
      ('학습 후보','랜드마크=소속 활성 카드; 개별 카드=선택 card_id; 일정 날짜=방문 순서별 연결 카드 중복 제거. 초기 큐는 sort_order, id 순.','SC03·04·07·13')])
    h('상태별 데이터 묶음')
    p('Session에는 출처 종류·id·이름 스냅샷, 복귀 주소, 카드 목록, 초기 큐, 현재 카드, 뜻 공개 여부, 판정 횟수·기록, 재출제 큐, 마지막 Undo 스냅샷을 저장한다. 순서·뜻 공개·마지막 판정이 함께 복구된다. Resume은 저장된 세션을 열고, 새 시작만 최신 콘텐츠·일정을 사용한다. 카드 1개 세션도 재출제와 10회 종료 규칙을 적용한다.')
    p('Landmark와 Place는 대표 사진·한국어/영어 이름·주소·좌표·설명·활성 여부를 가진다. SignCard는 sort_order·사진 대체 텍스트·출처를 가진다. 카드 수와 상태별 수는 기기 데이터에서 계산하고 0과 누락을 구분한다. TripStop은 id·목적지 type/id·순서·체류 시간을 가진다. 학습 기록 초기화는 진행 세션과 Undo도 삭제한다.')
    p('여행 정보 폼은 Save까지 임시값이고, 날짜별 순서 변경은 매 동작 자동 저장한다. 날짜 이동은 원래 날짜에서 삭제와 대상 날짜에 추가를 원자적으로 처리한다. 기간 축소는 확인된 범위 밖 날짜만 삭제한다. 앱 재시작은 확정된 데이터를 복구하며 미저장 폼의 복구는 보장하지 않는다.')

    page('36 핵심 화면 배치 기준과 예시')
    p('아래는 기본 글자 크기와 390dp 너비에서 영역 순서를 정하기 위한 배치 기준이다. 최종 색상·픽셀 화면은 디자이너가 구체화한다. 높이는 safe area를 제외한 기준이며 작은 화면·글자 확대 시 본문을 스크롤한다. 고정 영역과 본문은 겹치지 않아야 한다.')
    table(['화면','고정 영역','본문 영역과 우선순위'],[
      ('SC02 Explore','헤더 56·하단 탭 64','검색 48 → Resume 배너 조건부 → 랜드마크 목록. 사진 16:9, 이름·카드 수·Known 표시. 세로 목록 1열.'),
      ('SC04 Learn','헤더 56·보조 동작 56','진행 24 → 사진 4:3(342dp 너비이면 높이 257dp) → 한국어 → 뜻 영역 최소 72 → 좌우 판정 56. 사진·뜻·판정이 기본 화면에서 보이도록 우선. 북마크는 사진 우측 상단.'),
      ('SC08 Reviews','헤더 56','장소 헤더 → 일부 리뷰·출처 안내 → 리뷰 목록. 평점·작성자·날짜 뒤 본문. 하단 탭 없음. 원문 링크는 본문 말미.'),
      ('SC13 Day plan','헤더 56·날짜 칩 48·하단 동작 56','출발 요약 → 방문·이동 구간 목록 → Add place → 하루 시간 요약. 본문 세로 스크롤. 번호와 시각을 구별.'),
      ('SC18 Map','헤더 56·출발 요약 72','지도는 남은 공간 사용. 하단 결과 패널은 접힌 높이 144, 펼침은 본문 최대 60%. 손잡이·버튼으로 펼침. 지도 마커·출처를 패널 뒤에 가리지 않음.')])
    h('읽을 수 있는 영어 문구 예시')
    table(['위치','실제 표시 예시','해석과 제한'],[
      ('Learn 헤더','Myeongdong Cathedral · Decisions 3/10','랜드마크명은 학습 출처. 3은 판정 횟수.'),
      ('Learn 원문/뜻','약국 → pharmacy · A place to buy medicine.','Example 표시 시연 문구. 실제 간판·장소 확보를 뜻하지 않음.'),
      ('리뷰 예외','English reviews unavailable · View original reviews','임의 번역 대신 원문 접근 제공.'),
      ('일정 요약','Travel 30min · Stay 1h 10min · Total 1h 40min · Finish 10:40','9시 출발 검수 예시. 실제 지도 데이터와 구별.'),
      ('부분 실패','Route incomplete · Retry failed routes','전체 합계·이후 도착 시각은 미완료. 성공 구간은 유지.')])
    h('반드시 제작할 주요 변형')
    p('Learn은 뜻 숨김·뜻 공개·이미지 실패·저장 실패, Reviews는 정상·0건·영어 없음·통신 실패, Plan은 빈 여행·일정 있음·순서 편집·부분 경로 실패, Map은 정상·출발 미선택·경로 없음·오프라인을 별도 프레임으로 제작한다. 다이얼로그와 폼 오류는 해당 화면 위 오버레이로 표현한다.')

    page('37 요구사항과 화면 추적표')
    table(['기능 요구사항','화면 연결','설계 검수 포인트'],[
      ('FR01','SC02·03','랜드마크 범위·카드 0건·검색'),
      ('FR02','SC03~05·07','간판 원문·뜻 공개·확대'),
      ('FR03·04','SC01·04','좌=앎 우=모름·버튼 대체'),
      ('FR05','SC03·04·06·13','모드 후보·재출제·10회 종료'),
      ('FR06·07','SC02·04·06·20','Undo·종료/보관·재개'),
      ('FR08','SC06','판정 횟수·고유 카드 수 구별'),
      ('FR09~11','SC04·07·08','아래 리뷰·동일 장소·언어/출처 예외'),
      ('FR12','SC04·07·09','저장/해제·기기 내 유지'),
      ('FR13~15','SC03·04·07·17~19','실제 목적지·출발/경로/시간·권한 예외'),
      ('FR16','SC10·20','학습/북마크 별도 초기화'),
      ('FR17','SC11·12·20','여행 생성·기간·삭제'),
      ('FR18','SC03·07·09·14·15','등록 목적지·중복/상한'),
      ('FR19','SC12·13·16·17','날짜/순서/체류·영향 구간'),
      ('FR20','SC13·17·18','시각·총시간·미완료'),
      ('FR21','SC04·06·13','일정 범위 학습·전역 상태'),
      ('FR22','SC11~18','확정 일정 저장·오프라인 편집')])
    h('화면 설계용 확인 시나리오')
    table(['검수 ID','화면 설계에서 확인할 경로'],[
      ('UI01 첫 방문','SC01→SC02→SC03→SC04. 권한 없이 학습 시작, 방향 라벨·뜻 숨김·버튼 대체 확인.'),
      ('UI02 학습 보조','SC04 뜻 공개→SC08→SC04→SC18→SC04. 원래 카드·뜻·큐 유지. 사진 확대에서 판정 없음.'),
      ('UI03 저장과 계획','SC04 북마크→보관 종료→SC09→SC07→SC15→SC12 새 여행→SC15 추가→SC13.'),
      ('UI04 날짜 편집','SC13→SC14 추가→Reorder→SC16 날짜/체류→SC17 출발→SC18. 중복·10개·부분 실패 확인.'),
      ('UI05 일정 학습','SC13 Study this day→All/Review→SC04→SC06→SC13. 카드 없음·learning 0·출처 삭제 확인.'),
      ('UI06 권한과 장애','SC18 From→SC19 현재 위치→C09→권한 거부→등록 출발지. 오프라인·검색 0건·외부 지도 미설치 확인.'),
      ('UI07 변경과 삭제','SC12 기간 축소→C05 취소/확정; SC16 삭제→C08; SC10 초기화→C06/C07. 대상·유지 데이터 설명 확인.')])

    page('38 화면 설계 완료 조건과 남은 의존성')
    h('화면 설계서 완료 조건')
    p('SC01~SC20이 모두 화면 목록에 있고 SC20의 C01~C09 모달이 호출 화면에 연결되어야 한다. 각 프레임에는 화면 ID·상태명·요소 번호를 표기한다. 모든 주요 버튼은 목적 화면 또는 저장 효과에 연결하며 비활성 버튼은 이유를 표시한다. 정의되지 않은 버튼·메뉴는 추가하지 않는다.')
    p('아래 스와이프 리뷰·좌우 판정의 방향을 일관되게 표현한다. 예외 상태에서도 Retry·Back·수동 선택 등 다음 동작이 있어야 한다. UI01~UI07을 클릭 가능한 프로토타입 또는 주석으로 추적할 수 있어야 한다. 390dp 외에도 360dp·200% 글자 크기에서 핵심 버튼을 조작할 수 있어야 한다.')
    h('공급자 선정 후에만 확정할 요소')
    table(['의존성','현재 화면 설계 가능 범위','구현 전 확인'],[
      ('리뷰 제공','SC08 레이아웃·영어/번역/0건 상태','작성자·출처 형식, 실제 영어 제공, 일부 목록, 원문 링크, 타사 지도 결합 조건.'),
      ('지도와 도보 경로','SC18 지도·패널·실패 상태','국내 도보 실제 응답·지도 라벨·단위·필수 출처 위치·캐시 허용.'),
      ('출발지 검색','SC19 등록 목록·외부 검색/미지원 상태','영어 입력·국내 좌표·사용 조건·운영비. 검색 미지원 시 등록 출발지·현재 위치 유지.'),
      ('콘텐츠 사진','사진·강조 영역·credits 위치','사진 이용권·카드 60개·가게 좌표·장소 동일성·영문 검수.'),
      ('운영비와 유료 전환','무료 MVP·결제 화면 없음','요청 수·비용 한도·US$0.99 전환은 후속 결정. 현재 기능 완료와 구별.')])
    h('버전 변경 기록')
    p('1.0 2026년 10월 7일: 학습·리뷰·지도·북마크·여행 계획의 PRD/SRS 통합. 1.1 같은 날: 등록 간판 장소와 랜드마크만 일정에 추가하도록 사용자 확정. 화면 20개·요소·상태·입력·복귀·저장·모달 9종·화면 추적·검수 규칙 추가. 지도 공급자와 실제 구현은 후속 단계에서 검증한다.')
