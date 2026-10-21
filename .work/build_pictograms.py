"""표지판 픽토그램 벡터 세트 → android/app/src/main/res/drawable/pictogram_*.xml (IMPLEMENTATION_PLAN Phase 20 D3).

공공 안내 기호를 단순화해 직접 그린 그림이다(타사 이미지 아님). 좌표계는 프로토타입(app.js PICTOGRAMS)과 같은
-60..60 × -50..50 이고 그룹 이동으로 맞춘다. 색은 SignPlate 가 흰색으로 칠하므로 구멍(창문 등)은 evenOdd 로 뚫는다.
restroom·exit·noSmoking 세 장은 손으로 옮긴 파일이 이미 있어 여기서 만들지 않는다.

사용: py -X utf8 .work/build_pictograms.py
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'android' / 'app' / 'src' / 'main' / 'res' / 'drawable'


def circle(cx: float, cy: float, r: float) -> str:
    return f'M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z'


def rect(x: float, y: float, w: float, h: float) -> str:
    return f'M{x},{y} h{w} v{h} h{-w} Z'


def rrect(x: float, y: float, w: float, h: float, r: float) -> str:
    return (f'M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0,1 {r},{r} v{h - 2 * r} a{r},{r} 0 0,1 {-r},{r} '
            f'h{-(w - 2 * r)} a{r},{r} 0 0,1 {-r},{-r} v{-(h - 2 * r)} a{r},{r} 0 0,1 {r},{-r} Z')


def man(x: float) -> str:
    return circle(x, -34, 9) + f' M{x - 13},-22 h26 v28 h-6 v34 h-6 v-34 h-2 v34 h-6 v-34 h-6 Z'


def woman(x: float) -> str:
    return circle(x, -34, 9) + f' M{x - 9},-22 h18 l11,32 h-9 v30 h-6 v-30 h-2 v30 h-6 v-30 h-9 Z'


def fill(d: str, alpha: str | None = None) -> str:
    a = f' android:fillAlpha="{alpha}"' if alpha else ''
    return f'<path android:fillColor="#FFFFFFFF"{a} android:pathData="{d}" />'


def holes(d: str) -> str:
    return f'<path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{d}" />'


def stroke(width: float, d: str) -> str:
    return (f'<path android:strokeColor="#FFFFFFFF" android:strokeWidth="{width}" android:strokeLineCap="round" '
            f'android:strokeLineJoin="round" android:pathData="{d}" />')


BAN = [stroke(8, circle(0, 0, 42)), stroke(8, 'M-30,-30 L30,30')]

# id: (파일 이름, 설명, 요소들)
PICTOGRAMS: dict[str, tuple[str, str, list[str]]] = {
    'men': ('men', '남자', [fill(man(0))]),
    'women': ('women', '여자', [fill(woman(0))]),
    'accessible': ('accessible', '휠체어', [fill(circle(-8, -36, 8)), stroke(7, 'M-12,-24 v26 h24 l8,22'), stroke(6, circle(-6, 18, 22))]),
    'elevator': ('elevator', '엘리베이터', [stroke(6, rrect(-34, -44, 68, 88, 6)), fill('M0,-32 l-14,18 h28 Z M0,32 l-14,-18 h28 Z')]),
    'escalator': ('escalator', '에스컬레이터', [stroke(9, 'M-48,32 h20 l40,-52 h28'), fill(circle(-4, -30, 7)), stroke(7, 'M-4,-20 v16')]),
    'stairs': ('stairs', '계단', [stroke(8, 'M-44,40 v-16 h22 v-20 h22 v-20 h22 v-20 h22')]),
    'subway': ('subway', '지하철', [holes(rrect(-28, -42, 56, 70, 12) + ' ' + rect(-18, -32, 36, 22) + ' ' + circle(-14, 12, 5) + ' ' + circle(14, 12, 5)),
                                   stroke(6, 'M-20,32 l-10,12 M20,32 l10,12')]),
    'bus': ('bus', '버스', [holes(rrect(-34, -40, 68, 64, 10) + ' ' + rect(-26, -30, 52, 24)), fill(circle(-20, 30, 9) + ' ' + circle(20, 30, 9))]),
    'noPhoto': ('no_photo', '촬영 금지', [holes(rrect(-28, -16, 56, 36, 6) + ' ' + circle(0, 2, 11)), fill(rrect(-10, -24, 20, 10, 2))] + BAN),
    'wifi': ('wifi', '와이파이', [stroke(8, 'M-40,-10 a58,58 0 0,1 80,0 M-26,6 a38,38 0 0,1 52,0 M-12,22 a18,18 0 0,1 24,0'), fill(circle(0, 36, 6))]),
    'pharmacy': ('pharmacy', '약국 십자', [fill('M-12,-40 h24 v28 h28 v24 h-28 v28 h-24 v-28 h-28 v-24 h28 Z')]),
    # ---- Phase 20 D3 에 새로 그린 기호 ----
    'nursing': ('nursing', '젖병(수유실)', [fill('M-5,-44 h10 l3,12 h-16 Z'), fill(rect(-12, -32, 24, 8)), stroke(6, rrect(-14, -22, 28, 62, 8)), stroke(4, 'M-14,0 h12 M-14,14 h12')]),
    'babyChange': ('baby_change', '기저귀 교환대', [stroke(6, 'M-44,10 h88 M-36,10 v30 M36,10 v30'), fill(circle(-24, -8, 9)), fill(rrect(-14, -16, 42, 16, 8))]),
    'info': ('info', '안내 i', [stroke(6, circle(0, 0, 44)), fill(circle(0, -24, 7)), fill(rrect(-7, -12, 14, 42, 3))]),
    'atm': ('atm', '현금인출기', [stroke(6, rrect(-40, -38, 80, 56, 6)), fill(rect(-28, -28, 56, 18)), fill(rect(-20, 4, 40, 5)), fill(rect(-14, 12, 28, 30), '.8')]),
    'taxi': ('taxi', '택시', [holes('M-44,4 l10,-22 h68 l10,22 v20 h-88 Z M-28,0 l6,-14 h20 v14 Z M6,0 v-14 h18 l6,14 Z'),
                             fill(circle(-26, 26, 9) + ' ' + circle(26, 26, 9)), fill(rrect(-14, -36, 28, 12, 3))]),
    'parking': ('parking', '주차장 P', [stroke(6, rrect(-40, -46, 80, 92, 10)), holes('M-16,-32 h20 a18,18 0 0,1 0,36 h-6 v30 h-14 Z M-2,-19 v10 h6 a5,5 0 0,0 0,-10 Z')]),
    'airport': ('airport', '비행기', [fill('M0,-46 c5,0 6,6 6,12 v18 l40,20 v10 l-40,-10 v22 l12,10 v8 l-18,-6 l-18,6 v-8 l12,-10 v-22 l-40,10 v-10 l40,-20 v-18 c0,-6 1,-12 6,-12 Z')]),
    'lockers': ('lockers', '보관함', [stroke(6, rrect(-40, -42, 80, 84, 4)), stroke(5, 'M0,-42 v84 M-40,0 h80'),
                                     fill(rect(-12, -26, 6, 12) + ' ' + rect(28, -26, 6, 12) + ' ' + rect(-12, 14, 6, 12) + ' ' + rect(28, 14, 6, 12))]),
    'wasteBin': ('waste_bin', '휴지통', [fill(rect(-30, -34, 60, 8)), fill(rect(-8, -42, 16, 6)), stroke(6, 'M-24,-20 l6,60 h36 l6,-60'), stroke(4, 'M-8,-10 v40 M8,-10 v40')]),
    'drinkingWater': ('drinking_water', '음수대', [stroke(7, 'M-24,24 v-34 c0,-22 32,-22 36,-2'), fill('M14,0 c-4,6 -8,10 -8,15 a8,8 0 0,0 16,0 c0,-5 -4,-9 -8,-15 Z'), fill('M-42,26 h84 l-8,14 h-68 Z')]),
    'noEntry': ('no_entry', '진입 금지', [stroke(8, circle(0, 0, 42)), fill(rect(-28, -8, 56, 16))]),
    'caution': ('caution', '주의 삼각형', [stroke(8, 'M0,-42 L44,38 H-44 Z'), fill(rrect(-5, -18, 10, 32, 4)), fill(circle(0, 26, 6))]),
    'fireExtinguisher': ('fire_extinguisher', '소화기', [fill(rect(-16, -38, 28, 6)), fill(rect(-6, -32, 12, 10)), fill(rrect(-14, -22, 28, 64, 8)), stroke(6, 'M6,-28 h16 l10,16')]),
    'phoneCharging': ('phone_charging', '휴대폰 충전', [stroke(6, rrect(-22, -44, 44, 88, 8)), fill('M4,-26 l-18,30 h12 l-4,24 l18,-30 h-12 Z')]),
    'smokingRoom': ('smoking_room', '담배(흡연실)', [fill(rect(-40, 6, 56, 12)), fill(rect(18, 6, 10, 12), '.6'), stroke(5, 'M24,-4 c-6,-8 6,-14 0,-22 M12,-4 c-6,-8 6,-14 0,-22')]),
    'noPets': ('no_pets', '반려동물 금지', [fill('M-26,-2 h30 l8,-12 h10 l4,8 l-8,6 v22 h-7 v-12 h-28 v12 h-7 v-20 l-8,-10 Z')] + BAN),
    'noFood': ('no_food', '음식물 반입 금지', [stroke(5, 'M-22,-26 v40 M-28,-26 v12 a6,6 0 0,0 12,0 v-12'), fill('M2,-22 h26 l-4,44 h-18 Z')] + BAN),
    # 삼각형 세 변을 시계 방향으로 도는 화살표. 화살촉은 각 변의 진행 방향 끝에 있고 다음 변 시작에 거의 닿는다.
    'recycling': ('recycling', '재활용 화살표', [stroke(7, 'M4,-28.6 L24.5,8.6 M25.8,26 L-15,26 M-29.9,18.6 L-9.5,-18.6'),
                                             fill('M29.3,17.4 L17.5,12.5 L31.5,4.7 Z M-25,26 L-15,18 L-15,34 Z M-4.7,-27.4 L-2.5,-14.8 L-16.5,-22.4 Z')]),
    'crossing': ('crossing', '횡단보도 보행자', [fill(rect(-44, 30, 14, 12) + ' ' + rect(-22, 30, 14, 12) + ' ' + rect(0, 30, 14, 12) + ' ' + rect(22, 30, 14, 12)),
                                              fill(circle(0, -36, 8)), stroke(7, 'M0,-24 v26 M0,2 l-10,20 M0,2 l10,20 M-12,-12 l12,-6 l12,6')]),
    'police': ('police', '방패와 별(경찰)', [holes('M0,-44 l34,12 v20 c0,30 -18,46 -34,54 c-16,-8 -34,-24 -34,-54 v-20 Z '
                                                   'M0,-22 l5,11 h12 l-10,7 l4,12 l-11,-7 l-11,7 l4,-12 l-10,-7 h12 Z')]),
    'transfer': ('transfer', '환승 화살표', [stroke(7, 'M-36,-14 h62 M36,14 h-62'), fill('M28,-24 l14,10 l-14,10 Z M-28,4 l-14,10 l14,10 Z')]),
}


def xml(ko: str, elements: list[str]) -> str:
    body = '\n'.join('        ' + e for e in elements)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {ko} 픽토그램. .work/build_pictograms.py 가 만든다 — 직접 고치지 말고 스크립트를 고친다.
     공공 안내 기호를 단순화해 직접 그린 그림이며 타사 이미지가 아니다. 색은 SignPlate 가 흰색으로 칠한다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="100dp"
    android:viewportWidth="120"
    android:viewportHeight="100">
    <group android:translateX="60" android:translateY="50">
{body}
    </group>
</vector>
'''


def main() -> None:
    for _id, (name, ko, elements) in PICTOGRAMS.items():
        (OUT / f'pictogram_{name}.xml').write_text(xml(ko, elements), encoding='utf-8', newline='\n')
    print(f'픽토그램 {len(PICTOGRAMS)}개를 썼다')


if __name__ == '__main__':
    main()
