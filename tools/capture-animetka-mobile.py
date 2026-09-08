import asyncio
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'workspace-output/animetka-mobile-dark'
OUT = ROOT / 'handoff/ANIMETKA-mobile-dark-2026-09-09.zip'
BASE = 'https://animetka.com/'
observations = []
network = []


async def snap(page, name, full=False):
    await page.wait_for_timeout(1000)
    await page.screenshot(path=str(WORK / (name + '.png')), full_page=full, timeout=30000)
    (WORK / (name + '.html')).write_text(await page.content())
    (WORK / (name + '.txt')).write_text(await page.locator('body').inner_text())
    metrics = await page.evaluate('''() => ({url:location.href,rootClass:document.documentElement.className,
      viewport:{width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth},
      bodyBackground:getComputedStyle(document.body).backgroundColor,
      tokens:Object.fromEntries(['--background','--foreground','--primary','--muted','--border','--radius'].map(k=>[k,getComputedStyle(document.documentElement).getPropertyValue(k)])),
      controls:[...document.querySelectorAll('button,a,input,select,[role="tab"],[role="dialog"],h1,h2,h3')].map(e=>{
        const r=e.getBoundingClientRect(),s=getComputedStyle(e);return {tag:e.tagName,role:e.getAttribute('role'),text:(e.innerText||'').slice(0,160),label:e.getAttribute('aria-label'),title:e.getAttribute('title'),placeholder:e.getAttribute('placeholder'),href:e.getAttribute('href'),visible:r.width>0&&r.height>0,x:r.x,y:r.y,width:r.width,height:r.height,fontSize:s.fontSize,color:s.color,background:s.backgroundColor,radius:s.borderRadius}})
    })''')
    (WORK / (name + '.json')).write_text(json.dumps(metrics, ensure_ascii=False, indent=2))
    observations.append({'capture': name, 'url': page.url, 'dark': 'dark' in metrics['rootClass'].split(), 'viewport': metrics['viewport']})


async def step(name, action):
    try:
        await action()
        observations.append({'step': name, 'result': 'ok'})
    except Exception as e:
        observations.append({'step': name, 'result': 'failed', 'error': str(e)[:350]})


async def home(page):
    await page.goto(BASE, wait_until='domcontentloaded', timeout=45000)
    await page.wait_for_timeout(2200)
    if await page.get_by_text('Установите наше приложение', exact=True).count():
        await page.keyboard.press('Escape')


async def main():
    WORK.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        context = await browser.new_context(viewport={'width': 390, 'height': 844}, device_scale_factor=1,
            is_mobile=True, has_touch=True, locale='ru-RU', color_scheme='light',
            user_agent='Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36')
        async def route(r):
            url = r.request.url
            if r.request.resource_type == 'media' or any(x in url for x in ('mc.yandex.ru', 'cloudflareinsights.com', 'google-analytics', '.m3u8', '.mp4', '.m4s', '.mpd')):
                await r.abort()
            else:
                await r.continue_()
        await context.route('**/*', route)
        context.on('response', lambda r: network.append({'url':r.url, 'status':r.status, 'type':r.request.resource_type}) if r.url.startswith(BASE) else None)
        page = await context.new_page()
        page.set_default_timeout(6000)
        await home(page)
        if 'just a moment' in (await page.title()).lower():
            raise RuntimeError('Challenge encountered; no bypass attempted')
        # Use the site's own theme control, not injected CSS or a fake mockup.
        await page.locator('button').first.click()
        await snap(page, '01-menu-light-before-toggle')
        await page.get_by_role('button', name='Светлая тема', exact=True).click()
        await snap(page, '02-menu-dark')
        assert await page.locator('html').evaluate('(e)=>e.classList.contains("dark")'), 'Theme toggle did not enable dark mode'
        async def palette():
            await page.locator('[role="dialog"] button[aria-haspopup="dialog"]').first.click()
            await snap(page, '03-theme-palette')
        await step('Open accent palette', palette)
        await home(page)
        await snap(page, '04-home-dark')
        await snap(page, '04b-home-dark-full', full=True)
        assert await page.locator('html').evaluate('(e)=>e.classList.contains("dark")'), 'Dark mode not retained on reload'
        async def filters():
            await page.locator('button:visible').filter(has=page.locator('svg.lucide-settings2-icon')).click()
            await snap(page, '05-filters-dark')
            await page.get_by_role('button', name='Открыть', exact=True).first.click()
            await snap(page, '06-genres-dark')
        await step('Open filters and genre picker', filters)
        await home(page)
        async def top():
            await page.get_by_role('tab', name='Топовые', exact=True).click()
            await page.wait_for_timeout(2000)
            await snap(page, '07-top-dark')
        await step('Switch top tab', top)
        async def random_tab():
            await page.get_by_role('tab', name='Случайные', exact=True).click()
            await page.wait_for_timeout(2000)
            await snap(page, '08-random-dark')
        await step('Switch random tab', random_tab)
        await home(page)
        async def detail():
            await page.locator('h3').first.click()
            await page.wait_for_timeout(2000)
            await snap(page, '09-anime-sheet-dark')
            await page.get_by_role('button', name='Начать просмотр', exact=True).click()
            await page.wait_for_timeout(4000)
            await snap(page, '10-anime-player-dark')
            await snap(page, '10b-anime-player-dark-full', full=True)
            await page.get_by_role('button', name='Смотреть', exact=True).click()
            await page.wait_for_timeout(4000)
            await snap(page, '10c-player-open-dark')
        await step('Open title sheet and player page, media requests blocked', detail)
        async def search():
            await page.goto(BASE+'search?q='+ 'Наруто', wait_until='domcontentloaded')
            await page.wait_for_timeout(3000)
            await snap(page, '11-search-dark')
            await snap(page, '11b-search-dark-full', full=True)
        await step('Public search page with Naruto query', search)
        await home(page)
        async def search_overlay():
            await page.locator('button:visible').filter(has=page.locator('svg.lucide-search-icon')).click()
            await snap(page, '11c-mobile-search-overlay')
            await page.get_by_placeholder('Найдите своё любимое аниме').filter(visible=True).fill('Наруто')
            await page.wait_for_timeout(1800)
            await snap(page, '11d-mobile-search-suggestions')
        await step('Mobile search overlay and suggestions', search_overlay)
        await home(page)
        async def history():
            await page.locator('button').first.click()
            await page.get_by_role('button', name='История просмотра', exact=True).click()
            await snap(page, '12-guest-history-dark')
        await step('Guest history', history)
        await home(page)
        async def login():
            await page.locator('button:visible').filter(has=page.locator('svg.lucide-user-icon')).click()
            await snap(page, '13-login-dark')
        await step('Open login dialog only, do not authenticate', login)
        for width in (360, 430):
            await page.set_viewport_size({'width': width, 'height': 844})
            await home(page)
            await snap(page, f'14-home-dark-{width}')
        # Save only public primary bundles for provenance, no API bodies or account data.
        urls = await page.locator('script[src],link[rel="stylesheet"]').evaluate_all('(es)=>es.map(e=>e.src||e.href)')
        resources = []
        for url in urls:
            if not url.startswith(BASE+'assets/'):
                continue
            response = await context.request.get(url)
            if response.ok:
                body = await response.body()
                name = url.rsplit('/',1)[-1]
                (WORK / name).write_bytes(body)
                resources.append({'url':url,'file':name,'sha256':hashlib.sha256(body).hexdigest(),'bytes':len(body)})
        await context.close()
        await browser.close()
    report = {'date':'2026-09-09','site':BASE,'scope':'Mobile Chromium, Android user agent, touch enabled, DPR 1, theme enabled via real UI. No real phone test. No login. Media and analytics blocked.', 'observations':observations,'resources':resources,'network':network}
    (WORK/'EVIDENCE.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    shutil.copy2(__file__,WORK/'capture-animetka-mobile.py')
    (WORK/'README.ru.md').write_text('# Animetka: мобильная тёмная тема\n\nРеальный браузерный снимок сайта, не макет. Тёмная тема включена штатной кнопкой сайта. Chromium, Android User Agent, touch, DPR 1; ширины 390/360/430 px. Не тест физического телефона.\n\nPNG — скриншоты, HTML/TXT — DOM и текст, JSON — размеры и стили элементов. EVIDENCE.json — действия, результаты, URL/HTTP-статусы, происхождение JS/CSS.\n\nВидео и аналитика блокировались; вход и регистрация не выполнялись. Скриншот страницы плеера не доказывает работу видео. Первое изображение намеренно показывает меню ДО переключения темы. Файлы не предоставляют лицензию на копирование сайта. Android/YORU не изменялись.\n')
    with zipfile.ZipFile(OUT,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for f in sorted(WORK.iterdir()):
            if f.is_file():
                z.write(f,'ANIMETKA-mobile-dark/'+f.name)
    sha=hashlib.sha256(OUT.read_bytes()).hexdigest()
    Path(str(OUT)+'.sha256').write_text(f'{sha}  {OUT.relative_to(ROOT)}\n')
    summary={'sha256':sha,'zip_bytes':OUT.stat().st_size,'observations':observations,'resources':resources}
    (ROOT/'handoff/ANIMETKA-mobile-dark-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    print(json.dumps(summary,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    asyncio.run(main())
