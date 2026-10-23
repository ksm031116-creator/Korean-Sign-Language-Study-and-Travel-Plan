"""등록 장소의 좌표를 공개 출처에서 다시 받아 prototype/data.js 에 넣는다.

실행:  py -X utf8 .work/fetch_coordinates.py [--check]
결과:  prototype/data.js 의 lat·lng (소수 6자리), .work/coordinates_report.txt (장소별 출처)

출처 우선순위
  1. Wikidata 좌표(P625) — CC0. 그 장소 자체를 가리키는 항목만(아래 WIKIDATA). 건물 안의 전각·근처 역 등은 쓰지 않는다.
  2. TourAPI 좌표(mapy·mapx) — 한국관광공사, 공공누리 제1유형(출처표시). Wikidata 에 없는 곳만(아래 TOURAPI).
  3. 둘 다 없으면 지금 좌표를 그대로 두고 '근사치'로 적는다. 거리·골목은 한 점으로 정한 공개 출처가 없다 —
     동(洞)의 중심점을 대신 쓰면 그 거리가 아닌 곳을 가리키게 된다.
카카오 검색 결과는 쓰지 않는다(API 결과를 데이터로 저장하는 것은 운영정책 위반 소지).

항목은 사람이 후보를 보고 고른 것이다(2026-10-23). 검색 결과를 자동으로 고르지 않는다 — 장소 연결은 사실 주장이다(BUG-02).
받은 좌표가 지금 좌표에서 MAX_SHIFT_KM 넘게 떨어지면 멈춘다(엉뚱한 항목을 고른 경우).
--check: 파일을 쓰지 않고 data.js 가 출처와 같은지만 본다.
"""
from pathlib import Path
import json, math, re, sys, time, urllib.parse, urllib.request

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'prototype' / 'data.js'
REPORT = ROOT / '.work' / 'coordinates_report.txt'
UA = {'User-Agent': 'SignKoreanCoursework/0.1 (student project; ksm031116-creator on GitHub)'}
MAX_SHIFT_KM = 0.6

WIKIDATA = {
    'gyeongbokgung': 'Q482485', 'changdeokgung': 'Q477157', 'jongmyo': 'Q490497', 'folkmuseum': 'Q486449',
    'mmca': 'Q27207690', 'bukchon': 'Q490981', 'tongin': 'Q54858471', 'insadong': 'Q61097753',
    'gwanghwamun': 'Q5623300', 'myeongdong-cathedral': 'Q487036', 'namdaemun': 'Q494687', 'deoksugung': 'Q482631',
    'seoulstation': 'Q20415', 'namsan-tower': 'Q69134', 'ddp': 'Q5295847', 'gwangjang': 'Q12585067',
    'cheonggyecheon': 'Q495437', 'ikseon': 'Q16188499', 'changgyeonggung': 'Q482852', 'seoulmuseum': 'Q484862',
    'seoulforest': 'Q483014', 'ttukseom': 'Q488263', 'hongdae-station': 'Q54267', 'gyeongui-park': 'Q24861751',
    'nmk': 'Q494407', 'warmemorial': 'Q489764', 'namsan-park': 'Q27307220', 'yeouinaru': 'Q490655',
    'coex': 'Q490387', 'lotteworldtower': 'Q494895', 'olympicpark': 'Q486694', 'gangnam-station': 'Q17469',
}
# TourAPI contentId (tour_photos.js 와 같은 항목).
TOURAPI = {'lotte-main': None, 'seongsu-cafe': None, 'mangwon': None, 'yeouido-park': None, 'thehyundai': None}


def get(url):
    for i in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.load(r)
        except Exception:
            time.sleep(5 * (i + 1))
    raise RuntimeError(url.split('serviceKey')[0])


def km(a, b, c, d):
    p1, p2 = math.radians(a), math.radians(c)
    h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(d - b) / 2) ** 2
    return 12742 * math.asin(math.sqrt(h))


def tour_key():
    env = (ROOT / '.env').read_text(encoding='utf-8')
    return re.search(r'^\s*TOUR_API_KEY\s*=\s*(\S+)', env, re.M).group(1).strip('"\'')


def main(check: bool):
    src = DATA.read_text(encoding='utf-8')
    tour = json.loads(re.search(r'window\.TOUR_PHOTOS\s*=\s*(\{.*\});', (ROOT / 'prototype' / 'tour_photos.js').read_text(encoding='utf-8'), re.S).group(1))
    for pid in TOURAPI:
        TOURAPI[pid] = tour[pid]['contentId']

    ents = {}
    ids = list(WIKIDATA.values())
    for k in range(0, len(ids), 40):
        q = urllib.parse.urlencode({'action': 'wbgetentities', 'ids': '|'.join(ids[k:k + 40]), 'props': 'claims', 'format': 'json'})
        ents.update(get('https://www.wikidata.org/w/api.php?' + q)['entities'])
    found = {}
    for pid, qid in WIKIDATA.items():
        v = ents[qid]['claims']['P625'][0]['mainsnak']['datavalue']['value']
        found[pid] = (v['latitude'], v['longitude'], f'Wikidata {qid} (CC0)')
    key = tour_key()
    for pid, cid in TOURAPI.items():
        q = urllib.parse.urlencode({'serviceKey': key, 'MobileOS': 'ETC', 'MobileApp': 'SignKorean', '_type': 'json', 'contentId': cid}, safe='%')
        it = get('https://apis.data.go.kr/B551011/KorService2/detailCommon2?' + q)['response']['body']['items']['item'][0]
        found[pid] = (float(it['mapy']), float(it['mapx']), f'TourAPI {cid} (공공누리 제1유형)')

    lines, out, changed = [], src, 0
    pattern = re.compile(r"(\{id:'(?P<id>[^']+)',areaId:[^\n]*?lat:)(?P<lat>[\d.]+)(,lng:)(?P<lng>[\d.]+)")
    for m in pattern.finditer(src):
        pid, lat, lng = m.group('id'), float(m.group('lat')), float(m.group('lng'))
        example = 'example' in m.group(0).lower() or '-ph' in pid or pid.endswith('-bank')
        if pid not in found:
            lines.append(f"{pid:<22} {lat:.6f},{lng:.6f}  {'예시 장소(실제 상호 아님)' if example else '근사치 — 한 점으로 정한 공개 출처 없음'}")
            continue
        nlat, nlng, source = found[pid]
        shift = km(lat, lng, nlat, nlng)
        if shift > MAX_SHIFT_KM:
            raise SystemExit(f'{pid}: {source} 좌표가 {shift:.2f}km 떨어져 있다 — 항목을 다시 확인한다')
        lines.append(f'{pid:<22} {nlat:.6f},{nlng:.6f}  {source}  (이전 좌표에서 {shift * 1000:.0f}m)')
        new = f'{m.group(1)}{nlat:.6f}{m.group(4)}{nlng:.6f}'
        if new != m.group(0):
            changed += 1
            out = out.replace(m.group(0), new, 1)
    report = '등록 장소 좌표 출처 (.work/fetch_coordinates.py 가 만든다)\n\n' + '\n'.join(lines) + '\n'
    if check:
        if changed:
            raise SystemExit(f'data.js 좌표 {changed}곳이 출처와 다르다 — 스크립트를 다시 실행한다')
        print('OK: data.js 좌표가 출처와 같다')
        return
    DATA.write_text(out, encoding='utf-8')
    REPORT.write_text(report, encoding='utf-8')
    print(report)
    print(f'바꾼 곳 {changed}, 출처 있음 {len(found)}')


if __name__ == '__main__':
    main('--check' in sys.argv)
