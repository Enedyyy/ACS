
import { ref } from 'vue';
import Chart from 'chart.js/auto';
import type ChartView from '../components/ChartView.vue';
import type { TxItem } from './useTransactions';

export function useChart(
	transactions: { value: TxItem[] },
	groupPeers: { value: Array<{ userId: string; username?: string; income: number; expense: number }> },
	refreshGroupMeta: () => Promise<void>,
	refreshTx: () => Promise<void>
) {
	const chartViewRef = ref<InstanceType<typeof ChartView> | null>(null);
	const viewMode = ref<'categories' | 'group'>('categories');
	const chartMode = ref<'expenses' | 'income' | 'both'>('expenses');
	let chart: Chart | null = null;

	async function refreshChart() {
		let labels: string[] = [];
		let values: number[] = [];
		if (viewMode.value === 'group') {
			// Используем username вместо userId, как в ACSPRIMER
			if (chartMode.value === 'expenses' || chartMode.value === 'both') {
				for (const p of groupPeers.value) {
					const username = p.username || p.userId || 'Пользователь';
					labels.push(`${username} (расходы)`);
					values.push(Math.max(0, p.expense || 0));
				}
			}
			if (chartMode.value === 'income' || chartMode.value === 'both') {
				for (const p of groupPeers.value) {
					const username = p.username || p.userId || 'Пользователь';
					labels.push(`${username} (доходы)`);
					values.push(Math.max(0, p.income || 0));
				}
			}
		} else {
			const totals = new Map<string, number>();
			for (const t of transactions.value) {
				const cat = (t.category ?? 'Прочее');
				if (chartMode.value === 'expenses' && t.amount < 0) totals.set(cat, (totals.get(cat) ?? 0) + (-t.amount));
				if (chartMode.value === 'income' && t.amount > 0) totals.set(cat, (totals.get(cat) ?? 0) + t.amount);
				if (chartMode.value === 'both') totals.set(cat, (totals.get(cat) ?? 0) + Math.abs(t.amount));
			}
			const entries = Array.from(totals.entries()).sort((a, b) => b[1] - a[1]);
			labels = entries.map(e => e[0]);
			values = entries.map(e => Math.round(e[1] * 100) / 100);
		}
		if (chart) chart.destroy();
		const canvas = chartViewRef.value?.chartRef;
		if (canvas) {
			const colors = ['#22c55e', '#14b8a6', '#4ecdc4', '#45b7d1', '#f9ca24', '#6c5ce7', '#a29bfe', '#fd79a8', '#fdcb6e', '#e17055'];
			chart = new Chart(canvas, {
				type: 'doughnut',
				data: { labels, datasets: [{ data: values, backgroundColor: labels.map((_, i) => colors[i % colors.length]), borderWidth: 0 }] },
				options: { responsive: true, maintainAspectRatio: true, plugins: { legend: { position: 'bottom', labels: { color: '#e8f5ee', padding: 15 } } } }
			});
		}
	}

	async function handleChartRefresh() {
		if (viewMode.value === 'group') {
			await refreshGroupMeta();
			await refreshChart();
		} else {
			await refreshTx();
			await refreshChart();
		}
	}

	return { chartViewRef, viewMode, chartMode, refreshChart, handleChartRefresh };
}

