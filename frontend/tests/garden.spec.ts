import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
const password =
  process.env.NOTES_PREVIEW_PASSWORD || readFileSync('../.data/preview-password', 'utf8').trim()
async function login(page: any) {
  await page.goto('/')
  await page.getByLabel('私人预览密码').fill(password)
  await page.getByRole('button', { name: '进入我的留白' }).click()
  await expect(page.getByRole('heading', { name: '我的内容空间' })).toBeVisible()
}
test('private login, dashboard, persistent editor, search, favorite and delete', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '欢迎回到你的花园' })).toBeVisible()
  await page.screenshot({ path: '../artifacts/login-desktop.png', fullPage: true })
  await login(page)
  await page.screenshot({ path: '../artifacts/dashboard-desktop.png', fullPage: true })
  await page.getByRole('button', { name: '写一篇新记录' }).click()
  await page.getByLabel('文章标题').fill('浏览器验收 · 一页留白')
  await page.getByRole('button', { name: 'Markdown', exact: true }).click()
  await page
    .getByLabel('Markdown 源码')
    .fill(
      '## 我的小小花园\n\n这篇笔记用于验证持久化、搜索与权限。\n\n```java\nvar message = "hello";\n```\n\n| 记录 | 状态 |\n| --- | --- |\n| 灵感 | 已保存 |',
    )
  await page.getByLabel('标签，以逗号分隔').fill('验收, 灵感')
  await page.getByLabel('记录状态').selectOption('false')
  await page.getByRole('button', { name: '可视化', exact: true }).click()
  await expect(page.getByTestId('visual-editor')).toContainText('我的小小花园')
  await expect(page.getByTestId('visual-editor').locator('table')).toBeVisible()
  await page.getByTestId('visual-editor').press('Control+Home')
  await page.getByTestId('visual-editor').press('ArrowRight')
  await page.getByTestId('visual-editor').press('End')
  await page.getByTestId('visual-editor').press('Enter')
  await page.getByTestId('visual-editor').pressSequentially('可视化补充一句。')
  await page.getByRole('button', { name: 'Markdown', exact: true }).click()
  await expect(page.getByLabel('Markdown 源码')).toHaveValue(/可视化补充一句。/)
  await page.getByRole('button', { name: '可视化', exact: true }).click()
  await page.getByRole('button', { name: '保存记录', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: '浏览器验收 · 一页留白', exact: true }),
  ).toBeVisible()
  await expect(page.getByText('仅自己可见', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '收藏文章', exact: true }).click()
  await page.getByRole('button', { name: '分享', exact: true }).click()
  await expect(page.getByText('私人预览期间，外部访问已关闭')).toBeVisible()
  await page.getByRole('button', { name: '关闭分享设置' }).click()
  await page.reload()
  await expect(page.getByRole('heading', { name: '我的内容空间' })).toBeVisible()
  await page.getByRole('button', { name: '全局搜索' }).click()
  await page.getByLabel('全局搜索内容').fill('我的小小花园')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /浏览器验收 · 一页留白/ })
    .click()
  await expect(
    page.getByRole('heading', { name: '浏览器验收 · 一页留白', exact: true }),
  ).toBeVisible()
  await expect(page.locator('.article-content table')).toBeVisible()
  await page.screenshot({ path: '../artifacts/reader-desktop.png', fullPage: true })
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.screenshot({ path: '../artifacts/editor-desktop.png', fullPage: true })
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await page.getByRole('button', { name: '全局搜索' }).click()
  await page.getByLabel('全局搜索内容').fill('浏览器验收')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /浏览器验收 · 一页留白/ })
    .click()
  await page.getByRole('button', { name: '删除记录', exact: true }).click()
  await page.getByRole('button', { name: '永久删除', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: '浏览器验收 · 一页留白', exact: true }),
  ).toHaveCount(0)
  expect(errors).toEqual([])
})
test('mobile garden, navigation and dark mode', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page)
  await page.screenshot({ path: '../artifacts/dashboard-mobile.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  await page.getByRole('button', { name: '打开导航' }).click()
  await page.getByRole('button', { name: '深色模式', exact: true }).click()
  await page
    .getByRole('button', { name: /知识笔记/ })
    .first()
    .click()
  await expect(page.getByRole('heading', { name: '知识笔记', exact: true })).toBeVisible()
  await page.screenshot({ path: '../artifacts/knowledge-mobile-dark.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
})
test('anonymous requests cannot read notes or backups', async ({ request, page }) => {
  for (const path of ['/api/notes', '/api/backup', '/api/export'])
    expect((await request.get(path)).status()).toBe(401)
  await page.goto('/share/nonexistent')
  await expect(page.getByText('这篇记录尚未公开，或分享已结束。')).toBeVisible()
})
