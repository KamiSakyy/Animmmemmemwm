import asyncio,json,time,urllib.request
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
rows=[]
fields='id name russian english kind score status episodes episodesAired airedOn{year} poster{mainUrl originalUrl} genres{russian name}'
for name,args in [('feed','limit:20,page:1,order:ranked,rating:"!rx"'),('feed-page2','limit:20,page:2,order:ranked,rating:"!rx"'),('search-ru','limit:20,page:1,order:ranked,rating:"!rx",search:"Мастера меча онлайн"'),('search-en','limit:20,page:1,order:ranked,rating:"!rx",search:"Sword Art Online"')]:
 row={'name':name}
 try:
  req=urllib.request.Request('https://shikimori.io/api/graphql',data=json.dumps({'query':'{animes('+args+'){'+fields+'}}'}).encode(),headers={'Content-Type':'application/json','User-Agent':'YORU-VK/0.1.0 (Android; independent prototype)'})
  with urllib.request.urlopen(req,timeout=25) as r:data=json.load(r)
  items=data.get('data',{}).get('animes',[]);row.update(count=len(items),errors=data.get('errors'),titles=[i.get('russian') for i in items[:3]],ids=[i.get('id') for i in items],posters=[i.get('poster') for i in items[:1]])
 except Exception as e:row['error']=str(e)
 rows.append(row);time.sleep(1)
async def probe():
 async with async_playwright() as p:
  b=await p.chromium.launch();page=await b.new_page(viewport={'width':1280,'height':900},locale='ru-RU',user_agent='Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36')
  await page.route('**/*',lambda r:r.abort() if r.request.resource_type=='media' else r.continue_())
  row={'name':'vk-search-dom-submit'}
  try:
   await page.goto('https://vkvideo.ru/',wait_until='domcontentloaded',timeout=45000)
   await page.locator('input[type=search]').first.wait_for(timeout=20000)
   script=(ROOT/'yoru-vk-android/app/src/main/assets/vk-search.js').read_text().replace('__QUERY__',json.dumps('Мастера меча онлайн 1 сезон 1 серия',ensure_ascii=False))
   row['submit']=await page.evaluate(script)
   await page.wait_for_timeout(16000)
   row['url']=page.url
   row['videos']=await page.locator('a[href*="video"]').evaluate_all('(es)=>es.filter(e=>/video-?\\d+_\\d+/.test(e.href)&&e.innerText.includes("Мастера")).map(e=>({title:e.innerText,url:e.href})).slice(0,6)')
   row['body']=(await page.locator('body').inner_text())[:1500]
  except Exception as e:row['error']=str(e)
  rows.append(row);await b.close()
asyncio.run(probe())
(ROOT/'handoff/YORU-VK-0.2.1-search-probe.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(rows,ensure_ascii=False,indent=2))
