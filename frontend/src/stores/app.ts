import { defineStore } from 'pinia';

export const useAppStore = defineStore('app', {
    state: () => ({
        collapsed: false
    }),
    actions: {
        toggle() {
            this.collapsed = !this.collapsed;
        }
    }
});
