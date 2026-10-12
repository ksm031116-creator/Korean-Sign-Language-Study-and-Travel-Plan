"""TourAPI(한국관광공사 국문 관광정보 서비스 KorService2)에서 등록 장소의 대표 사진을 받아온다.

실행:  py -X utf8 .work/fetch_tourapi.py
키:    저장소 루트의 .env 에 TOUR_API_KEY=<일반 인증키(Decoding)>  (.gitignore 로 제외됨)
결과:  prototype/tour_photos.js  — 사진 주소와 라이선스 정보만 담는다. 키는 들어가지 않는다.
       .work/tourapi_report.txt — 장소별 매칭 결과 (채택·거부 사유)

검색 결과를 그대로 믿지 않는다. 카드→장소 연결이 사실 주장인 것처럼(BUGFIX_LOG),
사진→장소 연결도 사실 주장이다. 이름이 맞고 좌표가 가까운 결과만 채택한다.
"""
from pathlib import Path
from datetime import date
import json, math, re, time, urllib.parse, urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = 'https://apis.data.go.kr/B551011/KorService2/searchKeyword2'
MAX_KM = 1.5            # 등록 좌표와 이보다 멀면 다른 장소로 본다
# 관광지·문화시설·쇼핑만. 행사(15)·여행코스(25)·숙박(32)·음식점(39)은 장소 사진이 아니다.
PLACE_TYPES = {'12', '14', '38'}
LICENSES = {'Type1': '공공누리 제1유형', 'Type3': '공공누리 제3유형'}  # 상업적 이용 가능한 유형만


def load_key():
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('TOUR_API_KEY='):
            return line.split('=', 1)[1].strip()
    raise SystemExit('.env 에 TOUR_API_KEY 가 없습니다')


def load_places():
    """data.js 의 places 배열에서 필요한 필드만 읽는다."""
    src = (ROOT / 'prototype' / 'data.js').read_text(encoding='utf-8')
    start = src.index('places: [')
    block = src[start:src.index('\n  ],', start)]  # signWords:[...], 안의 '],' 와 구분
    places = []
    for row in re.findall(r'\{id:.*?\}(?=,?\s*\n)', block):
        get = lambda k: (re.search(k + r":'([^']*)'", row) or [None, None])[1]
        num = lambda k: float(re.search(k + r':([\d.]+)', row)[1])
        places.append({'id': get('id'), 'koName': get('koName'), 'lat': num('lat'), 'lng': num('lng'),
                       'example': 'example:true' in row, 'hasPhoto': 'photo:' in row})
    return places


def norm(s):
    s = re.sub(r'\[.*?\]|\(.*?\)', '', s or '')        # '종묘 [유네스코 세계유산]', '남산공원(서울)'
    return re.sub(r'[\s·]', '', s)


def km(lat1, lng1, lat2, lng2):
    p = math.pi / 180
    a = math.sin((lat2 - lat1) * p / 2) ** 2 + math.cos(lat1 * p) * math.cos(lat2 * p) * math.sin((lng2 - lng1) * p / 2) ** 2
    return 12742 * math.asin(math.sqrt(a))


def search(key, keyword):
    q = urllib.parse.urlencode({'serviceKey': key, 'MobileOS': 'ETC', 'MobileApp': 'SignKorean', '_type': 'json',
                                'keyword': keyword, 'numOfRows': 20, 'pageNo': 1, 'arrange': 'A'})
    body = json.loads(urllib.request.urlopen(BASE + '?' + q, timeout=20).read().decode('utf-8'))
    head = body['response']['header']
    if head['resultCode'] != '0000':
        raise RuntimeError(head['resultMsg'])
    items = body['response']['body']['items']
    items = items['item'] if items else []
    return items if isinstance(items, list) else [items]


def pick(place, items):
    """이름이 맞고 좌표가 가까운 후보 중 사진이 있는 것을 고른다. (채택, 거부 사유)"""
    want = norm(place['koName'])
    best, reasons = None, []
    for it in items:
        title = norm(it.get('title'))
        if it.get('contenttypeid') not in PLACE_TYPES:
            continue
        # 장소 이름으로 시작해야 한다. 'GS25 남산서울타워점'처럼 앞에 다른 상호가 붙거나
        # '다이소 서울역점'처럼 지점인 결과는 그 장소가 아니라 그 안·근처의 다른 가게다.
        if not title.startswith(want) or (title != want and title.endswith('점')):
            continue
        d = km(place['lat'], place['lng'], float(it['mapy']), float(it['mapx']))
        if d > MAX_KM:
            reasons.append(f"{it['title']} {d:.1f}km 떨어짐")
            continue
        if not it.get('firstimage'):
            reasons.append(f"{it['title']} 사진 없음")
            continue
        if it.get('cpyrhtDivCd') not in LICENSES:
            reasons.append(f"{it['title']} 라이선스 {it.get('cpyrhtDivCd')!r} 제외")
            continue
        exact = title == want
        if best is None or (exact, -d) > best[0]:
            best = ((exact, -d), it, d)
    return (best[1], best[2]) if best else (None, '; '.join(reasons) or '이름이 맞는 결과 없음')


def main():
    key = load_key()
    photos, report = {}, []
    for p in load_places():
        if p['example']:
            report.append(f"SKIP  {p['id']:<22} 예시 장소 (실제 상호 아님)")
            continue
        if p['hasPhoto']:
            report.append(f"KEEP  {p['id']:<22} 기존 Wikimedia 사진 유지 (assets/credits.json)")
            continue
        try:
            it, why = pick(p, search(key, p['koName']))
        except Exception as e:
            report.append(f"ERROR {p['id']:<22} {str(e).replace(key, '<KEY>')}")
            continue
        if it is None:
            report.append(f"NONE  {p['id']:<22} {p['koName']} — {why}")
        else:
            photos[p['id']] = {'contentId': it['contentid'], 'title': it['title'], 'image': it['firstimage'].replace('http://', 'https://', 1),  # http 사진은 https 페이지에서 차단된다
                               'license': it['cpyrhtDivCd'], 'licenseName': LICENSES[it['cpyrhtDivCd']]}
            report.append(f"OK    {p['id']:<22} {p['koName']} → {it['title']} ({why:.2f}km, {it['cpyrhtDivCd']})")
        time.sleep(0.2)

    out = ROOT / 'prototype' / 'tour_photos.js'
    out.write_text(
        '/* 자동 생성 파일 — .work/fetch_tourapi.py 로 다시 만든다. 직접 고치지 않는다.\n'
        f' * 출처: 한국관광공사 TourAPI (KorService2) · 생성일 {date.today()}\n'
        ' * 공공누리 제1유형: 출처표시 / 제3유형: 출처표시 + 변경금지(자르기·필터 금지)\n */\n'
        'window.TOUR_PHOTOS = ' + json.dumps(photos, ensure_ascii=False, indent=1) + ';\n', encoding='utf-8')
    (ROOT / '.work' / 'tourapi_report.txt').write_text('\n'.join(report) + '\n', encoding='utf-8')
    print('\n'.join(report))
    print(f'\n사진 {len(photos)}곳 → {out.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
