
<template>
	<section class="auth">
		<h2>Авторизация</h2>
		<form @submit.prevent="$emit('login', form)">
			<input v-model.trim="form.username" name="username" placeholder="Логин" required minlength="3" />
			<input v-model.trim="form.password" type="password" name="password" placeholder="Пароль" required minlength="3" />
			<button type="submit">Войти</button>
		</form>
		<div class="hint">Нет аккаунта? <a href="#" @click.prevent="$emit('register', form)">Зарегистрироваться</a></div>
	</section>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

const form = ref({ username: '', password: '' });

defineEmits<{
	login: [form: { username: string; password: string }];
	register: [form: { username: string; password: string }];
}>();
</script>

