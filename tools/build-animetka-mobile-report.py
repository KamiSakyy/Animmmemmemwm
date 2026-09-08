import base64
import hashlib
import html
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'handoff/YORU-vs-ANIMETKA-MOBILE-DARK-2026-09-09.ru.md'
PAGE = ROOT / 'handoff/YORU-vs-ANIMETKA-MOBILE-DARK-2026-09-09.html'
ZIP = ROOT / 'handoff/ANIMETKA-mobile-dark-2026-09-09.zip'


def inline(s):
    s = html.escape(s)
    s = re.sub(r'`([^`]+)`', r'<code>\1</code>', s)
    s = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', s)
    return s


def render(s):
    lines = s.splitlines()
    out = []
    toc = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                parts = [c.strip() for c in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r'[:\- ]+', c) for c in parts):
                    rows.append(parts)
                i += 1
            out.append('<div class="table-wrap"><table>')
            for j, row in enumerate(rows):
                tag = 'th' if j == 0 else 'td'
                out.append('<tr>' + ''.join(f'<{tag}>{inline(c)}</{tag}>' for c in row) + '</tr>')
            out.append('</table></div>')
            continue
        heading = re.match(r'^(#{1,6}) (.*)', line)
        if heading:
            level = len(heading[1])
            anchor = 'section-' + str(i)
            out.append(f'<h{level} id="{anchor}">{inline(heading[2])}</h{level}>')
            if level == 2:
                toc.append(f'<a href="#{anchor}">{inline(heading[2])}</a>')
        elif line == '---':
            out.append('<hr>')
        elif line.startswith('- '):
            out.append('<p class="bullet">' + inline(line[2:]) + '</p>')
        else:
            paragraph = [line]
            while i+1 < len(lines) and lines[i+1].strip() and not lines[i+1].startswith(('#', '|', '- ')):
                i += 1
                paragraph.append(lines[i])
            out.append('<p>' + inline(' '.join(paragraph)) + '</p>')
        i += 1
    return '\n'.join(out), ''.join(toc)


def main():
    text = REPORT.read_text()
    body, toc = render(text)
    names = [('04-home-dark','Главная'),('02-menu-dark','Меню'),('03-theme-palette','Палитра'),
             ('05-filters-dark','Фильтры'),('06-genres-dark','Жанры'),('09-anime-sheet-dark','Карточка'),
             ('11c-mobile-search-overlay','Мобильный поиск'),('11d-mobile-search-suggestions','Подсказки')]
    with zipfile.ZipFile(ZIP) as z:
        files = {n:z.read(n) for n in z.namelist()}
    gallery = []
    for name, label in names:
        key = 'ANIMETKA-mobile-dark/' + name + '.png'
        if key in files:
            data = base64.b64encode(files[key]).decode()
            gallery.append(f'<figure><img src="data:image/png;base64,{data}" alt="Реальный снимок Аниметки: {label}" width="390" height="844"><figcaption>{label}</figcaption></figure>')
    css = '''*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:#0d0e12;color:#e8e9ef;font:16px/1.7 system-ui,sans-serif}header{padding:36px max(24px,calc((100vw - 1160px)/2));background:#171923;border-bottom:1px solid #303340}header small{color:#b9c5ff;letter-spacing:.1em}header h1{font-size:clamp(25px,4vw,42px);line-height:1.2;margin:14px 0}header p{color:#b4b8c8;max-width:820px}main{max-width:1160px;margin:auto;padding:28px 24px 70px}h1,h2,h3{line-height:1.3;scroll-margin-top:20px}h2{margin-top:46px;color:#e5d9ff}h3{margin-top:30px}a{color:#b9c5ff}nav{display:grid;gap:8px;padding:20px 24px;background:#171923;border:1px solid #303340;border-radius:12px}nav a{text-decoration:none}.gallery{display:flex;gap:16px;overflow-x:auto;padding:12px 0 24px;scroll-snap-type:x proximity}figure{margin:0;flex:0 0 234px;scroll-snap-align:start}figure img{width:234px;height:auto;border:1px solid #383b49;border-radius:12px;display:block}figcaption{padding:9px 0;color:#c1c5d5}hr{border:0;border-top:1px solid #303340;margin:32px 0}code{color:#dfc6ff;background:#24212e;padding:2px 5px;border-radius:4px;overflow-wrap:anywhere;font-size:.9em}.table-wrap{overflow-x:auto;border:1px solid #303340;border-radius:10px;margin:20px 0}table{border-collapse:collapse;width:100%;min-width:660px;font-size:14px}td,th{text-align:left;vertical-align:top;padding:12px 14px;border-bottom:1px solid #303340}th{background:#242633;color:#fff}tr:nth-child(even){background:#14161e}strong{color:#fff}.bullet{padding-left:20px;position:relative;margin:8px 0}.bullet:before{content:'•';position:absolute;left:0;color:#b9c5ff}.notice{padding:18px 22px;border-left:3px solid #b9c5ff;background:#191c28;border-radius:0 10px 10px 0}footer{color:#969bad;margin-top:50px}@media(max-width:600px){main{padding:20px 16px 50px}header{padding:26px 16px}body{font-size:15px}h2{font-size:23px}figure{flex-basis:234px}}@media print{body{background:white;color:#111}header,nav{background:white}h2,strong,code{color:#111}.table-wrap{overflow:visible}table{min-width:0;font-size:10px}td,th,tr:nth-child(even){background:white;color:#111}.gallery{flex-wrap:wrap}figure{flex-basis:160px}figure img{width:160px}a{color:#111}}'''
    document = '<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Мобильная тёмная Аниметка × YORU 4.19.1 — отчёт</title><style>'+css+'</style><body><header><small>ИССЛЕДОВАНИЕ · 09 СЕНТЯБРЯ 2026</small><h1>Мобильная Аниметка × YORU</h1><p>Тёмная тема, реальные экраны и подробное сравнение со стабильной 4.19.1. Что уже есть, чего не хватает и что действительно стоит улучшить.</p></header><main><div class="notice">Только исследование. YORU, плеер и кэш не изменялись. Скриншоты ниже — настоящий сайт в Chromium с мобильными параметрами, не прототип YORU. Этот отчёт автономный: нет внешних скриптов, шрифтов или сетевых запросов.</div><h2>Мобильные экраны</h2><p>Листайте галерею по горизонтали. Полноразмерные PNG и доказательства находятся в отдельном ZIP.</p><div class="gallery">'+''.join(gallery)+'</div><nav aria-label="Содержание">'+toc+'</nav>'+body+'<footer>Отчёт подготовлен по публичной браузерной сборке сайта и исходникам YORU 4.19.1. Перенос дизайна требует отдельного одобрения.</footer></main></body></html>'
    PAGE.write_text(document)
    files['ANIMETKA-mobile-dark/COMPARISON-YORU.ru.md'] = text.encode()
    with zipfile.ZipFile(ZIP,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            z.writestr(name,data)
    sha = hashlib.sha256(ZIP.read_bytes()).hexdigest()
    Path(str(ZIP)+'.sha256').write_text(f'{sha}  {ZIP.relative_to(ROOT)}\n')
    summary_path = ROOT/'handoff/ANIMETKA-mobile-dark-summary.json'
    summary = json.loads(summary_path.read_text())
    summary.setdefault('capture_zip_sha256',summary['sha256'])
    summary.update(sha256=sha,zip_bytes=ZIP.stat().st_size,comparison_report_added=True)
    summary_path.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    print('HTML bytes',PAGE.stat().st_size,'ZIP bytes',ZIP.stat().st_size,'SHA256',sha)


if __name__ == '__main__':
    main()
