import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  changeCurrentUserPassword,
  checkCurrentUserPermission,
  getCurrentUser,
  getCurrentUserDataScope,
  getUserMasterChannels,
  getUserMasterGroupMembers,
  getUserMasterRecruiters
} from './sys'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn()
  }
}))

describe('system user domain API', () => {
  it('calls current user endpoint', () => {
    getCurrentUser()

    expect(request.get).toHaveBeenCalledWith('/users/current')
  })

  it('calls current user data scope endpoint', () => {
    getCurrentUserDataScope()

    expect(request.get).toHaveBeenCalledWith('/users/current/data-scope')
  })

  it('calls current user password endpoint', () => {
    changeCurrentUserPassword({ oldPassword: 'old-pass', newPassword: 'new-pass-123' })

    expect(request.put).toHaveBeenCalledWith('/users/current/password', {
      oldPassword: 'old-pass',
      newPassword: 'new-pass-123'
    })
  })

  it('calls current user permission check endpoint', () => {
    checkCurrentUserPermission({ resource: 'product', action: 'audit' })

    expect(request.post).toHaveBeenCalledWith('/users/current/permissions/check', {
      resource: 'product',
      action: 'audit'
    })
  })

  it('calls user master data channel endpoint', () => {
    getUserMasterChannels({ keyword: '渠', limit: 20 })

    expect(request.get).toHaveBeenCalledWith('/users/master-data/channels', {
      params: { keyword: '渠', limit: 20 }
    })
  })

  it('calls user master data recruiter endpoint', () => {
    getUserMasterRecruiters({ keyword: '招', limit: 20 })

    expect(request.get).toHaveBeenCalledWith('/users/master-data/recruiters', {
      params: { keyword: '招', limit: 20 }
    })
  })

  it('calls user master data group members endpoint', () => {
    getUserMasterGroupMembers({ deptId: 'DEPT-1', keyword: '组', limit: 50 })

    expect(request.get).toHaveBeenCalledWith('/users/master-data/group-members', {
      params: { deptId: 'DEPT-1', keyword: '组', limit: 50 }
    })
  })
})
