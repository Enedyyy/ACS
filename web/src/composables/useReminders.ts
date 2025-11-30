
import { ref } from 'vue';
import { api } from '../api/client';

export function useReminders(toast: (msg: string) => void) {
	const reminders = ref<any[]>([]);

	async function refreshReminders() {
		const data = await api('/api/reminders');
		reminders.value = data.items || [];
	}

	async function addReminder(form: { dueDate: string; message: string; amount: number | null }) {
		const res = await api('/api/reminder/add', { method: 'POST', form });
		if (res.ok) {
			toast('Напоминание добавлено');
			await refreshReminders();
		} else toast(res.error || 'Ошибка');
	}

	return { reminders, refreshReminders, addReminder };
}

