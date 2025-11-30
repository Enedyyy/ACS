
<template>
	<div class="tile col-7">
		<h3>Напоминания</h3>
		<form class="row" @submit.prevent="handleSubmit">
			<label>Дата
				<input v-model="reminderForm.dueDate" type="date" name="dueDate" required />
			</label>
			<label>Сообщение
				<input v-model.trim="reminderForm.message" name="message" placeholder="Напоминание" />
			</label>
			<label>Сумма
				<input v-model.number="reminderForm.amount" type="number" step="0.01" name="amount" placeholder="Опц." />
			</label>
			<button type="submit">Добавить</button>
		</form>
		<div id="reminderList">
			<div v-for="r in reminders" :key="r.id">{{ r.date }}: {{ r.message }}<span v-if="r.amount!=null"> ({{ r.amount }})</span></div>
			<div v-if="reminders.length===0">—</div>
		</div>
	</div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

const reminderForm = ref<{ dueDate: string; message: string; amount: number | null }>({ 
	dueDate: new Date().toISOString().split('T')[0], 
	message: 'Платёж', 
	amount: null 
});

defineProps<{
	reminders: Array<{ id: string; date: string; message: string; amount: number | null }>;
}>();

const emit = defineEmits<{
	'add-reminder': [form: { dueDate: string; message: string; amount: number | null }];
}>();

function handleSubmit() {
	emit('add-reminder', reminderForm.value);
	reminderForm.value = { dueDate: new Date().toISOString().split('T')[0], message: 'Платёж', amount: null };
}
</script>

