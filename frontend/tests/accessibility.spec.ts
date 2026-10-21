import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, writeFileSync } from 'node:fs'
test('dashboard and search support keyboard and accessible semantics', async ({ page }) => {
  await page.goto('/')
  await page
    .getByLabel('私人预览密码')
    .fill(
      process.env.NOTES_PREVIEW_PASSWORD ||
        readFileSync('../.data/preview-password', 'utf8').trim(),
    )
  await page.getByRole('button', { name: '进入我的留白' }).click()
  await expect(page.getByRole('heading', { name: '我的内容空间' })).toBeVisible()
  const report = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze()
  writeFileSync(
    '../artifacts/accessibility.json',
    JSON.stringify(
      report.violations.map((v) => ({
        id: v.id,
        description: v.description,
        nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
      })),
      null,
      2,
    ),
  )
  expect(report.violations.map((v) => ({ id: v.id, count: v.nodes.length }))).toEqual([])
  await page.keyboard.press('Control+k')
  await expect(page.getByLabel('全局搜索内容')).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('login and dark garden have readable contrast', async ({ page }) => {
  await page.goto('/')
  const login = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(
    login.violations.map((v) => ({
      id: v.id,
      nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
    })),
  ).toEqual([])
  await page
    .getByLabel('私人预览密码')
    .fill(
      process.env.NOTES_PREVIEW_PASSWORD ||
        readFileSync('../.data/preview-password', 'utf8').trim(),
    )
  await page.getByRole('button', { name: '进入我的留白' }).click()
  await expect(page.getByRole('heading', { name: '我的内容空间' })).toBeVisible()
  await page.getByRole('button', { name: '深色模式', exact: true }).click()
  await expect(page.locator('.tag-cloud button').first()).toHaveCSS(
    'background-color',
    'rgb(33, 39, 34)',
  )
  const dark = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(
    dark.violations.map((v) => ({
      id: v.id,
      nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
    })),
  ).toEqual([])
})
