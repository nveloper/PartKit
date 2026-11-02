"""app/src/main/res/drawable 의 도형·날씨 아이콘 벡터를 생성한다.

    python tools/gen_drawables.py

- shape_cookie9 : Material 3 Expressive의 9-스캘럽 "쿠키" 도형 근사 (틴트용 단색)
- shape_circle  : 1x1 크기용 원
- wx_*          : Pixel Weather 느낌의 그라데이션 날씨 아이콘 (48x48 viewport)
- ic_*          : 틴트해서 쓰는 작은 UI 아이콘과 런처 아이콘
"""
import math
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")
HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- tools/gen_drawables.py 로 생성됨. 직접 수정하지 말 것. -->\n')


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def polyline(points):
    head, *rest = points
    return "M" + f"{f(head[0])},{f(head[1])}" + "".join(f"L{f(x)},{f(y)}" for x, y in rest) + "Z"


def circle(cx, cy, r):
    # 화면 기준 시계 방향 원. 여러 원을 한 path에 넣어도 nonZero 규칙으로 합집합이 된다.
    return (f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,1 {f(2 * r)},0"
            f"a{f(r)},{f(r)} 0 1,1 {f(-2 * r)},0Z")


def linear(x1, y1, x2, y2, c1, c2):
    return ('\n        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}"'
            f' android:endX="{f(x2)}" android:endY="{f(y2)}"'
            f' android:startColor="{c1}" android:endColor="{c2}" />\n'
            '        </aapt:attr>')


def fill_path(d, gradient=None, color=None, alpha=None):
    attrs = f'android:pathData="{d}"'
    if color:
        attrs += f' android:fillColor="{color}"'
    if alpha is not None:
        attrs += f' android:fillAlpha="{alpha}"'
    if gradient:
        return f'    <path {attrs}>{gradient}\n    </path>\n'
    return f'    <path {attrs} />\n'


def stroke_path(d, color, width):
    return (f'    <path android:pathData="{d}" android:strokeColor="{color}"'
            f' android:strokeWidth="{f(width)}" android:strokeLineCap="round" />\n')


def vector(name, body, size=48):
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if "aapt:attr" in body else ""
    xml = (HEADER + f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
           f'    android:width="{size}dp" android:height="{size}dp"\n'
           f'    android:viewportWidth="{size}" android:viewportHeight="{size}">\n'
           + body + '</vector>\n')
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8") as fh:
        fh.write(xml)


def indent(body):
    return body.replace("    <path", "        <path").replace("\n        <aapt", "\n            <aapt") \
        .replace("\n            <gradient", "\n                <gradient") \
        .replace("\n        </aapt", "\n            </aapt").replace("\n    </path>", "\n        </path>")


# ── 도형 ────────────────────────────────────────────────────────────────────

def cookie(lobes, amplitude, samples=720):
    # r(θ) = R(1 + A·cos(nθ)). 최대 반지름이 50(viewport 100)에 꼭 맞게.
    radius = 50 / (1 + amplitude)
    pts = []
    for i in range(samples):
        t = 2 * math.pi * i / samples
        r = radius * (1 + amplitude * math.cos(lobes * t))
        a = t - math.pi / 2  # 봉우리 하나가 정확히 위를 향하게
        pts.append((50 + r * math.cos(a), 50 + r * math.sin(a)))
    return polyline(pts)


# ── 날씨 아이콘 구성 요소 ──────────────────────────────────────────────────

CLOUD_LIGHT = ("#FFF7F9FC", "#FFB4C0CA")
CLOUD_BACK = ("#FFD5DDE3", "#FF8D9CA8")
CLOUD_STORM = ("#FFB6C2CB", "#FF6B7B88")


def cloud(ox=0.0, oy=0.0, s=1.0, colors=CLOUD_LIGHT):
    """기준 구름(가로 7~42, 세로 13~37)을 중심 기준 s배로 줄이고 (ox, oy)만큼 옮긴다."""
    def p(x, y):
        return 24 + (x - 24) * s + ox, 24 + (y - 24) * s + oy
    parts = []
    for cx, cy, r in ((14, 30, 7), (24, 24, 11), (34, 29, 8)):
        x, y = p(cx, cy)
        parts.append(circle(x, y, r * s))
    x0, y0 = p(14, 28)
    x1, y1 = p(34, 37)
    parts.append(f"M{f(x0)},{f(y0)}H{f(x1)}V{f(y1)}H{f(x0)}Z")
    top = p(24, 13)[1]
    bottom = p(24, 37)[1]
    return fill_path("".join(parts), gradient=linear(0, top, 0, bottom, *colors))


def sun(cx, cy, r):
    halo = fill_path(circle(cx, cy, r * 1.4), color="#FFFFCA28", alpha=0.28)
    core = fill_path(circle(cx, cy, r),
                     gradient=linear(cx - r, cy - r, cx + r, cy + r, "#FFFFE17A", "#FFFFA726"))
    return halo + core


def moon(cx, cy, r):
    """(cx, cy) 반지름 r 원에서 오른쪽 위로 비껴난 원을 뺀 초승달."""
    c2x, c2y, r2 = cx + r * 0.55, cy - r * 0.45, r * 0.82
    d = math.hypot(c2x - cx, c2y - cy)
    base = math.atan2(c2y - cy, c2x - cx)
    a1 = math.acos((r * r + d * d - r2 * r2) / (2 * r * d))   # 교점: 큰 원 중심 기준 각
    a2 = math.acos((r2 * r2 + d * d - r * r) / (2 * r2 * d))  # 교점: 작은 원 중심 기준 각
    n = 48
    pts = []
    # 바깥 호: 큰 원에서 작은 원과 겹치지 않는 쪽 (교점 → 교점)
    start, end = base + a1, base + 2 * math.pi - a1
    for i in range(n + 1):
        t = start + (end - start) * i / n
        pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    # 안쪽 호: 작은 원 둘레 중 큰 원 안쪽(큰 원 중심을 향한 쪽)을 따라 처음 교점으로 되돌아간다.
    start2, end2 = base + math.pi + a2, base + math.pi - a2
    for i in range(n + 1):
        t = start2 + (end2 - start2) * i / n
        pts.append((c2x + r2 * math.cos(t), c2y + r2 * math.sin(t)))
    return fill_path(polyline(pts), gradient=linear(cx - r, cy - r, cx + r, cy + r, "#FFF1F3FF", "#FFA9B4E8"))


def drops(y=36.0, length=5.0, xs=(17, 24, 31), color="#FF4FC3F7"):
    return "".join(stroke_path(f"M{f(x)},{f(y)}L{f(x - length * 0.4)},{f(y + length)}", color, 2.6) for x in xs)


def flakes(points, color="#FF90CAF9", r=2.0):
    return "".join(fill_path(circle(x, y, r), color=color) for x, y in points)


def main():
    vector("shape_cookie9", fill_path(cookie(9, 0.075), color="#FFFFFFFF"), size=100)
    vector("shape_circle", fill_path(circle(50, 50, 50), color="#FFFFFFFF"), size=100)

    up = -5  # 강수 아이콘은 구름을 위로 올리고 아래에 강수를 그린다.
    vector("wx_clear_day", sun(24, 24, 11))
    vector("wx_clear_night", moon(23, 25, 13))
    vector("wx_partly_cloudy_day", sun(18, 17, 8.5) + cloud(5, 5, 0.82))
    vector("wx_partly_cloudy_night", moon(17, 18, 9) + cloud(5, 5, 0.82))
    vector("wx_cloudy", cloud(6, -5, 0.7, CLOUD_BACK) + cloud(-1, 3, 0.9))
    vector("wx_fog", cloud(0, up - 2, 0.9)
           + stroke_path("M10,37L38,37", "#FFB0BEC5", 2.6)
           + stroke_path("M14,42L34,42", "#FFB0BEC5", 2.6))
    vector("wx_drizzle", cloud(0, up, 0.9) + drops(y=36, length=3.0, xs=(18, 26)))
    vector("wx_rain", cloud(0, up, 0.9) + drops())
    vector("wx_sleet", cloud(0, up, 0.9) + drops(xs=(17, 31)) + flakes([(24.5, 40)]))
    vector("wx_snow", cloud(0, up, 0.9) + flakes([(16, 38), (24, 42), (32, 38)]))
    vector("wx_thunderstorm", cloud(0, up, 0.9, CLOUD_STORM)
           + fill_path("M25,31L18.5,40.5H23.5L21.5,47L30,36.5H25L27.5,31Z", color="#FFFFC107"))

    # 작은 UI 아이콘 (24dp, 코드에서 틴트)
    vector("ic_location", fill_path(
        "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7z"
        "M12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
        color="#FFFFFFFF"), size=24)
    vector("ic_event", fill_path(
        "M20,3h-1V1h-2v2H7V1H5v2H4C2.9,3 2,3.9 2,5v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5C22,3.9 21.1,3 20,3z"
        "M20,21H4V8h16V21z", color="#FFFFFFFF"), size=24)
    vector("ic_umbrella",
           fill_path("M12,3C6.8,3 3,6.9 3,12C4,11 5.3,10.8 6.3,11.6C7.5,10.6 9,10.6 10.2,11.6L11,12.2V12H13V12.2"
                     "L13.8,11.6C15,10.6 16.5,10.6 17.7,11.6C18.7,10.8 20,11 21,12C21,6.9 17.2,3 12,3Z",
                     color="#FFFFFFFF")
           + fill_path("M11,12V18.5C11,20.4 12.3,21.5 13.9,21.5C15.5,21.5 16.8,20.4 16.8,18.5V18H14.8V18.5"
                       "C14.8,19.2 14.4,19.5 13.9,19.5C13.4,19.5 13,19.2 13,18.5V12Z", color="#FFFFFFFF"),
           size=24)

    # 런처 아이콘 (adaptive 108dp, 48 viewport 아이콘을 가운데 54dp로)
    group = '    <group android:translateX="27" android:translateY="27" android:scaleX="1.125" android:scaleY="1.125">\n'
    vector("ic_launcher_foreground",
           group + indent(sun(18, 17, 8.5) + cloud(5, 5, 0.82)) + '    </group>\n', size=108)
    vector("ic_launcher_monochrome",
           group + indent(fill_path(circle(18, 17, 8.5), color="#FFFFFFFF")
                          + cloud(5, 5, 0.82, ("#FFFFFFFF", "#FFFFFFFF"))) + '    </group>\n', size=108)
    print("drawables written to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
