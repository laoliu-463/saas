import { defineStore } from 'pinia';
import { ROLE_CODES } from '../constants/rbac';

const LEGACY_ROLE_MAP: Record<string, string> = {
    zs_leader: ROLE_CODES.BIZ_LEADER,
    zs_staff: ROLE_CODES.BIZ_STAFF,
    colonel_leader: ROLE_CODES.BIZ_LEADER,
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

const normalizeStoredNumber = (value: unknown): number | null => {
    if (value == null || value === '') return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
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

const readStoredUserInfo = (): any => {
    try {
        return JSON.parse(localStorage.getItem('userInfo') || 'null');
    } catch (_error) {
        return null;
    }
};

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: normalizeStoredToken(localStorage.getItem('token')),
        refreshToken: normalizeStoredToken(localStorage.getItem('refreshToken')),
        refreshExpiresIn: normalizeStoredNumber(localStorage.getItem('refreshExpiresIn')),
        accessTokenExpiresIn: normalizeStoredNumber(localStorage.getItem('accessTokenExpiresIn')),
        userInfo: normalizeUserInfo(readStoredUserInfo())
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
            this.refreshToken = normalizeStoredToken(userInfo?.refreshToken);
            this.refreshExpiresIn = normalizeStoredNumber(userInfo?.refreshExpiresIn);
            this.accessTokenExpiresIn = normalizeStoredNumber(userInfo?.accessTokenExpiresIn ?? userInfo?.expiresIn);
            this.userInfo = normalizeUserInfo(userInfo);
            this.persistAuthState();
        },
        updateTokens(payload: {
            token: string;
            refreshToken?: string;
            refreshExpiresIn?: number | null;
            accessTokenExpiresIn?: number | null;
        }) {
            this.token = normalizeStoredToken(payload.token);
            if (payload.refreshToken !== undefined) {
                this.refreshToken = normalizeStoredToken(payload.refreshToken);
            }
            if (payload.refreshExpiresIn !== undefined) {
                this.refreshExpiresIn = normalizeStoredNumber(payload.refreshExpiresIn);
            }
            if (payload.accessTokenExpiresIn !== undefined) {
                this.accessTokenExpiresIn = normalizeStoredNumber(payload.accessTokenExpiresIn);
            }
            this.persistAuthState();
        },
        hydrateFromStorage() {
            this.token = normalizeStoredToken(localStorage.getItem('token'));
            this.refreshToken = normalizeStoredToken(localStorage.getItem('refreshToken'));
            this.refreshExpiresIn = normalizeStoredNumber(localStorage.getItem('refreshExpiresIn'));
            this.accessTokenExpiresIn = normalizeStoredNumber(localStorage.getItem('accessTokenExpiresIn'));
            this.userInfo = normalizeUserInfo(readStoredUserInfo());
        },
        persistAuthState() {
            if (this.token) {
                localStorage.setItem('token', this.token);
            } else {
                localStorage.removeItem('token');
            }
            if (this.refreshToken) {
                localStorage.setItem('refreshToken', this.refreshToken);
            } else {
                localStorage.removeItem('refreshToken');
            }
            if (this.refreshExpiresIn) {
                localStorage.setItem('refreshExpiresIn', String(this.refreshExpiresIn));
            } else {
                localStorage.removeItem('refreshExpiresIn');
            }
            if (this.accessTokenExpiresIn) {
                localStorage.setItem('accessTokenExpiresIn', String(this.accessTokenExpiresIn));
            } else {
                localStorage.removeItem('accessTokenExpiresIn');
            }
            localStorage.setItem('userInfo', JSON.stringify(this.userInfo));
        },
        clearAuth() {
            this.token = '';
            this.refreshToken = '';
            this.refreshExpiresIn = null;
            this.accessTokenExpiresIn = null;
            this.userInfo = null;
            localStorage.removeItem('token');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('refreshExpiresIn');
            localStorage.removeItem('accessTokenExpiresIn');
            localStorage.removeItem('userInfo');
        },
        logout() {
            this.clearAuth();
        }
    }
});
