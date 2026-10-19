from pathlib import Path
import base64,re
ROOT=Path(__file__).resolve().parents[1]
src=ROOT/'prototype'
html=(src/'index.html').read_text(encoding='utf-8')
css=(src/'styles.css').read_text(encoding='utf-8')
html=re.sub(r'<link[^>]+href=["\']styles.css["\'][^>]*>',lambda m:'<style>'+css+'</style>',html)
for name in ['data.js','tour_photos.js','commons_photos.js','app.js']:
    js=(src/name).read_text(encoding='utf-8').replace('</script','<\\/script')
    html=re.sub(r'<script[^>]+src=["\']'+re.escape(name)+r'["\'][^>]*>\s*</script>',lambda m:'<script>'+js+'</script>',html)
for image in (src/'assets').glob('*.jpg'):
    html=html.replace('assets/'+image.name,'data:image/jpeg;base64,'+base64.b64encode(image.read_bytes()).decode())
out=ROOT/'Sign_Korean_인터랙티브_디자인.html'
out.write_text(html,encoding='utf-8')
print('Packaged',out.name,len(html.encode('utf-8')),'bytes')
