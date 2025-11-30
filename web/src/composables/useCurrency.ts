
import { ref, watch } from 'vue';
import { api } from '../api/client';

export function useCurrency() {
	const currency = ref<{ base: string; symbol: string; val: number | null }>({ base: 'MDL', symbol: 'USD', val: null });
	const convert = ref<{ amount: number | null; result: number | null }>({ amount: null, result: null });

	async function refreshRate() {
		const data = await api('/api/currency?base=' + encodeURIComponent(currency.value.base) + '&symbols=' + encodeURIComponent(currency.value.symbol));
		const val = data && data.rates ? data.rates[currency.value.symbol] : null;
		currency.value.val = val ?? null;
	}

	watch(() => [currency.value.base, currency.value.symbol], () => refreshRate());

	async function convertCurrency() {
		const base = currency.value.base;
		const sym = currency.value.symbol;
		const amt = Number(convert.value.amount ?? 0);
		if (!amt || isNaN(amt)) {
			convert.value.result = null;
			return;
		}
		const data = await api(`/api/currency/convert?from=${encodeURIComponent(base)}&to=${encodeURIComponent(sym)}&amount=${encodeURIComponent(String(amt))}`);
		let res: number | null = null;
		if (data && data.ok && typeof data.result === 'number' && isFinite(data.result)) {
			res = Number(data.result);
		} else if (currency.value.val != null) {
			res = amt * Number(currency.value.val);
		}
		convert.value.result = res;
	}

	return { currency, convert, refreshRate, convertCurrency };
}

