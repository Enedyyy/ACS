
<template>
	<div class="tile col-12">
		<h3>История транзакций</h3>
		<div class="row" style="margin-bottom:12px">
			<button @click="$emit('set-quick', 'today')">Сегодня</button>
			<button @click="$emit('set-quick', 'week')">Неделя</button>
			<button @click="$emit('set-quick', 'month')">Месяц</button>
			<input :value="filters.search" @input="$emit('update:search', ($event.target as HTMLInputElement).value)" placeholder="Поиск по описанию" />
			<select :value="filters.onlyFav" @change="$emit('update:onlyFav', ($event.target as HTMLSelectElement).value === 'true')">
				<option :value="false">Все категории</option>
				<option :value="true">Избранные</option>
			</select>
			<button @click="$emit('export-csv')">Экспорт CSV</button>
		</div>
		<form class="row" @submit.prevent="$emit('apply-filter')">
			<label>От даты
				<input :value="filters.from" @input="$emit('update:from', ($event.target as HTMLInputElement).value)" name="from" type="date" />
			</label>
			<label>До даты
				<input :value="filters.to" @input="$emit('update:to', ($event.target as HTMLInputElement).value)" name="to" type="date" />
			</label>
			<label>Категория
				<input :value="filters.category" @input="$emit('update:category', ($event.target as HTMLInputElement).value)" name="category" placeholder="Категория" />
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
						<td><button @click="$emit('delete-tx', x.id)" title="Удалить">🗑</button></td>
					</tr>
				</tbody>
			</table>
		</div>
	</div>
</template>

<script lang="ts" setup>
defineProps<{
	filters: { from: string | undefined; to: string | undefined; category: string | undefined; search: string; onlyFav: boolean };
	filteredTx: Array<{ id: string; date: string; category: string | null; description: string | null; amount: number }>;
}>();

defineEmits<{
	'set-quick': [kind: 'today' | 'week' | 'month'];
	'update:search': [value: string];
	'update:onlyFav': [value: boolean];
	'update:from': [value: string];
	'update:to': [value: string];
	'update:category': [value: string];
	'apply-filter': [];
	'export-csv': [];
	'delete-tx': [id: string];
}>();
</script>

