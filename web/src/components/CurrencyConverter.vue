
<template>
	<div class="tile col-7">
		<h3>Курсы валют</h3>
		<div class="row">
			<label>База
				<select :value="currency.base" @change="$emit('update:base', ($event.target as HTMLSelectElement).value)" id="baseSelect">
					<option value="MDL">MDL</option>
					<option value="USD">USD</option>
					<option value="EUR">EUR</option>
					<option value="RUB">RUB</option>
				</select>
			</label>
			<label>К валюте
				<select :value="currency.symbol" @change="$emit('update:symbol', ($event.target as HTMLSelectElement).value)" id="currencySelect">
					<option value="USD">USD</option>
					<option value="EUR">EUR</option>
					<option value="RUB">RUB</option>
					<option value="MDL">MDL</option>
				</select>
			</label>
			<label>Сумма
				<input type="number" step="0.01" :value="convert.amount" @input="handleAmountUpdate($event)" placeholder="0" />
			</label>
			<button @click="$emit('convert')">Пересчитать</button>
		</div>
		<div id="rateBox">{{ currencyText }}<span v-if="convert.result!=null"> • {{ fmt(convert.amount) }} {{ currency.base }} = <strong>{{ fmt(convert.result) }}</strong> {{ currency.symbol }}</span></div>
	</div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

const props = defineProps<{
	currency: { base: string; symbol: string; val: number | null };
	convert: { amount: number | null; result: number | null };
}>();

const emit = defineEmits<{
	'update:base': [value: string];
	'update:symbol': [value: string];
	'update:amount': [value: number | null];
	convert: [];
}>();

function fmt(n: number | null | undefined) {
	if (n == null || isNaN(Number(n))) return '—';
	return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(Number(n));
}

const currencyText = computed(() => 
	props.currency.val != null 
		? `1 ${props.currency.base} = ${fmt(props.currency.val)} ${props.currency.symbol}` 
		: '—'
);

function handleAmountUpdate(event: Event) {
	const value = (event.target as HTMLInputElement).value;
	const num = value === '' ? null : Number(value);
	emit('update:amount', isNaN(num as number) ? null : num);
}
</script>

