import asyncio
import hashlib
import json
import re
import shutil
import zipfile
from collections import deque
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from playwright.async_api import async_playwright

BASE = 'https://animetka.com/'
ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'workspace-output/animetka-public-frontend'
OUTPUT = ROOT / 'handoff/ANIMETKA-public-frontend-2026-09-09.zip'
MAX_BYTES = 48 * 1024 * 1024
MAX_FILE = 8 * 1024 * 1024
MAX_FILES = 240
records = {}
issues = []
tasks = set()
total = 0
images = 0


def safe_name(url, kind):
    u = urlsplit(url)
    path = u.path.strip('/') or 'index.html'
    parts = [re.sub(r'[^a-zA-Z0-9._-]', '_', p) for p in path.split('/') if p not in ('', '.', '..')]
    if not Path(parts[-1]).suffix:
        parts[-1] += '.html' if kind == 'document' else '.txt'
    if u.query:
        p = Path(parts[-1])
        parts[-1] = p.stem + '-' + hashlib.sha256(u.query.encode()).hexdigest()[:10] + p.suffix
    return Path('public') / u.netloc.replace(':', '_') / Path(*parts)


def save_resource(url, body, kind, status, content_type):
    global total
    if url in records:
        return
    if len(body) > MAX_FILE or total + len(body) > MAX_BYTES or len(records) >= MAX_FILES:
        issues.append({'url': url, 'reason': 'snapshot size limit'})
        return
    relative = safe_name(url, kind)
    path = WORK / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(body)
    total += len(body)
    records[url] = {'url': url, 'file': relative.as_posix(), 'kind': kind, 'status': status,
                    'content_type': content_type, 'bytes': len(body), 'sha256': hashlib.sha256(body).hexdigest()}


def permitted(url):
    u = urlsplit(url)
    if u.scheme not in ('https', 'http'):
        return False
    if any(token in url.lower() for token in ('/cdn-cgi/', 'google-analytics', 'googletagmanager', 'mc.yandex', 'doubleclick', 'clarity.ms')):
        return False
    return u.hostname in ('animetka.com', 'www.animetka.com', 'fonts.googleapis.com', 'fonts.gstatic.com',
                          'cdn.jsdelivr.net', 'cdnjs.cloudflare.com', 'unpkg.com')


async def capture_response(response):
    global images
    try:
        kind = response.request.resource_type
        url = response.url
        if kind not in ('document', 'stylesheet', 'script', 'font', 'image') or response.status != 200:
            return
        if not permitted(url):
            if kind != 'image' or images >= 8 or urlsplit(url).hostname not in ('shikimori.io', 'shikimori.one', 'shikimori.me'):
                return
        if kind == 'image':
            if images >= 12:
                return
            images += 1
        length = int(response.headers.get('content-length', '0') or 0)
        if length > MAX_FILE:
            issues.append({'url': url, 'reason': 'file exceeds size limit'})
            return
        body = await response.body()
        save_resource(url, body, kind, response.status, response.headers.get('content-type', ''))
    except Exception as error:
        issues.append({'url': response.url, 'reason': str(error)[:200]})


def on_response(response):
    task = asyncio.create_task(capture_response(response))
    tasks.add(task)
    task.add_done_callback(tasks.discard)


async def drain():
    while tasks:
        await asyncio.gather(*list(tasks), return_exceptions=True)


async def snapshot(page, name):
    await page.wait_for_timeout(1200)
    (WORK / 'pages').mkdir(exist_ok=True)
    (WORK / 'screenshots').mkdir(exist_ok=True)
    (WORK / 'pages' / (name + '.rendered.html')).write_text(await page.content())
    (WORK / 'pages' / (name + '.text.txt')).write_text(await page.locator('body').inner_text())
    await page.screenshot(path=str(WORK / 'screenshots' / (name + '.png')), full_page=True, timeout=30000)
    controls = await page.evaluate('''() => Array.from(document.querySelectorAll('a,button,input,select,textarea,h1,h2,h3')).map(e => ({
        tag:e.tagName,text:(e.innerText||'').trim().slice(0,300),href:e.getAttribute('href'),
        type:e.getAttribute('type'),placeholder:e.getAttribute('placeholder'),ariaLabel:e.getAttribute('aria-label'),
        color:getComputedStyle(e).color,background:getComputedStyle(e).backgroundColor,
        font:getComputedStyle(e).fontFamily,fontSize:getComputedStyle(e).fontSize,borderRadius:getComputedStyle(e).borderRadius,
        options:e.tagName==='SELECT'?Array.from(e.options).map(o=>({text:o.text,value:o.value})):undefined
    }))''')
    (WORK / 'pages' / (name + '.controls.json')).write_text(json.dumps({'url': page.url, 'title': await page.title(), 'controls': controls}, ensure_ascii=False, indent=2))
    await drain()


def references(url, body):
    result = set()
    for value in re.findall(r'''["']([^"'\s<>]+?\.(?:js|mjs|css|woff2?|ttf|otf|svg|ico)(?:\?[^"'\s<>]*)?)["']''', body):
        if '${' not in value:
            result.add(urljoin(url, value))
    for value in re.findall(r'url\(\s*[\'"]?([^\)\'"\s]+)', body):
        if not value.startswith('data:'):
            result.add(urljoin(url, value))
    for value in re.findall(r'sourceMappingURL=([^\s*]+)', body):
        if not value.startswith('data:'):
            result.add(urljoin(url, value))
    return result


async def linked_assets(context):
    queue = deque(records.keys())
    parsed = set()
    fetched = set(records.keys())
    while queue and len(records) < MAX_FILES and total < MAX_BYTES:
        url = queue.popleft()
        if url in parsed or url not in records:
            continue
        parsed.add(url)
        record = records[url]
        if record['kind'] not in ('document', 'stylesheet', 'script'):
            continue
        body = (WORK / record['file']).read_text(errors='replace')
        for link in sorted(references(url, body)):
            if link in fetched or not permitted(link):
                continue
            fetched.add(link)
            try:
                response = await context.request.get(link, timeout=15000)
                if response.status != 200:
                    issues.append({'url': link, 'reason': 'HTTP ' + str(response.status)})
                    continue
                content_type = response.headers.get('content-type', '')
                path = urlsplit(link).path
                if 'text/html' in content_type and not path.endswith('.html'):
                    issues.append({'url': link, 'reason': 'HTML fallback instead of static asset'})
                    continue
                kind = 'script' if path.endswith(('.js', '.mjs')) else 'stylesheet' if path.endswith('.css') else 'source-map' if path.endswith('.map') else 'asset'
                save_resource(link, await response.body(), kind, response.status, content_type)
                queue.append(link)
                await asyncio.sleep(0.12)
            except Exception as error:
                issues.append({'url': link, 'reason': str(error)[:200]})


def extract_public_sources():
    count = 0
    for item in records.values():
        if item['kind'] != 'source-map':
            continue
        try:
            source_map = json.loads((WORK / item['file']).read_text())
            for index, (name, content) in enumerate(zip(source_map.get('sources', []), source_map.get('sourcesContent', []))):
                if content is None:
                    continue
                group = hashlib.sha256(item['url'].encode()).hexdigest()[:12]
                relative = Path('published-sources') / group / (str(index) + '-' + re.sub(r'[^a-zA-Z0-9._-]', '_', name)[-160:])
                path = WORK / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                count += 1
        except Exception as error:
            issues.append({'url': item['url'], 'reason': 'source-map parse: ' + str(error)[:100]})
    return count


async def main():
    WORK.mkdir(parents=True, exist_ok=True)
    observations = []
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        context = await browser.new_context(viewport={'width': 1440, 'height': 1000}, locale='ru-RU')
        context.on('response', on_response)
        page = await context.new_page()
        response = await page.goto(BASE, wait_until='domcontentloaded', timeout=45000)
        await page.wait_for_timeout(4500)
        title = await page.title()
        if response is None or response.status >= 400 or 'just a moment' in title.lower():
            raise RuntimeError('Site unavailable or challenge page; no attempt to bypass: ' + title)
        await snapshot(page, '01-home-desktop')
        filters = page.get_by_text('Фильтры', exact=True)
        if await filters.count():
            try:
                await filters.first.click(timeout=5000)
                await snapshot(page, '02-filters-desktop')
                observations.append('Filters clicked and captured')
            except Exception as error:
                issues.append({'page': 'filters', 'reason': str(error)[:200]})
        await page.goto(BASE, wait_until='domcontentloaded', timeout=45000)
        await page.wait_for_timeout(2000)
        headings = page.locator('h3')
        if await headings.count():
            try:
                await headings.first.click(timeout=5000)
                await page.wait_for_timeout(2500)
                await snapshot(page, '03-anime-desktop')
                observations.append('First catalog card clicked; resulting URL: ' + page.url)
            except Exception as error:
                issues.append({'page': 'anime', 'reason': str(error)[:200]})
        await page.set_viewport_size({'width': 390, 'height': 844})
        await page.goto(BASE, wait_until='domcontentloaded', timeout=45000)
        await page.wait_for_timeout(2000)
        await snapshot(page, '04-home-mobile')
        await drain()
        await linked_assets(context)
        try:
            robots = await context.request.get(urljoin(BASE, 'robots.txt'), timeout=15000)
            (WORK / 'robots.txt').write_text(await robots.text())
        except Exception as error:
            issues.append({'page': 'robots.txt', 'reason': str(error)[:200]})
        await context.close()
        await browser.close()
    await drain()
    source_count = extract_public_sources()
    manifest = {'site': BASE, 'date': '2026-09-09', 'scope': 'Published public frontend; not the private development repository',
                'total_resource_bytes': total, 'resource_count': len(records), 'published_source_count': source_count,
                'observations': observations, 'resources': list(records.values()), 'issues': issues,
                'limits': {'files': MAX_FILES, 'resource_bytes': MAX_BYTES, 'per_file_bytes': MAX_FILE, 'poster_samples': 12}}
    (WORK / 'MANIFEST.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    shutil.copy2(__file__, WORK / 'capture-animetka.py')
    (WORK / 'README.ru.md').write_text(f'''# Animetka — публичный фронтенд для изучения

Источник: {BASE}
Дата: 2026-09-09. Ресурсов: {len(records)}. Объём исходных ресурсов: {total} байт.
Файлов исходников, извлечённых из опубликованных source maps: {source_count}.

Это снимок браузерной поставки сайта: HTML, CSS, JavaScript, шрифты и ограниченная выборка изображений.
Это НЕ полный закрытый репозиторий, НЕ серверный код, НЕ гарантированно все страницы и состояния сайта.
Встроенные сторонние видеоплееры, видеопотоки, авторизация, пользовательские аккаунты и аналитика не выгружались.
Закрытые API, каталоги сервера и непубличные файлы не сканировались. Защита/авторизация не обходились.

## Папки

- `public/` — реальные файлы опубликованного сайта, без форматирования и переписывания содержимого.
- `pages/` — DOM после JavaScript, видимый текст, список элементов управления и часть computed styles.
- `screenshots/` — снимки страниц в Chromium, включая мобильную ширину.
- `published-sources/` — только sourcesContent из явно опубликованных и связанных source maps, если они были доступны.
- `MANIFEST.json` — URL, путь, тип, HTTP-статус, размер, SHA-256 каждого ресурса, ограничения и ошибки.
- `capture-animetka.py` — наш скрипт получения снимка; он не принадлежит исходному сайту.

## Как изучать

Начните со скриншотов, затем откройте CSS и JS из `public/animetka.com/`. По MANIFEST.json можно сопоставить файл и его URL.
JavaScript может быть минифицирован: это оригинальная опубликованная сборка, не восстановленный вручную исходный проект.
HTML/DOM могут ссылаться на рабочий сайт. Простое открытие HTML офлайн не гарантирует работу API, маршрутов и интерактивных функций.
Для повторного получения: Python 3, `pip install playwright`, `playwright install chromium`, затем запуск скрипта из репозитория с ожидаемой структурой директорий.

## Права

Права на оформление, код, шрифты и изображения принадлежат их правообладателям. Этот снимок не предоставляет лицензию на перепубликацию сайта.
Изучение и возможное использование конкретных файлов — с учётом лицензий и разрешений владельцев. YORU этим снимком не изменяется.

## Полнота

Сняты только доступные публичные состояния главной, фильтров и первой карточки. Набор рекурсивно дополнен явно связанными статическими файлами.
Все ограничения и неудачные запросы перечислены в MANIFEST.json. Наличие UI-элемента не доказывает успешность всей серверной функции.
''')
    OUTPUT.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(OUTPUT, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(WORK.rglob('*')):
            if path.is_file():
                archive.write(path, Path('ANIMETKA-public-frontend') / path.relative_to(WORK))
    with zipfile.ZipFile(OUTPUT) as archive:
        assert archive.testzip() is None
    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    Path(str(OUTPUT) + '.sha256').write_text(digest + '  ' + OUTPUT.relative_to(ROOT).as_posix() + '\n')
    summary = {'resources': len(records), 'resource_bytes': total, 'source_map_sources': source_count,
               'zip_bytes': OUTPUT.stat().st_size, 'sha256': digest, 'observations': observations, 'issues': issues}
    (ROOT / 'handoff/ANIMETKA-capture-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    asyncio.run(main())
