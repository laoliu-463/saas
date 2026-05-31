import request from '../utils/request';

export const getMerchants = (params: any) => request.get('/merchants', { params });
