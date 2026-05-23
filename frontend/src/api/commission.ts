import request from '../utils/request';

export interface CommissionRuleItem {
  id?: string
  dimensionType: string
  dimensionId?: string | null
  commissionType: string
  ratio: number
  effectiveStart?: string | null
  effectiveEnd?: string | null
  status?: number
}

export const getCommissionRulePage = (params?: {
  page?: number
  size?: number
  dimensionType?: string
  commissionType?: string
}) => request.get('/commission-rules', { params });

export const createCommissionRule = (data: CommissionRuleItem) => request.post('/commission-rules', data);

export const updateCommissionRule = (id: string, data: CommissionRuleItem) =>
  request.put(`/commission-rules/${id}`, data);

export const deleteCommissionRule = (id: string) => request.delete(`/commission-rules/${id}`);
