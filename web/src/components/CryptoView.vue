
<template>
	<div class="tile col-12">
		<h3>Криптовалюты</h3>
		<div class="row" style="margin-bottom:12px">
			<label>Валюта отображения
				<select :value="crypto.currency" @change="$emit('update:currency', ($event.target as HTMLSelectElement).value)">
					<option value="usd">USD</option>
					<option value="eur">EUR</option>
				</select>
			</label>
			<button @click="$emit('load-crypto')">Обновить</button>
		</div>
		<div class="table-container">
			<table>
				<thead><tr><th>Монета</th><th>Цена ({{ crypto.currency.toUpperCase() }})</th><th>Изм. 24ч</th></tr></thead>
				<tbody>
					<tr v-for="c in cryptoList" :key="c.id">
						<td>{{ c.symbol.toUpperCase() }}</td>
						<td>{{ formatPrice(c.current_price) }}</td>
						<td :style="{color: c.price_change_percentage_24h>=0?'#22c55e':'#ff6b6b'}">
							{{ c.price_change_percentage_24h.toFixed(2) }}%
						</td>
					</tr>
				</tbody>
			</table>
		</div>
	</div>
</template>

<script lang="ts" setup>
function formatPrice(price: number): string {
	if (price < 0.01) return price.toFixed(8);
	if (price < 1) return price.toFixed(4);
	if (price < 100) return price.toFixed(2);
	return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(price);
}

defineProps<{
	crypto: { symbol: string; currency: string };
	cryptoList: Array<{ id: string; symbol: string; current_price: number; price_change_percentage_24h: number }>;
}>();

defineEmits<{
	'update:currency': [value: string];
	'load-crypto': [];
}>();
</script>

