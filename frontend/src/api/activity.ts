import request from '../utils/request';

export const getActivityPage = (params: any) => request.get('/activities', { params });
export const getColonelActivityPage = (params: any) => request.get('/colonel/activities', { params });
export const getActivityDouyinDetail = (activityId: number) => request.get(`/activities/${activityId}/douyin-detail`);
export const syncColonelActivity = (activityId: string | number) => request.post(`/colonel/activities/${activityId}/sync`);
