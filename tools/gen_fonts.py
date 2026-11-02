"""위젯용 폰트(PartKit Sans)를 만든다: Google Sans + Noto Sans KR 한글.

    python -m pip install fonttools
    python tools/gen_fonts.py

- 라틴·그리스·키릴·숫자·기호: Google Sans  https://github.com/google/fonts/tree/main/ofl/googlesans
- 한글(현대 음절 11,172자 전체·자모·CJK 문장부호): Noto Sans KR  https://github.com/google/fonts/tree/main/ofl/notosanskr
- 둘 다 SIL OFL 1.1. 합친 폰트는 수정본이므로 이름을 "PartKit Sans"로 바꾼다
  (Noto Sans KR의 예약 이름 'Source'와 Google 상표를 쓰지 않기 위해).
- 출력: app/src/main/res/font/partkit_sans_{regular,medium}.ttf
        app/src/main/assets/licenses/{GoogleSans,NotoSansKR}-OFL.txt

위젯의 TextView(RemoteViews)는 android:fontFamily로 폰트 리소스 하나만 지정할 수 있고 대체 폰트 체인을
만들 수 없다. 그래서 두 폰트의 글리프를 한 파일로 합쳐 시스템 폰트와 무관하게 한글까지 같은 폰트로 그린다.
"""
import os
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.join(os.path.dirname(__file__), "..")
CACHE = os.path.join(ROOT, "build", "fonts")
FONT_DIR = os.path.join(ROOT, "app", "src", "main", "res", "font")
LICENSE_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "licenses")

GOOGLE_SANS = "https://raw.githubusercontent.com/google/fonts/main/ofl/googlesans/"
NOTO_KR = "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanskr/"

FAMILY = "PartKit Sans"
WEIGHTS = {"regular": (400, "Regular"), "medium": (500, "Medium")}

LATIN = [
    *range(0x0020, 0x0250),   # Basic Latin ~ Latin Extended-B
    *range(0x0300, 0x0370),   # Combining diacritics
    *range(0x0370, 0x0400),   # Greek
    *range(0x0400, 0x0530),   # Cyrillic
    *range(0x1E00, 0x1F00),   # Latin Extended Additional (Vietnamese)
    *range(0x2000, 0x2070),   # General punctuation
    *range(0x20A0, 0x20C1),   # Currency
    *range(0x2100, 0x2150),   # Letterlike (℃, ℉)
    *range(0x2190, 0x2200),   # Arrows
    0x2212,                   # Minus sign
]

HANGUL = [
    *range(0x1100, 0x1200),   # Hangul Jamo
    *range(0x3000, 0x3040),   # CJK symbols & punctuation (「」、。 등)
    *range(0x3130, 0x3190),   # Hangul Compatibility Jamo
    *range(0xA960, 0xA980),   # Hangul Jamo Extended-A
    *range(0xAC00, 0xD7A4),   # Hangul Syllables (11,172)
    *range(0xD7B0, 0xD800),   # Hangul Jamo Extended-B
    *range(0xFF01, 0xFF61),   # Fullwidth forms
]


def fetch(base, name, cache_name=None):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, cache_name or name)
    if not os.path.exists(path):
        url = base + urllib.request.quote(name)
        print("download", url)
        urllib.request.urlretrieve(url, path)
    return path


def instance_and_subset(path, axes, unicodes, keep_layout):
    font = instancer.instantiateVariableFont(TTFont(path), axes)
    options = subset.Options()
    options.layout_features = ["*"] if keep_layout else []
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.glyph_names = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=unicodes)
    subsetter.subset(font)
    return font


def add_glyphs(base, donor, prefix="kr."):
    """donor의 cmap 글리프 중 base에 없는 코드포인트만 base로 복사한다(합성 글리프 구성요소 포함)."""
    assert base["head"].unitsPerEm == donor["head"].unitsPerEm, "UPM mismatch"
    base_cmap = base.getBestCmap()
    donor_cmap = {cp: g for cp, g in donor.getBestCmap().items() if cp not in base_cmap}

    # 필요한 글리프 = 매핑된 글리프 + 그 합성 구성요소(재귀)
    donor_glyf = donor["glyf"]
    needed, stack = set(), list(set(donor_cmap.values()))
    while stack:
        name = stack.pop()
        if name in needed:
            continue
        needed.add(name)
        glyph = donor_glyf[name]
        if glyph.isComposite():
            stack.extend(c.glyphName for c in glyph.components)

    rename = {name: prefix + name for name in needed}
    order = base.getGlyphOrder() + [rename[n] for n in donor.getGlyphOrder() if n in needed]
    base_glyf, base_hmtx = base["glyf"], base["hmtx"]
    for old, new in rename.items():
        glyph = donor_glyf[old]
        if glyph.isComposite():
            for component in glyph.components:
                component.glyphName = rename[component.glyphName]
        base_glyf[new] = glyph
        base_hmtx[new] = donor["hmtx"][old]
    base.setGlyphOrder(order)
    base_glyf.glyphOrder = order

    for table in base["cmap"].tables:
        if table.isUnicode():
            table.cmap.update({cp: rename[g] for cp, g in donor_cmap.items()
                               if table.format != 4 or cp <= 0xFFFF})
    base["maxp"].numGlyphs = len(order)
    base["post"].formatType = 3.0  # 글리프 이름 표 제거(크기 절약)
    return len(donor_cmap)


def fit_vertical_metrics(font):
    """한글이 위아래로 잘리지 않도록 줄 높이 지표를 전체 글리프 범위에 맞춘다."""
    glyf = font["glyf"]
    y_max = max((glyf[g].yMax for g in font.getGlyphOrder() if hasattr(glyf[g], "yMax")), default=0)
    y_min = min((glyf[g].yMin for g in font.getGlyphOrder() if hasattr(glyf[g], "yMin")), default=0)
    os2 = font["OS/2"]
    os2.usWinAscent = max(os2.usWinAscent, y_max)
    os2.usWinDescent = max(os2.usWinDescent, -y_min)


def set_names(font, style):
    full = f"{FAMILY} {style}"
    postscript = f"{FAMILY.replace(' ', '')}-{style}"
    names = font["name"]
    copyright_notice = ("Google Sans: Copyright 2025 The Google Sans Project Authors. "
                        "Noto Sans KR: (c) 2014-2021 Adobe, with Reserved Font Name 'Source'. "
                        "Merged and subset for PartKit.")
    keep = {0, 13, 14}
    names.names = [n for n in names.names if n.nameID in keep]
    for name_id, value in (
        (0, copyright_notice),
        (1, FAMILY if style == "Regular" else full),
        (2, "Regular"),
        (3, f"PartKit;{postscript}"),
        (4, full),
        (6, postscript),
        (16, FAMILY),
        (17, style),
    ):
        names.setName(value, name_id, 3, 1, 0x409)


def main():
    google_sans = fetch(GOOGLE_SANS, "GoogleSans[GRAD,opsz,wght].ttf")
    noto_kr = fetch(NOTO_KR, "NotoSansKR[wght].ttf")
    os.makedirs(FONT_DIR, exist_ok=True)
    for suffix, (weight, style) in WEIGHTS.items():
        font = instance_and_subset(google_sans, {"wght": weight, "opsz": 18, "GRAD": 0}, LATIN, keep_layout=True)
        korean = instance_and_subset(noto_kr, {"wght": weight}, HANGUL, keep_layout=False)
        added = add_glyphs(font, korean)
        fit_vertical_metrics(font)
        set_names(font, style)
        out = os.path.join(FONT_DIR, f"partkit_sans_{suffix}.ttf")
        font.save(out)
        print(f"{out}: +{added} Hangul/CJK code points, {os.path.getsize(out) // 1024} KB")

    os.makedirs(LICENSE_DIR, exist_ok=True)
    for base, target in ((GOOGLE_SANS, "GoogleSans-OFL.txt"), (NOTO_KR, "NotoSansKR-OFL.txt")):
        with open(fetch(base, "OFL.txt", cache_name=target), encoding="utf-8") as src:
            text = src.read()
        with open(os.path.join(LICENSE_DIR, target), "w", encoding="utf-8") as dst:
            dst.write(text)


if __name__ == "__main__":
    main()
