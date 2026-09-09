(() => {
  const input = Array.from(document.querySelectorAll('input[type="search"],input[placeholder]'))
    .find(e => e.getBoundingClientRect().width > 0 && /поиск|search/i.test(e.placeholder));
  if (!input) return 'waiting';
  input.focus();
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, __QUERY__);
  input.dispatchEvent(new Event('input', {bubbles: true}));
  input.dispatchEvent(new Event('change', {bubbles: true}));
  input.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
  input.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
  return 'submitted';
})()
