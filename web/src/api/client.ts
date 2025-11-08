type ApiOptions = {
	method?: 'GET'|'POST'|'PUT'|'DELETE';
	form?: Record<string, any>;
	body?: BodyInit;
};

const CACHE_DB_NAME = 'fintrack-cache';
const CACHE_STORE = 'responses';

let dbPromise: Promise<IDBDatabase> | null = null;
function idb(): Promise<IDBDatabase> {
	if (dbPromise) return dbPromise;
	dbPromise = new Promise((resolve, reject) => {
		const open = indexedDB.open(CACHE_DB_NAME, 1);
		open.onupgradeneeded = () => {
			const db = open.result;
			if (!db.objectStoreNames.contains(CACHE_STORE)) {
				db.createObjectStore(CACHE_STORE);
			}
		};
		open.onsuccess = () => resolve(open.result);
		open.onerror = () => reject(open.error);
	});
	return dbPromise;
}

async function cacheGet(key: string): Promise<any | null> {
	try {
		const db = await idb();
		return await new Promise((resolve, reject) => {
			const tx = db.transaction(CACHE_STORE, 'readonly');
			const store = tx.objectStore(CACHE_STORE);
			const req = store.get(key);
			req.onsuccess = () => resolve(req.result || null);
			req.onerror = () => reject(req.error);
		});
	} catch { return null; }
}
async function cacheSet(key: string, value: any): Promise<void> {
	try {
		const db = await idb();
		await new Promise<void>((resolve, reject) => {
			const tx = db.transaction(CACHE_STORE, 'readwrite');
			const store = tx.objectStore(CACHE_STORE);
			const req = store.put(value, key);
			req.onsuccess = () => resolve();
			req.onerror = () => reject(req.error);
		});
	} catch {}
}

function formToBody(form: Record<string, any>): string {
	const data = new URLSearchParams();
	for (const [k, v] of Object.entries(form)) {
		if (v === null || v === undefined) continue;
		data.append(k, String(v));
	}
	return data.toString();
}

export async function api(path: string, opts: ApiOptions = {}): Promise<any> {
	const method = opts.method ?? 'GET';
	const headers: HeadersInit = { 'Content-Type': 'application/x-www-form-urlencoded' };
	const token = localStorage.getItem('auth.jwt'); // if backend adds JWT later
	if (token) {
		(headers as any)['Authorization'] = `Bearer ${token}`;
	}
	const fetchOpts: RequestInit = {
		method,
		headers,
		credentials: token ? 'omit' : 'include'
	};
	if (opts.form) fetchOpts.body = formToBody(opts.form);
	if (opts.body) fetchOpts.body = opts.body;

	const cacheKey = method === 'GET' ? `GET ${path}` : null;

	try {
		const resp = await fetch(path, fetchOpts);
		const text = await resp.text();
		const json = safeJson(text);
		if (resp.ok && cacheKey) {
			await cacheSet(cacheKey, { json, at: Date.now() });
		}
		return json;
	} catch {
		if (cacheKey) {
			const cached = await cacheGet(cacheKey);
			if (cached) return cached.json;
		}
		return { ok:false, error:'offline' };
	}
}

function safeJson(txt: string): any {
	try { return JSON.parse(txt); } catch { return { ok:false, error:'Bad JSON', raw:txt }; }
}

export function sseConnect(onEvent: (e: any)=>void) {
	try {
		const es = new EventSource('/api/events');
		es.onmessage = (ev) => { try { onEvent(JSON.parse(ev.data)); } catch {} };
		es.onerror = () => { /* auto reconnect */ };
		return es;
	} catch { return null; }
}

export async function suggestCategory(desc: string): Promise<string | null> {
	const data = await api('/api/categorizer/suggest?desc='+encodeURIComponent(desc));
	return data.category || null;
}

export function exportTransactionsCsv(items: Array<any>): string {
	const header = ['id','date','category','description','amount'];
	const rows = items.map(x => [x.id, x.date, x.category ?? '', x.description ?? '', x.amount]);
	const all = [header, ...rows];
	return all.map(r => r.map(v => `"${String(v).replace(/"/g,'""')}"`).join(',')).join('\r\n');
}


