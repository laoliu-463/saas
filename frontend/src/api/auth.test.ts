import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { login, logout } from './auth'

vi.mock('../utils/request', () => ({
  default: {
    post: vi.fn()
  }
}))

describe('auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('calls login endpoint with username and password', () => {
      const credentials = { username: 'admin', password: 'password123' }

      login(credentials)

      expect(request.post).toHaveBeenCalledWith('/auth/login', credentials)
    })

    it('passes additional data fields to login request', () => {
      const credentials = { username: 'user@test.com', password: 'test', captcha: 'abc123' }

      login(credentials)

      expect(request.post).toHaveBeenCalledWith('/auth/login', credentials)
    })

    it('handles login error (401 Unauthorized)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(login({ username: 'bad', password: 'creds' })).rejects.toThrow('401 Unauthorized')
    })

    it('handles login error (500 Server Error)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(login({ username: 'user', password: 'pass' })).rejects.toThrow('500 Internal Server Error')
    })

    it('handles network timeout error', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(login({ username: 'user', password: 'pass' })).rejects.toThrow('timeout of 30000ms exceeded')
    })
  })

  describe('logout', () => {
    it('calls logout endpoint with refreshToken', () => {
      const data = { refreshToken: 'refresh-token-xyz' }

      logout(data)

      expect(request.post).toHaveBeenCalledWith('/auth/logout', data)
    })

    it('calls logout endpoint with both tokens', () => {
      const data = {
        accessToken: 'access-token-abc',
        refreshToken: 'refresh-token-xyz'
      }

      logout(data)

      expect(request.post).toHaveBeenCalledWith('/auth/logout', data)
    })

    it('handles logout error (network failure)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(logout({ refreshToken: 'token' })).rejects.toThrow('Network Error')
    })

    it('handles logout error (500 Server Error)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(logout({ refreshToken: 'token' })).rejects.toThrow('500 Internal Server Error')
    })
  })
})
