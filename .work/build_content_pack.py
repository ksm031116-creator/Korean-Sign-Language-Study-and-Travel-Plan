"""프로토타입 데이터 → Android 콘텐츠 팩 (IMPLEMENTATION_PLAN Phase 2 D2).

입력: prototype/data.js, tour_photos.js, commons_photos.js, assets/credits.json
출력: android/adapter/src/main/assets/content/content-pack.json
      android/adapter/src/main/assets/content/photos/*.jpg  (프로토타입에 내장한 사진)
      android/content-id-lock.json                          (ID → 한국어 원문. 한 번 정한 짝은 바뀌지 않는다)

ID 는 프로토타입이 배열 순서로 계산한 현재 값(b01~b100, t-palace-1 …)을 팩에 명시 필드로 적는다.
한 번 정한 ID 와 원문의 짝은 content-id-lock.json 에 쌓이고, 다음 생성에서 같은 ID 의 원문이 바뀌거나
ID 가 사라지면 실패한다(C-08). 카드를 중간에 끼워 넣으면 뒤쪽 ID 가 밀려서 바로 여기서 걸린다.

사용: py -X utf8 .work/build_content_pack.py [--check]
  --check  파일을 쓰지 않고 현재 팩·잠금과 비교만 한다(CI·커밋 전 확인용). 다르면 종료 코드 1.
"""
from __future__ import annotations

import hashlib
import html
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTO = ROOT / 'prototype'
ASSETS = ROOT / 'android' / 'adapter' / 'src' / 'main' / 'assets' / 'content'
PACK = ASSETS / 'content-pack.json'
LOCK = ROOT / 'android' / 'content-id-lock.json'
SCHEMA_VERSION = 1

# 프로토타입 JS 를 그대로 실행해 window.* 값을 꺼낸다. 데이터를 두 번 적지 않기 위해서다.
NODE_DUMP = r"""
const fs = require('fs'), vm = require('vm');
// 브라우저처럼 window 가 전역 객체 자신이어야 data.js 의 DATA.xxx 참조가 풀린다.
const ctx = {}; ctx.window = ctx; vm.createContext(ctx);
for (const f of process.argv.slice(1)) vm.runInContext(fs.readFileSync(f, 'utf8'), ctx, { filename: f });
const w = ctx.window;
process.stdout.write(JSON.stringify({ data: w.DATA, tour: w.TOUR_PHOTOS || {}, commons: w.COMMONS_PHOTOS || {} }));
"""

ALLOWED_CC = re.compile(r'^(CC0|CC BY (\d\.\d))$')


class BuildError(Exception):
    pass


def dump_prototype() -> dict:
    files = [str(PROTO / f) for f in ('data.js', 'tour_photos.js', 'commons_photos.js')]
    out = subprocess.run(['node', '-e', NODE_DUMP, *files], capture_output=True)
    if out.returncode != 0:
        raise BuildError('프로토타입 데이터를 읽지 못했다:\n' + out.stderr.decode('utf-8', 'replace'))
    return json.loads(out.stdout.decode('utf-8'))


def strip_html(s: str) -> str:
    return html.unescape(re.sub(r'<[^>]+>', '', s)).strip()


def commons_license(name: str, author: str) -> dict:
    m = ALLOWED_CC.match(name.strip())
    if not m:
        # SA·NC·ND 를 포함한 다른 라이선스는 팩에 넣지 않는다 (C-10).
        raise BuildError(f'허용되지 않는 사진 라이선스: {name!r}')
    if m.group(1) == 'CC0':
        return {'type': 'CC0'}
    if not author:
        raise BuildError(f'CC BY 사진에 저작자가 없다: {name!r}')
    return {'type': 'CC_BY', 'version': m.group(2), 'author': author}


def place_photo(place: dict, tour: dict, commons: dict, bundled: dict) -> dict | None:
    """프로토타입 app.js placePhoto() 와 같은 우선순위: 내장 사진 → TourAPI → Commons."""
    pid = place['id']
    if place.get('photo'):
        name = Path(place['photo']).name
        credit = bundled[Path(name).stem]
        return {
            'url': f'asset://content/photos/{name}',
            'license': commons_license(credit['license'], strip_html(credit['author'])),
            'source': 'Wikimedia Commons',
            'sourceUrl': credit['source'],
        }
    if pid in tour:
        t = tour[pid]
        kind = {'Type1': 'KOGL_1', 'Type3': 'KOGL_3'}.get(t['license'])
        if kind is None:
            raise BuildError(f'{pid}: 허용되지 않는 공공누리 유형 {t["license"]!r}')
        return {
            'url': t['image'],
            'license': {'type': kind},
            'source': '한국관광공사',
            'sourceUrl': f'https://korean.visitkorea.or.kr/detail/ms_detail.do?cotid={t["contentId"]}' if t.get('contentId') else None,
        }
    if pid in commons:
        c = commons[pid]
        return {
            'url': c['image'],
            'license': commons_license(c['license'], strip_html(c.get('author', ''))),
            'source': 'Wikimedia Commons',
            'sourceUrl': c.get('source'),
        }
    return None


def build(proto: dict) -> tuple[dict, list[Path]]:
    data, tour, commons = proto['data'], proto['tour'], proto['commons']
    bundled = {c['id']: c for c in json.loads((PROTO / 'assets' / 'credits.json').read_text(encoding='utf-8'))}

    areas = [{'id': a['id'], 'koName': a['koName'], 'name': {'en': a['name']}, 'active': True} for a in data['areas']]

    cards = []
    for c in data['cards']:
        card = {
            'id': c['id'],
            'kind': 'basic' if c['kind'] == 'basic' else 'place',
            'group': c['groupId'] if c['kind'] == 'basic' else c['typeId'],
            'koText': c['word'],
            'romanization': c['roman'],
            'reading': {},
            'meaning': {'en': c['meaning']},
            'note': {'en': c['note']},
            'pictogram': c.get('pictogram'),
            'plateColor': c['color'],
            'audio': None,
            'reviewed': True,
        }
        cards.append(card)

    places, photo_files = [], []
    for p in data['places']:
        photo = place_photo(p, tour, commons, bundled)
        if p.get('photo'):
            photo_files.append(PROTO / p['photo'])
        places.append({
            'id': p['id'],
            'areaId': p['areaId'],
            'typeId': p['type'],
            'koName': p['koName'],
            'name': {'en': p['name']},
            'address': {'en': p['address']},
            'lat': p['lat'],
            'lng': p['lng'],
            'signWords': p.get('signWords'),
            'photo': photo,
            'isExample': bool(p.get('example')),
        })

    body = {'areas': areas, 'cards': cards, 'places': places}
    # 내용이 같으면 버전도 같도록 내용 해시를 쓴다(생성 날짜를 쓰면 매번 diff 가 생긴다).
    version = hashlib.sha256(json.dumps(body, ensure_ascii=False, sort_keys=True).encode('utf-8')).hexdigest()[:12]
    pack = {
        'schemaVersion': SCHEMA_VERSION,
        'version': version,
        'areas': areas,
        'cards': cards,
        'places': places,
    }
    return pack, photo_files


def check_lock(pack: dict, lock: dict) -> dict:
    """C-08: 잠긴 ID 는 사라지거나 원문이 바뀌면 안 된다. 새 ID 만 잠금에 더한다."""
    current = {c['id']: c['koText'] for c in pack['cards']}
    current.update({f"place:{p['id']}": p['koName'] for p in pack['places']})
    errors = []
    for id_, text in lock.items():
        if id_ not in current:
            errors.append(f'{id_}: 잠긴 ID 가 사라졌다 (원문 {text!r})')
        elif current[id_] != text:
            errors.append(f'{id_}: 원문이 {text!r} → {current[id_]!r} 로 바뀌었다. 새 카드는 새 ID 로 추가해야 한다')
    if errors:
        raise BuildError('ID 고정 위반 (C-08)\n  ' + '\n  '.join(errors))
    return {**lock, **{k: v for k, v in current.items() if k not in lock}}


def to_json(obj) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=1) + '\n'


def main() -> int:
    check_only = '--check' in sys.argv[1:]
    try:
        pack, photo_files = build(dump_prototype())
        lock = json.loads(LOCK.read_text(encoding='utf-8')) if LOCK.exists() else {}
        new_lock = check_lock(pack, lock)
    except BuildError as e:
        print(f'실패: {e}', file=sys.stderr)
        return 1

    pack_text, lock_text = to_json(pack), to_json(dict(sorted(new_lock.items())))
    if check_only:
        same = PACK.exists() and PACK.read_text(encoding='utf-8') == pack_text \
            and LOCK.exists() and LOCK.read_text(encoding='utf-8') == lock_text
        print('콘텐츠 팩이 최신이다' if same else '콘텐츠 팩이 프로토타입 데이터와 다르다. 스크립트를 다시 실행하라')
        return 0 if same else 1

    (ASSETS / 'photos').mkdir(parents=True, exist_ok=True)
    PACK.write_text(pack_text, encoding='utf-8', newline='\n')
    LOCK.write_text(lock_text, encoding='utf-8', newline='\n')
    for f in photo_files:
        shutil.copyfile(f, ASSETS / 'photos' / f.name)
    added = len(new_lock) - len(lock)
    print(f"카드 {len(pack['cards'])} · 장소 {len(pack['places'])} · 지역 {len(pack['areas'])} · 사진 파일 {len(photo_files)} · 새로 잠근 ID {added}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
