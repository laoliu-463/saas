import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/login', component: () => import('../views/Login.vue') },
        { 
            path: '/', 
            component: () => import('../views/layout/index.vue'),
            children: [
                { path: 'product', component: () => import('../views/product/index.vue') },
                { path: 'talent', component: () => import('../views/talent/index.vue') },
                { path: 'sample', component: () => import('../views/sample/index.vue') },
                { path: 'data', component: () => import('../views/data/index.vue') },
                { path: '', redirect: '/data' }
            ]
        }
    ]
});

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore();
    if (to.path !== '/login' && !authStore.isLoggedIn) {
        next('/login');
    } else if (to.path === '/login' && authStore.isLoggedIn) {
        next('/');
    } else {
        next();
    }
});

export default router;
