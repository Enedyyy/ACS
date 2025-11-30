/**
 * Composable для управления групповым бюджетом
 * 
 * Предоставляет функционал для совместного управления бюджетом несколькими пользователями:
 * - Создание группы и присоединение к существующей
 * - Просмотр бюджета группы и статистики участников
 * - Копирование ссылки-приглашения для присоединения к группе
 * - Выход из группы
 */
import { ref } from 'vue';
import { api } from '../api/client';

export function useGroup(toast: (msg: string) => void) {
	const groupBudget = ref<any[]>([]);
	const groupPeers = ref<Array<{ userId: string; username?: string; income: number; expense: number }>>([]);
	const myGroup = ref<{ groupId: string | null; groupName: string | null; share: number | null }>({ groupId: null, groupName: null, share: null });
	const groupForm = ref<{ name: string; groupId: string }>({ name: 'Семейный бюджет', groupId: '' });
	// Флаг для предотвращения одновременных вызовов refreshGroupMeta
	let refreshGroupMetaInProgress = false;

	async function refreshGroupBudget() {
		const data = await api('/api/group/budget');
		groupBudget.value = data.items || [];
	}

	async function groupCreate(name: string) {
		const res = await api('/api/group/create', { method: 'POST', form: { name } });
		if (res.ok) {
			toast('Группа создана');
			groupForm.value.groupId = res.groupId || '';
			await refreshGroupMeta();
		} else {
			toast(res.error || 'Ошибка');
		}
	}

	async function groupJoin(groupId: string) {
		const res = await api('/api/group/join', { method: 'POST', form: { groupId } });
		if (res.ok) {
			toast('Присоединились к группе');
			await refreshGroupMeta();
		} else {
			toast(res.error || 'Ошибка');
		}
	}

	/**
	 * Обновляет метаданные группы: информацию о группе пользователя и список участников.
	 * Защищено от одновременных вызовов флагом refreshGroupMetaInProgress.
	 * Очищает данные группы ТОЛЬКО если сервер явно говорит что пользователь не в группе (ok: false).
	 * Не очищает данные при сетевых ошибках, чтобы сохранить данные даже при временных проблемах.
	 */
	async function refreshGroupMeta() {
		// Защита от повторных одновременных вызовов
		if (refreshGroupMetaInProgress) {
			return;
		}
		refreshGroupMetaInProgress = true;
		try {
			const me = await api('/api/group/me');
			// Очищаем данные группы ТОЛЬКО если сервер явно говорит что пользователь не в группе
			// (ok: false означает что пользователь не в группе, но это нормально)
			// НЕ очищаем при сетевых ошибках или других проблемах
			if (me && me.ok) {
				// Пользователь в группе - обновляем данные
				myGroup.value = { groupId: me.groupId, groupName: me.groupName || 'Группа', share: me.share };
				
				// Загружаем данные участников
				const peers = await api('/api/group/peers');
				if (peers && peers.ok !== false && Array.isArray(peers.items)) {
					groupPeers.value = peers.items;
				} else if (peers && Array.isArray(peers.items)) {
					// Даже если ok === false, но есть items - используем их
					groupPeers.value = peers.items;
				}
				// Если ошибка загрузки peers, НЕ очищаем существующие данные
			} else if (me && me.ok === false) {
				// Сервер явно говорит что пользователь НЕ в группе - очищаем данные
				// Это происходит только когда пользователь действительно не в группе
				myGroup.value = { groupId: null, groupName: null, share: null };
				groupPeers.value = [];
			} else if (me && me.error === 'unauthorized') {
				// Ошибка авторизации - не трогаем данные, просто выходим
				return;
			}
			// Если me === null или undefined (сетевая ошибка) - НЕ трогаем существующие данные
			// Это позволяет сохранить данные группы даже при временных проблемах с сетью
		} finally {
			refreshGroupMetaInProgress = false;
		}
	}

	async function groupLeave() {
		const res = await api('/api/group/leave', { method: 'POST' });
		if (res.ok) {
			toast('Вы вышли из группы');
			await refreshGroupMeta();
		}
	}

	function copyInvite() {
		if (!myGroup.value.groupId && !groupForm.value.groupId) return;
		const gid = myGroup.value.groupId || groupForm.value.groupId;
		const url = new URL(window.location.href);
		url.searchParams.set('join', gid);
		navigator.clipboard.writeText(url.toString()).then(() => toast('Ссылка приглашения скопирована'));
	}

	return { groupBudget, groupPeers, myGroup, groupForm, refreshGroupBudget, groupCreate, groupJoin, refreshGroupMeta, groupLeave, copyInvite };
}

