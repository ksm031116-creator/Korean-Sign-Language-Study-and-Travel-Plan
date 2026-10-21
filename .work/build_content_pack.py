"""프로토타입 데이터 → Android 콘텐츠 팩 (IMPLEMENTATION_PLAN Phase 2 D2).

입력: prototype/data.js, tour_photos.js, commons_photos.js, assets/credits.json
출력: android/adapter/src/main/assets/content/content-pack.json
      android/adapter/src/main/assets/content/photos/*.jpg  (프로토타입에 내장한 사진)
      android/content-id-lock.json                          (ID → 한국어 원문. 한 번 정한 짝은 바뀌지 않는다)

ID 는 프로토타입이 배열 순서로 계산한 현재 값(b01~b100, t-palace-1 …)을 팩에 명시 필드로 적는다.
한 번 정한 ID 와 원문의 짝은 content-id-lock.json 에 쌓이고, 다음 생성에서 같은 ID 의 원문이 바뀌거나
ID 가 사라지면 실패한다(C-08). 카드를 중간에 끼워 넣으면 뒤쪽 ID 가 밀려서 바로 여기서 걸린다.

사용: py -X utf8 .work/build_content_pack.py [--check] [--init-lock]
  --check      파일을 쓰지 않고 현재 팩·잠금·사진과 비교만 한다(CI·커밋 전 확인용). 다르면 종료 코드 1.
  --init-lock  잠금 파일이 없을 때 새로 만든다. 처음 한 번만 쓴다.
잠금은 늘기만 한다: HEAD 와 origin/main 분기점의 잠금에서 항목이 지워지거나 바뀌면 실패한다.
"""
from __future__ import annotations

import filecmp
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
AUDIO_SOURCE = PROTO / 'audio'                      # generate_tts.py 가 만든 mp3
TTS_MANIFEST = ROOT / '.work' / 'tts_manifest.json'  # 청취 검사(전사) 결과
AUDIO_CACHE = ROOT / '.work' / 'audio_cache.json'    # 원본 mp3 해시 → 변환 결과. 원본이 같으면 다시 변환하지 않는다
AUDIO_BUDGET_BYTES = 10 * 1024 * 1024                # 전체 오디오 10MB 이하 (PRD §7.3, NFR10)
# 변환 설정. 바꾸면 캐시 열쇠가 달라져 모든 음성을 다시 변환한다.
FFMPEG_ARGS = ['-ac', '1', '-ar', '24000', '-c:a', 'aac', '-b:a', '40k',
               '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact']
ENCODER_KEY = ' '.join(FFMPEG_ARGS)
# 설명 언어와 검수 상태. ja 는 원어민 검수(Q-01) 전까지 draft 이며 출시 빌드 검사가 막는다 (설계서 §8.8).
LOCALES = {'en': 'reviewed', 'ja': 'draft'}
# 일본어 콘텐츠 원본 (Phase 20 D2). 직역이 아닌 현지화 초안이며 검수 전까지 ja 는 draft 다.
JA_SOURCE = PROTO / 'data_ja.json'

# 기초 표지판 분류의 한국어 이름. 프로토타입 데이터에는 영어 이름만 있다.
BASIC_GROUP_KO = {
    'restroom': '화장실·시설', 'door': '문·방향', 'hours': '영업시간',
    'warning': '주의·금지', 'transit': '교통·이동', 'service': '생활 서비스',
}

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
        raise BuildError(f'허용되지 않는 사진 라이선스: {name!r}. 허용: CC0, CC BY x.y. '
                         '같은 라이선스의 다른 표기(예: "CC0 1.0")라면 ALLOWED_CC 에 정규화를 추가한다')
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


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verified_audio_ids() -> set[str]:
    """청취 검사(전사가 원문과 같음)를 통과한 카드만 음성을 싣는다. CHECK 표시 카드는 검수 전까지 뺀다(Q-02)."""
    if not TTS_MANIFEST.exists():
        return set()
    manifest = json.loads(TTS_MANIFEST.read_text(encoding='utf-8'))
    return {cid for cid, m in manifest.items() if m.get('ok') and (AUDIO_SOURCE / f'{cid}.mp3').exists()}


def convert_audio(card_id: str, cache: dict, write: bool) -> dict:
    """mp3 → AAC 모노 m4a (PRD §7.3). 같은 원본이면 이전 변환 결과를 그대로 쓴다(파일·해시가 매번 바뀌지 않게)."""
    src = AUDIO_SOURCE / f'{card_id}.mp3'
    out = ASSETS / 'audio' / f'{card_id}.m4a'
    src_hash = sha256_file(src)
    entry = cache.get(card_id)
    if (entry and entry['source'] == src_hash and entry.get('encoder') == ENCODER_KEY
            and out.exists() and sha256_file(out) == entry['sha256']):
        return entry
    if not write:
        raise BuildError(f'{card_id}: 음성 변환이 최신이 아니다. 스크립트를 다시 실행하라')
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_name(out.stem + '.tmp.m4a')  # 중간에 실패해도 반쯤 쓴 파일이 남지 않게
    try:
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(src), *FFMPEG_ARGS, str(tmp)], check=True, capture_output=True)
        probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', str(tmp)],
                               capture_output=True, check=True)
    except FileNotFoundError:
        raise BuildError('ffmpeg/ffprobe 를 찾을 수 없다. 음성 변환에 필요하다 (winget install Gyan.FFmpeg)')
    except subprocess.CalledProcessError as e:
        tmp.unlink(missing_ok=True)
        raise BuildError(f'{card_id}: 음성 변환 실패: {e.stderr.decode("utf-8", "replace")[:300]}')
    tmp.replace(out)
    entry = {'source': src_hash, 'encoder': ENCODER_KEY, 'sha256': sha256_file(out), 'bytes': out.stat().st_size,
             'durationMs': round(float(probe.stdout.decode().strip()) * 1000)}
    cache[card_id] = entry
    return entry


def apply_ja(areas: list, groups: list, cards: list, places: list, ja: dict) -> None:
    """일본어 값을 각 항목에 넣는다. 원본에 없는 항목·모르는 ID 는 실패한다 — 새 카드·장소는 일본어도 함께 적는다."""
    def table(kind: str, items: list) -> dict:
        source = ja.get(kind, {})
        ids = {i['id'] for i in items}
        missing, unknown = sorted(ids - source.keys()), sorted(source.keys() - ids)
        if missing or unknown:
            raise BuildError(f'data_ja.json {kind}: 빠짐 {missing} / 모르는 ID {unknown}')
        return source
    for a in areas:
        t = table('areas', areas)[a['id']]
        a['name']['ja'], a['tag']['ja'], a['description']['ja'] = t['name'], t['tag'], t['description']
    for g in groups:
        g['label']['ja'] = table('groups', groups)[g['id']]
    for c in cards:
        t = table('cards', cards)[c['id']]
        c['reading']['ja'], c['meaning']['ja'], c['note']['ja'] = t['reading'], t['meaning'], t['note']
    for p in places:
        t = table('places', places)[p['id']]
        p['name']['ja'], p['address']['ja'] = t['name'], t['address']


def build(proto: dict, write_audio: bool = False) -> tuple[dict, list[Path]]:
    data, tour, commons = proto['data'], proto['tour'], proto['commons']
    bundled = {c['id']: c for c in json.loads((PROTO / 'assets' / 'credits.json').read_text(encoding='utf-8'))}

    areas = [{
        'id': a['id'], 'koName': a['koName'], 'name': {'en': a['name']},
        'tag': {'en': a['tag']}, 'description': {'en': a['description']}, 'color': a['color'], 'active': True,
    } for a in data['areas']]

    # 카드 묶음: 기초 표지판 분류(Basics)와 장소 유형. 화면의 묶음 이름·판 색에 쓴다.
    groups = [{'id': g['id'], 'kind': 'basic', 'koLabel': BASIC_GROUP_KO[g['id']], 'label': {'en': g['label']}, 'color': g['color']}
              for g in data['basicGroups']]
    groups += [{'id': t['id'], 'kind': 'place', 'koLabel': t['koLabel'], 'label': {'en': t['label']}, 'color': t['color']}
               for t in data['placeTypes']]

    cache = json.loads(AUDIO_CACHE.read_text(encoding='utf-8')) if AUDIO_CACHE.exists() else {}
    with_audio = verified_audio_ids()
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
            'audio': audio_entry(c['id'], with_audio, cache, write_audio),
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

    apply_ja(areas, groups, cards, places, json.loads(JA_SOURCE.read_text(encoding='utf-8')))
    body = {'locales': LOCALES, 'areas': areas, 'groups': groups, 'cards': cards, 'places': places}
    # 내용이 같으면 버전도 같도록 내용 해시를 쓴다(생성 날짜를 쓰면 매번 diff 가 생긴다).
    version = hashlib.sha256(json.dumps(body, ensure_ascii=False, sort_keys=True).encode('utf-8')).hexdigest()[:12]
    pack = {
        'schemaVersion': SCHEMA_VERSION,
        'version': version,
        'locales': LOCALES,
        'areas': areas,
        'groups': groups,
        'cards': cards,
        'places': places,
    }
    total_audio = sum(c['audio']['bytes'] for c in cards if c['audio'])
    if total_audio > AUDIO_BUDGET_BYTES:
        raise BuildError(f'음성 합계 {total_audio} 바이트가 10MB 를 넘는다 (NFR10)')
    if write_audio:
        AUDIO_CACHE.write_text(json.dumps(dict(sorted(cache.items())), indent=1) + '\n', encoding='utf-8', newline='\n')
        # 검수를 통과하지 못한 카드의 옛 파일은 지운다.
        for f in (ASSETS / 'audio').glob('*.m4a'):
            if f.stem not in with_audio:
                f.unlink()
    return pack, photo_files


def audio_entry(card_id: str, with_audio: set[str], cache: dict, write: bool):
    if card_id not in with_audio:
        return None
    e = convert_audio(card_id, cache, write)
    return {'path': f'audio/{card_id}.m4a', 'sha256': e['sha256'], 'bytes': e['bytes'], 'durationMs': e['durationMs']}


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


def _git_show_lock(rev: str) -> dict | None:
    rel = LOCK.relative_to(ROOT).as_posix()
    try:
        out = subprocess.run(['git', '-C', str(ROOT), 'show', f'{rev}:{rel}'], capture_output=True)
    except FileNotFoundError:
        return None  # git 이 없는 환경
    if out.returncode != 0:
        return None
    return json.loads(out.stdout.decode('utf-8'))


def _merge_base_with_main() -> str | None:
    try:
        out = subprocess.run(['git', '-C', str(ROOT), 'merge-base', 'HEAD', 'origin/main'], capture_output=True)
    except FileNotFoundError:
        return None
    return out.stdout.decode().strip() if out.returncode == 0 else None


def committed_locks() -> list[tuple[str, dict]]:
    """비교 기준 잠금들. 잠금 파일을 지우거나 줄여서 C-08 을 우회하는 것을 막는다.

    - HEAD: 작업 트리에서 지우거나 고친 것을 잡는다.
    - origin/main 과의 merge-base: 잠금을 줄인 채 커밋까지 해 버린 것을 잡는다. CI 는 작업 트리가 HEAD 와 같아서
      HEAD 비교만으로는 아무것도 잡지 못한다.
    git 밖이거나 커밋된 잠금이 없으면(최초 생성) 비교하지 않는다.
    """
    refs = [('HEAD', 'HEAD')]
    base = _merge_base_with_main()
    if base:
        refs.append(('origin/main 분기점', base))
    return [(name, lock) for name, rev in refs if (lock := _git_show_lock(rev))]


def load_lock(init: bool) -> dict:
    """작업 트리의 잠금. 커밋된 잠금보다 줄어들거나 바뀌었으면 거부한다(잠금은 늘기만 한다)."""
    if not LOCK.exists():
        if not init:
            raise BuildError(f'{LOCK.name} 이 없다. 잠금을 지우면 ID 이력이 사라진다. 처음 만들 때만 --init-lock 을 쓴다')
        lock = {}
    else:
        lock = json.loads(LOCK.read_text(encoding='utf-8'))
    for name, committed in committed_locks():
        changed = [k for k, v in committed.items() if lock.get(k) != v]
        if changed:
            raise BuildError(f'{name} 의 잠금에서 지워지거나 바뀐 ID 가 있다 (C-08): ' + ', '.join(changed[:10]))
    return lock


def to_json(obj) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=1) + '\n'


def audio_up_to_date(pack: dict) -> bool:
    """음성 폴더에 팩이 쓰는 파일만 있는지(남은 파일이 APK 에 실리지 않게)."""
    folder = ASSETS / 'audio'
    expected = {Path(c['audio']['path']).name for c in pack['cards'] if c['audio']}
    present = {f.name for f in folder.glob('*')} if folder.is_dir() else set()
    return present == expected


def lock_view(data: dict) -> dict:
    """잠금 검사에 필요한 ID·원문만. 음성 변환 같은 쓰기보다 먼저 검사하려고 따로 만든다."""
    return {'cards': [{'id': c['id'], 'koText': c['word']} for c in data['cards']],
            'places': [{'id': p['id'], 'koName': p['koName']} for p in data['places']]}


def photos_up_to_date(photo_files: list[Path]) -> bool:
    """내장 사진이 원본과 같고, 팩이 쓰지 않는 사진 파일이 남아 있지 않은지."""
    folder = ASSETS / 'photos'
    expected = {f.name for f in photo_files}
    present = {f.name for f in folder.iterdir()} if folder.is_dir() else set()
    return present == expected and all(filecmp.cmp(f, folder / f.name, shallow=False) for f in photo_files)


def main() -> int:
    args = sys.argv[1:]
    check_only = '--check' in args
    try:
        proto = dump_prototype()
        lock = load_lock(init='--init-lock' in args)
        # ID 잠금을 먼저 본다. 잠금 위반이면 음성 변환·캐시·파일 삭제 같은 쓰기를 하지 않는다.
        check_lock(lock_view(proto['data']), lock)
        pack, photo_files = build(proto, write_audio=not check_only)
        new_lock = check_lock(pack, lock)
    except BuildError as e:
        print(f'실패: {e}', file=sys.stderr)
        return 1
    except (KeyError, ValueError, OSError) as e:
        # 프로토타입 데이터·사진 출처·잠금 파일의 형식 문제. 추적 내용 대신 원인을 한 줄로 알린다.
        print(f'실패: 입력 데이터를 읽지 못했다 ({type(e).__name__}: {e})', file=sys.stderr)
        return 1

    pack_text, lock_text = to_json(pack), to_json(dict(sorted(new_lock.items())))
    if check_only:
        same = (PACK.exists() and PACK.read_text(encoding='utf-8') == pack_text
                and LOCK.exists() and LOCK.read_text(encoding='utf-8') == lock_text
                and photos_up_to_date(photo_files) and audio_up_to_date(pack))
        print('콘텐츠 팩이 최신이다' if same else '콘텐츠 팩이 프로토타입 데이터와 다르다. 스크립트를 다시 실행하라')
        return 0 if same else 1

    (ASSETS / 'photos').mkdir(parents=True, exist_ok=True)
    PACK.write_text(pack_text, encoding='utf-8', newline='\n')
    LOCK.write_text(lock_text, encoding='utf-8', newline='\n')
    for f in photo_files:
        shutil.copyfile(f, ASSETS / 'photos' / f.name)
    for stale in (ASSETS / 'photos').iterdir():
        if stale.name not in {f.name for f in photo_files}:
            stale.unlink()  # 팩이 더는 쓰지 않는 사진. 앱 크기만 키운다
    added = len(new_lock) - len(lock)
    print(f"카드 {len(pack['cards'])} · 장소 {len(pack['places'])} · 지역 {len(pack['areas'])} · 사진 파일 {len(photo_files)} · 새로 잠근 ID {added}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
