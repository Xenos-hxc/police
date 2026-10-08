import { expect, test } from '@playwright/test'

test('local models: real browser question, quality check and human review', async ({ page }, testInfo) => {
  test.skip(process.env.AI_LIVE_E2E !== '1', 'Requires isolated evaluation backend and local models')
  test.setTimeout(300000)
  const businessWrites: string[] = []
  page.on('request', (request) => {
    if (request.method() !== 'GET' && /\/api\/(check-records|hidden-dangers|check-tasks)/.test(request.url())) {
      businessWrites.push(request.url())
    }
  })
  await page.goto('/login')
  await page.getByPlaceholder('请输入账号').fill('station01')
  await page.getByPlaceholder('请输入密码').fill(process.env.AI_EVAL_PASSWORD || 'Demo-Only-Change-Me!2026')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).not.toHaveURL(/\/login$/)
  const taskId = process.env.AI_EVAL_TASK_ID || '1'
  const taskResponse = page.waitForResponse((response) => response.url().endsWith(`/api/check-tasks/${taskId}/full`))
  await page.goto(`/tasks/${taskId}`)
  const task = await (await taskResponse).json()
  expect(task.data.task.taskNo).toMatch(/^AI-EVAL-/)
  await expect(page.getByText('智能质检与整改助手')).toBeVisible()
  await page.getByPlaceholder('询问本任务所属部门的规章依据').fill('安全出口被纸箱堵塞时应当如何处理？')
  const answerResponse = page.waitForResponse((response) => response.url().endsWith('/api/ai/questions'))
  await page.getByRole('button', { name: '检索规章并提问' }).click()
  const answer = await (await answerResponse).json()
  expect(answer.code).toBe(200)
  expect(answer.data.insufficientEvidence).toBe(false)
  expect(answer.data.citations.length).toBeGreaterThan(0)
  const row = page.getByRole('row').filter({ hasText: 'record.txt' }).first()
  const qualityResponse = page.waitForResponse((response) =>
    /\/api\/ai\/attachments\/\d+\/quality$/.test(response.url())
  )
  await row.getByRole('button', { name: '智能质检', exact: true }).click()
  const quality = await (await qualityResponse).json()
  expect(quality.code).toBe(200)
  expect(quality.data.insufficientEvidence).toBe(false)
  await expect(page.getByText(`材料质检 #${quality.data.runId}`)).toBeVisible()
  await page.getByRole('button', { name: '人工确认建议' }).click()
  const completedRun = page.getByRole('row').filter({
    has: page.getByRole('cell', { name: String(quality.data.runId), exact: true })
  })
  await expect(completedRun.getByRole('cell', { name: 'ACCEPTED', exact: true })).toBeVisible()
  expect(businessWrites).toEqual([])
  await page.screenshot({ path: testInfo.outputPath('live-browser.png'), fullPage: true })
})
