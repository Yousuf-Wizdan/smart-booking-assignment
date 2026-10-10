const $ = (s, r = document) => r.querySelector(s);

const state = {
  baseUrl: localStorage.getItem('sbe.baseUrl') || 'http://localhost:8080',
  token: localStorage.getItem('sbe.token') || '',
  role: localStorage.getItem('sbe.role') || '',
  username: localStorage.getItem('sbe.username') || '',
  employeeId: localStorage.getItem('sbe.employeeId') || '',
  lastBookingId: localStorage.getItem('sbe.lastBookingId') || '',
};

$('#baseUrl').value = state.baseUrl;
updateBadges();

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function updateBadges() {
  const tb = $('#tokenBadge'), rb = $('#roleBadge');
  tb.textContent = state.token ? 'signed in ✓' : 'not signed in';
  tb.className = 'badge ' + (state.token ? 'on' : 'off');
  rb.textContent = state.role ? state.role : 'no role';
  rb.className = 'badge ' + (state.role ? 'on' : 'off');
  $('#userLine').textContent = state.username
    ? `Signed in as ${state.username} (${state.role || 'unknown role'}).`
    : 'Not signed in — start with Step 1 below.';
}

function toast(msg, isErr = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast' + (isErr ? ' err' : '');
  clearTimeout(t._h);
  t._h = setTimeout(() => t.classList.add('hidden'), 5000);
}

function formJson(form, { checkboxes = [], numbers = [], jsonFields = [], skipEmpty = false } = {}) {
  const data = {};
  new FormData(form).forEach((v, k) => { data[k] = v; });
  for (const c of checkboxes) {
    const el = form.elements[c];
    data[c] = el ? el.checked : false;
  }
  for (const n of numbers) {
    if (data[n] === '' || data[n] == null) { if (skipEmpty) delete data[n]; }
    else data[n] = Number(data[n]);
  }
  for (const j of jsonFields) {
    if (data[j] === '' || data[j] == null) { delete data[j]; continue; }
    try { data[j] = JSON.parse(data[j]); }
    catch { throw new Error(`"${j}" is not valid JSON — check commas and quotes.`); }
  }
  if (skipEmpty) for (const k of Object.keys(data)) if (data[k] === '') delete data[k];
  return data;
}

function setForm(form, values) {
  for (const [k, v] of Object.entries(values)) {
    const el = form.elements[k];
    if (!el) continue;
    if (el.type === 'checkbox') el.checked = !!v;
    else el.value = v ?? '';
  }
}

const REASON_HINTS = {
  OUTSIDE_SHIFT_WINDOW: 'Requested time is outside the employee shift window.',
  PAST_CUTOFF: 'Booked too late — past the cut-off minutes before departure.',
  DUPLICATE: 'An active booking already exists for this employee and slot.',
  QUOTA_EXCEEDED: 'No free seat left in the cost-centre quota.',
  QUOTA_NOT_CONFIGURED: 'No quota row exists for this cost centre (admin must create one).',
  NIGHT_SAFETY: 'Woman employee as first-pickup/last-drop at night without escort.',
  INVALID_RULE_PARAMETERS: 'A policy rule has bad/missing parameters (fails closed).',
};

function ruleRows(rules) {
  if (!rules || !rules.length) return '<p>No rules ran.</p>';
  const rows = rules.map(r => {
    const msg = r.message ? `<br><span style="color:var(--mut)">${esc(r.message)}</span>` : '';
    return `<tr><td>${esc(r.priority)}</td><td><code>${esc(r.ruleType)}</code>${r.hardDeny ? ' 🔒' : ''}</td>`
      + `<td><span class="pill ${esc(r.outcome)}">${esc(r.outcome)}</span></td>`
      + `<td>${r.reasonCode ? `<code>${esc(r.reasonCode)}</code>${msg}` : '—'}</td></tr>`;
  }).join('');
  return `<h4>Rule breakdown (ran in this order)</h4>
    <table class="rules"><tr><th>#</th><th>Rule</th><th>Result</th><th>Reason</th></tr>${rows}</table>`;
}

function summarize(body, status, method, path) {
  if (body == null || typeof body === 'string') {
    if (status >= 200 && status < 300) return `<p>Done — empty response body.</p>`;
    return `<p class="err-text">${esc(body || 'Request failed.')}</p>`;
  }
  if (body.decision) {
    const hint = REASON_HINTS[body.decidingReason] || '';
    return `<h4>${esc(body.decision)}${body.decidingReason ? ` — <code>${esc(body.decidingReason)}</code>` : ''}</h4>`
      + (hint ? `<p>${esc(hint)}</p>` : '')
      + ((body.failureReasons && body.failureReasons.length) ? `<p>All failures: ${body.failureReasons.map(r => `<code>${esc(r)}</code>`).join(', ')}</p>` : '')
      + ruleRows(body.ruleResults);
  }
  if (body.token) {
    return `<h4>Signed in ✓</h4>
      <div class="krow"><span>User</span><code>${esc(body.username)} (${esc(body.role)})</code></div>
      <div class="krow"><span>Employee id</span><code>${esc(body.employeeId)}</code></div>
      <p>Token saved — it is sent automatically with every request below.</p>`;
  }
  if (body.username && !body.content) {
    return `<h4>Current user</h4>
      <div class="krow"><span>Username</span><code>${esc(body.username)}</code></div>
      <div class="krow"><span>Role</span><code>${esc(body.role)}</code></div>
      <div class="krow"><span>Employee id</span><code>${esc(body.employeeId)}</code></div>`;
  }
  if (body.bookingDate) {
    return `<h4>Booking <code>${esc(body.id)}</code></h4>
      <div class="krow"><span>Status</span><code>${esc(body.status)}</code></div>
      <div class="krow"><span>Decision</span><code>${esc(body.decision)}${body.decidingReason ? ' / ' + esc(body.decidingReason) : ''}</code></div>
      <div class="krow"><span>When</span><span>${esc(body.bookingDate)} · ${esc(body.direction)} · ${esc(body.requestedTime)}</span></div>
      <div class="krow"><span>Route</span><span>${esc(body.pickupLocation)} → ${esc(body.dropLocation)} (${esc(body.routePosition)})</span></div>
      <p>The id is filled into the Get / Cancel / Explanation / Approve / Reject forms.</p>`;
  }
  if (Array.isArray(body.content)) {
    const total = body.totalElements ?? body.content.length;
    const first = body.content[0];
    const what = path.includes('approvals') ? 'pending bookings' : path.includes('employees') ? 'employees' : 'bookings';
    let extra = '';
    if (first && first.bookingDate) {
      extra = '<table class="rules"><tr><th>Id</th><th>Slot</th><th>Status</th></tr>' + body.content.slice(0, 10).map(b =>
        `<tr><td><code>${esc(String(b.id).slice(0, 8))}…</code></td><td>${esc(b.bookingDate)} ${esc(b.direction)}</td><td>${esc(b.status)}</td></tr>`).join('') + '</table>';
      if (body.content.length > 10) extra += `<p>Showing 10 of ${body.content.length} on this page.</p>`;
    } else if (first && first.fullName) {
      extra = '<table class="rules"><tr><th>Code</th><th>Name</th><th>Cost centre</th></tr>' + body.content.slice(0, 10).map(e =>
        `<tr><td><code>${esc(e.employeeCode)}</code></td><td>${esc(e.fullName)}</td><td>${esc(e.costCentreCode)}</td></tr>`).join('') + '</table>';
    }
    return `<h4>${total} ${esc(what)} (page ${body.number ?? 0})</h4>` + extra;
  }
  if (Array.isArray(body)) {
    if (body[0] && body[0].costCentreCode && body[0].capacity != null) {
      return `<h4>${body.length} quota(s)</h4><table class="rules"><tr><th>Cost centre</th><th>Capacity</th><th>Reserved</th><th>Free</th></tr>`
        + body.map(q => `<tr><td><code>${esc(q.costCentreCode)}</code></td><td>${q.capacity}</td><td>${q.reservedSeats}</td><td>${q.freeSeats}</td></tr>`).join('') + '</table>';
    }
    if (body[0] && body[0].ruleType) {
      return `<h4>${body.length} rule(s)</h4><table class="rules"><tr><th>Priority</th><th>Type</th><th>Scope</th><th>On?</th></tr>`
        + body.map(p => `<tr><td>${p.priority}</td><td><code>${esc(p.ruleType)}</code></td><td>${esc(p.businessUnit || '*')}/${esc(p.costCentreCode || '*')}</td><td>${p.enabled ? 'on' : 'off'}</td></tr>`).join('') + '</table>';
    }
    if (body[0] && body[0].employeeCode) {
      return `<h4>Employee <code>${esc(body[0].employeeCode)}</code></h4>`
        + `<div class="krow"><span>Name</span><span>${esc(body[0].fullName)}</span></div>`
        + `<div class="krow"><span>Shift</span><span>${esc(body[0].shiftStart)} – ${esc(body[0].shiftEnd)}</span></div>`;
    }
    return `<h4>Done — ${body.length} item(s).</h4>`;
  }
  if (body.ruleType) {
    return `<h4>Rule <code>${esc(body.ruleType)}</code></h4>
      <div class="krow"><span>Id</span><code>${esc(body.id)}</code></div>
      <div class="krow"><span>Scope</span><code>${esc(body.businessUnit || '*')} / ${esc(body.costCentreCode || '*')}</code></div>
      <div class="krow"><span>Priority / enabled / hard-deny</span><span>${body.priority} / ${body.enabled ? 'on' : 'off'} / ${body.hardDeny ? 'yes' : 'no'}</span></div>
      <p>The id is filled into the Get / Update / Patch forms.</p>`;
  }
  if (body.costCentreCode && body.capacity != null) {
    return `<h4>Quota <code>${esc(body.costCentreCode)}</code></h4>
      <div class="krow"><span>Capacity</span><span>${body.capacity}</span></div>
      <div class="krow"><span>Reserved / free</span><span>${body.reservedSeats} / ${body.freeSeats}</span></div>`;
  }
  if (body.employeeCode) {
    return `<h4>Employee <code>${esc(body.employeeCode)}</code></h4>
      <div class="krow"><span>Id</span><code>${esc(body.id)}</code></div>
      <div class="krow"><span>Name</span><span>${esc(body.fullName)}</span></div>
      <div class="krow"><span>Shift</span><span>${esc(body.shiftStart)} – ${esc(body.shiftEnd)}</span></div>`;
  }
  if (body.status || body.error) {
    const msg = body.message || body.error || 'Request failed.';
    let hint = '';
    if (status === 401) hint = 'You are not signed in (or the token expired). Complete Step 1 first.';
    else if (status === 403) hint = 'Your role cannot use this endpoint — check the badge next to its title (e.g. ADMIN only). Sign in as a different user.';
    else if (status === 404) hint = 'Not found — usually a wrong or stale id. Create the item first, or re-list to get a fresh id.';
    else if (status === 409) hint = 'Conflict — e.g. username/employeeCode already exists, or a duplicate booking.';
    return `<p class="err-text"><strong>${status}: ${esc(msg)}</strong></p>` + (hint ? `<p>${hint}</p>` : '');
  }
  return `<p>Done — see raw JSON below.</p>`;
}

function render(method, path, status, body) {
  const url = state.baseUrl + path;
  const title = $('#respTitle');
  title.textContent = `${status} · ${method} ${path}`;
  title.className = (status >= 200 && status < 300) ? 'ok' : 'err';
  title.title = url;
  $('#summary').innerHTML = summarize(body, status, method, path);
  $('#response').innerHTML = `<code>${esc(typeof body === 'string' ? body : JSON.stringify(body, null, 2))}</code>`;
  $('#response').scrollTop = 0;
  if (status === 401) toast('Not signed in — complete Step 1 (Register/Login) first.', true);
  else if (status === 403) toast('Wrong role for this endpoint — see the badge by its title.', true);
  else if (status >= 500) toast(`Server error (${status}) — check the backend console.`, true);
}

async function api(method, path, body, { auth = true } = {}) {
  state.baseUrl = $('#baseUrl').value.trim().replace(/\/$/, '');
  localStorage.setItem('sbe.baseUrl', state.baseUrl);
  const headers = { 'Content-Type': 'application/json' };
  if (auth && state.token) headers['Authorization'] = 'Bearer ' + state.token;
  let res, text;
  try {
    res = await fetch(state.baseUrl + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    text = await res.text();
  } catch (e) {
    render(method, path, 'OFFLINE', 'Could not reach the backend.\n\n1. Is it running? (./mvnw spring-boot:run)\n2. Is Base URL correct (top of page)?\n3. Did you open this page via http:// (not file://)?\n\n' + String(e));
    return null;
  }
  let parsed;
  try { parsed = text ? JSON.parse(text) : null; } catch { parsed = text; }
  render(method, path, res.status, parsed);
  document.querySelector('.viewer').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  return { status: res.status, body: parsed };
}

function storeToken(body) {
  if (body && body.token) {
    state.token = body.token;
    state.role = body.role || '';
    state.username = body.username || '';
    state.employeeId = body.employeeId || state.employeeId;
    localStorage.setItem('sbe.token', state.token);
    localStorage.setItem('sbe.role', state.role);
    localStorage.setItem('sbe.username', state.username);
    localStorage.setItem('sbe.employeeId', state.employeeId);
    updateBadges();
  }
}

function on(id, fn) {
  $(id).addEventListener('submit', e => { e.preventDefault(); fn(e.target); });
}

on('#f-register', async f => {
  const b = formJson(f, { skipEmpty: true });
  const r = await api('POST', '/api/v1/auth/register', b, { auth: false });
  if (r && r.body) storeToken(r.body);
});
on('#f-login', async f => {
  const b = formJson(f);
  const r = await api('POST', '/api/v1/auth/login', b, { auth: false });
  if (r && r.body) storeToken(r.body);
});
on('#f-me', async () => { await api('GET', '/api/v1/auth/me'); });

$('#btnClearToken').onclick = () => {
  state.token = ''; state.role = ''; state.username = '';
  localStorage.removeItem('sbe.token'); localStorage.removeItem('sbe.role'); localStorage.removeItem('sbe.username');
  updateBadges(); toast('Signed out — token cleared.');
};

function bookingBody(f) {
  const b = formJson(f, { checkboxes: ['hasPackedLunch', 'requiresParking', 'escortAvailable'], skipEmpty: true });
  b.hasPackedLunch = !!b.hasPackedLunch; b.requiresParking = !!b.requiresParking; b.escortAvailable = !!b.escortAvailable;
  return b;
}

on('#f-evaluate', async f => {
  const b = bookingBody(f);
  const explain = f.elements.explain.checked;
  delete b.explain;
  await api('POST', `/api/v1/bookings/evaluate?explain=${explain}`, b);
});
on('#f-bcreate', async f => {
  const r = await api('POST', '/api/v1/bookings', bookingBody(f));
  if (r && r.body && r.body.id) {
    state.lastBookingId = r.body.id;
    localStorage.setItem('sbe.lastBookingId', state.lastBookingId);
    setForm($('#f-bget'), { id: state.lastBookingId });
    setForm($('#f-bcancel'), { id: state.lastBookingId });
    setForm($('#f-bexplain'), { id: state.lastBookingId });
    setForm($('#f-aapprove'), { bookingId: state.lastBookingId });
    setForm($('#f-areject'), { bookingId: state.lastBookingId });
  }
});
on('#f-bget', async f => {
  const { id } = formJson(f);
  await api('GET', `/api/v1/bookings/${id}`);
});
on('#f-blist', async f => {
  const q = formJson(f, { numbers: ['page', 'size'], skipEmpty: true });
  const qs = new URLSearchParams(q).toString();
  await api('GET', '/api/v1/bookings' + (qs ? `?${qs}` : ''));
});
on('#f-bcancel', async f => {
  const { id } = formJson(f);
  if (!confirm('Cancel this booking? Its seat returns to the quota.')) return;
  await api('POST', `/api/v1/bookings/${id}/cancel`);
});
on('#f-bexplain', async f => {
  const { id } = formJson(f);
  await api('GET', `/api/v1/bookings/${id}/explanation`);
});

on('#f-ecreate', async f => {
  const b = formJson(f, { checkboxes: ['active'], skipEmpty: true });
  b.active = f.elements.active.checked;
  const r = await api('POST', '/api/v1/employees', b);
  if (r && r.body && r.body.id) {
    setForm($('#f-eget'), { id: r.body.id });
    setForm($('#f-eupdate'), { id: r.body.id });
  }
});
on('#f-eget', async f => {
  const { id } = formJson(f);
  await api('GET', `/api/v1/employees/${id}`);
});
on('#f-elist', async f => {
  const q = formJson(f, { numbers: ['page', 'size'], skipEmpty: true });
  const qs = new URLSearchParams(q).toString();
  await api('GET', '/api/v1/employees' + (qs ? `?${qs}` : ''));
});
on('#f-eupdate', async f => {
  const b = formJson(f, { checkboxes: ['active'] });
  const { id, ...rest } = b;
  rest.active = f.elements.active.checked;
  await api('PUT', `/api/v1/employees/${id}`, rest);
});

on('#f-apending', async f => {
  const q = formJson(f, { numbers: ['page', 'size'], skipEmpty: true });
  const qs = new URLSearchParams(q).toString();
  await api('GET', '/api/v1/approvals/pending' + (qs ? `?${qs}` : ''));
});
on('#f-aapprove', async f => {
  const { bookingId } = formJson(f);
  await api('POST', `/api/v1/approvals/${bookingId}/approve`);
});
on('#f-areject', async f => {
  const { bookingId } = formJson(f);
  if (!confirm('Reject this booking? This is terminal.')) return;
  await api('POST', `/api/v1/approvals/${bookingId}/reject`);
});

on('#f-qlist', async () => { await api('GET', '/api/v1/quotas'); });
on('#f-qcreate', async f => {
  const b = formJson(f, { numbers: ['capacity'] });
  const r = await api('POST', '/api/v1/quotas', b);
  if (r && r.body && r.body.id) setForm($('#f-qupdate'), { id: r.body.id });
});
on('#f-qupdate', async f => {
  const b = formJson(f, { numbers: ['capacity', 'reservedSeats'], skipEmpty: true });
  const { id, ...rest } = b;
  await api('PUT', `/api/v1/quotas/${id}`, rest);
});

function policyBody(f) {
  const b = formJson(f, { checkboxes: ['enabled', 'hardDeny'], numbers: ['priority'], jsonFields: ['parameters'], skipEmpty: true });
  b.enabled = f.elements.enabled.checked;
  b.hardDeny = f.elements.hardDeny.checked;
  return b;
}
on('#f-plist', async () => { await api('GET', '/api/v1/policies'); });
on('#f-pcreate', async f => {
  try {
    const r = await api('POST', '/api/v1/policies', policyBody(f));
    if (r && r.body && r.body.id) {
      for (const sel of ['#f-pget', '#f-pupdate', '#f-penabled', '#f-ppriority']) setForm($(sel), { id: r.body.id });
    }
  } catch (e) { toast(e.message, true); }
});
on('#f-pget', async f => {
  const { id } = formJson(f);
  await api('GET', `/api/v1/policies/${id}`);
});
on('#f-pupdate', async f => {
  try {
    const b = policyBody(f);
    const { id, ...rest } = b;
    await api('PUT', `/api/v1/policies/${id}`, rest);
  } catch (e) { toast(e.message, true); }
});
on('#f-penabled', async f => {
  const { id } = formJson(f);
  await api('PATCH', `/api/v1/policies/${id}/enabled`, { enabled: f.elements.enabled.checked });
});
on('#f-ppriority', async f => {
  const b = formJson(f, { numbers: ['priority'] });
  await api('PATCH', `/api/v1/policies/${b.id}/priority`, { priority: b.priority });
});

function fillBooking(dateStr, timeStr, direction, route, escort) {
  for (const sel of ['#f-evaluate', '#f-bcreate']) {
    setForm($(sel), {
      bookingDate: dateStr, requestedTime: `${dateStr}T${timeStr}`,
      direction, pickupLocation: 'Gate A', dropLocation: 'Office', routePosition: route,
    });
    $(sel).elements.escortAvailable.checked = escort;
  }
  document.getElementById('card-bookings').scrollIntoView({ behavior: 'smooth' });
  toast(`Evaluate form filled: ${dateStr} ${timeStr} ${direction} — press "Evaluate (dry-run)".`);
}
$('#btnGood').onclick = () => fillBooking('2026-10-11', '09:00', 'LOGIN', 'OTHER', false);
$('#btnBad').onclick = () => fillBooking('2026-10-11', '23:30', 'LOGOUT', 'FIRST_PICKUP', false);

$('#btnCopy').onclick = async () => {
  try {
    await navigator.clipboard.writeText($('#summary').innerText + '\n\n' + $('#response').innerText);
    toast('Summary + JSON copied.');
  } catch { toast('Copy failed — select the text manually.', true); }
};
$('#btnClearResp').onclick = () => {
  $('#response').innerHTML = '<code>Nothing yet. Complete Step 1 (Register), then Evaluate a booking.</code>';
  $('#summary').innerHTML = ''; $('#verdict').innerHTML = ''; $('#respTitle').textContent = 'Response — results appear here';
};

if (state.lastBookingId) {
  for (const sel of ['#f-bget', '#f-bcancel', '#f-bexplain']) setForm($(sel), { id: state.lastBookingId });
  setForm($('#f-aapprove'), { bookingId: state.lastBookingId });
  setForm($('#f-areject'), { bookingId: state.lastBookingId });
}
