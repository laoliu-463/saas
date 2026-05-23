import { ROLE_CODES } from '../../constants/rbac';

const SAMPLE_EXPORT_ROLES = new Set<string>([
  ROLE_CODES.ADMIN,
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.OPS_STAFF
]);

export const canExportSamplesByRole = (roles: string[] = []) =>
  roles.some((role) => SAMPLE_EXPORT_ROLES.has(role));
