
import { ref } from 'vue';
import type { TxItem } from './useTransactions';

type Rec = { message: string; potentialSave?: number; criteria?: string };
type RecOpts = { periodDays: number; subWindowDays: number; subMinCount: number; subMinSum: number; topSharePct: number; top1CutPct: number; top2CutPct: number };

function loadRecOpts(): RecOpts {
	try {
		const raw = localStorage.getItem('rec.options');
		if (raw) return JSON.parse(raw);
	} catch { }
	return { periodDays: 30, subWindowDays: 60, subMinCount: 3, subMinSum: 100, topSharePct: 35, top1CutPct: 12, top2CutPct: 10 };
}

export function useRecommendations(
	transactions: { value: TxItem[] },
	budgets: { value: Array<{ category: string; limit: number; spent: number }> }
) {
	const recommendations = ref<Array<Rec>>([]);
	const recOpts = ref<RecOpts>(loadRecOpts());

	function saveRecOpts() {
		localStorage.setItem('rec.options', JSON.stringify(recOpts.value));
	}

	function buildRecommendations() {
		const items = transactions.value;
		const today = new Date();
		const monthAgo = new Date();
		monthAgo.setDate(today.getDate() - recOpts.value.periodDays);
		const inLast30 = items.filter(t => new Date(t.date) >= monthAgo);

		const totalIncome = inLast30.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0);
		const totalExpense = inLast30.filter(t => t.amount < 0).reduce((s, t) => s + (-t.amount), 0);

		const catMap = new Map<string, number>();
		for (const t of inLast30) {
			if (t.amount < 0) {
				const c = t.category ?? 'Прочее';
				catMap.set(c, (catMap.get(c) ?? 0) + (-t.amount));
			}
		}
		const top = Array.from(catMap.entries()).sort((a, b) => b[1] - a[1]);

		const recs: Array<Rec> = [];

		if (totalExpense > totalIncome && totalIncome > 0) {
			const gap = Math.round(totalExpense - totalIncome);
			recs.push({ message: `Расходы превышают доходы на ≈ ${gap}. Сократите необязательные траты`, potentialSave: gap, criteria: 'Период 30 дней: расходы > доходов' });
		}

		if (top.length > 0) {
			const [cat, amt] = top[0];
			recs.push({ message: `Оптимизируйте категорию '${cat}' на ${recOpts.value.top1CutPct}%`, potentialSave: Math.round(amt * (recOpts.value.top1CutPct / 100)), criteria: 'Топ‑1 категория по расходам' });
		}
		if (top.length > 1) {
			const [cat2, amt2] = top[1];
			recs.push({ message: `Снизьте траты во второй по размеру категории '${cat2}' на ${recOpts.value.top2CutPct}%`, potentialSave: Math.round(amt2 * (recOpts.value.top2CutPct / 100)), criteria: 'Топ‑2 категория по расходам' });
		}

		const twoMonthsAgo = new Date();
		twoMonthsAgo.setDate(today.getDate() - recOpts.value.subWindowDays);
		const byDesc = new Map<string, { count: number; total: number }>();
		for (const t of items) {
			if (new Date(t.date) < twoMonthsAgo) continue;
			const d = (t.description ?? '').trim().toLowerCase();
			if (!d) continue;
			const e = byDesc.get(d) ?? { count: 0, total: 0 };
			byDesc.set(d, { count: e.count + 1, total: e.total + Math.abs(t.amount) });
		}
		for (const [desc, agg] of byDesc.entries()) {
			if (agg.count >= recOpts.value.subMinCount && agg.total >= recOpts.value.subMinSum) {
				recs.push({ message: `Проверьте возможную подписку: '${desc}' (${Math.round(agg.total)} за ${recOpts.value.subWindowDays} дней)`, criteria: `≥${recOpts.value.subMinCount} совпадений, сумма ≥ ${recOpts.value.subMinSum}` });
			}
		}

		for (const b of budgets.value) {
			if (b.limit > 0 && b.spent > b.limit) {
				recs.push({ message: `Превышен бюджет по '${b.category}'`, potentialSave: Math.round(b.spent - b.limit), criteria: 'spent > limit' });
			}
		}

		if (totalExpense > 0 && top.length > 0) {
			const share = top[0][1] / totalExpense;
			if (share > (recOpts.value.topSharePct / 100)) {
				recs.push({ message: `Категория '${top[0][0]}' занимает ${Math.round(share * 100)}% расходов. Рассмотрите лимит`, criteria: `>${recOpts.value.topSharePct}% расходов в одной категории` });
			}
		}

		const budgetSet = new Set(budgets.value.map((b: any) => (b.category || '').toLowerCase()));
		for (let i = 0; i < Math.min(3, top.length); i++) {
			const cat = top[i][0];
			if (!budgetSet.has((cat || '').toLowerCase())) {
				recs.push({ message: `Добавьте бюджет для категории '${cat}'`, criteria: 'Топ‑3 категория без бюджета' });
			}
		}

		recommendations.value = recs.slice(0, 10);
	}

	function handleSaveRecOpts() {
		saveRecOpts();
		buildRecommendations();
	}

	return { recommendations, recOpts, buildRecommendations, handleSaveRecOpts };
}

