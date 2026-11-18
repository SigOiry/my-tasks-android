/* Layout-only acceptance on maintained HTML/CSS/handlers with synthetic HTTP.
 * Not cookie-auth, backend, provider or physical-device evidence.
 * Execute after real pointer/keyboard activation by the CDP driver.
 */
(() => {
  if (location.origin !== 'http://127.0.0.1:9187') throw Error('Wrong layout fixture origin');
  const checks = [], failures = [];
  const check = (condition, label) => { checks.push(label); if (!condition) failures.push(label); };
  const visible = e => !!e && e.getClientRects().length > 0 && getComputedStyle(e).visibility !== 'hidden';
  const rect = e => e.getBoundingClientRect().toJSON();
  const root = document.documentElement;
  const buttons = [...document.querySelectorAll('.destinations button')];
  check(root.scrollWidth <= root.clientWidth, 'document scrollWidth <= clientWidth');
  const privateHidden = document.getElementById('app').hidden;
  const compact = matchMedia('(max-width:700px)').matches;
  const modal = document.querySelector('dialog:modal');
  if (privateHidden) {
    check(buttons.length === 2 && buttons.every(e => !visible(e)), 'private destinations hidden');
    check(!visible(document.getElementById('new-mobile')), 'private Add task hidden');
    check(!visible(document.getElementById('new-free')), 'private Add discussion hidden');
    return {checks, failures, branch:'private-hidden', privateHidden, compact, clientWidth: root.clientWidth};
  }
  if (modal) {
    check(buttons.length === 2 && buttons.every(e => !visible(e)), 'modal destinations hidden');
    check(!visible(document.getElementById('new-mobile')), 'modal Add task hidden');
    check(!compact || !visible(document.getElementById('new-free')), 'compact modal Add discussion hidden');
    return {checks, failures, branch:'modal', modal:modal.id, privateHidden, compact, clientWidth:root.clientWidth};
  }
  check(document.fonts.check('12px Inter'), 'maintained Inter font loaded');
  check(buttons.length === 2, 'two maintained destinations');
  check(buttons.map(b => b.textContent.trim()).join('/') === 'Tasks/Discussions', 'full unchanged labels');
  const geometry = buttons.map(b => {
    const box = b.getBoundingClientRect(), style = getComputedStyle(b);
    const inner = {left: box.left + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft), right: box.right - parseFloat(style.borderRightWidth) - parseFloat(style.paddingRight), top: box.top + parseFloat(style.borderTopWidth) + parseFloat(style.paddingTop), bottom: box.bottom - parseFloat(style.borderBottomWidth) - parseFloat(style.paddingBottom)};
    const labels = [...b.childNodes].filter(n => n.nodeType === Node.TEXT_NODE && n.textContent.trim()).flatMap(n => {
      const r = document.createRange();
      const start = n.textContent.search(/\S/), end = n.textContent.trimEnd().length;
      r.setStart(n, start); r.setEnd(n, end);
      return [...r.getClientRects()].map(r => r.toJSON());
    });
    check(visible(b), b.id + ' visible');
    check(b.contains(document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)), b.id + ' center hit-test ownership');
    check(box.width >= 44 && box.height >= 44, b.id + ' target >= 44px');
    check(b.scrollWidth <= b.clientWidth, b.id + ' scrollWidth <= clientWidth');
    check(labels.length > 0 && labels.every(r => r.left >= inner.left - .5 && r.right <= inner.right + .5 && r.top >= inner.top - .5 && r.bottom <= inner.bottom + .5), b.id + ' label Range inside padded inner bounds');
    const icon = b.querySelector('svg').getBoundingClientRect();
    check(labels.every(r => r.left >= icon.right - .5 || r.top >= icon.bottom - .5 || r.bottom <= icon.top + .5), b.id + ' label separated from icon');
    return {id:b.id, box:box.toJSON(), inner, labels, icon:icon.toJSON(), font:style.fontSize, weight:style.fontWeight, current:b.getAttribute('aria-current'), clientWidth:b.clientWidth, scrollWidth:b.scrollWidth, focusVisible:b.matches(':focus-visible'), outline:style.outline, outlineOffset:style.outlineOffset};
  });
  check(geometry.length === 2 && (() => {
    const [a, b] = geometry.map(g => g.box);
    return a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top;
  })(), 'destination targets disjoint');
  check(buttons.filter(b => b.getAttribute('aria-current') === 'page').length === 1, 'exactly one selected destination');
  const destination = root.dataset.destination || 'tasks';
  check(buttons.some(b => b.id === 'nav-' + destination && b.getAttribute('aria-current') === 'page'), 'selected destination matches route');
  const nav = document.querySelector('.destinations'), nb = nav.getBoundingClientRect();
  // Required controls are selected by the expected route/layout, never by visibility.
  // Desktop Tasks uses the sidebar action, not the compact floating Add.
  const addId = compact ? (destination === 'tasks' ? 'new-mobile' : 'new-free') : (destination === 'discussions' ? 'new-free' : null);
  const add = addId ? [document.getElementById(addId)] : [];
  for (const e of add) {
    const shown = visible(e);
    check(shown, 'required ' + (compact ? 'compact' : 'desktop') + ' Add ' + addId + ' visible');
    const r = e?.getBoundingClientRect();
    check(shown && (r.left >= nb.right || r.right <= nb.left || r.top >= nb.bottom || r.bottom <= nb.top), 'destination separate from '+addId);
    check(shown && r.left >= 0 && r.top >= 0 && r.right <= root.clientWidth && r.bottom <= innerHeight, addId+' inside viewport');
  }
  if (compact) {
    check(nb.left >= 0 && nb.right <= root.clientWidth && nb.bottom <= innerHeight, 'compact navigation within viewport');
    check(innerHeight - nb.bottom >= 12, 'compact bottom safe-area minimum retained');
    check(getComputedStyle(nav).overflow === 'visible', 'navigation does not clip focus outline');
  }
  return {checks, failures, branch:'workspace', geometry, add:add.filter(visible).map(e => ({id:e.id,box:rect(e)})), nav:rect(nav), compact, privateHidden, clientWidth:root.clientWidth, scrollWidth:root.scrollWidth, active:document.activeElement.id, destination, theme:root.dataset.theme, forcedColors:matchMedia('(forced-colors:active)').matches};
})()
