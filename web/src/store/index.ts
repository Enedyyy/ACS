import { defineStore } from 'pinia';

type User = { id: string; username: string } | null;

function loadSet(key: string): Set<string> {
	try {
		const arr = JSON.parse(localStorage.getItem(key) || '[]');
		return new Set<string>(Array.isArray(arr) ? arr : []);
	} catch { return new Set(); }
}
function saveSet(key: string, s: Set<string>) {
	localStorage.setItem(key, JSON.stringify(Array.from(s)));
}

export const useStore = defineStore('app', {
	state: () => ({
		user: null as User,
		favoritesSet: loadSet('favorites.categories') as Set<string>,
		theme: localStorage.getItem('ui.theme') || 'dark',
		baseCurrency: localStorage.getItem('ui.baseCurrency') || 'MDL'
	}),
	getters: {
		favorites: (s) => s.favoritesSet
	},
	actions: {
		setUser(u: User) { this.user = u; },
		toggleFavorite(cat: string) {
			if (this.favoritesSet.has(cat)) this.favoritesSet.delete(cat);
			else this.favoritesSet.add(cat);
			saveSet('favorites.categories', this.favoritesSet);
		},
		setTheme(t: string) {
			this.theme = t; localStorage.setItem('ui.theme', t);
			document.documentElement.dataset.theme = t;
			try { (document.body as any).dataset.theme = t; } catch {}
		},
		setBaseCurrency(c: string) {
			this.baseCurrency = c; localStorage.setItem('ui.baseCurrency', c);
		}
	}
});


