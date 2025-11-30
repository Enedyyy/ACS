
<template>
	<div class="tile col-7">
		<h3>Графики</h3>
		<div class="row" style="margin-bottom:8px">
			<label>Режим
				<select :value="viewMode" @change="$emit('update:viewMode', ($event.target as HTMLSelectElement).value)">
					<option value="categories">Категории</option>
					<option value="group">Группа</option>
				</select>
			</label>
			<label>Показать
				<select :value="chartMode" @change="$emit('update:chartMode', ($event.target as HTMLSelectElement).value)">
					<option value="expenses">Расходы</option>
					<option value="income">Доходы</option>
					<option value="both">Вместе</option>
				</select>
			</label>
			<button @click="$emit('refresh')">Обновить</button>
		</div>
		<canvas ref="chartRef"></canvas>
	</div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

defineProps<{
	viewMode: 'categories' | 'group';
	chartMode: 'expenses' | 'income' | 'both';
}>();

const chartRef = ref<HTMLCanvasElement | null>(null);

defineExpose({ chartRef });

defineEmits<{
	'update:viewMode': [value: string];
	'update:chartMode': [value: string];
	refresh: [];
}>();
</script>

