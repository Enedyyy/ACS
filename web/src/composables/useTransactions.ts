
import { ref, computed } from 'vue';
import { useStore } from '../store';
import { api, suggestCategory, exportTransactionsCsv } from '../api/client';

export type TxItem = { id: string; date: string; category: string | null; description: string | null; amount: number };
export type TxTemplate = { name: string; type: 'expense' | 'income'; amount: number; category: string; description: string };

export function useTransactions(toast: (msg: string) => void, refreshBudget: () => Promise<void>) {
	const refreshChartRef: { current: () => Promise<void> } = { current: async () => { } };
	const buildRecommendationsRef: { current: () => void } = { current: () => { } };
	const store = useStore();
	const favSet = computed(() => store.favorites);

	const filters = ref<{ from: string | undefined; to: string | undefined; category: string | undefined; search: string; onlyFav: boolean }>({
		from: undefined,
		to: undefined,
		category: undefined,
		search: '',
		onlyFav: false
	});

	const transactions = ref<TxItem[]>([]);
	const stats = ref({ total: 0, expenses: '—', income: '—' as any });

	const filteredTx = computed(() => {
		let list = transactions.value.slice().reverse();
		if (filters.value.search) {
			const s = filters.value.search.toLowerCase();
			list = list.filter((x: TxItem) => (x.description ?? '').toLowerCase().includes(s));
		}
		if (filters.value.onlyFav) {
			list = list.filter((x: TxItem) => favSet.value.has(x.category ?? ''));
		}
		return list;
	});

	async function refreshTx() {
		const params = new URLSearchParams();
		if (filters.value.from) params.set('from', filters.value.from!);
		if (filters.value.to) params.set('to', filters.value.to!);
		if (filters.value.category) params.set('category', filters.value.category!);
		const data = await api('/api/transactions?' + params.toString());
		transactions.value = data.items || [];
		stats.value.total = transactions.value.length;
		const expenses = transactions.value.filter((x: TxItem) => x.amount < 0).reduce((s: number, x: TxItem) => s + x.amount, 0);
		const income = transactions.value.filter((x: TxItem) => x.amount > 0).reduce((s: number, x: TxItem) => s + x.amount, 0);
		stats.value.expenses = expenses.toFixed(2);
		stats.value.income = income.toFixed(2);
	}

	async function deleteTx(id: string) {
		if (!confirm('Удалить транзакцию?')) return;
		const res = await api('/api/transaction/delete', { method: 'POST', form: { id } });
		if (res && res.ok) {
			toast('Удалено');
			await refreshTx();
			await refreshChartRef.current();
			await refreshBudget();
			buildRecommendationsRef.current();
		} else toast(res.error || 'Не удалось удалить');
	}

	const txForm = ref<{ date: string; type: 'expense' | 'income'; amount: number | null; category: string; description: string }>({
		date: new Date().toISOString().split('T')[0],
		type: 'expense',
		amount: null,
		category: '',
		description: ''
	});

	let suggestTimer: any;
	const isFavTxCat = computed(() => !!txForm.value.category && favSet.value.has(txForm.value.category));

	function toggleFavForTx() {
		if (!txForm.value.category) return;
		store.toggleFavorite(txForm.value.category);
	}

	const quickAmounts = ['-100', '-500', '-1000', '+100', '+500', '+1000'];

	function applyQuick(q: string) {
		const n = Number(q);
		if (isNaN(n)) return;
		if (n < 0) {
			txForm.value.type = 'expense';
			txForm.value.amount = Math.abs(n);
		} else {
			txForm.value.type = 'income';
			txForm.value.amount = n;
		}
	}

	// Templates
	const templates = ref<TxTemplate[]>(loadTemplates());

	function loadTemplates(): TxTemplate[] {
		try {
			const raw = localStorage.getItem('tx.templates');
			return raw ? JSON.parse(raw) : [];
		} catch {
			return [];
		}
	}

	function persistTemplates() {
		localStorage.setItem('tx.templates', JSON.stringify(templates.value));
	}

	function saveTemplate(name: string) {
		if (!name.trim()) return;
		const t: TxTemplate = {
			name: name.trim(),
			type: txForm.value.type,
			amount: 0,
			category: txForm.value.category.trim(),
			description: name.trim()
		};
		const idx = templates.value.findIndex(x => x.name.toLowerCase() === t.name.toLowerCase());
		if (idx >= 0) templates.value[idx] = t;
		else templates.value.push(t);
		persistTemplates();
		toast('Шаблон сохранён');
	}

	function useTemplate(name: string) {
		const t = templates.value.find(x => x.name === name);
		if (!t) return;
		txForm.value.type = t.type;
		txForm.value.category = t.name;
	}

	function deleteTemplate(name: string) {
		const i = templates.value.findIndex(x => x.name === name);
		if (i >= 0) {
			templates.value.splice(i, 1);
			persistTemplates();
			toast('Шаблон удалён');
		}
	}

	async function maybeSuggestCategory() {
		clearTimeout(suggestTimer);
		suggestTimer = setTimeout(async () => {
			if (!txForm.value.category && txForm.value.description) {
				const cat = await suggestCategory(txForm.value.description);
				if (cat) txForm.value.category = cat;
			}
		}, 300);
	}

	async function addTransaction() {
		const signAdjusted = (() => {
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
		const res = await api('/api/transaction/add', { method: 'POST', form });
		if (res.ok) {
			toast('Транзакция добавлена');
			await refreshTx();
			buildRecommendationsRef.current();
			txForm.value = {
				date: new Date().toISOString().split('T')[0],
				type: 'expense',
				amount: null,
				category: '',
				description: ''
			};
		} else toast(res.error || 'Ошибка');
	}

	function setQuick(kind: 'today' | 'week' | 'month') {
		const now = new Date();
		const to = now.toISOString().split('T')[0];
		let from: string;
		if (kind === 'today') from = to;
		else if (kind === 'week') {
			const d = new Date(now);
			d.setDate(now.getDate() - 6);
			from = d.toISOString().split('T')[0];
		} else {
			const d = new Date(now);
			d.setDate(1);
			from = d.toISOString().split('T')[0];
		}
		filters.value.from = from;
		filters.value.to = to;
	}

	function exportCsv() {
		const csv = exportTransactionsCsv(filteredTx.value);
		const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = 'transactions.csv';
		a.click();
		URL.revokeObjectURL(a.href);
	}

	return {
		filters,
		transactions,
		stats,
		filteredTx,
		txForm,
		templates,
		isFavTxCat,
		quickAmounts,
		refreshTx,
		deleteTx,
		addTransaction,
		toggleFavForTx,
		applyQuick,
		saveTemplate,
		useTemplate,
		deleteTemplate,
		maybeSuggestCategory,
		setQuick,
		exportCsv,
		setRefreshChart: (fn: () => Promise<void>) => { refreshChartRef.current = fn; },
		setBuildRecommendations: (fn: () => void) => { buildRecommendationsRef.current = fn; }
	};
}

