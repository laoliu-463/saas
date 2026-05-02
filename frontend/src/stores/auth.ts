import { defineStore } from 'pinia';
import { ROLE_CODES } from '../constants/rbac';

const LEGACY_ROLE_MAP: Record<string, string> = {
    zs_leader: ROLE_CODES.BIZ_LEADER,
    zs_staff: ROLE_CODES.BIZ_STAFF,
    qd_leader: ROLE_CODES.CHANNEL_LEADER,
    qd_staff: ROLE_CODES.CHANNEL_STAFF
};

const normalizeRoleCodes = (roleCodes: unknown): string[] => {
    if (!Array.isArray(roleCodes)) return [];
    return Array.from(
        new Set(
            roleCodes
                .map((code) => String(code))
                .map((code) => LEGACY_ROLE_MAP[code] || code)
                .filter(Boolean)
        )
    );
};

const normalizeStoredToken = (token: unknown): string => {
    if (typeof token !== 'string') return '';
    const value = token.trim();
    if (!value || value === 'undefined' || value === 'null') return '';
    return value;
};

const normalizeUserInfo = (userInfo: any): any => {
    if (!userInfo || typeof userInfo !== 'object') return null;
    const normalized = userInfo as Record<string, any>;
    const normalizedId = normalized.id || normalized.userId || '';
    return {
        ...normalized,
        id: normalizedId,
        userId: normalized.userId || normalizedId,
        roleCodes: normalizeRoleCodes(normalized.roleCodes)
    };
};

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: normalizeStoredToken(localStorage.getItem('token')),
        userInfo: normalizeUserInfo(JSON.parse(localStorage.getItem('userInfo') || 'null'))
    }),
    getters: {
        isLoggedIn: (state) => !!normalizeStoredToken(state.token),
        roleCodes: (state) => normalizeRoleCodes(state.userInfo?.roleCodes),
        isAdmin: (state) => normalizeRoleCodes(state.userInfo?.roleCodes).includes(ROLE_CODES.ADMIN),
        isLeader: (state) =>
            [ROLE_CODES.BIZ_LEADER, ROLE_CODES.CHANNEL_LEADER].some((r) =>
                normalizeRoleCodes(state.userInfo?.roleCodes).includes(r)
            ),
        dataScope: (state) => state.userInfo?.dataScope
    },
    actions: {
        login(token: string, userInfo: any) {
            this.token = normalizeStoredToken(token);
            this.userInfo = normalizeUserInfo(userInfo);
            if (this.token) {
                localStorage.setItem('token', this.token);
            } else {
                localStorage.removeItem('token');
            }
            localStorage.setItem('userInfo', JSON.stringify(this.userInfo));
        },
        logout() {
            this.token = '';
            this.userInfo = null;
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
        }
    }
});
