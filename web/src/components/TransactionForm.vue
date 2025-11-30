
<template>
	<div class="tile tile--accent col-5">
		<h3>Добавить транзакцию</h3>
		<form @submit.prevent="$emit('submit')">
			<div class="form-grid">
				<input :value="txForm.date" @input="update('date', ($event.target as HTMLInputElement).value)" name="date" type="date" required />
				<select :value="txForm.type" @change="update('type', ($event.target as HTMLSelectElement).value as 'expense'|'income')" name="type">
					<option value="expense">Расход</option>
					<option value="income">Доход</option>
				</select>
				<input :value="txForm.amount" @input="update('amount', Number(($event.target as HTMLInputElement).value))" name="amount" type="number" step="0.01" placeholder="Сумма" required />
				<div class="full" style="display:flex;gap:8px;align-items:center;">
					<input :value="txForm.category" @input="update('category', ($event.target as HTMLInputElement).value)" name="category" placeholder="Категория" style="flex:1;" />
					<button type="button" title="Добавить в избранное" @click="$emit('toggle-fav')" :aria-pressed="isFav">
						{{ isFav ? '★' : '☆' }}
					</button>
				</div>
				<input :value="txForm.description" @input="update('description', ($event.target as HTMLInputElement).value); $emit('suggest-category')" name="description" placeholder="Описание" class="full" />
				<div class="full row" style="justify-content:flex-start;">
					<button type="button" v-for="q in quickAmounts" :key="q" @click="$emit('apply-quick', q)">{{ q }}</button>
				</div>
				<div class="full" style="display:flex;gap:8px;align-items:center;">
					<select v-model="selectedTemplate" style="flex:1;">
						<option value="" disabled selected>Выбрать шаблон…</option>
						<option v-for="t in templates" :key="t.name" :value="t.name">{{ t.name }}</option>
					</select>
					<button type="button" @click="$emit('use-template', selectedTemplate)" :disabled="!selectedTemplate">Подставить</button>
				</div>
				<div class="full" style="display:flex;gap:8px;align-items:center;">
					<input v-model.trim="newTemplateName" placeholder="Название шаблона" style="flex:1;" />
					<button type="button" @click="$emit('save-template', newTemplateName)" :disabled="!newTemplateName">Сохранить шаблон</button>
					<button type="button" @click="$emit('delete-template', selectedTemplate)" :disabled="!selectedTemplate">Удалить</button>
				</div>
				<button type="submit" class="full">Сохранить</button>
			</div>
		</form>
	</div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

const props = defineProps<{
	txForm: { date: string; type: 'expense' | 'income'; amount: number | null; category: string; description: string };
	templates: Array<{ name: string }>;
	isFav: boolean;
	quickAmounts: string[];
}>();

const emit = defineEmits<{
	'update:txForm': [value: typeof props.txForm];
	submit: [];
	'toggle-fav': [];
	'suggest-category': [];
	'apply-quick': [amount: string];
	'use-template': [name: string];
	'save-template': [name: string];
	'delete-template': [name: string];
}>();

const selectedTemplate = ref<string>('');
const newTemplateName = ref<string>('');

function update(key: keyof typeof props.txForm, value: any) {
	emit('update:txForm', { ...props.txForm, [key]: value });
}
</script>

