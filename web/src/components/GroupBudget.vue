
<template>
	<div class="tile col-12">
		<h3>Групповой бюджет</h3>
		<form class="row" @submit.prevent="handleCreate">
			<input v-model.trim="groupForm.name" name="name" placeholder="Название группы" />
			<button type="submit" class="btn-primary">Создать</button>
		</form>
		<form class="row" @submit.prevent="handleJoin">
			<input v-model.trim="groupForm.groupId" name="groupId" placeholder="ID группы" required />
			<button type="submit" class="btn-primary">Присоединиться</button>
		</form>
		<div class="row" style="margin:8px 0">
			<div>Текущая группа: <strong>{{ myGroup.groupName || '—' }}</strong></div>
			<button @click="$emit('copy-invite')" class="btn-primary">Скопировать приглашение</button>
			<button v-if="myGroup.groupId" @click="$emit('leave-group')" class="btn-primary">Выйти из группы</button>
		</div>
		<div style="margin-top:16px">
			<div class="row" style="margin-bottom:8px; align-items:center;">
				<h4 style="margin:0; flex:1;">Участники (Доходы/Расходы)</h4>
				<button @click="$emit('refresh-group-meta')" class="btn-primary" style="margin-left:8px;">Обновить</button>
			</div>
			<div v-if="myGroup.groupId">
				<div v-if="groupPeers.length > 0">
					<table id="groupPeersTable">
						<thead><tr><th>Пользователь</th><th>Доход</th><th>Расход</th></tr></thead>
						<tbody>
							<tr v-for="m in groupPeers" :key="m.userId">
								<td>{{ m.username || 'Пользователь' }}</td>
								<td style="color: var(--acc);">{{ fmt(m.income) }}</td>
								<td style="color: var(--bad);">{{ fmt(m.expense) }}</td>
							</tr>
						</tbody>
					</table>
				</div>
				<div v-else style="text-align: center; padding: 32px; color: var(--muted);">Нет участников</div>
			</div>
			<div v-else style="text-align: center; padding: 32px; color: var(--muted);">Вы не в группе</div>
		</div>
	</div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { fmt } from '../utils/format';

const groupForm = ref<{ name: string; groupId: string }>({ name: 'Семейный бюджет', groupId: '' });

defineProps<{
	groupBudget: Array<{ category: string; limit: number; spent: number }>;
	groupPeers: Array<{ userId: string; username?: string; income: number; expense: number }>;
	myGroup: { groupId: string | null; groupName: string | null; share: number | null };
}>();

const emit = defineEmits<{
	'create-group': [name: string];
	'join-group': [groupId: string];
	'copy-invite': [];
	'leave-group': [];
	'refresh-group-meta': [];
}>();

function handleCreate() {
	emit('create-group', groupForm.value.name);
}

function handleJoin() {
	emit('join-group', groupForm.value.groupId);
}
</script>

