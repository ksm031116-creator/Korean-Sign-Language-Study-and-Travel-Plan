/* Sign Korean — 프로토타입 데이터
 *
 * 구조 (v2.1)
 *  - areas      : Explore에 노출되는 지역 묶음
 *  - places     : 여행 계획의 목적지. 실존 공공·관광시설 위주 (좌표는 근사치)
 *  - placeTypes : 장소 유형. 유형마다 그 자리에서 보이는 간판 단어를 갖는다
 *  - cards      : 단어 1개 = 카드 1장. 장소마다 복제하지 않는다.
 *                 카드 → 장소는 typeId로 1:N 연결되며, 장소에 signWords가 있으면
 *                 그 단어에만 연결한다. 아래로 스와이프하면 그 간판을 볼 수 있는
 *                 장소 목록이 가게 사진·리뷰·위치와 함께 나온다 (FR09).
 *
 * 사진: 간판 사진은 이용권 확보 전이라 프로토타입에서는 앱이 SVG 판으로 그린다.
 *       실제 사진은 공공누리(한국관광공사·서울시) / Unsplash·Pexels / Wikimedia에서
 *       확보하며 문서 단계 1의 의존성이다.
 */
// 사진 경로는 한 곳에서만 정의한다. 단일 파일 번들이 같은 사진을 두 번 내장하지 않도록.
const PHOTO = {
  gyeongbokgung:'assets/gyeongbokgung.jpg',
  myeongdong:'assets/myeongdong.jpg',
  ddp:'assets/ddp.jpg'
};

window.DATA = {

  // ---------- 지역 묶음 ----------
  areas: [
    {id:'gyeongbokgung',name:'Gyeongbokgung & Seochon',koName:'경복궁·서촌',tag:'History & quiet lanes',color:'#2f6f5e',image:PHOTO.gyeongbokgung,description:'Palace courtyards, museum halls and the slow lanes of Seochon and Samcheong-dong.'},
    {id:'myeongdong',name:'Myeongdong & Namdaemun',koName:'명동·남대문',tag:'Food & shopping',color:'#b8554a',image:PHOTO.myeongdong,description:'A dense grid of shops, street food and the oldest market in the city.'},
    {id:'ddp',name:'Dongdaemun & Euljiro',koName:'동대문·을지로',tag:'Design & night walks',color:'#4a5b8c',image:PHOTO.ddp,description:'Curved architecture, fabric markets and the printing alleys of Euljiro.'},
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
    {id:'landmark',label:'Landmark & plaza',koLabel:'랜드마크·광장',color:'#3d6f8f',signs:[
      ['안내도','annaedo','Area map','The board showing where everything is. 도 means map or drawing.'],
      ['포토존','potojon','Photo spot','A Konglish loanword — marks the spot with the best view.']
    ]},
    {id:'tower',label:'Observation tower',koLabel:'전망대',color:'#2f7f94',signs:[
      ['전망대','jeonmangdae','Observation deck','The reason you queued for the lift.'],
      ['기념품','ginyeompum','Souvenirs','기념 means commemoration.']
    ]},
    {id:'station',label:'Station & terminal',koLabel:'역·터미널',color:'#1f6f78',signs:[
      ['교통카드 충전','gyotongkadeu chungjeon','Transit card top-up','Machines that reload T-money cards.'],
      ['고객안내센터','gogaek annae senteo','Customer service center','Ask here for route help or lost items.']
    ]},
    {id:'park',label:'Park & riverside',koLabel:'공원·한강',color:'#6b8e3d',signs:[
      ['산책로','sanchaengno','Walking path','로 at the end means road or path.'],
      ['쉼터','swimteo','Rest area','Benches and shade. 쉼 means rest.']
    ]},
    {id:'services',label:'Pharmacy, clinic & bank',koLabel:'약국·병원·은행',color:'#3a7d44',signs:[
      ['약국','yakguk','Pharmacy','Usually with a green cross outside.'],
      ['병원','byeongwon','Clinic / hospital','치과 is dental, 내과 is internal medicine.'],
      ['은행','eunhaeng','Bank','ATMs are labelled ATM or 현금인출기.'],
      ['환전','hwanjeon','Currency exchange','Useful near markets and tourist streets.']
    ]}
  ],

  // ---------- 등록 장소 ----------
  // 실존 공공·관광시설 위주. 좌표는 근사치이며 출시 전 실측·검증이 필요하다.
  // example:true 는 개별 상점을 대신하는 예시 장소로, 실제 상호가 아니다.
  places: [
    // 경복궁·서촌
    {id:'gyeongbokgung',areaId:'gyeongbokgung',type:'palace',photo:PHOTO.gyeongbokgung,name:'Gyeongbokgung Palace',koName:'경복궁',address:'161 Sajik-ro, Jongno-gu, Seoul',lat:37.5796,lng:126.9770},
    {id:'changdeokgung',areaId:'gyeongbokgung',type:'palace',name:'Changdeokgung Palace',koName:'창덕궁',address:'99 Yulgok-ro, Jongno-gu, Seoul',lat:37.5794,lng:126.9910},
    {id:'jongmyo',areaId:'gyeongbokgung',type:'palace',name:'Jongmyo Shrine',koName:'종묘',address:'157 Jong-ro, Jongno-gu, Seoul',lat:37.5745,lng:126.9940},
    {id:'folkmuseum',areaId:'gyeongbokgung',type:'museum',name:'National Folk Museum of Korea',koName:'국립민속박물관',address:'37 Samcheong-ro, Jongno-gu, Seoul',lat:37.5814,lng:126.9793},
    {id:'mmca',areaId:'gyeongbokgung',type:'museum',name:'MMCA Seoul',koName:'국립현대미술관 서울',address:'30 Samcheong-ro, Jongno-gu, Seoul',lat:37.5785,lng:126.9800},
    {id:'bukchon',areaId:'gyeongbokgung',type:'hanok',name:'Bukchon Hanok Village',koName:'북촌한옥마을',address:'37 Gyedong-gil, Jongno-gu, Seoul',lat:37.5826,lng:126.9830},
    {id:'samcheong',areaId:'gyeongbokgung',type:'cafe',name:'Samcheong-dong Street',koName:'삼청동길',address:'Samcheong-dong, Jongno-gu, Seoul',lat:37.5840,lng:126.9820},
    {id:'tongin',areaId:'gyeongbokgung',type:'market',name:'Tongin Market',koName:'통인시장',address:'18 Jahamun-ro 15-gil, Jongno-gu, Seoul',lat:37.5800,lng:126.9700},
    {id:'insadong',areaId:'gyeongbokgung',type:'hanok',name:'Insadong Street',koName:'인사동',address:'Insadong-gil, Jongno-gu, Seoul',lat:37.5740,lng:126.9850},
    {id:'gwanghwamun',areaId:'gyeongbokgung',type:'landmark',name:'Gwanghwamun Square',koName:'광화문광장',address:'172 Sejong-daero, Jongno-gu, Seoul',lat:37.5720,lng:126.9769},
    {id:'seochon-ph',areaId:'gyeongbokgung',type:'services',signWords:['약국'],name:'Example pharmacy · Seochon',koName:'서촌 약국',address:'Example place near Gyeongbokgung',lat:37.5790,lng:126.9700,example:true},

    // 명동·남대문
    {id:'myeongdong-cathedral',areaId:'myeongdong',type:'landmark',photo:PHOTO.myeongdong,name:'Myeongdong Cathedral',koName:'명동성당',address:'74 Myeongdong-gil, Jung-gu, Seoul',lat:37.5633,lng:126.9872},
    {id:'namdaemun',areaId:'myeongdong',type:'market',name:'Namdaemun Market',koName:'남대문시장',address:'21 Namdaemunsijang 4-gil, Jung-gu, Seoul',lat:37.5591,lng:126.9772},
    {id:'shinsegae',areaId:'myeongdong',type:'shopping',name:'Shinsegae Department Store Main',koName:'신세계백화점 본점',address:'63 Sogong-ro, Jung-gu, Seoul',lat:37.5610,lng:126.9810},
    {id:'lotte-main',areaId:'myeongdong',type:'shopping',name:'Lotte Department Store Main',koName:'롯데백화점 본점',address:'81 Namdaemun-ro, Jung-gu, Seoul',lat:37.5650,lng:126.9820},
    {id:'deoksugung',areaId:'myeongdong',type:'palace',name:'Deoksugung Palace',koName:'덕수궁',address:'99 Sejong-daero, Jung-gu, Seoul',lat:37.5658,lng:126.9751},
    {id:'seoulstation',areaId:'myeongdong',type:'station',name:'Seoul Station',koName:'서울역',address:'405 Hangang-daero, Yongsan-gu, Seoul',lat:37.5547,lng:126.9707},
    {id:'namsan-tower',areaId:'myeongdong',type:'tower',name:'N Seoul Tower',koName:'남산서울타워',address:'105 Namsangongwon-gil, Yongsan-gu, Seoul',lat:37.5512,lng:126.9882},
    {id:'myeongdong-bank',areaId:'myeongdong',type:'services',signWords:['환전'],name:'Example exchange booth · Myeongdong',koName:'명동 환전소',address:'Example place in Myeongdong',lat:37.5640,lng:126.9860,example:true},
    {id:'myeongdong-food',areaId:'myeongdong',type:'street',name:'Myeongdong Street Food Alley',koName:'명동 먹자골목',address:'Myeongdong-gil, Jung-gu, Seoul',lat:37.5636,lng:126.9850},

    // 동대문·을지로
    {id:'ddp',areaId:'ddp',type:'landmark',photo:PHOTO.ddp,name:'Dongdaemun Design Plaza',koName:'동대문디자인플라자',address:'281 Eulji-ro, Jung-gu, Seoul',lat:37.5669,lng:127.0094},
    {id:'gwangjang',areaId:'ddp',type:'market',name:'Gwangjang Market',koName:'광장시장',address:'88 Changgyeonggung-ro, Jongno-gu, Seoul',lat:37.5701,lng:126.9996},
    {id:'cheonggyecheon',areaId:'ddp',type:'park',name:'Cheonggyecheon Stream',koName:'청계천',address:'Cheonggyecheon-ro, Jung-gu, Seoul',lat:37.5692,lng:126.9784},
    {id:'euljiro',areaId:'ddp',type:'street',name:'Euljiro Printing Alleys',koName:'을지로 인쇄골목',address:'Euljiro 3-ga, Jung-gu, Seoul',lat:37.5660,lng:126.9910},
    {id:'ikseon',areaId:'ddp',type:'hanok',name:'Ikseon-dong Hanok Street',koName:'익선동',address:'Ikseon-dong, Jongno-gu, Seoul',lat:37.5740,lng:126.9900},
    {id:'changgyeonggung',areaId:'ddp',type:'palace',name:'Changgyeonggung Palace',koName:'창경궁',address:'185 Changgyeonggung-ro, Jongno-gu, Seoul',lat:37.5783,lng:126.9950},
    {id:'seoulmuseum',areaId:'ddp',type:'museum',name:'Seoul Museum of History',koName:'서울역사박물관',address:'55 Saemunan-ro, Jongno-gu, Seoul',lat:37.5704,lng:126.9700},

    // 성수·서울숲
    {id:'seoulforest',areaId:'seongsu',type:'park',name:'Seoul Forest',koName:'서울숲',address:'273 Ttukseom-ro, Seongdong-gu, Seoul',lat:37.5444,lng:127.0374},
    {id:'seongsu-cafe',areaId:'seongsu',type:'cafe',name:'Seongsu Café Street',koName:'성수동 카페거리',address:'Seongsui-ro, Seongdong-gu, Seoul',lat:37.5445,lng:127.0557},
    {id:'seongsu-ph',areaId:'seongsu',type:'services',signWords:['병원'],name:'Example clinic · Seongsu',koName:'성수 병원',address:'Example place near Seoul Forest',lat:37.5450,lng:127.0500,example:true},
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
    {id:'itaewon-bank',areaId:'yongsan',type:'services',signWords:['은행','환전'],name:'Example bank branch · Itaewon',koName:'이태원 은행',address:'Example place in Itaewon',lat:37.5340,lng:126.9950,example:true},

    // 여의도·한강
    {id:'yeouido-park',areaId:'yeouido',type:'park',name:'Yeouido Hangang Park',koName:'여의도한강공원',address:'330 Yeouidong-ro, Yeongdeungpo-gu, Seoul',lat:37.5285,lng:126.9340},
    {id:'thehyundai',areaId:'yeouido',type:'shopping',name:'The Hyundai Seoul',koName:'더현대 서울',address:'108 Yeoui-daero, Yeongdeungpo-gu, Seoul',lat:37.5259,lng:126.9285},
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
    ['service','우체국','ucheguk','Post office','Red and white branding across the country.'],
    // ---- v2.4 확충 (31~100). 카드 ID가 배열 순서로 매겨지므로 기존 30장 뒤에만 붙인다. ----
    ['restroom','공중 화장실','gongjung hwajangsil','Public restroom','Free to use. Parks, stations and tourist streets point to the nearest one.'],
    ['restroom','사용중','sayongjung','Occupied','On restroom and fitting-room doors. 중 means in the middle of.'],
    ['restroom','휴지통','hyujitong','Waste bin','In many restrooms the sign asks you to put paper in the bin, not the toilet.'],
    ['restroom','기저귀 교환대','gijeogwi gyohwandae','Baby changing table','Found in both men’s and women’s restrooms in newer buildings.'],
    ['restroom','비데','bide','Bidet','Most Korean toilets have one. The buttons are labelled in Korean too.'],
    ['restroom','흡연실','heubyeonsil','Smoking room','Smoking is only allowed in these rooms or marked zones.'],
    ['restroom','탈의실','taruisil','Changing room','In shops, gyms and swimming pools. 실 at the end means room.'],
    ['restroom','음수대','eumsudae','Drinking fountain','Free drinking water in parks and public buildings.'],
    ['restroom','남녀 공용','namnyeo gongyong','Unisex','One restroom shared by men and women, common in small cafés.'],
    ['restroom','손 세정제','son sejeongje','Hand sanitizer','By lifts, entrances and counters.'],
    ['door','자동문','jadongmun','Automatic door','Wait for it to open. 문 means door.'],
    ['door','열림','yeollim','Open (button)','On lift buttons, next to 닫힘.'],
    ['door','닫힘','dachim','Close (button)','The other lift button. Press 열림 if someone is still coming.'],
    ['door','정문','jeongmun','Main gate','The front entrance of a palace, school or park.'],
    ['door','후문','humun','Back gate','Often closer to the subway than the main gate.'],
    ['door','좌측 통행','jwacheuk tonghaeng','Keep left','On some stairs and corridors. 좌 is left.'],
    ['door','우측 통행','ucheuk tonghaeng','Keep right','The usual rule in stations. 우 is right.'],
    ['door','직진','jikjin','Go straight','On arrows in stations and malls.'],
    ['door','우회','uhoe','Detour','The usual way is blocked. Follow the arrow around.'],
    ['door','회전문','hoejeonmun','Revolving door','Hotels and department stores. A side door is usually next to it.'],
    ['door','비상계단','bisang gyedan','Emergency stairs','비상 means emergency, the same word as in 비상구.'],
    ['door','옥상','oksang','Rooftop','On lift buttons and stairwell signs. Some rooftops are cafés or gardens.'],
    ['door','지하 1층','jiha il-cheung','Basement level 1','층 means floor. Lifts show it as B1.'],
    ['door','문 닫아 주세요','mun dada juseyo','Please close the door','On doors that let the heating or cooling out.'],
    ['door','안내','annae','Information','A desk or board where you can ask the way.'],
    ['hours','영업시간','yeongeop sigan','Business hours','Followed by the times, for example 11:00~21:00.'],
    ['hours','브레이크 타임','beureikeu taim','Break time','Many restaurants close between lunch and dinner, often 15:00~17:00.'],
    ['hours','정기 휴무','jeonggi hyumu','Regular closing day','매주 월요일 정기 휴무 means closed every Monday.'],
    ['hours','연중무휴','yeonjung muhyu','Open every day of the year','No day off at all.'],
    ['hours','오픈','opeun','Open','A loanword used on new shops, often with a date: 10월 오픈.'],
    ['hours','마감','magam','Closing / last entry','입장 마감 means last admission.'],
    ['hours','라스트 오더','raseuteu odeo','Last order','The kitchen stops taking orders after this time.'],
    ['hours','점심시간','jeomsim sigan','Lunch break','Banks, post offices and clinics may pause service.'],
    ['hours','매진','maejin','Sold out','On ticket windows and food stalls.'],
    ['hours','임시 휴업','imsi hyueop','Temporarily closed','The shop is closed for a while, not for good.'],
    ['hours','평일','pyeongil','Weekdays','Opposite of 주말 (weekend). Hours often differ between them.'],
    ['warning','위험','wiheom','Danger','Usually on a red or yellow sign. Stay back.'],
    ['warning','주차 금지','jucha geumji','No parking','금지 again — anything followed by it is forbidden.'],
    ['warning','음식물 반입 금지','eumsingmul banip geumji','No food or drink inside','반입 means bringing in. Common at museums and theatres.'],
    ['warning','손대지 마세요','sondaeji maseyo','Do not touch','On exhibits and shop displays.'],
    ['warning','발밑 조심','balmit josim','Watch your step','발밑 is under your feet. Gaps, steps and uneven floors.'],
    ['warning','공사중','gongsajung','Under construction','Expect detours or closed exits.'],
    ['warning','반려동물 동반 금지','ballyeodongmul dongban geumji','No pets','반려동물 is a companion animal.'],
    ['warning','소화기','sohwagi','Fire extinguisher','Red cabinets in stations and corridors.'],
    ['warning','고장','gojang','Out of order','On lifts, ticket machines and vending machines.'],
    ['warning','화기 엄금','hwagi eomgeum','No open flames','A stronger form of no fire, near fuel or gas.'],
    ['transit','에스컬레이터','eseukeolleiteo','Escalator','A loanword. Stand still or walk carefully.'],
    ['transit','택시 타는 곳','taeksi taneun got','Taxi stand','타는 곳 means where you board.'],
    ['transit','내리는 문','naerineun mun','Exit doors (this side)','The subway screen shows which side the doors will open.'],
    ['transit','노약자석','noyakjaseok','Priority seating','For older people, pregnant women and people with disabilities.'],
    ['transit','임산부 배려석','imsanbu baeryeoseok','Seat for pregnant women','Pink seats on Seoul subways. Leave them empty.'],
    ['transit','첫차','cheotcha','First train / bus','차 means vehicle.'],
    ['transit','막차','makcha','Last train / bus','Check it before a late night out.'],
    ['transit','급행','geupaeng','Express','Stops at fewer stations. Some lines run both.'],
    ['transit','종점','jongjeom','Last stop','Everyone gets off here.'],
    ['transit','방면','bangmyeon','Bound for','시청 방면 means toward City Hall. Pick the platform by it.'],
    ['transit','횡단보도','hoengdan bodo','Pedestrian crossing','Painted stripes on the road.'],
    ['transit','주차장','juchajang','Car park','장 at the end means place.'],
    ['transit','공항','gonghang','Airport','On airport railroad and bus signs.'],
    ['transit','하차','hacha','Get off','Bus card readers at the back door say 하차. Tap again when you get off.'],
    ['service','무료','muryo','Free of charge','무 means without. 무료 입장 is free entry.'],
    ['service','셀프','selpeu','Self-service','Water, side dishes or returning trays are up to you.'],
    ['service','계산대','gyesandae','Checkout','Where you pay. 계산 means paying the bill.'],
    ['service','영수증','yeongsujeung','Receipt','Machines ask if you want one printed.'],
    ['service','휴대폰 충전','hyudaepon chungjeon','Phone charging','Charging stations in cafés, stations and malls.'],
    ['service','빨래방','ppallaebang','Laundromat','Coin-operated, often open 24 hours.'],
    ['service','미용실','miyongsil','Hair salon','You will see one on almost every block.'],
    ['service','경찰서','gyeongchalseo','Police station','Smaller local posts are called 파출소 or 지구대.'],
    ['service','관광안내소','gwangwang annaeso','Tourist information','Maps and help in English. Look for the i sign.'],
    ['service','분리수거','bullisugeo','Recycling','Bins are split by type: 일반 (general), 플라스틱, 캔, 종이.']
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

// ---------- 표지판 픽토그램 (PRD §8.3, v2.6) ----------
// 실제로 그림과 함께 쓰이는 표지판만. 카드 뒷면에서만 그린다 — 앞면에 그림이 있으면 글자를 읽지 않고도 맞힐 수 있다.
// 그림은 app.js 의 PICTOGRAMS 에서 공공 기호를 단순화해 직접 그린 SVG.
// 출구(일반 출구)에는 그리지 않는다. exit 그림은 달리는 사람 + 문, 곧 비상구 기호(ISO 7010 E001)라 일반 출구를 비상구로 오해하게 한다.
DATA.pictograms = {
  '화장실':'restroom', '공중 화장실':'restroom', '남자 화장실':'men', '여자 화장실':'women', '장애인 화장실':'accessible', '비상구':'exit', '엘리베이터':'elevator', '에스컬레이터':'escalator', '계단':'stairs', '비상계단':'stairs', '지하철':'subway', '버스 정류장':'bus', '금연':'noSmoking', '촬영 금지':'noPhoto', '와이파이':'wifi', '약국':'pharmacy', '수유실':'nursing', '기저귀 교환대':'babyChange', '안내':'info', '관광안내소':'info', '현금인출기':'atm', '택시 타는 곳':'taxi', '주차장':'parking', '공항':'airport', '보관함':'lockers', '휴지통':'wasteBin', '음수대':'drinkingWater', '출입 금지':'noEntry', '미끄럼 주의':'caution', '위험':'caution', '소화기':'fireExtinguisher', '휴대폰 충전':'phoneCharging', '흡연실':'smokingRoom', '반려동물 동반 금지':'noPets', '음식물 반입 금지':'noFood', '분리수거':'recycling', '횡단보도':'crossing', '경찰서':'police', '환승':'transfer'
};

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
  pictogram: DATA.pictograms[s[0]] || null,
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
    pictogram: DATA.pictograms[s[1]] || null,
    image: null,
    alt: 'Street sign plate showing ' + s[1]
  });
});

// 조회 도우미
// signWords가 있는 장소는 그 단어의 카드에만 연결한다 (유형 전체를 주장하지 않는다)
const showsSign = (p, c) => p.type === c.typeId && (!p.signWords || p.signWords.includes(c.word));
DATA.placesForCard = card => card.typeId ? DATA.places.filter(p => showsSign(p, card)) : [];
DATA.cardsForPlace = place => DATA.cards.filter(c => showsSign(place, c));
DATA.cardsForArea  = areaId => {
  const types = [...new Set(DATA.places.filter(p => p.areaId === areaId).map(p => p.type))];
  return DATA.cards.filter(c => types.includes(c.typeId));
};
