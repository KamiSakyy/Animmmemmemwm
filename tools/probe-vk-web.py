import asyncio, json, zipfile
from pathlib import Path
from urllib.parse import quote
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'workspace-output/vk-web-probe'
QUERY='Мастера меча онлайн 1 сезон 1 серия'

async def main():
    WORK.mkdir(parents=True,exist_ok=True)
    rows=[]
    async with async_playwright() as p:
        browser=await p.chromium.launch()
        ctx=await browser.new_context(viewport={'width':390,'height':844},is_mobile=True,has_touch=True,locale='ru-RU',user_agent='Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36')
        async def route(r):
            if r.request.resource_type=='media' or any(x in r.request.url for x in ('.m3u8','.mp4','.m4s','.mpd')):await r.abort()
            else:await r.continue_()
        await ctx.route('**/*',route)
        page=await ctx.new_page()
        page.set_default_timeout(6000)
        async def capture(name,url,interact=False):
            row={'name':name,'requested':url}
            try:
                response=await page.goto(url,wait_until='domcontentloaded',timeout=40000)
                await page.wait_for_timeout(3500)
                row.update(status=response.status if response else None,title=await page.title(),final_url=page.url)
                row['inputs']=await page.locator('input').evaluate_all('(es)=>es.map(e=>({placeholder:e.placeholder,type:e.type,name:e.name,value:e.value,visible:!!e.getBoundingClientRect().width}))')
                if interact and response and response.status<400:
                    inputs=page.locator('input:visible').filter(visible=True)
                    found=False
                    for field in await inputs.all():
                        placeholder=(await field.get_attribute('placeholder') or '').lower()
                        if 'поиск' in placeholder or 'search' in placeholder:
                            await field.fill(QUERY)
                            await field.press('Enter')
                            await page.wait_for_timeout(9000)
                            row['search_submitted']=True;found=True;break
                    row['search_control_found']=found
                    row['after_search_url']=page.url
                row['text']=(await page.locator('body').inner_text())[:7000]
                row['video_links']=await page.locator('a[href*="video"]').evaluate_all('(es)=>es.map(e=>({text:(e.innerText||e.title||" ").slice(0,130),href:e.href})).filter(e=>/video-?\\d+_\\d+/.test(e.href)).slice(0,12)')
                await page.screenshot(path=str(WORK/(name+'.png')),full_page=False)
            except Exception as e:row['error']=str(e)[:250]
            rows.append(row)
        await capture('01-mobile-utf8','https://m.vkvideo.ru/?q='+quote(QUERY)+'&action=search')
        await capture('02-mobile-ui-submit','https://m.vkvideo.ru/?q='+quote(QUERY)+'&action=search',True)
        desktop=await browser.new_context(viewport={'width':1280,'height':900},locale='ru-RU')
        await desktop.route('**/*',route)
        page=await desktop.new_page()
        await capture('03-desktop-ui-submit','https://vkvideo.ru/?q='+quote(QUERY),True)
        await browser.close()
    result={'date':'2026-09-09','scope':'Public mobile Chromium navigation; no login, cookies exported, protection bypass or video playback. Media requests blocked. HTTP 200 alone is not proof of functional search.','rows':rows}
    out=ROOT/'handoff/YORU-VK-web-verified-2026-09-09.json'
    out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    with zipfile.ZipFile(ROOT/'handoff/YORU-VK-web-verified-2026-09-09.zip','w',zipfile.ZIP_DEFLATED) as z:
        z.write(out,out.name)
        for f in WORK.glob('*.png'):z.write(f,f.name)
    print(json.dumps([{k:v for k,v in r.items() if k not in ('text','inputs','video_links')} for r in rows],ensure_ascii=False,indent=2))

asyncio.run(main())
