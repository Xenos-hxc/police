import { expect, test } from '@playwright/test'

test('AI quality suggestion requires human confirmation and never writes business records', async ({ page }) => {
  let reviewed = false
  const businessWrites: string[] = []
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      if (
        route.request().method() !== 'GET' &&
        (path.includes('/check-records') || path.includes('/hidden-dangers') || path.includes('/check-tasks'))
      ) {
        businessWrites.push(path)
      }
      if (path.endsWith('/files/events')) {
        await route.fulfill({ status: 200, contentType: 'text/event-stream', body: '' })
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
          attachments: [
            {
              id: 9,
              taskId: 5,
              attachmentType: 'RECORD',
              originalName: 'record.txt',
              fileSize: 80,
              extension: 'txt',
              storageStatus: 'ACTIVE',
              scanStatus: 'CLEAN'
            }
          ]
        }
      else if (path.endsWith('/hidden-dangers/default-deadline-days')) data = 30
      else if (path.endsWith('/ai/status')) data = { enabled: true }
      else if (path.endsWith('/ai/policies')) data = []
      else if (path.endsWith('/ai/runs'))
        data = [
          {
            id: 11,
            attachmentId: 9,
            status: 'SUCCEEDED',
            reviewStatus: reviewed ? 'ACCEPTED' : 'PENDING',
            traceId: '00000000-0000-0000-0000-000000000001',
            createdAt: '2026-09-30T00:00:00'
          }
        ]
      else if (path.endsWith('/ai/attachments/9/quality') || path.endsWith('/ai/runs/11'))
        data = {
          runId: 11,
          category: 'RECORD',
          documentDate: '2026年9月30日',
          unit: '测试单位',
          missingItems: [],
          suggestion: '请人工复核[1]。',
          citations: [
            {
              number: 1,
              policyId: 1,
              title: '合成规范',
              revision: '测试版',
              sourceRef: '测试出处',
              excerpt: '请复核。'
            }
          ],
          reviewStatus: reviewed ? 'ACCEPTED' : 'PENDING',
          disclaimer: 'AI 结果仅供参考，须人工确认'
        }
      else if (path.endsWith('/ai/runs/11/review')) reviewed = true
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
  await expect(page.getByText('智能质检与整改助手')).toBeVisible()
  await page.getByRole('button', { name: '智能质检', exact: true }).click()
  await expect(page.getByText('材料质检 #11')).toBeVisible()
  await page.getByRole('button', { name: '人工确认建议' }).click()
  await expect(page.getByText('ACCEPTED').first()).toBeVisible()
  expect(reviewed).toBe(true)
  expect(businessWrites).toEqual([])
})
