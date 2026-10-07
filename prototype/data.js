window.DATA = {
  landmarks: [
    {id:'palace',name:'Gyeongbokgung Palace',koName:'경복궁',description:'Palace courtyards, quiet lanes and everyday Korean signs in nearby Seochon.',address:'161 Sajik-ro, Jongno-gu, Seoul',lat:37.5796,lng:126.977,image:'assets/gyeongbokgung.jpg',tag:'History & quiet streets'},
    {id:'myeongdong',name:'Myeongdong',koName:'명동',description:'A lively neighborhood of shops, cafés and food, anchored by Myeongdong Cathedral.',address:'Myeongdong, Jung-gu, Seoul',lat:37.5633,lng:126.9872,image:'assets/myeongdong.jpg',tag:'Food & shopping'},
    {id:'ddp',name:'Dongdaemun Design Plaza',koName:'동대문디자인플라자',description:'Curved architecture and a design district full of little discoveries.',address:'281 Eulji-ro, Jung-gu, Seoul',lat:37.5669,lng:127.0094,image:'assets/ddp.jpg',tag:'Design & city walks'}
  ],

  // SC02/SC03 Basics — 장소에 연결되지 않는 공공·범용 표지판 (FR30·FR31)
  basics: {
    id:'basics',
    name:'Everyday street signs',
    koName:'기초 간판',
    tag:'Start here',
    description:'The signs you meet before you meet anyone. Doors, restrooms, opening hours, stations. No shop, no place — just the words the city repeats everywhere.'
  },

  // 기초 표지판 분류 (PRD §8.1) — 출시 목표 100장, 아래는 프로토타입 표본
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
    ['service','약국','yakguk','Pharmacy','Usually with a green cross outside.'],
    ['service','병원','byeongwon','Clinic / hospital','치과 is a dental clinic, 내과 is internal medicine.'],
    ['service','은행','eunhaeng','Bank','ATM machines are labelled ATM or 현금인출기.'],
    ['service','우체국','ucheguk','Post office','Red and white branding across the country.']
  ],

  places: [
    {id:'p1',landmarkId:'palace',name:'Seochon Pharmacy',koName:'서촌 약국',address:'Example place near Gyeongbokgung',lat:37.578,lng:126.972,type:'Pharmacy'},
    {id:'p2',landmarkId:'palace',name:'Garden Café',koName:'정원 카페',address:'Example place near Gyeongbokgung',lat:37.580,lng:126.974,type:'Café'},
    {id:'p3',landmarkId:'myeongdong',name:'Myeongdong Dumplings',koName:'명동 만두',address:'Example place in Myeongdong',lat:37.562,lng:126.985,type:'Restaurant'},
    {id:'p4',landmarkId:'myeongdong',name:'Corner Pharmacy',koName:'모퉁이 약국',address:'Example place in Myeongdong',lat:37.564,lng:126.986,type:'Pharmacy'},
    {id:'p5',landmarkId:'ddp',name:'Design Café',koName:'디자인 카페',address:'Example place near DDP',lat:37.566,lng:127.006,type:'Café'},
    {id:'p6',landmarkId:'ddp',name:'Little Dumpling House',koName:'작은 만두집',address:'Example place near DDP',lat:37.568,lng:127.008,type:'Restaurant'}
  ],
  cards: [],
  reviews: [
    {id:'r1',placeId:'p1',author:'Alex',rating:5,date:'2026-09-12',text:'An easy-to-spot pharmacy sign. A useful word to recognize when walking around the neighborhood.',original:'골목에서 약국 간판을 쉽게 찾을 수 있었어요.',translated:true},
    {id:'r2',placeId:'p2',author:'Jamie',rating:4,date:'2026-09-18',text:'A quiet café for a short break after the palace. The coffee sign helped me find the entrance.',original:'궁을 둘러본 뒤 잠깐 쉬기 좋은 조용한 카페예요.',translated:true},
    {id:'r3',placeId:'p3',author:'Morgan',rating:5,date:'2026-09-20',text:'A nice lunch stop on our walk. Recognizing the takeout sign made ordering a little easier.',original:'산책하다 점심 먹기 좋았어요. 포장도 가능했어요.',translated:true},
    {id:'r4',placeId:'p4',author:'Taylor',rating:4,date:'2026-09-14',text:'The large sign was easy to read from across the street.',original:'길 건너에서도 큰 간판이 잘 보였어요.',translated:true},
    {id:'r5',placeId:'p5',author:'Sam',rating:4,date:'2026-09-22',text:'A small coffee break before exploring the design plaza.',original:'디자인플라자를 구경하기 전에 커피를 마셨어요.',translated:true},
    {id:'r6',placeId:'p6',author:'Casey',rating:5,date:'2026-09-23',text:'A cozy stop with a clear dumpling sign. We took our food to go.',original:'만두 간판이 잘 보이는 아늑한 가게예요. 포장했어요.',translated:true}
  ],
  originPoints:[{id:'seoulstation',name:'Seoul Station',koName:'서울역',lat:37.5547,lng:126.9707},{id:'cityhall',name:'Seoul City Hall',koName:'서울시청',lat:37.5663,lng:126.9779}],
  routeMinutes:{'palace|p1':7,'p1|p2':4,'palace|p2':8,'myeongdong|p3':5,'p3|p4':4,'ddp|p5':6,'p5|p6':5},
  photoCredits:[
    {title:'Gyeonghoeru Pavilion',author:'Suohros',license:'CC0',url:'https://commons.wikimedia.org/wiki/File:Gyeonghoeru_Pavilion_at_Gyeongbokgung_Palace.jpg',licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/'},
    {title:'Myeongdong Cathedral',author:'Sean Young (@assanges)',license:'CC BY 4.0',url:'https://commons.wikimedia.org/wiki/File:Myeongdong_Cathedral_20240217.jpg',licenseUrl:'https://creativecommons.org/licenses/by/4.0/'},
    {title:'Dongdaemun Design Plaza',author:'Tycho Atsma',license:'CC0',url:'https://commons.wikimedia.org/wiki/File:DongDaemun_Design_Plaza_(동대문디자인플라자_(DDP)),_Seoul,_South_Korea_(Unsplash_aNSniQXO424).jpg',licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/'},
    {title:'Three example storefront photos',author:'AI-generated for this prototype',license:'Fictional examples',url:''},
    {title:'Everyday street signs',author:'Drawn in-app as SVG plates',license:'Prototype illustration',url:''}
  ]
};

// ---- 랜드마크 간판 카드: [원문, 로마자, 뜻, 설명, 이미지키] ----
const signSets = {
  Pharmacy:[
    ['약국','yakguk','Pharmacy','Look for this word when you need a pharmacy.','pharmacy'],
    ['영업중','yeongeopjung','Open for business','A common sign showing a shop is currently open.','pharmacy']
  ],
  'Café':[
    ['카페','kape','Café','A familiar loanword you can spot on many café signs.','cafe'],
    ['커피','keopi','Coffee','A useful word when looking for a coffee break.','cafe']
  ],
  Restaurant:[
    ['만두','mandu','Dumplings','A common dish name on restaurant signs.','dumpling'],
    ['포장','pojang','Takeout','Often used to show that takeaway orders are available.','dumpling']
  ]
};

DATA.places.forEach(p => signSets[p.type].forEach((s,i) => DATA.cards.push({
  id: p.id + 'c' + (i+1),
  collectionId: p.landmarkId,
  kind: 'landmark',
  placeId: p.id,
  landmarkId: p.landmarkId,
  word: s[0],
  roman: s[1],
  meaning: s[2],
  note: s[3],
  image: 'assets/' + s[4] + '.jpg',
  alt: 'Example Korean storefront showing ' + s[0]
})));

// ---- 기초 표지판 카드: 장소 없음(placeId null) → 리뷰·지도·일정 진입점 없음 ----
DATA.basicSigns.forEach((s,i) => {
  const group = DATA.basicGroups.find(g => g.id === s[0]);
  DATA.cards.push({
    id: 'b' + String(i+1).padStart(2,'0'),
    collectionId: 'basics',
    kind: 'basic',
    placeId: null,
    landmarkId: null,
    groupId: s[0],
    groupLabel: group.label,
    color: group.color,
    word: s[1],
    roman: s[2],
    meaning: s[3],
    note: s[4],
    image: null,
    alt: 'Street sign plate showing ' + s[1]
  });
});
