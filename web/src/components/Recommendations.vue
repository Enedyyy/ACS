
<template>
	<div class="tile col-5">
		<h3>Рекомендации</h3>
		<div class="row" style="margin-bottom:8px">
			<button @click="recShowOpts=!recShowOpts">⚙ Показать настройки</button>
			<button @click="$emit('save-opts')" v-if="recShowOpts">Сохранить настройки</button>
			<button @click="$emit('refresh')" style="margin-left: auto;">Обновить</button>
		</div>
					<div v-if="recShowOpts" class="form-grid" style="margin-bottom:10px">
						<label>Период анализа (дней)
							<input type="number" min="7" :value="recOpts.periodDays" @input="update('periodDays', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Берём операции за последние N дней</div>
						</label>
						<label>Окно для подписок (дней)
							<input type="number" min="14" :value="recOpts.subWindowDays" @input="update('subWindowDays', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Ищем повторы по описанию в этом окне</div>
						</label>
						<label>Повторения подписки (раз)
							<input type="number" min="2" :value="recOpts.subMinCount" @input="update('subMinCount', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Сколько одинаковых операций считать подпиской</div>
						</label>
						<label>Порог суммы подписки (за период)
							<input type="number" min="0" step="1" :value="recOpts.subMinSum" @input="update('subMinSum', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Минимальная сумма повторов за окно</div>
						</label>
						<label>Сократить топ‑категорию на (%)
							<input type="number" min="1" max="50" :value="recOpts.top1CutPct" @input="update('top1CutPct', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Рекомендация для самой затратной категории</div>
						</label>
						<label>Сократить вторую категорию на (%)
							<input type="number" min="1" max="50" :value="recOpts.top2CutPct" @input="update('top2CutPct', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Рекомендация для категории №2 по расходам</div>
						</label>
						<label>Считать доминирующей, если доля > (%)
							<input type="number" min="10" max="90" :value="recOpts.topSharePct" @input="update('topSharePct', Number(($event.target as HTMLInputElement).value))" />
							<div style="color:var(--muted);font-size:0.85em">Порог доли одной категории в расходах</div>
						</label>
					</div>
		<div id="recoList">
			<div v-for="r in recommendations" :key="r.message">- {{ r.message }}<span v-if="r.potentialSave"> (≈ {{ r.potentialSave }})</span><div v-if="r.criteria" style="color:var(--muted);font-size:0.85em">Основание: {{ r.criteria }}</div></div>
			<div v-if="recommendations.length===0">—</div>
		</div>
	</div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

const recShowOpts = ref(false);

const props = defineProps<{
	recommendations: Array<{ message: string; potentialSave?: number; criteria?: string }>;
	recOpts: { periodDays: number; subWindowDays: number; subMinCount: number; subMinSum: number; topSharePct: number; top1CutPct: number; top2CutPct: number };
}>();

const emit = defineEmits<{
	'update:recOpts': [value: typeof props.recOpts];
	'save-opts': [];
	'refresh': [];
}>();

function update(key: keyof typeof props.recOpts, value: number) {
	emit('update:recOpts', { ...props.recOpts, [key]: value });
}
</script>

