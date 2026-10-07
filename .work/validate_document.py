from pathlib import Path
from zipfile import ZipFile
import re
from docx import Document
from screen_detail import SCREENS

root=Path(__file__).resolve().parent.parent
path=root/'랜드마크_간판_한국어앱_PRD_SRS.docx'
d=Document(path)
text='\n'.join([p.text for p in d.paragraphs]+[c.text for t in d.tables for r in t.rows for c in r.cells])
expected={f'SC{i:02d}' for i in range(1,21)}
assert {s['id'] for s in SCREENS}==expected
assert len(SCREENS)==20
refs=set(re.findall(r'SC\d{2}',text))
assert refs==expected, refs-expected
for prefix,count in [('FR',22),('AT',14),('NFR',8),('UI',7),('C',9)]:
    for i in range(1,count+1):
        assert f'{prefix}{i:02d}' in text, f'{prefix}{i:02d}'
for screen in SCREENS:
    assert len(screen['rows'])>=4 and len(screen['states'])>=3
    assert screen['entry'] and screen['exit'] and screen['refs']
assert '버전 1.1' in text
assert '숙소 등 외부 검색 장소를 목적지로 추가하는 버튼은 없다' in text
assert '오른쪽' in text and '왼쪽' in text and '아래' in text
assert not re.search('[ぁ-んァ-ン]',text)
assert '再조회' not in text
with ZipFile(path) as z: assert z.testzip() is None
headings=[p.text for p in d.paragraphs if p.style.name=='Heading 1']
assert len(headings)==37, len(headings)
print('PASS: DOCX integrity, 20 screen specs, 9 modal types, FR01-22, AT01-14, NFR01-08, UI01-07, 37 chapter headings.')
print('Screen rows:',sum(len(s['rows']) for s in SCREENS),'State variants:',sum(len(s['states']) for s in SCREENS))
print('Document bytes:',path.stat().st_size)
