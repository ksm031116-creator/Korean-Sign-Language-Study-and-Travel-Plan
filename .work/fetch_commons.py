"""TourAPI에 맞는 항목이 없는 장소의 사진을 Wikimedia Commons에서 받아온다.

실행:  py -X utf8 .work/fetch_commons.py
결과:  prototype/commons_photos.js — 사진 주소·저작자·라이선스만 담는다.

PICKS 는 사람이 사진을 직접 보고 그 장소가 맞는지 확인한 파일만 적는다.
검색 결과를 자동으로 고르지 않는다. 사진→장소 연결은 사실 주장이다 (BUGFIX_LOG BUG-02).
라이선스는 실행할 때 Commons에서 다시 읽어 허용 범위(CC0·Public domain·CC BY)를
벗어나면 멈춘다. CC BY-SA·NC 계열은 PRD §11.2에 따라 쓰지 않는다.
"""
from pathlib import Path
from datetime import date
import json, re, time, urllib.error, urllib.parse, urllib.request

ROOT = Path(__file__).resolve().parents[1]
API = 'https://commons.wikimedia.org/w/api.php'
UA = {'User-Agent': 'SignKoreanCoursework/0.1 (student project; ksm031116-creator on GitHub)'}
WIDTH = 1280

# 장소 id → Commons 파일 이름. 확인 근거(제목·설명·좌표·사진 내용)는 커밋 본문에 남긴다.
# 연남동 카페거리는 허용 라이선스로 그 장소를 보여 주는 사진을 찾지 못해 비워 둔다.
# (근처의 다른 장소 사진 — 경의선숲길, TourAPI 의 연남동 공방거리(도자기 공방) 등 — 을 대신 붙이지 않는다.)
# 을지로 인쇄골목은 2026-10-23 다시 찾아 CC0 사진을 붙였다(업로더 설명 "을지로 인쇄골목", 좁은 골목의 작은 가게들).
PICKS = {
    'samcheong': 'Seoul-Samcheong.dong-01.jpg',
    'shinsegae': '新世界百貨店 - panoramio.jpg',
    'seoulstation': 'Seoul Station (145738821).jpeg',
    'namsan-tower': 'N Seoul Tower (13952097192).jpg',
    'myeongdong-food': 'Seoul-Myeongdong-03.jpg',
    'cheonggyecheon': 'Seoul (53853383087).jpg',
    'ttukseom': '20260501 뚝섬역 역명판.jpg',
    'hongdae-station': '20250510 홍대입구역 3번출구.jpg',
    'gyeongui-park': '20250510 경의선숲길 1.jpg',
    'hongdae-street': 'Hongdae, Seoul- Part II - Hongdae2237.jpg',
    'itaewon': 'Itaewon Street (230036983).jpeg',
    'yeouinaru': '20250727 여의나루역 1번출구.jpg',
    'coex': 'Starfield Library COEX 20240218.jpg',
    'gangnam-station': 'Gangnam Station sign.jpg',
    'garosugil': 'Garosu-gil at night.jpg',
    'euljiro': '20150514최광모DSC00447.JPG',
}


def fetch(url):
    for n in range(6):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read()
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(15 * (n + 1))
    raise RuntimeError('rate limited')


def allowed(lic):
    l = lic.lower()
    if re.search(r'\b(nc|sa)\b', l.replace('-', ' ')):
        return False
    return l.startswith('cc0') or 'public domain' in l or re.match(r'cc by \d', l) is not None


def main():
    q = urllib.parse.urlencode({'action': 'query', 'format': 'json', 'formatversion': 2, 'prop': 'imageinfo',
                                'iiprop': 'url|extmetadata', 'iiurlwidth': WIDTH,
                                'titles': '|'.join('File:' + f for f in PICKS.values())})
    pages = {p['title']: p for p in json.loads(fetch(API + '?' + q))['query']['pages']}
    photos = {}
    for pid, name in PICKS.items():
        page = pages.get('File:' + name.replace('_', ' '))
        if not page or 'imageinfo' not in page:
            raise SystemExit(f'{pid}: Commons에 파일이 없습니다 — {name}')
        ii = page['imageinfo'][0]; m = ii['extmetadata']
        lic = m['LicenseShortName']['value']
        if not allowed(lic):
            raise SystemExit(f'{pid}: 허용하지 않는 라이선스 {lic!r} — {name}')
        artist = re.sub(r'<[^>]+>', '', m.get('Artist', {}).get('value', '')).strip() or 'Unknown'
        photos[pid] = {'file': name, 'image': ii['thumburl'], 'source': ii['descriptionurl'],
                       'author': artist, 'license': lic, 'licenseUrl': m.get('LicenseUrl', {}).get('value', '')}
        print(f'OK  {pid:<16} {lic:<12} {artist[:30]:<30} {name}')
    out = ROOT / 'prototype' / 'commons_photos.js'
    out.write_text(
        '/* 자동 생성 파일 — .work/fetch_commons.py 로 다시 만든다. 직접 고치지 않는다.\n'
        f' * 출처: Wikimedia Commons · 생성일 {date.today()}\n'
        ' * 허용 라이선스: CC0 · Public domain · CC BY (저작자·라이선스 표시)\n */\n'
        'window.COMMONS_PHOTOS = ' + json.dumps(photos, ensure_ascii=False, indent=1) + ';\n', encoding='utf-8')
    print(f'\n사진 {len(photos)}곳 → {out.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
