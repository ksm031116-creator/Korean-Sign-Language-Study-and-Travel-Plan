import json, urllib.request, urllib.parse, concurrent.futures
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'prototype'
items=[('gyeongbokgung','Gyeonghoeru Pavilion at Gyeongbokgung Palace.jpg'),('myeongdong','Myeongdong Cathedral 20240217.jpg'),('ddp','DongDaemun Design Plaza (동대문디자인플라자 (DDP)), Seoul, South Korea (Unsplash aNSniQXO424).jpg')]
def get(item):
    key,title=item
    url='https://commons.wikimedia.org/w/api.php?'+urllib.parse.urlencode(dict(action='query',format='json',prop='imageinfo',titles='File:'+title,iiprop='url|extmetadata',iiurlwidth=1000))
    def fetch(u):
        return urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'SignKoreanClassPrototype/1.0 (educational local design)'}),timeout=60).read()
    result=json.loads(fetch(url)); info=next(iter(result['query']['pages'].values()))['imageinfo'][0]
    (ROOT/'assets'/f'{key}.jpg').write_bytes(fetch(info.get('thumburl',info['url'])))
    meta=info['extmetadata']; print(key,'downloaded')
    return dict(id=key,title=title,source=info['descriptionurl'],author=meta.get('Artist',{}).get('value',''),license=meta.get('LicenseShortName',{}).get('value',''),licenseUrl=meta.get('LicenseUrl',{}).get('value',''))
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    credits=list(pool.map(get,items))
(ROOT/'assets'/'credits.json').write_text(json.dumps(credits,ensure_ascii=False,indent=2),encoding='utf-8')
