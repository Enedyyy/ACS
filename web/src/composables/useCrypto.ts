
import { ref, watch } from 'vue';

export function useCrypto() {
	const crypto = ref<{ currency: string }>({ currency: 'usd' });
	const cryptoList = ref<any[]>([]);

	async function loadCrypto() {
		try {
			const ids = ['bitcoin', 'ethereum', 'binancecoin', 'solana', 'toncoin', 'dogecoin', 'cardano', 'ripple', 'polkadot'].join(',');
			const url = `https://api.coingecko.com/api/v3/coins/markets?vs_currency=${crypto.value.currency}&ids=${ids}&price_change_percentage=24h`;
			const res = await fetch(url);
			const data = await res.json();
			cryptoList.value = data || [];
			localStorage.setItem('crypto.latest', JSON.stringify(cryptoList.value));
		} catch {
			const cached = localStorage.getItem('crypto.latest');
			cryptoList.value = cached ? JSON.parse(cached) : [];
		}
	}

	// Автоматическая перезагрузка при изменении валюты
	watch(() => crypto.value.currency, () => {
		loadCrypto();
	});

	return { crypto, cryptoList, loadCrypto };
}

