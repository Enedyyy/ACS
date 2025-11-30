
import { ref } from 'vue';
import { api } from '../api/client';

export function useBudgets(toast: (msg: string) => void) {
	const budgets = ref<any[]>([]);

	async function refreshBudget() {
		const data = await api('/api/budget');
		budgets.value = data.items || [];
	}

	async function setBudget(form: { category: string; limit: number | null }) {
		const res = await api('/api/budget/set', { method: 'POST', form });
		if (res.ok) {
			toast('Бюджет обновлён');
			await refreshBudget();
		} else toast(res.error || 'Ошибка');
	}

	async function deleteBudget(category: string) {
		if (!confirm(`Удалить лимит категории "${category}"?`)) return;
		const res = await api('/api/budget/delete', { method: 'POST', form: { category } });
		if (res.ok) {
			toast('Лимит удалён');
			await refreshBudget();
		} else toast(res.error || 'Ошибка');
	}

	return { budgets, refreshBudget, setBudget, deleteBudget };
}

