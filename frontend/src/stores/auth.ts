import { defineStore } from 'pinia';

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem('token') || '',
        userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
    }),
    getters: {
        isLoggedIn: (state) => !!state.token,
        isAdmin: (state) => state.userInfo?.roleCodes?.includes('admin'),
        isLeader: (state) => ['zs_leader', 'qd_leader'].some(r => state.userInfo?.roleCodes?.includes(r)),
        dataScope: (state) => state.userInfo?.dataScope
    },
    actions: {
        login(token: string, userInfo: any) {
            this.token = token;
            this.userInfo = userInfo;
            localStorage.setItem('token', token);
            localStorage.setItem('userInfo', JSON.stringify(userInfo));
        },
        logout() {
            this.token = '';
            this.userInfo = null;
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
        }
    }
});
