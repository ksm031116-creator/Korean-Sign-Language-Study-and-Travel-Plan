from pathlib import Path
import json
import pypdfium2 as pdfium
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parent/'render'
pdf=pdfium.PdfDocument(root/'requirements.pdf')
report=[]
thumbs=[]
for i in range(len(pdf)):
    page=pdf[i]
    textpage=page.get_textpage()
    text=textpage.get_text_range()
    scale=1400/page.get_width()
    img=page.render(scale=scale).to_pil().convert('RGB')
    img.save(root/f'page-{i+1}.png')
    thumb=img.copy(); thumb.thumbnail((330,470))
    card=Image.new('RGB',(350,505),'#e9eef3')
    card.paste(thumb,((350-thumb.width)//2,25))
    ImageDraw.Draw(card).text((12,5),f'Page {i+1} / {len(pdf)}',fill='black')
    thumbs.append(card)
    report.append({'page':i+1,'characters':len(text.strip()),'first':text.strip()[:90],'last':text.strip()[-100:]})
    textpage.close(); page.close()
for batch in range(0,len(thumbs),12):
    chunk=thumbs[batch:batch+12]
    sheet=Image.new('RGB',(1400,505*((len(chunk)+3)//4)),'white')
    for j,thumb in enumerate(chunk): sheet.paste(thumb,((j%4)*350,(j//4)*505))
    sheet.save(root/f'contact-{batch//12+1}.png')
(root/'pagination.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Rendered pages:',len(pdf))
print('Short pages:',[{'page':r['page'],'characters':r['characters'],'first':r['first']} for r in report if r['characters']<220])
pdf.close()
