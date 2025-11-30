

<template>
	<Header :user="user" @toggle-theme="toggleTheme" @show-auth="showAuth = true" @logout="logout" />

	<main class="container">
		<AuthForm v-if="!user" @login="login" @register="register" />

		<section v-else class="dashboard">
			<StatsCards :stats="stats" />

			<div class="grid">
				<TransactionForm
					v-model:txForm="txForm"
					:templates="templates"
					:is-fav="isFavTxCat"
					:quick-amounts="quickAmounts"
					@submit="addTransaction"
					@toggle-fav="toggleFavForTx"
					@suggest-category="maybeSuggestCategory"
					@apply-quick="applyQuick"
					@use-template="useTemplate"
					@save-template="saveTemplate"
					@delete-template="deleteTemplate"
				/>

				<ChartView
					:view-mode="viewMode"
					:chart-mode="chartMode"
					ref="chartViewRef"
					@update:viewMode="viewMode = $event as 'categories' | 'group'"
					@update:chartMode="chartMode = $event as 'expenses' | 'income' | 'both'"
					@refresh="handleChartRefresh"
				/>

				<BudgetManager
					:budgets="budgets"
					@set-budget="setBudget"
					@delete-budget="deleteBudget"
				/>

				<CurrencyConverter
					:currency="currency"
					:convert="convert"
					@update:base="currency.base = $event"
					@update:symbol="currency.symbol = $event"
					@update:amount="convert.amount = $event"
					@convert="convertCurrency"
				/>

				<TransactionList
					:filters="filters"
					:filtered-tx="filteredTx"
					@set-quick="setQuick"
					@update:search="filters.search = $event"
					@update:onlyFav="filters.onlyFav = $event"
					@update:from="filters.from = $event || undefined"
					@update:to="filters.to = $event || undefined"
					@update:category="filters.category = $event || undefined"
					@apply-filter="handleApplyFilter"
					@export-csv="exportCsv"
					@delete-tx="deleteTx"
				/>

				<Recommendations
					:rec-opts="recOpts"
					:recommendations="recommendations"
					@update:recOpts="recOpts = $event"
					@save-opts="handleSaveRecOpts"
					@refresh="buildRecommendations()"
				/>

				<Reminders
					:reminders="reminders"
					@add-reminder="addReminder"
				/>

				<GroupBudget
					:group-budget="groupBudget"
					:group-peers="groupPeers"
					:my-group="myGroup"
					@create-group="groupCreate"
					@join-group="groupJoin"
					@copy-invite="copyInvite"
					@leave-group="groupLeave"
					@refresh-group-meta="refreshGroupMeta"
				/>

				<CryptoView
					v-model:currency="crypto.currency"
					:crypto="crypto"
					:crypto-list="cryptoList"
					@load-crypto="loadCrypto"
				/>
			</div>
		</section>
	</main>

	<div id="toast" class="toast" v-show="toastMsg">{{ toastMsg }}</div>
</template>

<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';
import { useStore } from './store';
import { api } from './api/client';
import Header from './components/Header.vue';
import AuthForm from './components/AuthForm.vue';
import StatsCards from './components/StatsCards.vue';
import TransactionForm from './components/TransactionForm.vue';
import ChartView from './components/ChartView.vue';
import BudgetManager from './components/BudgetManager.vue';
import CurrencyConverter from './components/CurrencyConverter.vue';
import TransactionList from './components/TransactionList.vue';
import Recommendations from './components/Recommendations.vue';
import Reminders from './components/Reminders.vue';
import GroupBudget from './components/GroupBudget.vue';
import CryptoView from './components/CryptoView.vue';

import { useToast } from './composables/useToast';
import { useAuth } from './composables/useAuth';
import { useTransactions } from './composables/useTransactions';
import { useBudgets } from './composables/useBudgets';
import { useChart } from './composables/useChart';
import { useCurrency } from './composables/useCurrency';
import { useRecommendations } from './composables/useRecommendations';
import { useReminders } from './composables/useReminders';
import { useGroup } from './composables/useGroup';
import { useCrypto } from './composables/useCrypto';

const store = useStore();
const showAuth = ref(false);

// Toast
const { toastMsg, toast } = useToast();

function toggleTheme() {
	store.setTheme(store.theme === 'dark' ? 'light' : 'dark');
}

// Initialize composables in order
const { budgets, refreshBudget, setBudget, deleteBudget } = useBudgets(toast);
const { reminders, refreshReminders, addReminder } = useReminders(toast);
const { groupBudget, groupPeers, myGroup, groupForm, refreshGroupBudget, groupCreate, groupJoin, refreshGroupMeta, groupLeave, copyInvite } = useGroup(toast);
const { crypto, cryptoList, loadCrypto } = useCrypto();
const { currency, convert, refreshRate, convertCurrency } = useCurrency();

// Transactions
const {
	filters,
	transactions,
	stats,
	filteredTx,
	txForm,
	templates,
	isFavTxCat,
	quickAmounts,
	refreshTx,
	deleteTx,
	addTransaction,
	toggleFavForTx,
	applyQuick,
	saveTemplate,
	useTemplate,
	deleteTemplate,
	maybeSuggestCategory,
	setQuick,
	exportCsv,
	setRefreshChart,
	setBuildRecommendations
} = useTransactions(toast, refreshBudget);

// Recommendations (needs transactions and budgets)
const { recommendations, recOpts, buildRecommendations, handleSaveRecOpts } = useRecommendations(transactions, budgets);

// Chart (needs transactions, groupPeers)
const { chartViewRef, viewMode, chartMode, refreshChart, handleChartRefresh } = useChart(transactions, groupPeers, refreshGroupMeta, refreshTx);

// Update callbacks in transactions
setRefreshChart(refreshChart);
setBuildRecommendations(buildRecommendations);

async function refreshAll() {
	await Promise.all([refreshBudget(), refreshRate(), buildRecommendations(), refreshReminders(), refreshGroupBudget(), refreshGroupMeta(), loadCrypto()]);
	await refreshTx();
	await refreshChart();
}

// Auth (needs refresh functions)
const { user, login, register, logout, afterAuth } = useAuth(toast, refreshBudget, refreshTx, refreshAll);

async function handleApplyFilter() {
	await refreshTx();
	await refreshChart();
}

onMounted(async () => {
	store.setTheme(store.theme);
	document.body.classList.toggle('auth-mode', !user.value);
	watch(user, (v) => document.body.classList.toggle('auth-mode', !v));
	try {
		const u = new URL(window.location.href);
		const join = u.searchParams.get('join');
		if (join) groupForm.value.groupId = join;
	} catch { }
	const res = await api('/api/me');
	if (res && res.ok) store.setUser(res.user);
	if (user.value) await afterAuth();
});
</script>

<style scoped>
</style>
