const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const i18n = require('../i18n.js');

test('Chinese is the default; explicit links and saved choices override it', () => {
  assert.equal(i18n.resolveLanguage({ query: 'en-GB', saved: 'zh-TW', browser: ['zh-CN'] }), 'en');
  assert.equal(i18n.resolveLanguage({ query: 'bad', saved: 'zh_TW', browser: ['en-US'] }), 'zh-TW');
  assert.equal(i18n.resolveLanguage({ browser: ['fr-FR', 'zh-Hant-HK', 'en'] }), 'zh-CN');
  assert.equal(i18n.resolveLanguage({ browser: ['en-US'] }), 'zh-CN');
  assert.equal(i18n.resolveLanguage({ browser: ['zh-SG'] }), 'zh-CN');
  assert.equal(i18n.resolveLanguage({ browser: ['zh-HK'] }), 'zh-CN');
  assert.equal(i18n.normalizeLanguage('zh-HK'), 'zh-TW');
  assert.equal(i18n.normalizeLanguage('zh-MO'), 'zh-TW');
  assert.equal(i18n.resolveLanguage({}), 'zh-CN');
});

test('三种语言的键与插值完整，页面翻译引用均有效', () => {
  const keys = Object.keys(i18n.messages['zh-CN']).sort();
  for (const language of i18n.languages) {
    assert.deepEqual(Object.keys(i18n.messages[language]).sort(), keys);
    for (const key of keys) {
      const text = i18n.messages[language][key];
      assert.ok(text.trim(), `${language}: ${key}`);
      const params = (text) => [...text.matchAll(/\{\w+\}/g)].map((m) => m[0]).sort();
      assert.deepEqual(params(text), params(i18n.messages['zh-CN'][key]), `${language}: ${key}`);
    }
  }
  const html = fs.readFileSync(path.join(__dirname, '../index.html'), 'utf8');
  for (const match of html.matchAll(/data-i18n(?:-[\w-]+)?="([^"]+)"/g)) {
    assert.ok(keys.includes(match[1]), match[1]);
  }
  assert.ok(html.indexOf('src="i18n.js"') < html.indexOf('src="main.js"'));
});

test('动态日期和带占位符的文案与语言一致', () => {
  assert.equal(i18n.t('notes.page', { page: 3 }, 'en'), 'Page 3');
  assert.equal(i18n.t('notes.page', { page: 3 }, 'zh-TW'), '第 3 頁');
  assert.match(i18n.formatDate(new Date('2026-10-01T12:00:00Z'), 'en'), /October/);
  assert.throws(() => i18n.t('missing.key'), /Unknown translation key/);
});
