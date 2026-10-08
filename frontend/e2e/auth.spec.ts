import { expect, test } from '@playwright/test'

test('unauthenticated visitor is redirected and admin can sign in', async ({ page }) => {
  let signedIn = false
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/auth/login')) signedIn = true
      if ((!signedIn && path.endsWith('/auth/me')) || path.endsWith('/auth/refresh-token')) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ code: 401, message: 'unauthorized' })
        })
        return
      }
      const data = path.endsWith('/auth/captcha')
        ? { enabled: false }
        : ['/depts/tree', '/roles', '/menus'].some((suffix) => path.endsWith(suffix))
          ? []
          : path.endsWith('/auth/login')
            ? { accessToken: 'e2e-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00' }
            : path.endsWith('/auth/me')
              ? {
                  id: 1,
                  username: 'admin',
                  realName: '管理员',
                  deptId: 1,
                  deptName: '公安处',
                  roles: ['ADMIN'],
                  permissions: []
                }
              : path.endsWith('/system-period')
                ? {
                    startYear: 2026,
                    startQuarter: 1,
                    currentYear: 2026,
                    currentQuarter: 3,
                    effectiveYear: 2026,
                    effectiveQuarter: 3
                  }
                : {}
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data })
      })
    }
  )

  await page.goto('/system/admin')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByPlaceholder('请输入账号').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('test-password')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/\/system\/admin$/)
  await expect(page.getByText('管理员总览').first()).toBeVisible()
})

test('uploaded material remains inaccessible until the scan completes', async ({ page }) => {
  let listCalls = 0
  let eventCalls = 0
  let uploaded = false
  const attachment = {
    id: 9,
    taskId: 5,
    attachmentType: 'RECORD',
    originalName: 'record.txt',
    fileSize: 11,
    extension: 'txt',
    storageStatus: 'QUARANTINED',
    scanStatus: 'PENDING'
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/files/events')) {
        eventCalls++
        await route.fulfill({
          status: 200,
          contentType: 'text/event-stream',
          body: uploaded ? 'event: scans\ndata: [{"id":9,"scanStatus":"CLEAN","storageStatus":"ACTIVE"}]\n\n' : ''
        })
        return
      }
      if (path.endsWith('/auth/refresh-token')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 200, data: { accessToken: 'e2e-token' } })
        })
        return
      }
      if (path.endsWith('/auth/me') && !route.request().headers()['authorization']) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
        return
      }
      let data: unknown = {}
      if (path.endsWith('/auth/login')) data = { accessToken: 'e2e-token' }
      else if (path.endsWith('/auth/captcha')) data = { enabled: false }
      else if (path.endsWith('/auth/me'))
        data = {
          id: 2,
          username: 'station',
          realName: '测试账号',
          deptId: 2,
          deptName: '派出所',
          roles: ['STATION'],
          permissions: []
        }
      else if (path.endsWith('/system-period'))
        data = {
          startYear: 2026,
          startQuarter: 1,
          currentYear: 2026,
          currentQuarter: 3,
          effectiveYear: 2026,
          effectiveQuarter: 3
        }
      else if (path.endsWith('/check-tasks/5/full'))
        data = {
          task: {
            id: 5,
            targetName: '测试单位',
            targetType: 'KEY_UNIT',
            taskYear: 2026,
            quarter: 3,
            deadline: '2026-10-30 00:00:00',
            executorDeptId: 2,
            countCoverage: 1,
            status: 'PENDING'
          },
          attachments: []
        }
      else if (path.endsWith('/hidden-dangers/default-deadline-days')) data = 30
      else if (path.endsWith('/files/upload')) {
        uploaded = true
        data = attachment
      } else if (path.endsWith('/files')) {
        listCalls++
        data = uploaded
          ? [
              {
                ...attachment,
                storageStatus: listCalls > 1 ? 'ACTIVE' : 'QUARANTINED',
                scanStatus: listCalls > 1 ? 'CLEAN' : 'PENDING'
              }
            ]
          : []
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data })
      })
    }
  )

  await page.goto('/login')
  await page.getByPlaceholder('请输入账号').fill('station')
  await page.getByPlaceholder('请输入密码').fill('test-password')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.goto('/tasks/5')
  await expect(page.getByText('测试单位').first()).toBeVisible()
  await page
    .locator('input[type="file"]')
    .nth(1)
    .setInputFiles({
      name: 'record.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('test record')
    })
  await expect(page.getByText('安全检测中')).toBeVisible()
  await expect.poll(() => eventCalls).toBeGreaterThan(0)
  await expect(page.getByRole('button', { name: '下载' })).toHaveCount(0)
  await expect.poll(() => eventCalls).toBeGreaterThan(1)
  await expect(page.getByText('已通过')).toBeVisible({ timeout: 10_000 })
  await expect(page.getByRole('button', { name: '下载' })).toBeVisible()
})
