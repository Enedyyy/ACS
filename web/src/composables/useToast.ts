
import { ref } from 'vue';

export function useToast() {
	const toastMsg = ref('');

	function toast(m: string) {
		toastMsg.value = m;
		setTimeout(() => toastMsg.value = '', 2500);
	}

	return { toastMsg, toast };
}

