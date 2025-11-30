
<template>
	<header class="topbar">
		<div class="brand">💰 FinTrack</div>
		<nav class="nav">
			<div class="nav-right">
				<!-- Меню пользователя (показывается только для авторизованных) -->
				<div v-if="user" class="user-menu-wrapper">
					<div class="user-trigger" @mouseenter="showUserMenu = true" @mouseleave="showUserMenu = false">
						<div class="user-badge">👤 <span>{{ user.username }}</span></div>
						<!-- Выпадающее меню: переключение темы и выход -->
						<div v-if="showUserMenu" class="user-dropdown" @mouseenter="showUserMenu = true" @mouseleave="showUserMenu = false">
							<div class="user-menu-item" @click="$emit('toggle-theme')">
								<span class="menu-icon">🌓</span>
								<span class="menu-text">Тема</span>
							</div>
							<div class="user-menu-divider"></div>
							<div class="user-menu-item logout-item" @click="$emit('logout')">
								<span class="menu-icon">🚪</span>
								<span class="menu-text">Выйти</span>
							</div>
						</div>
					</div>
				</div>
				<button v-if="!user" @click="$emit('show-auth')">Войти</button>
			</div>
		</nav>
	</header>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

defineProps<{ user: { username: string } | null }>();
defineEmits<{
	'toggle-theme': [];
	'show-auth': [];
	'logout': [];
}>();

const showUserMenu = ref(false);
</script>

