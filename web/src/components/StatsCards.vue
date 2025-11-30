
<template>
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
</template>

<script lang="ts" setup>
defineProps<{
	stats: { total: number; expenses: string; income: string };
}>();
</script>

