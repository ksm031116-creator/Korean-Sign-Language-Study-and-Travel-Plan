"""카드 단어의 한국어 발음을 Gemini TTS로 미리 만들어 앱에 넣는다 (FR29, NFR10).

실행:  py -X utf8 .work/generate_tts.py          (이미 만든 파일은 건너뛴다. 중간에 멈춰도 다시 실행하면 이어서)
       py -X utf8 .work/generate_tts.py --redo b05 t-market-2   (지정한 카드만 다시 생성)
키:    .env 의 GEMINI_API_KEY (저장소 제외)
결과:  prototype/audio/<카드ID>.mp3, prototype/audio_index.js, .work/tts_report.txt

검수: 직접 들을 수 없으므로 생성한 음성을 다른 Gemini 모델로 받아 적게 해 원래 단어와 대조한다.
      다르면 한 번 다시 생성하고, 그래도 다르면 보고서에 CHECK 로 남겨 사람이 듣고 판단한다.
"""
from pathlib import Path
from datetime import date
import base64, json, re, subprocess, sys, tempfile, time, urllib.error, urllib.request

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / 'prototype' / 'audio'
TTS_MODEL = 'gemini-3.8-flash-tts'
CHECK_MODEL = 'gemini-3.8-flash'
VOICE = 'Kore'                      # 차분한 여성 목소리 (사용자 선택)
STYLE = 'calm, clear and slow, standard Seoul Korean pronunciation'
API = 'https://generativelanguage.googleapis.com/v1beta'
# 숫자는 받아쓰기에서 한글로 나올 수 있어 읽는 법으로 바꿔 비교한다.
READ_AS = {'24시간': '이십사시간', '지하 1층': '지하일층'}


def load_key():
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('GEMINI_API_KEY='):
            return line.split('=', 1)[1].strip()
    raise SystemExit('.env 에 GEMINI_API_KEY 가 없습니다')


def load_cards():
    js = ("const vm=require('vm'),fs=require('fs');const c={};c.window=c;"
          "vm.runInNewContext(fs.readFileSync(process.argv[1],'utf8'),c);"
          "process.stdout.write(JSON.stringify(c.DATA.cards.map(x=>({id:x.id,word:x.word}))))")
    out = subprocess.run(['node', '-e', js, str(ROOT / 'prototype' / 'data.js')], capture_output=True, check=True)
    return json.loads(out.stdout.decode('utf-8'))


class DailyLimit(Exception):
    """무료 등급의 하루 요청 한도. 기다려도 풀리지 않으므로 즉시 멈추고 다음 날 이어서 한다."""


def post(key, path, body):
    req = urllib.request.Request(f'{API}/{path}', data=json.dumps(body).encode(),
                                 headers={'x-goog-api-key': key, 'Content-Type': 'application/json'})
    for n in range(8):
        try:
            return json.loads(urllib.request.urlopen(req, timeout=120).read())
        except urllib.error.HTTPError as e:
            msg = e.read().decode('utf-8', 'replace').replace(key, '<KEY>')
            if e.code == 429 and 'per day' in msg:
                raise DailyLimit(msg[:300])
            if e.code in (429, 500, 503) and n < 7:          # 분당 한도·일시 오류만 재시도
                wait = 20 * (n + 1); print(f'   {e.code}, {wait}s 대기'); time.sleep(wait); continue
            raise RuntimeError(f'HTTP {e.code}: {msg[:300]}')
        except (urllib.error.URLError, TimeoutError, OSError) as e:   # 연결 끊김·응답 시간 초과
            if n < 7:
                wait = 20 * (n + 1); print(f'   네트워크 오류({type(e).__name__}), {wait}s 대기'); time.sleep(wait); continue
            raise RuntimeError(f'네트워크 오류가 계속됩니다: {e}')
    raise RuntimeError('재시도 횟수를 넘었습니다')


def speak(key, word):
    r = post(key, 'interactions', {
        'model': TTS_MODEL,
        'input': [{'type': 'user_input', 'content': [{'type': 'text', 'text': word,
                   'annotations': [{'type': 'speech_metadata', 'style': STYLE}]}]}],
        'response_format': {'type': 'audio'},
        'generation_config': {'speech_config': [{'voice': VOICE}]}})
    audio = [c for s in r.get('steps', []) if s.get('type') == 'model_output'
             for c in s.get('content', []) if c.get('type') == 'audio']
    if not audio:
        raise RuntimeError('응답에 음성이 없습니다')
    return base64.b64decode(audio[-1]['data'])


def transcribe(key, mp3):
    r = post(key, f'models/{CHECK_MODEL}:generateContent', {'contents': [{'parts': [
        {'inline_data': {'mime_type': 'audio/mpeg', 'data': base64.b64encode(mp3).decode()}},
        {'text': 'Transcribe this Korean audio exactly as spoken, in Hangul only. '
                 'Write numbers the way they are read, in Hangul. Output only the transcript.'}]}]})
    return ''.join(p.get('text', '') for p in r['candidates'][0]['content']['parts']).strip()


def norm(s):
    return re.sub(r'[\s.,!?·"\'~]', '', s)


def to_mp3(wav, dst):
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        f.write(wav); src = f.name
    trim = ('silenceremove=start_periods=1:start_threshold=-50dB,areverse,'
            'silenceremove=start_periods=1:start_threshold=-50dB,areverse,'
            'adelay=80,apad=pad_dur=0.15')                      # 앞뒤 무음 제거 후 짧은 여유
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-af', trim, '-ac', '1', '-ar', '24000',
                    '-b:a', '48k', str(dst)], check=True)
    Path(src).unlink()


def main():
    key = load_key(); AUDIO.mkdir(exist_ok=True)
    redo = set(sys.argv[sys.argv.index('--redo') + 1:]) if '--redo' in sys.argv else set()
    log_path = ROOT / '.work' / 'tts_manifest.json'   # 받아쓰기 결과. 앱에는 넣지 않는다
    log = json.loads(log_path.read_text(encoding='utf-8')) if log_path.exists() else {}
    cards = load_cards()
    save = lambda: log_path.write_text(json.dumps(log, ensure_ascii=False, indent=1), encoding='utf-8')
    stopped = None
    try:
        for c in cards:
            dst = AUDIO / f"{c['id']}.mp3"
            done = log.get(c['id'])
            fresh = done and done['word'] == c['word'] and dst.exists()
            if fresh and c['id'] not in redo and done.get('heard') is not None:
                continue
            want = norm(READ_AS.get(c['word'], c['word']))
            if fresh and c['id'] not in redo:
                # 음성은 만들었지만 받아쓰기 전에 한도에 걸린 경우: 받아쓰기만 다시 한다 (TTS 한도 아낌)
                heard = transcribe(key, dst.read_bytes()); ok = norm(heard) in (want, norm(c['word'])); attempt = done['attempts']
            else:
                for attempt in (1, 2):
                    to_mp3(speak(key, c['word']), dst)
                    log[c['id']] = {'word': c['word'], 'heard': None, 'ok': False, 'attempts': attempt}; save()
                    heard = transcribe(key, dst.read_bytes())
                    ok = norm(heard) in (want, norm(c['word']))
                    if ok:
                        break
            log[c['id']] = {'word': c['word'], 'heard': heard, 'ok': ok, 'attempts': attempt}; save()
            print(f"{'OK   ' if ok else 'CHECK'} {c['id']:<14} {c['word']} → {heard}", flush=True)
            time.sleep(2)
    except DailyLimit as e:
        stopped = str(e)
        print(f'\n하루 한도에 도달해 멈춥니다. 다음 실행 때 이어서 만듭니다.\n   {stopped[:160]}')

    # 받아쓰기 검수를 통과한 음성만 앱에 연결한다. 불일치(CHECK)·검수 전 음성은 브라우저 음성으로 대체된다.
    ids = [c['id'] for c in cards
           if (AUDIO / f"{c['id']}.mp3").exists() and log.get(c['id'], {}).get('ok') and log[c['id']]['word'] == c['word']]
    (ROOT / 'prototype' / 'audio_index.js').write_text(
        '/* 자동 생성 파일 — .work/generate_tts.py 로 다시 만든다. 직접 고치지 않는다.\n'
        f' * 발음: Gemini TTS ({TTS_MODEL}, voice {VOICE}) · 생성일 {date.today()}\n */\n'
        'window.AUDIO_FILES = ' + json.dumps({i: f'audio/{i}.mp3' for i in ids}, indent=1) + ';\n', encoding='utf-8')
    bad = [k for k, v in log.items() if not v['ok']]
    size = sum(p.stat().st_size for p in AUDIO.glob('*.mp3'))
    report = [f"{'OK   ' if v['ok'] else 'CHECK'} {k:<14} {v['word']} → {v['heard']} (시도 {v['attempts']})" for k, v in log.items()]
    (ROOT / '.work' / 'tts_report.txt').write_text('\n'.join(report) + '\n', encoding='utf-8')
    print(f'\n검수 통과 음성 {len(ids)}/{len(cards)}개, 합계 {size / 1e6:.2f} MB, 받아쓰기 불일치·미검수 {len(bad)}개 {bad}')
    if stopped:
        sys.exit(3)          # 3 = 하루 한도로 중단 (오류 아님). 예약 작업이 구별할 수 있게 한다


if __name__ == '__main__':
    main()
