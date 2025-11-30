
import { computed } from 'vue';
import { useStore } from '../store';
import { api, sseConnect } from '../api/client';

export function useAuth(
	toast: (msg: string) => void,
	refreshBudget: () => Promise<void>,
	refreshTx: () => Promise<void>,
	refreshAll: () => Promise<void>
) {
	const store = useStore();
	const user = computed(() => store.user);

	async function login(form: { username: string; password: string }) {
		const res = await api('/api/login', { method: 'POST', form });
		if (res.ok) {
			store.setUser(res.user);
			await afterAuth();
			toast('Добро пожаловать, ' + res.user.username);
		} else toast(res.error || 'Ошибка входа');
	}

	async function register(form: { username: string; password: string }) {
		const res = await api('/api/register', { method: 'POST', form });
		if (res.ok) toast('Регистрация успешна, войдите');
		else toast(res.error || 'Ошибка регистрации');
	}

	async function logout() {
		await api('/api/logout', { method: 'POST' });
		store.setUser(null);
	}

	async function afterAuth() {
		sseConnect((e) => {
			if (e.type === 'budget-update') refreshBudget();
			if (e.type === 'reminder' || e.type === 'alert') toast(e.message || 'Уведомление');
			if (e.type === 'tx-added') refreshTx();
		});
		await refreshAll();
	}

	return { user, login, register, logout, afterAuth };
}

