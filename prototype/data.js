/* Sign Korean — 프로토타입 데이터
 *
 * 구조 (v2.1)
 *  - areas      : Explore에 노출되는 지역 묶음
 *  - places     : 여행 계획의 목적지. 실존 공공·관광시설 위주 (좌표는 근사치)
 *  - placeTypes : 장소 유형. 유형마다 그 자리에서 보이는 간판 단어를 갖는다
 *  - cards      : 단어 1개 = 카드 1장. 장소마다 복제하지 않는다.
 *                 카드 → 장소는 typeId로 1:N 연결되며, 아래로 스와이프하면
 *                 그 간판을 볼 수 있는 장소 목록이 나온다 (FR09).
 *
 * 사진: 간판 사진은 이용권 확보 전이라 프로토타입에서는 앱이 SVG 판으로 그린다.
 *       실제 사진은 공공누리(한국관광공사·서울시) / Unsplash·Pexels / Wikimedia에서
 *       확보하며 문서 단계 1의 의존성이다.
 */
window.DATA = {

  // ---------- 지역 묶음 ----------
  areas: [
    {id:'gyeongbokgung',name:'Gyeongbokgung & Seochon',koName:'경복궁·서촌',tag:'History & quiet lanes',color:'#2f6f5e',image:'assets/gyeongbokgung.jpg',description:'Palace courtyards, museum halls and the slow lanes of Seochon and Samcheong-dong.'},
    {id:'myeongdong',name:'Myeongdong & Namdaemun',koName:'명동·남대문',tag:'Food & shopping',color:'#b8554a',image:'assets/myeongdong.jpg',description:'A dense grid of shops, street food and the oldest market in the city.'},
    {id:'ddp',name:'Dongdaemun & Euljiro',koName:'동대문·을지로',tag:'Design & night walks',color:'#4a5b8c',image:'assets/ddp.jpg',description:'Curved architecture, fabric markets and the printing alleys of Euljiro.'},
    {id:'seongsu',name:'Seongsu & Seoul Forest',koName:'성수·서울숲',tag:'Cafés & parks',color:'#6b8e3d',image:null,description:'Converted factories, a long green park and the city’s busiest café street.'},
    {id:'hongdae',name:'Hongdae & Yeonnam',koName:'홍대·연남',tag:'Youth & late nights',color:'#9c5a9e',image:null,description:'Music venues, student streets and a converted railway park.'},
    {id:'yongsan',name:'Yongsan & Itaewon',koName:'용산·이태원',tag:'Museums & global food',color:'#c07a2c',image:null,description:'The national museum, a war memorial, and the most international street in Seoul.'},
    {id:'yeouido',name:'Yeouido & the Han River',koName:'여의도·한강',tag:'River & skyline',color:'#2f7f94',image:null,description:'Riverside parks, a department store and the city’s financial district.'},
    {id:'gangnam',name:'Gangnam & Jamsil',koName:'강남·잠실',tag:'Towers & malls',color:'#5b4b8a',image:null,description:'Underground malls, an observation tower and a large Olympic park.'}
  ],

  // ---------- 장소 유형 ----------
  // 유형마다 그 자리에서 실제로 보이는 간판 단어를 갖는다.
  // signs: [원문, 로마자, 영어 뜻, 영어 설명]
  placeTypes: [
    {id:'palace',label:'Palace & heritage site',koLabel:'고궁·유적',color:'#2f6f5e',signs:[
      ['매표소','mapyoso','Ticket office','Where you buy the entry ticket. The same word appears at towers and terminals.'],
      ['입장료','ipjangnyo','Admission fee','무료 next to it means free entry.'],
      ['관람 시간','gwallam sigan','Opening hours','관람 is viewing. You will see it at museums too.']
    ]},
    {id:'museum',label:'Museum & gallery',koLabel:'박물관·미술관',color:'#4a5b8c',signs:[
      ['박물관','bangmulgwan','Museum','관 at the end means a public hall or building.'],
      ['전시','jeonsi','Exhibition','기획 전시 is a special exhibition.'],
      ['보관함','bogwamham','Lockers','Leave your bag before going in.']
    ]},
    {id:'market',label:'Traditional market',koLabel:'전통시장',color:'#b8554a',signs:[
      ['국밥','gukbap','Rice and soup','A cheap, filling market meal. The word is on dozens of stalls.'],
      ['만두','mandu','Dumplings','Steamed or fried, sold by the plate.'],
      ['칼국수','kalguksu','Knife-cut noodles','A hand-cut noodle soup, common in market alleys.'],
      ['떡볶이','tteokbokki','Spicy rice cakes','The most common street food sign in the country.'],
      ['순대','sundae','Blood sausage','Often sold at the same stall as 떡볶이.']
    ]},
    {id:'cafe',label:'Café street',koLabel:'카페거리',color:'#8c6239',signs:[
      ['카페','kape','Café','A loanword — read it aloud and you will hear it.'],
      ['커피','keopi','Coffee','Another loanword you can decode on sight.'],
      ['포장','pojang','Takeout','Also used at restaurants for food to go.']
    ]},
    {id:'street',label:'Food street & shops',koLabel:'먹자골목·상점가',color:'#a8492f',signs:[
      ['식당','sikdang','Restaurant','The plain, general word for an eating place.'],
      ['분식','bunsik','Snack food','Cheap, quick Korean snacks — kimbap, ramyeon, fried bites.'],
      ['호프','hopeu','Beer pub','From the German Hof. Usually paired with 치킨 on the same sign.']
    ]},
    {id:'shopping',label:'Department store & mall',koLabel:'백화점·쇼핑몰',color:'#9c5a9e',signs:[
      ['백화점','baekhwajeom','Department store','점 at the end means shop.'],
      ['면세점','myeonsejeom','Duty free','면세 means tax exempt.'],
      ['할인','harin','Discount','세일 is also used on the same signs.']
    ]},
    {id:'hanok',label:'Hanok village & craft street',koLabel:'한옥마을·공방거리',color:'#7a6a3a',signs:[
      ['한복 대여','hanbok daeyeo','Hanbok rental','대여 means rental. Wearing hanbok gets you free palace entry.'],
      ['공방','gongbang','Craft workshop','A small studio where things are made and sold.'],
      ['찻집','chatjip','Tea house','집 at the end means house or shop.']
    ]},
    {id:'tower',label:'Observation tower & landmark',koLabel:'전망대·랜드마크',color:'#2f7f94',signs:[
      ['전망대','jeonmangdae','Observation deck','The reason you queued for the lift.'],
      ['기념품','ginyeompum','Souvenirs','기념 means commemoration.']
    ]},
    {id:'station',label:'Station & terminal',koLabel:'역·터미널',color:'#1f6f78',signs:[
      ['대합실','daehapsil','Waiting room','Where you wait for a train or bus.'],
      ['안내소','annaeso','Information desk','소 at the end marks a place or office.']
    ]},
    {id:'park',label:'Park & riverside',koLabel:'공원·한강',color:'#6b8e3d',signs:[
      ['산책로','sanchaengno','Walking path','로 at the end means road or path.'],
      ['매점','maejeom','Kiosk / snack shop','Where you buy drinks inside a park.'],
      ['자전거 대여','jajeongeo daeyeo','Bicycle rental','Common along the Han River parks.']
    ]},
    {id:'pharmacy',label:'Pharmacy & clinic',koLabel:'약국·병원',color:'#3a7d44',signs:[
      ['약국','yakguk','Pharmacy','Usually with a green cross outside.'],
      ['병원','byeongwon','Clinic / hospital','치과 is dental, 내과 is internal medicine.']
    ]},
    {id:'bank',label:'Bank & money',koLabel:'은행·환전',color:'#5b6b8a',signs:[
      ['은행','eunhaeng','Bank','On every branch sign in the country.'],
      ['환전','hwanjeon','Currency exchange','Useful near markets and tourist streets.']
    ]}
  ],

  // ---------- 등록 장소 ----------
  // 실존 공공·관광시설 위주. 좌표는 근사치이며 출시 전 실측·검증이 필요하다.
  // example:true 는 개별 상점을 대신하는 예시 장소로, 실제 상호가 아니다.
  places: [
    // 경복궁·서촌
    {id:'gyeongbokgung',areaId:'gyeongbokgung',type:'palace',name:'Gyeongbokgung Palace',koName:'경복궁',address:'161 Sajik-ro, Jongno-gu, Seoul',lat:37.5796,lng:126.9770},
    {id:'changdeokgung',areaId:'gyeongbokgung',type:'palace',name:'Changdeokgung Palace',koName:'창덕궁',address:'99 Yulgok-ro, Jongno-gu, Seoul',lat:37.5794,lng:126.9910},
    {id:'jongmyo',areaId:'gyeongbokgung',type:'palace',name:'Jongmyo Shrine',koName:'종묘',address:'157 Jong-ro, Jongno-gu, Seoul',lat:37.5745,lng:126.9940},
    {id:'folkmuseum',areaId:'gyeongbokgung',type:'museum',name:'National Folk Museum of Korea',koName:'국립민속박물관',address:'37 Samcheong-ro, Jongno-gu, Seoul',lat:37.5814,lng:126.9793},
    {id:'mmca',areaId:'gyeongbokgung',type:'museum',name:'MMCA Seoul',koName:'국립현대미술관 서울',address:'30 Samcheong-ro, Jongno-gu, Seoul',lat:37.5785,lng:126.9800},
    {id:'bukchon',areaId:'gyeongbokgung',type:'hanok',name:'Bukchon Hanok Village',koName:'북촌한옥마을',address:'37 Gyedong-gil, Jongno-gu, Seoul',lat:37.5826,lng:126.9830},
    {id:'samcheong',areaId:'gyeongbokgung',type:'cafe',name:'Samcheong-dong Street',koName:'삼청동길',address:'Samcheong-dong, Jongno-gu, Seoul',lat:37.5840,lng:126.9820},
    {id:'tongin',areaId:'gyeongbokgung',type:'market',name:'Tongin Market',koName:'통인시장',address:'18 Jahamun-ro 15-gil, Jongno-gu, Seoul',lat:37.5800,lng:126.9700},
    {id:'insadong',areaId:'gyeongbokgung',type:'hanok',name:'Insadong Street',koName:'인사동',address:'Insadong-gil, Jongno-gu, Seoul',lat:37.5740,lng:126.9850},
    {id:'gwanghwamun',areaId:'gyeongbokgung',type:'tower',name:'Gwanghwamun Square',koName:'광화문광장',address:'172 Sejong-daero, Jongno-gu, Seoul',lat:37.5720,lng:126.9769},
    {id:'seochon-ph',areaId:'gyeongbokgung',type:'pharmacy',name:'Example pharmacy · Seochon',koName:'서촌 약국',address:'Example place near Gyeongbokgung',lat:37.5790,lng:126.9700,example:true},

    // 명동·남대문
    {id:'myeongdong-cathedral',areaId:'myeongdong',type:'tower',name:'Myeongdong Cathedral',koName:'명동성당',address:'74 Myeongdong-gil, Jung-gu, Seoul',lat:37.5633,lng:126.9872},
    {id:'namdaemun',areaId:'myeongdong',type:'market',name:'Namdaemun Market',koName:'남대문시장',address:'21 Namdaemunsijang 4-gil, Jung-gu, Seoul',lat:37.5591,lng:126.9772},
    {id:'shinsegae',areaId:'myeongdong',type:'shopping',name:'Shinsegae Department Store Main',koName:'신세계백화점 본점',address:'63 Sogong-ro, Jung-gu, Seoul',lat:37.5610,lng:126.9810},
    {id:'lotte-main',areaId:'myeongdong',type:'shopping',name:'Lotte Department Store Main',koName:'롯데백화점 본점',address:'81 Namdaemun-ro, Jung-gu, Seoul',lat:37.5650,lng:126.9820},
    {id:'deoksugung',areaId:'myeongdong',type:'palace',name:'Deoksugung Palace',koName:'덕수궁',address:'99 Sejong-daero, Jung-gu, Seoul',lat:37.5658,lng:126.9751},
    {id:'seoulstation',areaId:'myeongdong',type:'station',name:'Seoul Station',koName:'서울역',address:'405 Hangang-daero, Jung-gu, Seoul',lat:37.5547,lng:126.9707},
    {id:'namsan-tower',areaId:'myeongdong',type:'tower',name:'N Seoul Tower',koName:'남산서울타워',address:'105 Namsangongwon-gil, Yongsan-gu, Seoul',lat:37.5512,lng:126.9882},
    {id:'myeongdong-bank',areaId:'myeongdong',type:'bank',name:'Example exchange booth · Myeongdong',koName:'명동 환전소',address:'Example place in Myeongdong',lat:37.5640,lng:126.9860,example:true},
    {id:'myeongdong-food',areaId:'myeongdong',type:'street',name:'Myeongdong Street Food Alley',koName:'명동 먹자골목',address:'Myeongdong-gil, Jung-gu, Seoul',lat:37.5636,lng:126.9850},

    // 동대문·을지로
    {id:'ddp',areaId:'ddp',type:'tower',name:'Dongdaemun Design Plaza',koName:'동대문디자인플라자',address:'281 Eulji-ro, Jung-gu, Seoul',lat:37.5669,lng:127.0094},
    {id:'gwangjang',areaId:'ddp',type:'market',name:'Gwangjang Market',koName:'광장시장',address:'88 Changgyeonggung-ro, Jongno-gu, Seoul',lat:37.5701,lng:126.9996},
    {id:'cheonggyecheon',areaId:'ddp',type:'park',name:'Cheonggyecheon Stream',koName:'청계천',address:'Cheonggyecheon-ro, Jung-gu, Seoul',lat:37.5692,lng:126.9784},
    {id:'euljiro',areaId:'ddp',type:'street',name:'Euljiro Printing Alleys',koName:'을지로 인쇄골목',address:'Euljiro 3-ga, Jung-gu, Seoul',lat:37.5660,lng:126.9910},
    {id:'ikseon',areaId:'ddp',type:'hanok',name:'Ikseon-dong Hanok Street',koName:'익선동',address:'Ikseon-dong, Jongno-gu, Seoul',lat:37.5740,lng:126.9900},
    {id:'changgyeonggung',areaId:'ddp',type:'palace',name:'Changgyeonggung Palace',koName:'창경궁',address:'185 Changgyeonggung-ro, Jongno-gu, Seoul',lat:37.5783,lng:126.9950},
    {id:'seoulmuseum',areaId:'ddp',type:'museum',name:'Seoul Museum of History',koName:'서울역사박물관',address:'55 Saemunan-ro, Jongno-gu, Seoul',lat:37.5704,lng:126.9700},

    // 성수·서울숲
    {id:'seoulforest',areaId:'seongsu',type:'park',name:'Seoul Forest',koName:'서울숲',address:'273 Ttukseom-ro, Seongdong-gu, Seoul',lat:37.5444,lng:127.0374},
    {id:'seongsu-cafe',areaId:'seongsu',type:'cafe',name:'Seongsu Café Street',koName:'성수동 카페거리',address:'Seongsui-ro, Seongdong-gu, Seoul',lat:37.5445,lng:127.0557},
    {id:'seongsu-ph',areaId:'seongsu',type:'pharmacy',name:'Example clinic · Seongsu',koName:'성수 병원',address:'Example place near Seoul Forest',lat:37.5450,lng:127.0500,example:true},
    {id:'ttukseom',areaId:'seongsu',type:'station',name:'Ttukseom Station',koName:'뚝섬역',address:'Seongsui-ro, Seongdong-gu, Seoul',lat:37.5471,lng:127.0474},

    // 홍대·연남
    {id:'hongdae-station',areaId:'hongdae',type:'station',name:'Hongik Univ. Station',koName:'홍대입구역',address:'Yanghwa-ro, Mapo-gu, Seoul',lat:37.5572,lng:126.9254},
    {id:'yeonnam',areaId:'hongdae',type:'cafe',name:'Yeonnam-dong Café Street',koName:'연남동 카페거리',address:'Yeonnam-dong, Mapo-gu, Seoul',lat:37.5600,lng:126.9250},
    {id:'gyeongui-park',areaId:'hongdae',type:'park',name:'Gyeongui Line Forest Park',koName:'경의선숲길',address:'Yeonnam-dong, Mapo-gu, Seoul',lat:37.5610,lng:126.9270},
    {id:'mangwon',areaId:'hongdae',type:'market',name:'Mangwon Market',koName:'망원시장',address:'14 Poeun-ro 8-gil, Mapo-gu, Seoul',lat:37.5560,lng:126.9050},
    {id:'hongdae-street',areaId:'hongdae',type:'street',name:'Hongdae Walking Street',koName:'홍대 걷고싶은거리',address:'Eoulmadang-ro, Mapo-gu, Seoul',lat:37.5540,lng:126.9230},

    // 용산·이태원
    {id:'nmk',areaId:'yongsan',type:'museum',name:'National Museum of Korea',koName:'국립중앙박물관',address:'137 Seobinggo-ro, Yongsan-gu, Seoul',lat:37.5240,lng:126.9803},
    {id:'warmemorial',areaId:'yongsan',type:'museum',name:'War Memorial of Korea',koName:'전쟁기념관',address:'29 Itaewon-ro, Yongsan-gu, Seoul',lat:37.5365,lng:126.9770},
    {id:'itaewon',areaId:'yongsan',type:'street',name:'Itaewon Street',koName:'이태원 거리',address:'Itaewon-ro, Yongsan-gu, Seoul',lat:37.5345,lng:126.9946},
    {id:'namsan-park',areaId:'yongsan',type:'park',name:'Namsan Park',koName:'남산공원',address:'Samil-daero, Jung-gu, Seoul',lat:37.5512,lng:126.9910},
    {id:'itaewon-bank',areaId:'yongsan',type:'bank',name:'Example bank branch · Itaewon',koName:'이태원 은행',address:'Example place in Itaewon',lat:37.5340,lng:126.9950,example:true},

    // 여의도·한강
    {id:'yeouido-park',areaId:'yeouido',type:'park',name:'Yeouido Hangang Park',koName:'여의도한강공원',address:'330 Yeouidong-ro, Yeongdeungpo-gu, Seoul',lat:37.5285,lng:126.9340},
    {id:'thehyundai',areaId:'yeouido',type:'shopping',name:'The Hyundai Seoul',koName:'더현대 서울',address:'108 Yeoui-daero, Yeongdeungpo-gu, Seoul',lat:37.5259,lng:126.9285},
    {id:'noryangjin',areaId:'yeouido',type:'market',name:'Noryangjin Fish Market',koName:'노량진수산시장',address:'674 Nodeul-ro, Dongjak-gu, Seoul',lat:37.5133,lng:126.9540},
    {id:'yeouinaru',areaId:'yeouido',type:'station',name:'Yeouinaru Station',koName:'여의나루역',address:'Yeouidong-ro, Yeongdeungpo-gu, Seoul',lat:37.5270,lng:126.9325},

    // 강남·잠실
    {id:'coex',areaId:'gangnam',type:'shopping',name:'COEX Mall',koName:'코엑스몰',address:'513 Yeongdong-daero, Gangnam-gu, Seoul',lat:37.5126,lng:127.0589},
    {id:'lotteworldtower',areaId:'gangnam',type:'tower',name:'Lotte World Tower',koName:'롯데월드타워',address:'300 Olympic-ro, Songpa-gu, Seoul',lat:37.5125,lng:127.1025},
    {id:'olympicpark',areaId:'gangnam',type:'park',name:'Olympic Park',koName:'올림픽공원',address:'424 Olympic-ro, Songpa-gu, Seoul',lat:37.5202,lng:127.1216},
    {id:'gangnam-station',areaId:'gangnam',type:'station',name:'Gangnam Station',koName:'강남역',address:'Gangnam-daero, Gangnam-gu, Seoul',lat:37.4979,lng:127.0276},
    {id:'garosugil',areaId:'gangnam',type:'cafe',name:'Garosu-gil',koName:'가로수길',address:'Sinsa-dong, Gangnam-gu, Seoul',lat:37.5205,lng:127.0230}
  ],

  // ---------- 기초 표지판 (장소 없음) ----------
  basics: {
    id:'basics',
    name:'Everyday street signs',
    koName:'기초 간판',
    tag:'Start here',
    description:'The signs you meet before you meet anyone. Doors, restrooms, opening hours, stations. No shop, no place — just the words the city repeats everywhere.'
  },
  basicGroups: [
    {id:'restroom',label:'Restrooms & facilities',color:'#2f6fb0',target:15},
    {id:'door',label:'Doors & direction',color:'#3a7d44',target:20},
    {id:'hours',label:'Opening hours',color:'#b9772a',target:15},
    {id:'warning',label:'Warnings & bans',color:'#c0392b',target:15},
    {id:'transit',label:'Transit & moving around',color:'#1f6f78',target:20},
    {id:'service',label:'Everyday services',color:'#5b4b8a',target:15}
  ],
  basicSigns: [
    ['restroom','화장실','hwajangsil','Restroom','The word itself. Everything else on a restroom door is a variation of this.'],
    ['restroom','남자 화장실','namja hwajangsil','Men’s restroom','남자 means man. Often shown with a blue plate or figure.'],
    ['restroom','여자 화장실','yeoja hwajangsil','Women’s restroom','여자 means woman. Often shown with a red plate or figure.'],
    ['restroom','장애인 화장실','jangaein hwajangsil','Accessible restroom','Marked separately in stations, museums and larger cafés.'],
    ['restroom','수유실','suyusil','Nursing room','Common in department stores and large stations.'],
    ['door','미세요','miseyo','Push','On the door you are about to walk into. Push, don’t pull.'],
    ['door','당기세요','danggiseyo','Pull','The other half of every Korean door.'],
    ['door','입구','ipgu','Entrance','입 is enter, 구 is opening. The pair shows up on buildings and stations.'],
    ['door','출구','chulgu','Exit','Subway exits are numbered: 3번 출구 is Exit 3.'],
    ['door','비상구','bisanggu','Emergency exit','Always on the green running-figure sign.'],
    ['hours','영업중','yeongeopjung','Open for business','Hung on the door while the shop is serving.'],
    ['hours','준비중','junbijung','Preparing / not open yet','The shop exists and is staffed — it just isn’t serving you yet.'],
    ['hours','휴무','hyumu','Closed (day off)','금일 휴무 means closed today.'],
    ['hours','24시간','isipsa sigan','Open 24 hours','Convenience stores, saunas and some cafés.'],
    ['warning','금연','geumyeon','No smoking','금 is to forbid. You will see it on 금지 signs too.'],
    ['warning','촬영 금지','chwaryeong geumji','No photography','Museums, some shops, and parts of palaces.'],
    ['warning','출입 금지','churip geumji','No entry','Staff-only doors and closed-off areas.'],
    ['warning','미끄럼 주의','mikkeureom juui','Caution: slippery','주의 means caution. It pairs with many other words.'],
    ['warning','머리 조심','meori josim','Mind your head','Low doorways in older buildings and markets.'],
    ['transit','지하철','jihacheol','Subway','지하 is underground, 철 is rail.'],
    ['transit','버스 정류장','beoseu jeongnyujang','Bus stop','정류장 is the stop itself.'],
    ['transit','환승','hwanseung','Transfer','On subway platforms where lines meet.'],
    ['transit','승강장','seunggangjang','Platform','Follow it to reach the train.'],
    ['transit','계단','gyedan','Stairs','Paired with 엘리베이터 and 에스컬레이터 on station signs.'],
    ['transit','엘리베이터','ellibeiteo','Elevator','A loanword — read it aloud and you will hear it.'],
    ['service','편의점','pyeonuijeom','Convenience store','편의 means convenience, 점 means shop.'],
    ['service','현금인출기','hyeongeum inchulgi','Cash machine (ATM)','Often labelled ATM as well.'],
    ['service','와이파이','waipai','Wi-Fi','무료 와이파이 means free Wi-Fi.'],
    ['service','분실물 센터','bunsilmul senteo','Lost and found','In stations, malls and museums.'],
    ['service','우체국','ucheguk','Post office','Red and white branding across the country.']
  ],

  cards: [],

  // 샘플 리뷰 — 실제 공급자 연동은 문서 단계 3
  reviews: [
    {id:'r1',placeId:'gwangjang',author:'Alex',rating:5,date:'2026-09-12',text:'Busy and loud in the best way. Reading the stall signs made choosing a lot easier.',original:'시장 골목이 활기차고 간판만 읽어도 고르기 쉬웠어요.',translated:true},
    {id:'r2',placeId:'gwangjang',author:'Priya',rating:4,date:'2026-09-19',text:'Go early if you want a seat at the noodle stalls.',original:'칼국수 집은 일찍 가야 자리가 있어요.',translated:true},
    {id:'r3',placeId:'namdaemun',author:'Jamie',rating:4,date:'2026-09-18',text:'Enormous. The food alley at the back was the highlight.',original:'규모가 크고 뒤쪽 먹자골목이 제일 좋았어요.',translated:true},
    {id:'r4',placeId:'tongin',author:'Morgan',rating:5,date:'2026-09-20',text:'The coin lunchbox system is fun and the signs are easy to follow.',original:'엽전 도시락이 재미있고 안내 간판이 알아보기 쉬웠어요.',translated:true},
    {id:'r5',placeId:'seongsu-cafe',author:'Sam',rating:4,date:'2026-09-22',text:'Old factories turned into cafés. Plenty of takeout signs if you are in a hurry.',original:'공장을 개조한 카페가 많고 포장 간판도 자주 보여요.',translated:true},
    {id:'r6',placeId:'gyeongbokgung',author:'Casey',rating:5,date:'2026-09-23',text:'Wearing hanbok gets you in free. The ticket office sign is easy to spot.',original:'한복을 입으면 무료 입장이고 매표소 간판이 잘 보여요.',translated:true},
    {id:'r7',placeId:'nmk',author:'Taylor',rating:5,date:'2026-09-14',text:'Free general admission and clear bilingual signs throughout.',original:'상설 전시는 무료이고 안내 표지가 잘 되어 있어요.',translated:true},
    {id:'r8',placeId:'mangwon',author:'Robin',rating:4,date:'2026-09-25',text:'A neighbourhood market rather than a tourist one. Good for practising food words.',original:'관광지보다는 동네 시장이라 음식 단어 익히기 좋아요.',translated:true}
  ],

  originPoints:[
    {id:'origin-seoulstation',name:'Seoul Station',koName:'서울역',lat:37.5547,lng:126.9707},
    {id:'origin-cityhall',name:'Seoul City Hall',koName:'서울시청',lat:37.5663,lng:126.9779}
  ],
  routeMinutes:{},
  photoCredits:[
    {title:'Gyeonghoeru Pavilion',author:'Suohros',license:'CC0',url:'https://commons.wikimedia.org/wiki/File:Gyeonghoeru_Pavilion_at_Gyeongbokgung_Palace.jpg',licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/'},
    {title:'Myeongdong Cathedral',author:'Sean Young (@assanges)',license:'CC BY 4.0',url:'https://commons.wikimedia.org/wiki/File:Myeongdong_Cathedral_20240217.jpg',licenseUrl:'https://creativecommons.org/licenses/by/4.0/'},
    {title:'Dongdaemun Design Plaza',author:'Tycho Atsma',license:'CC0',url:'https://commons.wikimedia.org/wiki/File:DongDaemun_Design_Plaza_(동대문디자인플라자_(DDP)),_Seoul,_South_Korea_(Unsplash_aNSniQXO424).jpg',licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/'},
    {title:'All sign cards',author:'Drawn in-app as SVG plates',license:'Prototype illustration — real photos pending rights clearance (공공누리 / Unsplash / Wikimedia)',url:''}
  ]
};

// Explore 그리드는 지역 묶음을 쓴다. 기존 코드 호환을 위해 같은 배열을 가리킨다.
DATA.landmarks = DATA.areas;

// ---------- 카드 생성 ----------
// 단어 1개 = 카드 1장. 장소마다 복제하지 않는다.
DATA.placeTypes.forEach(t => t.signs.forEach((s,i) => DATA.cards.push({
  id: 't-' + t.id + '-' + (i+1),
  kind: 'place',
  typeId: t.id,
  collectionId: null,          // 지역이 아니라 유형에 속한다
  placeId: null,               // 장소는 typeId로 1:N 연결된다
  groupLabel: t.label,
  color: t.color,
  word: s[0], roman: s[1], meaning: s[2], note: s[3],
  image: null,
  alt: 'Sign plate showing ' + s[0]
})));

DATA.basicSigns.forEach((s,i) => {
  const g = DATA.basicGroups.find(x => x.id === s[0]);
  DATA.cards.push({
    id: 'b' + String(i+1).padStart(2,'0'),
    kind: 'basic',
    typeId: null,
    collectionId: 'basics',
    placeId: null,
    groupId: s[0], groupLabel: g.label, color: g.color,
    word: s[1], roman: s[2], meaning: s[3], note: s[4],
    image: null,
    alt: 'Street sign plate showing ' + s[1]
  });
});

// 조회 도우미
DATA.placesForCard = card => card.typeId ? DATA.places.filter(p => p.type === card.typeId) : [];
DATA.cardsForPlace = place => DATA.cards.filter(c => c.typeId === place.type);
DATA.cardsForArea  = areaId => {
  const types = [...new Set(DATA.places.filter(p => p.areaId === areaId).map(p => p.type))];
  return DATA.cards.filter(c => types.includes(c.typeId));
};
