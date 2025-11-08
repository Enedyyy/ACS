<template>
	<header class="topbar">
		<div class="brand">💰 FinTrack</div>
		<nav class="nav">
			<div class="user-col">
				<button @click="toggleTheme" title="Тема">🌓</button>
				<div v-if="user" id="userBadge" class="user-badge">👤 <span id="userName">{{ user.username }}</span></div>
				<button v-if="!user" id="loginBtn" @click="showAuth = true">Войти</button>
				<button v-else id="logoutBtn" @click="logout">Выйти</button>
			</div>
		</nav>
	</header>

	<main class="container">
		<section class="auth" v-if="!user">
			<h2>Авторизация</h2>
			<form @submit.prevent="login">
				<input v-model.trim="form.username" name="username" placeholder="Логин" required minlength="3" />
				<input v-model.trim="form.password" type="password" name="password" placeholder="Пароль" required minlength="3" />
				<button type="submit">Войти</button>
			</form>
			<div class="hint">Нет аккаунта? <a href="#" @click.prevent="register">Зарегистрироваться</a></div>
		</section>

		<section v-else class="dashboard">
			<div class="stats-row">
				<div class="stat-card">
					<div class="stat-label">Всего транзакций</div>
					<div class="stat-value">{{ stats.total }}</div>
				</div>
				<div class="stat-card">
					<div class="stat-label">Расходы</div>
					<div class="stat-value" id="statExpenses">{{ stats.expenses }}</div>
				</div>
				<div class="stat-card">
					<div class="stat-label">Доходы</div>
					<div class="stat-value" id="statIncome">{{ stats.income }}</div>
				</div>
			</div>

			<div class="grid">
				<div class="tile tile--accent col-5">
					<h3>Добавить транзакцию</h3>
					<form @submit.prevent="addTransaction">
						<div class="form-grid">
							<input v-model="txForm.date" name="date" type="date" required />
							<select v-model="txForm.type" name="type">
								<option value="expense">Расход</option>
								<option value="income">Доход</option>
							</select>
							<input v-model.number="txForm.amount" name="amount" type="number" step="0.01" placeholder="Сумма" required />
							<div class="full" style="display:flex;gap:8px;align-items:center;">
								<input v-model.trim="txForm.category" name="category" placeholder="Категория" style="flex:1;" />
								<button type="button" title="Добавить в избранное" @click="toggleFavForTx" :aria-pressed="isFavTxCat">
									{{ isFavTxCat ? '★' : '☆' }}
								</button>
							</div>
							<input v-model.trim="txForm.description" name="description" placeholder="Описание" class="full" @input="maybeSuggestCategory" />
							<div class="full row" style="justify-content:flex-start;">
								<button type="button" v-for="q in quickAmounts" :key="q" @click="applyQuick(q)">{{ q }}</button>
							</div>
							<div class="full" style="display:flex;gap:8px;align-items:center;">
								<select v-model="selectedTemplate" style="flex:1;">
									<option value="" disabled selected>Выбрать шаблон…</option>
									<option v-for="t in templates" :key="t.name" :value="t.name">{{ t.name }}</option>
								</select>
								<button type="button" @click="useTemplate" :disabled="!selectedTemplate">Подставить</button>
							</div>
							<div class="full" style="display:flex;gap:8px;align-items:center;">
								<input v-model.trim="newTemplateName" placeholder="Название шаблона" style="flex:1;" />
								<button type="button" @click="saveTemplate" :disabled="!newTemplateName">Сохранить шаблон</button>
								<button type="button" @click="deleteTemplate" :disabled="!selectedTemplate">Удалить</button>
							</div>
							<button type="submit" class="full">Сохранить</button>
						</div>
					</form>
				</div>

				<div class="tile col-7">
					<h3>Графики</h3>
					<div class="row" style="margin-bottom:8px">
						<label>Режим
							<select v-model="viewMode">
								<option value="categories">Категории</option>
								<option value="group">Группа</option>
							</select>
						</label>
						<!-- Категории -->
						<label>Показать
							<select v-model="chartMode">
								<option value="expenses">Расходы</option>
								<option value="income">Доходы</option>
								<option value="both">Оба (|сумма|)</option>
							</select>
						</label>
						<button @click="async ()=>{ if (viewMode==='group') { await refreshGroupMeta(); await refreshChart(); } else { await refreshTx(); await refreshChart(); } }">Обновить</button>
					</div>
					<canvas ref="chartRef"></canvas>
				</div>

				<div class="tile col-5">
					<h3>Бюджеты</h3>
					<form @submit.prevent="setBudget">
						<div class="form-grid">
							<input v-model.trim="budgetForm.category" name="category" placeholder="Категория" required />
							<input v-model.number="budgetForm.limit" name="limit" type="number" step="0.01" placeholder="Лимит" required />
							<button type="submit" class="full">Установить</button>
						</div>
					</form>
					<div id="budgetList">
						<div v-for="b in budgets" :key="b.category">
							{{ b.category }}: <strong>{{ b.spent }}</strong> / {{ b.limit }}
							<button style="margin-left:8px" title="Удалить лимит" @click="deleteBudget(b.category)">🗑</button>
						</div>
					</div>
				</div>

				<div class="tile col-7">
					<h3>Курсы валют</h3>
					<div class="row">
						<label>База
							<select v-model="currency.base" id="baseSelect">
								<option value="MDL">MDL</option>
								<option value="USD">USD</option>
								<option value="EUR">EUR</option>
								<option value="RUB">RUB</option>
							</select>
						</label>
						<label>К валюте
							<select v-model="currency.symbol" id="currencySelect">
								<option value="USD">USD</option>
								<option value="EUR">EUR</option>
								<option value="RUB">RUB</option>
								<option value="MDL">MDL</option>
							</select>
						</label>
						<label>Сумма
							<input type="number" step="0.01" v-model.number="convert.amount" placeholder="0" />
						</label>
						<button @click="convertCurrency">Пересчитать</button>
					</div>
					<div id="rateBox">{{ currencyText }}<span v-if="convert.result!=null"> • {{ fmt(convert.amount) }} {{ currency.base }} = <strong>{{ fmt(convert.result) }}</strong> {{ currency.symbol }}</span></div>
				</div>

				<div class="tile col-12">
					<h3>История транзакций</h3>
					<div class="row" style="margin-bottom:12px">
						<button @click="setQuick('today')">Сегодня</button>
						<button @click="setQuick('week')">Неделя</button>
						<button @click="setQuick('month')">Месяц</button>
						<input v-model.trim="filters.search" placeholder="Поиск по описанию" />
						<select v-model="filters.onlyFav">
							<option :value="false">Все категории</option>
							<option :value="true">Избранные</option>
						</select>
						<button @click="exportCsv">Экспорт CSV</button>
					</div>
					<form class="row" @submit.prevent="(async ()=>{ await refreshTx(); await refreshChart(); })()">
						<label>От даты
							<input v-model="filters.from" name="from" type="date" />
						</label>
						<label>До даты
							<input v-model="filters.to" name="to" type="date" />
						</label>
						<label>Категория
							<input v-model.trim="filters.category" name="category" placeholder="Категория" />
						</label>
						<button type="submit">Применить фильтр</button>
					</form>
					<div class="table-container">
						<table id="txTable">
							<thead>
								<tr>
									<th>Дата</th>
									<th>Категория</th>
									<th>Описание</th>
									<th>Сумма</th>
									<th></th>
								</tr>
							</thead>
							<tbody>
								<tr v-for="x in filteredTx" :key="x.id">
									<td>{{ x.date }}</td>
									<td>{{ x.category ?? '—' }}</td>
									<td>{{ x.description ?? '—' }}</td>
									<td>{{ x.amount }}</td>
									<td><button @click="deleteTx(x.id)" title="Удалить">🗑</button></td>
								</tr>
							</tbody>
						</table>
					</div>
				</div>

				<div class="tile col-5">
					<h3>Рекомендации</h3>
					<div class="row" style="margin-bottom:8px">
						<button @click="recShowOpts=!recShowOpts">⚙ Показать настройки</button>
						<button @click="()=>{ saveRecOpts(); buildRecommendations(); }" v-if="recShowOpts">Сохранить настройки</button>
					</div>
					<div v-if="recShowOpts" class="form-grid" style="margin-bottom:10px">
						<label>Период анализа (дней)
							<input type="number" min="7" v-model.number="recOpts.periodDays" />
							<div style="color:var(--muted);font-size:0.85em">Берём операции за последние N дней</div>
						</label>
						<label>Окно для подписок (дней)
							<input type="number" min="14" v-model.number="recOpts.subWindowDays" />
							<div style="color:var(--muted);font-size:0.85em">Ищем повторы по описанию в этом окне</div>
						</label>
						<label>Повторения подписки (раз)
							<input type="number" min="2" v-model.number="recOpts.subMinCount" />
							<div style="color:var(--muted);font-size:0.85em">Сколько одинаковых операций считать подпиской</div>
						</label>
						<label>Порог суммы подписки (за период)
							<input type="number" min="0" step="1" v-model.number="recOpts.subMinSum" />
							<div style="color:var(--muted);font-size:0.85em">Минимальная сумма повторов за окно</div>
						</label>
						<label>Сократить топ‑категорию на (%)
							<input type="number" min="1" max="50" v-model.number="recOpts.top1CutPct" />
							<div style="color:var(--muted);font-size:0.85em">Рекомендация для самой затратной категории</div>
						</label>
						<label>Сократить вторую категорию на (%)
							<input type="number" min="1" max="50" v-model.number="recOpts.top2CutPct" />
							<div style="color:var(--muted);font-size:0.85em">Рекомендация для категории №2 по расходам</div>
						</label>
						<label>Считать доминирующей, если доля > (%)
							<input type="number" min="10" max="90" v-model.number="recOpts.topSharePct" />
							<div style="color:var(--muted);font-size:0.85em">Порог доли одной категории в расходах</div>
						</label>
					</div>
					<div id="recoList">
						<div v-for="r in recommendations" :key="r.message">- {{ r.message }}<span v-if="r.potentialSave"> (≈ {{ r.potentialSave }})</span><div v-if="r.criteria" style="color:var(--muted);font-size:0.85em">Основание: {{ r.criteria }}</div></div>
						<div v-if="recommendations.length===0">—</div>
					</div>
				</div>

				<div class="tile col-7">
					<h3>Напоминания</h3>
					<form class="row" @submit.prevent="addReminder">
						<label>Дата
							<input v-model="reminderForm.dueDate" type="date" name="dueDate" required />
						</label>
						<label>Сообщение
							<input v-model.trim="reminderForm.message" name="message" placeholder="Напоминание" />
						</label>
						<label>Сумма
							<input v-model.number="reminderForm.amount" type="number" step="0.01" name="amount" placeholder="Опц." />
						</label>
						<button type="submit">Добавить</button>
					</form>
					<div id="reminderList">
						<div v-for="r in reminders" :key="r.id">{{ r.date }}: {{ r.message }}<span v-if="r.amount!=null"> ({{ r.amount }})</span></div>
						<div v-if="reminders.length===0">—</div>
					</div>
				</div>

				<div class="tile col-12">
					<h3>Групповой бюджет</h3>
					<form class="row" @submit.prevent="groupCreate">
						<input v-model.trim="groupForm.name" name="name" placeholder="Название группы" />
						<button type="submit">Создать</button>
					</form>
					<form class="row" @submit.prevent="groupJoin">
						<input v-model.trim="groupForm.groupId" name="groupId" placeholder="ID группы" required />
						<button type="submit">Присоединиться</button>
					</form>
					<div class="row" style="margin:8px 0">
						<div>Текущая группа: <strong>{{ myGroup.groupId || '—' }}</strong></div>
						<button @click="copyInvite">Скопировать приглашение</button>
						<button v-if="myGroup.groupId" @click="groupLeave">Выйти из группы</button>
					</div>
					<table id="groupBudgetTable">
						<thead><tr><th>Категория</th><th>Лимит</th><th>Траты</th></tr></thead>
						<tbody>
							<tr v-for="x in groupBudget" :key="x.category">
								<td>{{ x.category }}</td><td>{{ x.limit }}</td><td>{{ x.spent }}</td>
							</tr>
						</tbody>
					</table>
					<div style="margin-top:12px">
						<h4>Участники (Доходы/Расходы)</h4>
						<table>
							<thead><tr><th>UserId</th><th>Доход</th><th>Расход</th></tr></thead>
							<tbody>
								<tr v-for="m in groupPeers" :key="m.userId">
									<td>{{ m.userId }}</td>
									<td>{{ m.income }}</td>
									<td>{{ m.expense }}</td>
								</tr>
								<tr v-if="groupPeers.length===0"><td colspan="3">—</td></tr>
							</tbody>
						</table>
					</div>
				</div>

				<div class="tile col-12">
					<h3>Криптовалюты</h3>
					<div class="row" style="margin-bottom:12px">
						<select v-model="crypto.symbol">
							<option value="bitcoin">BTC</option>
							<option value="ethereum">ETH</option>
						</select>
						<button @click="loadCrypto">Обновить</button>
					</div>
					<div class="table-container">
						<table>
							<thead><tr><th>Монета</th><th>Цена (USD)</th><th>Изм. 24ч</th></tr></thead>
							<tbody>
								<tr v-for="c in cryptoList" :key="c.id">
									<td>{{ c.symbol.toUpperCase() }}</td>
									<td>{{ c.current_price }}</td>
									<td :style="{color: c.price_change_percentage_24h>=0?'#22c55e':'#ff6b6b'}">
										{{ c.price_change_percentage_24h.toFixed(2) }}%
									</td>
								</tr>
							</tbody>
						</table>
					</div>
				</div>
			</div>
		</section>
	</main>

	<div id="toast" class="toast" v-show="toastMsg">{{ toastMsg }}</div>
</template>

<script lang="ts" setup>
import { onMounted, ref, computed, watch } from 'vue';
import { useStore } from './store';
import { api, sseConnect, suggestCategory, exportTransactionsCsv } from './api/client';
import Chart from 'chart.js/auto';

const store = useStore();
const user = computed(()=>store.user);
const toastMsg = ref('');
const showAuth = ref(false);

function toast(m: string) {
	toastMsg.value = m;
	setTimeout(()=> toastMsg.value = '', 2500);
}

function toggleTheme() {
	store.setTheme(store.theme === 'dark' ? 'light' : 'dark');
}

// auth
const form = ref({ username: '', password: '' });
async function login() {
	const res = await api('/api/login', { method:'POST', form: form.value });
	if (res.ok) { store.setUser(res.user); await afterAuth(); toast('Добро пожаловать, '+res.user.username); }
	else toast(res.error || 'Ошибка входа');
}
async function register() {
	const res = await api('/api/register', { method:'POST', form: form.value });
	if (res.ok) toast('Регистрация успешна, войдите'); else toast(res.error || 'Ошибка регистрации');
}
async function logout() {
	await api('/api/logout', { method:'POST' });
	store.setUser(null);
}

async function afterAuth() {
	sseConnect((e)=>{
		if (e.type === 'budget-update') refreshBudget();
		if (e.type === 'reminder' || e.type === 'alert') toast(e.message || 'Уведомление');
		if (e.type === 'tx-added') { refreshTx(); }
	});
	await Promise.all([refreshAll()]);
}

// stats
const stats = ref({ total: 0, expenses: '—', income: '—' as any });

// transactions
type TxItem = { id: string; date: string; category: string | null; description: string | null; amount: number };
const filters = ref<{from:string|undefined;to:string|undefined;category:string|undefined;search:string;onlyFav:boolean}>({from:undefined,to:undefined,category:undefined,search:'',onlyFav:false});
const transactions = ref<TxItem[]>([]);
const filteredTx = computed(()=> {
	let list = transactions.value.slice().reverse();
	if (filters.value.search) {
		const s = filters.value.search.toLowerCase();
		list = list.filter((x: TxItem)=> (x.description ?? '').toLowerCase().includes(s));
	}
	if (filters.value.onlyFav) {
		list = list.filter((x: TxItem)=> favSet.value.has(x.category ?? ''));
	}
	return list;
});
async function refreshTx() {
	const params = new URLSearchParams();
	if (filters.value.from) params.set('from', filters.value.from!);
	if (filters.value.to) params.set('to', filters.value.to!);
	if (filters.value.category) params.set('category', filters.value.category!);
	const data = await api('/api/transactions?'+params.toString());
	transactions.value = data.items || [];
	stats.value.total = transactions.value.length;
	const expenses = transactions.value.filter((x: TxItem) => x.amount < 0).reduce((s: number, x: TxItem) => s + x.amount, 0);
	const income = transactions.value.filter((x: TxItem) => x.amount > 0).reduce((s: number, x: TxItem) => s + x.amount, 0);
	stats.value.expenses = expenses.toFixed(2);
	stats.value.income = income.toFixed(2);
}

async function deleteTx(id: string) {
	if (!confirm('Удалить транзакцию?')) return;
	const res = await api('/api/transaction/delete', { method:'POST', form: { id } });
	if (res && res.ok) { toast('Удалено'); await refreshTx(); await refreshChart(); await refreshBudget(); buildRecommendations(); }
	else toast(res.error || 'Не удалось удалить');
}

const txForm = ref<{date:string; type:'expense'|'income'; amount:number|null; category:string; description:string}>({date: new Date().toISOString().split('T')[0], type:'expense', amount: null, category: '', description: ''});
let suggestTimer: any;
const isFavTxCat = computed(()=> !!txForm.value.category && favSet.value.has(txForm.value.category));
function toggleFavForTx() {
	if (!txForm.value.category) return;
	store.toggleFavorite(txForm.value.category);
}
const quickAmounts = ['-100','-500','-1000','+100','+500','+1000'];
function applyQuick(q: string) {
	const n = Number(q);
	if (isNaN(n)) return;
	if (n < 0) { txForm.value.type = 'expense'; txForm.value.amount = Math.abs(n); }
	else { txForm.value.type = 'income'; txForm.value.amount = n; }
}

// Templates (LocalStorage)
type TxTemplate = { name:string; type:'expense'|'income'; amount:number; category:string; description:string };
const templates = ref<TxTemplate[]>(loadTemplates());
const selectedTemplate = ref<string>('');
const newTemplateName = ref<string>('');
function loadTemplates(): TxTemplate[] {
	try { const raw = localStorage.getItem('tx.templates'); return raw ? JSON.parse(raw) : []; } catch { return []; }
}
function persistTemplates() { localStorage.setItem('tx.templates', JSON.stringify(templates.value)); }
function saveTemplate() {
	if (!newTemplateName.value.trim()) return;
	const t: TxTemplate = {
		name: newTemplateName.value.trim(),
		type: txForm.value.type,
		// сумма для шаблона не критична — используем быстрые кнопки; фиксируем 0
		amount: 0,
		category: txForm.value.category.trim(),
		// описание в шаблоне хранить не обязательно; используем имя как заголовок
		description: newTemplateName.value.trim()
	};
	const idx = templates.value.findIndex(x => x.name.toLowerCase() === t.name.toLowerCase());
	if (idx >= 0) templates.value[idx] = t; else templates.value.push(t);
	persistTemplates();
	selectedTemplate.value = t.name; newTemplateName.value = '';
	toast('Шаблон сохранён');
}
function useTemplate() {
	const t = templates.value.find(x => x.name === selectedTemplate.value);
	if (!t) return;
	txForm.value.type = t.type;
	// сумму не трогаем — есть быстрые кнопки
	// подставляем НАЗВАНИЕ шаблона в поле "Категория" (как название операции)
	txForm.value.category = t.name;
	// описание не изменяем
}
function deleteTemplate() {
	const i = templates.value.findIndex(x=>x.name===selectedTemplate.value);
	if (i>=0) { templates.value.splice(i,1); persistTemplates(); selectedTemplate.value=''; toast('Шаблон удалён'); }
}
async function maybeSuggestCategory() {
	clearTimeout(suggestTimer);
	suggestTimer = setTimeout(async ()=>{
		if (!txForm.value.category && txForm.value.description) {
			const cat = await suggestCategory(txForm.value.description);
			if (cat) txForm.value.category = cat;
		}
	}, 300);
}
async function addTransaction() {
	const signAdjusted = (()=>{
		const amt = Number(txForm.value.amount ?? 0);
		if (txForm.value.type === 'expense') return -Math.abs(amt);
		return Math.abs(amt);
	})();
	const form = {
		date: txForm.value.date,
		amount: signAdjusted,
		category: txForm.value.category,
		description: txForm.value.description
	};
	const res = await api('/api/transaction/add', { method:'POST', form });
	if (res.ok) { toast('Транзакция добавлена'); await refreshTx(); buildRecommendations(); txForm.value = {date: new Date().toISOString().split('T')[0], type:'expense', amount: null, category: '', description: ''}; }
	else toast(res.error||'Ошибка');
}

// budgets
const budgets = ref<any[]>([]);
const budgetForm = ref<{category:string; limit:number|null}>({category:'', limit:null});
async function refreshBudget() {
	const data = await api('/api/budget');
	budgets.value = data.items || [];
}
async function setBudget() {
	const res = await api('/api/budget/set', { method:'POST', form: budgetForm.value });
	if (res.ok) { toast('Бюджет обновлён'); await refreshBudget(); budgetForm.value = {category:'', limit:null}; }
	else toast(res.error||'Ошибка');
}
async function deleteBudget(category: string) {
	if (!confirm(`Удалить лимит категории "${category}"?`)) return;
	const res = await api('/api/budget/delete', { method:'POST', form: { category } });
	if (res.ok) { toast('Лимит удалён'); await refreshBudget(); }
	else toast(res.error||'Ошибка');
}

// favorites
const favSet = computed(()=> store.favorites);
function toggleFav(cat: string) { store.toggleFavorite(cat); }

// reports chart
const chartRef = ref<HTMLCanvasElement|null>(null);
const viewMode = ref<'categories'|'group'>('categories');
const chartMode = ref<'expenses'|'income'|'both'>('expenses');
let chart: Chart | null = null;
async function refreshChart() {
	let labels: string[] = [];
	let values: number[] = [];
	if (viewMode.value === 'group') {
		// по участникам
		labels = groupPeers.value.map(p => p.userId.slice(0, 6)+'…');
		if (chartMode.value === 'income') values = groupPeers.value.map(p=>Math.round(p.income*100)/100);
		else if (chartMode.value === 'expenses') values = groupPeers.value.map(p=>Math.round(p.expense*100)/100);
		else values = groupPeers.value.map(p=>Math.round((p.income+p.expense)*100)/100);
	} else {
		const totals = new Map<string, number>();
		for (const t of transactions.value) {
			const cat = (t.category ?? 'Прочее');
			if (chartMode.value === 'expenses' && t.amount < 0) totals.set(cat, (totals.get(cat) ?? 0) + (-t.amount));
			if (chartMode.value === 'income' && t.amount > 0) totals.set(cat, (totals.get(cat) ?? 0) + t.amount);
			if (chartMode.value === 'both') totals.set(cat, (totals.get(cat) ?? 0) + Math.abs(t.amount));
		}
		const base = totals;
		const entries = Array.from(base.entries()).sort((a,b)=> b[1]-a[1]);
		labels = entries.map(e=>e[0]);
		values = entries.map(e=>Math.round(e[1]*100)/100);
	}
	if (chart) chart.destroy();
	if (chartRef.value) {
		chart = new Chart(chartRef.value, {
			type: 'doughnut',
			data: { labels, datasets: [{ data: values, backgroundColor:['#22c55e','#14b8a6','#f59e0b','#ef4444','#8b5cf6','#ec4899'], borderWidth: 0 }] },
			options: { responsive: true, maintainAspectRatio: true, plugins: { legend: { position: 'bottom', labels: { color: '#e8f5ee', padding: 15 } } } }
		});
	}
}

// Currency convert
const convert = ref<{amount:number|null; result:number|null}>({ amount:null, result:null });
function fmt(n: number|null|undefined) {
	if (n==null || isNaN(Number(n))) return '—';
	return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(Number(n));
}
async function convertCurrency() {
	const base = currency.value.base; const sym = currency.value.symbol;
	const amt = Number(convert.value.amount ?? 0);
	if (!amt || isNaN(amt)) { convert.value.result = null; return; }
	const data = await api(`/api/currency/convert?from=${encodeURIComponent(base)}&to=${encodeURIComponent(sym)}&amount=${encodeURIComponent(String(amt))}`);
	let res: number | null = null;
	if (data && data.ok && typeof data.result === 'number' && isFinite(data.result)) {
		res = Number(data.result);
	} else if (currency.value.val != null) {
		res = amt * Number(currency.value.val);
	}
	convert.value.result = res;
}

// currency
const currency = ref<{base:string; symbol:string; val:number|null}>({ base:'MDL', symbol:'USD', val:null });
const currencyText = computed(()=> currency.value.val!=null ? `1 ${currency.value.base} = ${fmt(currency.value.val)} ${currency.value.symbol}` : '—');
watch(()=>[currency.value.base, currency.value.symbol], ()=> refreshRate());
async function refreshRate() {
	const data = await api('/api/currency?base='+encodeURIComponent(currency.value.base)+'&symbols='+encodeURIComponent(currency.value.symbol));
	const val = data && data.rates ? data.rates[currency.value.symbol] : null;
	currency.value.val = val ?? null;
}

// recommendations (клиентская логика поверх загруженных данных)
type Rec = { message: string; potentialSave?: number; criteria?: string };
const recommendations = ref<Array<Rec>>([]);
const recShowOpts = ref(false);
type RecOpts = { periodDays:number; subWindowDays:number; subMinCount:number; subMinSum:number; topSharePct:number; top1CutPct:number; top2CutPct:number };
function loadRecOpts(): RecOpts {
	try { const raw = localStorage.getItem('rec.options'); if (raw) return JSON.parse(raw); } catch {}
	return { periodDays:30, subWindowDays:60, subMinCount:3, subMinSum:100, topSharePct:35, top1CutPct:12, top2CutPct:10 };
}
function saveRecOpts() { localStorage.setItem('rec.options', JSON.stringify(recOpts.value)); }
const recOpts = ref<RecOpts>(loadRecOpts());
function buildRecommendations() {
	const items = transactions.value;
	// период по настройкам
	const today = new Date();
	const monthAgo = new Date(); monthAgo.setDate(today.getDate() - recOpts.value.periodDays);
	const inLast30 = items.filter(t => new Date(t.date) >= monthAgo);

	// суммы
	const totalIncome = inLast30.filter(t=>t.amount>0).reduce((s,t)=>s+t.amount,0);
	const totalExpense = inLast30.filter(t=>t.amount<0).reduce((s,t)=>s+(-t.amount),0);

	// по категориям (расходы)
	const catMap = new Map<string, number>();
	for (const t of inLast30) {
		if (t.amount < 0) {
			const c = t.category ?? 'Прочее';
			catMap.set(c, (catMap.get(c) ?? 0) + (-t.amount));
		}
	}
	const top = Array.from(catMap.entries()).sort((a,b)=>b[1]-a[1]);

	// построить список
	const recs: Array<Rec> = [];

	// 1) если расходов > доходов — совет сократить необязательные траты
	if (totalExpense > totalIncome && totalIncome > 0) {
		const gap = Math.round(totalExpense - totalIncome);
		recs.push({ message: `Расходы превышают доходы на ≈ ${gap}. Сократите необязательные траты` , potentialSave: gap, criteria: 'Период 30 дней: расходы > доходов' });
	}

	// 2) top категории: предложить снизить на 10–15%
	if (top.length > 0) {
		const [cat, amt] = top[0];
		const save = Math.round(amt * 0.12);
		recs.push({ message: `Оптимизируйте категорию '${cat}' на ${recOpts.value.top1CutPct}%`, potentialSave: Math.round(amt * (recOpts.value.top1CutPct/100)), criteria: 'Топ‑1 категория по расходам' });
	}
	if (top.length > 1) {
		const [cat2, amt2] = top[1];
		const save2 = Math.round(amt2 * (recOpts.value.top2CutPct/100));
		recs.push({ message: `Снизьте траты во второй по размеру категории '${cat2}' на ${recOpts.value.top2CutPct}%`, potentialSave: save2, criteria: 'Топ‑2 категория по расходам' });
	}

	// 3) подозрение на подписки: повторяющиеся описания 3+ раз за 60 дней
	const twoMonthsAgo = new Date(); twoMonthsAgo.setDate(today.getDate() - recOpts.value.subWindowDays);
	const byDesc = new Map<string, {count:number,total:number}>();
	for (const t of items) {
		if (new Date(t.date) < twoMonthsAgo) continue;
		const d = (t.description ?? '').trim().toLowerCase();
		if (!d) continue;
		const e = byDesc.get(d) ?? {count:0,total:0};
		byDesc.set(d, {count:e.count+1,total: e.total + Math.abs(t.amount)});
	}
	for (const [desc, agg] of byDesc.entries()) {
		if (agg.count >= recOpts.value.subMinCount && agg.total >= recOpts.value.subMinSum) {
			recs.push({ message: `Проверьте возможную подписку: '${desc}' (${Math.round(agg.total)} за ${recOpts.value.subWindowDays} дней)`, criteria: `≥${recOpts.value.subMinCount} совпадений, сумма ≥ ${recOpts.value.subMinSum}` });
		}
	}

	// 4) бюджеты: если есть бюджеты и категория переполнена — уведомление
	for (const b of budgets.value) {
		if (b.limit > 0 && b.spent > b.limit) {
			recs.push({ message: `Превышен бюджет по '${b.category}'`, potentialSave: Math.round(b.spent - b.limit), criteria: 'spent > limit' });
		}
	}

	// 5) Доля топ‑категории > 35% расходов
	if (totalExpense > 0 && top.length > 0) {
		const share = top[0][1] / totalExpense;
		if (share > (recOpts.value.topSharePct/100)) {
			recs.push({ message: `Категория '${top[0][0]}' занимает ${Math.round(share*100)}% расходов. Рассмотрите лимит`, criteria: `>${recOpts.value.topSharePct}% расходов в одной категории` });
		}
	}
	// 6) Нет бюджета на топ‑3
	const budgetSet = new Set(budgets.value.map((b:any)=>(b.category||'').toLowerCase()));
	for (let i=0;i<Math.min(3, top.length);i++) {
		const cat = top[i][0];
		if (!budgetSet.has((cat||'').toLowerCase())) {
			recs.push({ message:`Добавьте бюджет для категории '${cat}'`, criteria:'Топ‑3 категория без бюджета' });
		}
	}

	// итого
	recommendations.value = recs.slice(0, 10);
}
async function refreshRecommendations() {
	// вместо запроса к /api/recommendations строим локально
	buildRecommendations();
}

// reminders
const reminders = ref<any[]>([]);
const reminderForm = ref<{dueDate:string; message:string; amount:number|null}>({ dueDate: new Date().toISOString().split('T')[0], message:'Платёж', amount:null });
async function refreshReminders() {
	const data = await api('/api/reminders');
	reminders.value = data.items || [];
}
async function addReminder() {
	const res = await api('/api/reminder/add', { method:'POST', form: reminderForm.value });
	if (res.ok) { toast('Напоминание добавлено'); await refreshReminders(); reminderForm.value = { dueDate: new Date().toISOString().split('T')[0], message:'Платёж', amount:null }; }
	else toast(res.error||'Ошибка');
}

// group
const groupBudget = ref<any[]>([]);
const groupPeers = ref<Array<{userId:string; income:number; expense:number}>>([]);
const myGroup = ref<{groupId:string|null; share:number|null}>({ groupId:null, share:null });
const groupForm = ref<{name:string; groupId:string}>({ name:'Семейный бюджет', groupId:'' });
async function refreshGroupBudget() {
	const data = await api('/api/group/budget');
	groupBudget.value = data.items || [];
}
async function groupCreate() {
	const res = await api('/api/group/create', { method:'POST', form: { name: groupForm.value.name } });
	if (res.ok) { 
		toast('Группа создана'); 
		groupForm.value.groupId = res.groupId || '';
		await refreshGroupMeta();
	} else toast(res.error||'Ошибка');
}
async function groupJoin() {
	const res = await api('/api/group/join', { method:'POST', form: { groupId: groupForm.value.groupId } });
	if (res.ok) { toast('Присоединились к группе'); await refreshGroupMeta(); await refreshGroupBudget(); } else toast(res.error||'Ошибка');
}
async function refreshGroupMeta() {
	const me = await api('/api/group/me');
	if (me && me.ok) { myGroup.value = { groupId: me.groupId, share: me.share }; } else { myGroup.value = { groupId:null, share:null }; }
	const peers = await api('/api/group/peers');
	groupPeers.value = peers.items || [];
}
async function groupLeave() {
	const res = await api('/api/group/leave', { method:'POST' });
	if (res.ok) { toast('Вы вышли из группы'); await refreshGroupMeta(); await refreshGroupBudget(); }
}
function copyInvite() {
	if (!myGroup.value.groupId && !groupForm.value.groupId) return;
	const gid = myGroup.value.groupId || groupForm.value.groupId;
	const url = new URL(window.location.href);
	url.searchParams.set('join', gid);
	navigator.clipboard.writeText(url.toString()).then(()=> toast('Ссылка приглашения скопирована'));
}

// crypto (client-side, independent from backend)
const crypto = ref<{symbol:string}>({ symbol:'bitcoin' });
const cryptoList = ref<any[]>([]);
async function loadCrypto() {
	try {
		const ids = [
			'bitcoin','ethereum','binancecoin','solana','toncoin','dogecoin','cardano','ripple','polkadot'
		].join(',');
		const url = `https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=${ids}&price_change_percentage=24h`;
		const res = await fetch(url);
		const data = await res.json();
		cryptoList.value = data || [];
		// cache for offline
		localStorage.setItem('crypto.latest', JSON.stringify(cryptoList.value));
	} catch {
		const cached = localStorage.getItem('crypto.latest');
		cryptoList.value = cached ? JSON.parse(cached) : [];
	}
}

function setQuick(kind: 'today'|'week'|'month') {
	const now = new Date();
	const to = now.toISOString().split('T')[0];
	let from: string;
	if (kind === 'today') from = to;
	else if (kind === 'week') {
		const d = new Date(now); d.setDate(now.getDate()-6);
		from = d.toISOString().split('T')[0];
	} else {
		const d = new Date(now); d.setDate(1);
		from = d.toISOString().split('T')[0];
	}
	filters.value.from = from;
	filters.value.to = to;
	(async ()=>{ await refreshTx(); await refreshChart(); })();
}

function exportCsv() {
	const csv = exportTransactionsCsv(filteredTx.value);
	const blob = new Blob([csv], { type:'text/csv;charset=utf-8' });
	const a = document.createElement('a');
	a.href = URL.createObjectURL(blob);
	a.download = 'transactions.csv';
	a.click();
	URL.revokeObjectURL(a.href);
}

async function refreshAll() {
	// Сначала подгружаем справочники/виджеты, потом транзакции и график
	await Promise.all([refreshBudget(), refreshRate(), refreshRecommendations(), refreshReminders(), refreshGroupBudget(), refreshGroupMeta(), loadCrypto()]);
	await refreshTx();
	await refreshChart();
}

onMounted(async () => {
	store.setTheme(store.theme);
	// центрирование формы авторизации: класс на body
	document.body.classList.toggle('auth-mode', !user.value);
	watch(user, (v)=> document.body.classList.toggle('auth-mode', !v));
	// автозаполнение groupId по ссылке ?join=...
	try {
		const u = new URL(window.location.href);
		const join = u.searchParams.get('join');
		if (join) groupForm.value.groupId = join;
	} catch {}
	// try session
	const res = await api('/api/me');
	if (res && res.ok) store.setUser(res.user);
	if (user.value) await afterAuth();
});
</script>

<style scoped>
</style>


