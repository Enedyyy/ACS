
<template>
	<div class="tile col-5">
		<h3>Бюджеты</h3>
		<form @submit.prevent="handleSubmit">
			<div class="form-grid">
				<input v-model.trim="budgetForm.category" name="category" placeholder="Категория" required />
				<input v-model.number="budgetForm.limit" name="limit" type="number" step="0.01" placeholder="Лимит" required />
				<button type="submit" class="full">Установить</button>
			</div>
		</form>
		<div id="budgetList">
			<div v-for="b in budgets" :key="b.category">
				{{ b.category }}: <strong>{{ b.spent }}</strong> / {{ b.limit }}
				<button style="margin-left:8px" title="Удалить лимит" @click="$emit('delete-budget', b.category)">🗑</button>
			</div>
		</div>
	</div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

const budgetForm = ref<{ category: string; limit: number | null }>({ category: '', limit: null });

defineProps<{
	budgets: Array<{ category: string; spent: number; limit: number }>;
}>();

const emit = defineEmits<{
	'set-budget': [form: { category: string; limit: number | null }];
	'delete-budget': [category: string];
}>();

function handleSubmit() {
	emit('set-budget', budgetForm.value);
	budgetForm.value = { category: '', limit: null };
}
</script>

