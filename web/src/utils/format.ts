/**
 * Утилиты для форматирования чисел
 */

export function fmt(n: number | null | undefined): string {
	if (n == null || isNaN(Number(n))) return '—';
	return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(Number(n));
}

