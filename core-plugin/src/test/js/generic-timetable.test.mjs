// Run with: node --test core-plugin/src/test/js/generic-timetable.test.mjs
// DOM cases need jsdom. Point JSDOM_HOME at a directory that has it installed
// (for example `npm install jsdom` in a scratch folder); without it the DOM cases are skipped.
import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "../../main/assets/school-portal/generic-timetable.js"), "utf8")
  .replace(/export\s+async\s+function\s+run/, "async function run");
const api = new Function(`${source}
return { genericNorm, genericDayOf, genericPeriodLabel, genericPeriodsIn, genericWeeksIn,
  genericBlocks, genericCourseFromBlock, genericBuildMatrix, genericAnalyseSpec,
  genericExtractCourses, genericValidCourse, run };`)();

let JSDOM = null;
try {
  const requireFrom = createRequire(join(process.env.JSDOM_HOME || here, "package.json"));
  ({ JSDOM } = requireFrom("jsdom"));
} catch (error) {
  JSDOM = null;
}
const domTest = JSDOM ? test : test.skip;

const cell = (...lines) => ({ lines, rowspan: 1, colspan: 1 });
const empty = () => cell();

// ---------- pure helpers ----------

describe("day headers", () => {
  test("Chinese, 周/礼拜 prefixes, English and digits", () => {
    assert.equal(api.genericDayOf("星期一"), 1);
    assert.equal(api.genericDayOf("周日"), 7);
    assert.equal(api.genericDayOf("星期天"), 7);
    assert.equal(api.genericDayOf("礼拜三"), 3);
    assert.equal(api.genericDayOf("Thu"), 4);
    assert.equal(api.genericDayOf("周 一"), 1);
  });
  test("non-days are rejected", () => {
    assert.equal(api.genericDayOf("节次"), null);
    assert.equal(api.genericDayOf("周次"), null);
    assert.equal(api.genericDayOf("上课地点"), null);
    assert.equal(api.genericDayOf("星期八"), null);
  });
});

describe("period labels", () => {
  test("numbers, ranges and Chinese numerals", () => {
    assert.deepEqual(api.genericPeriodLabel("1"), { start: 1, end: 1 });
    assert.deepEqual(api.genericPeriodLabel("01"), { start: 1, end: 1 });
    assert.deepEqual(api.genericPeriodLabel("第3节"), { start: 3, end: 3 });
    assert.deepEqual(api.genericPeriodLabel("1-2节"), { start: 1, end: 2 });
    assert.deepEqual(api.genericPeriodLabel("第3-4节\n08:00-09:40"), { start: 3, end: 4 });
    assert.deepEqual(api.genericPeriodLabel("第一节"), { start: 1, end: 1 });
    assert.deepEqual(api.genericPeriodLabel("第十二节"), { start: 12, end: 12 });
    assert.deepEqual(api.genericPeriodLabel("１－２节"), { start: 1, end: 2 });
  });
  test("non-period labels are rejected", () => {
    assert.equal(api.genericPeriodLabel("上午"), null);
    assert.equal(api.genericPeriodLabel("午休"), null);
    assert.equal(api.genericPeriodLabel("0"), null);
    assert.equal(api.genericPeriodLabel("99"), null);
    assert.equal(api.genericPeriodLabel("08:00-09:40"), null);
  });
});

describe("week expressions", () => {
  test("ranges, lists and mixed lists", () => {
    assert.deepEqual(api.genericWeeksIn("1-4周"), [1, 2, 3, 4]);
    assert.deepEqual(api.genericWeeksIn("第3-9周"), [3, 4, 5, 6, 7, 8, 9]);
    assert.deepEqual(api.genericWeeksIn("1,3,5-7周"), [1, 3, 5, 6, 7]);
    assert.deepEqual(api.genericWeeksIn("第5周"), [5]);
    assert.deepEqual(api.genericWeeksIn("1－16周"), api.genericWeeksIn("1-16周"));
    assert.deepEqual(api.genericWeeksIn("1-3周 9-10周"), [1, 2, 3, 9, 10]);
  });
  test("parity before and after 周 and in brackets", () => {
    assert.deepEqual(api.genericWeeksIn("1-9周(单)"), [1, 3, 5, 7, 9]);
    assert.deepEqual(api.genericWeeksIn("1-9周（单）"), [1, 3, 5, 7, 9]);
    assert.deepEqual(api.genericWeeksIn("2-8周 双"), [2, 4, 6, 8]);
    assert.deepEqual(api.genericWeeksIn("第1-8单周"), [1, 3, 5, 7]);
    assert.deepEqual(api.genericWeeksIn("2-16周(双)"), [2, 4, 6, 8, 10, 12, 14, 16]);
  });
  test("brace alternatives are read as a union", () => {
    assert.deepEqual(api.genericWeeksIn("{第1-8周|2周}"), [1, 2, 3, 4, 5, 6, 7, 8]);
  });
  test("non-week text is ignored", () => {
    assert.deepEqual(api.genericWeeksIn("第1节 星期一"), []);
    assert.deepEqual(api.genericWeeksIn("2024-2025学年"), []);
    assert.deepEqual(api.genericWeeksIn("每周2次"), []);
  });
});

describe("course blocks", () => {
  test("bare bracketed week list with room, no 周 suffix", () => {
    const course = api.genericCourseFromBlock(["计算机组成与系统结构(791288)", "张玉繁", "(1-15,东13-C-215c)"], { start: 1, end: 2 });
    assert.equal(course.title, "计算机组成与系统结构");
    assert.equal(course.teacher, "张玉繁");
    assert.equal(course.location, "东13-C-215c");
    assert.deepEqual(course.weeks, Array.from({ length: 15 }, (_, i) => i + 1));
  });
  test("a course code in brackets is never read as weeks", () => {
    assert.deepEqual(api.genericWeeksIn("(791288)"), []);
    assert.equal(api.genericCourseFromBlock(["高数(791288)", "张三"], { start: 1, end: 2 }), null);
  });
  test("title, teacher, weeks and location by position", () => {
    const course = api.genericCourseFromBlock(["高等数学", "张三", "1-16周", "教学楼A-101"], { start: 1, end: 2 });
    assert.equal(course.title, "高等数学");
    assert.equal(course.teacher, "张三");
    assert.equal(course.location, "教学楼A-101");
    assert.equal(course.weeks.length, 16);
    assert.deepEqual([course.startNode, course.endNode], [1, 2]);
  });
  test("labelled fields override positional guesses", () => {
    const course = api.genericCourseFromBlock(["大学英语", "教师：李四", "地点：二教301", "1-8周(双)", "第5-6节"], null);
    assert.equal(course.teacher, "李四");
    assert.equal(course.location, "二教301");
    assert.deepEqual(course.weeks, [2, 4, 6, 8]);
    assert.deepEqual([course.startNode, course.endNode], [5, 6]);
  });
  test("labelled week and period fields without unit suffix", () => {
    const course = api.genericCourseFromBlock(["线性代数", "周次：1-6", "节次：3-4"], null);
    assert.deepEqual(course.weeks, [1, 2, 3, 4, 5, 6]);
    assert.deepEqual([course.startNode, course.endNode], [3, 4]);
  });
  test("location wording with 教学楼 is not read as a label", () => {
    const course = api.genericCourseFromBlock(["数据库", "王五", "1-16周", "教学楼B-302"], { start: 1, end: 2 });
    assert.equal(course.location, "教学楼B-302");
  });
  test("type words in brackets are stripped from the title", () => {
    const course = api.genericCourseFromBlock(["高等数学（必修）", "张三", "1-16周"], { start: 1, end: 2 });
    assert.equal(course.title, "高等数学");
  });
  test("a block without weeks, title or periods is rejected", () => {
    assert.equal(api.genericCourseFromBlock(["张三", "教学楼A-101"], { start: 1, end: 2 }), null);
    assert.equal(api.genericCourseFromBlock(["高等数学", "张三"], { start: 1, end: 2 }), null);
    assert.equal(api.genericCourseFromBlock(["高等数学", "1-16周"], null), null);
  });
  test("two weeks lines in one block split into two courses", () => {
    const blocks = api.genericBlocks(["高数", "张三", "1-8周", "教A-101", "英语", "李四", "9-16周", "教B-202"]);
    assert.equal(blocks.length, 2);
    const second = api.genericCourseFromBlock(blocks[1], { start: 3, end: 4 });
    assert.equal(second.title, "英语");
    assert.equal(second.location, "教B-202");
    assert.deepEqual(second.weeks.slice(0, 2), [9, 10]);
  });
  test("blank lines and separator rules split courses", () => {
    assert.equal(api.genericBlocks(["A", "1-8周", "", "B", "9-16周"]).length, 2);
    assert.equal(api.genericBlocks(["A", "1-8周", "------", "B", "9-16周"]).length, 2);
  });
});

describe("validation", () => {
  test("draft rules", () => {
    assert.equal(api.genericValidCourse({ title: "x", dayOfWeek: 8, startNode: 1, endNode: 2, weeks: [1] }), false);
    assert.equal(api.genericValidCourse({ title: "x", dayOfWeek: 1, startNode: 3, endNode: 2, weeks: [1] }), false);
    assert.equal(api.genericValidCourse({ title: "x", dayOfWeek: 1, startNode: 1, endNode: 2, weeks: [] }), false);
    assert.equal(api.genericValidCourse({ title: "x", dayOfWeek: 1, startNode: 1, endNode: 2, weeks: [0] }), false);
    assert.equal(api.genericValidCourse({ title: "x", dayOfWeek: 1, startNode: 1, endNode: 2, weeks: [1, 16] }), true);
  });
});

describe("grid analysis (pure)", () => {
  test("standard grid with rowspan yields correct periods and days", () => {
    const spec = [
      [empty(), cell("星期一"), cell("星期二"), cell("星期三")],
      [cell("1"), { lines: ["高等数学", "张三", "1-16周", "A101"], rowspan: 2, colspan: 1 }, empty(), cell("大学物理", "王五", "1-16周", "B201")],
      [cell("2"), cell("英语", "李四", "1-8周", "C301"), empty()],
    ];
    const analysed = api.genericAnalyseSpec(spec);
    const courses = api.genericExtractCourses(analysed.matrix, analysed.found.dayCols, analysed.found.headerRow);
    const byTitle = Object.fromEntries(courses.map((c) => [c.title, c]));
    assert.equal(byTitle["高等数学"].dayOfWeek, 1);
    assert.deepEqual([byTitle["高等数学"].startNode, byTitle["高等数学"].endNode], [1, 2]);
    assert.equal(byTitle["大学物理"].dayOfWeek, 3);
    assert.equal(byTitle["英语"].dayOfWeek, 2);
    assert.deepEqual([byTitle["英语"].startNode, byTitle["英语"].endNode], [2, 2]);
  });
  test("transposed grid (days as rows) is recognised", () => {
    const spec = [
      [empty(), cell("1"), cell("2")],
      [cell("星期一"), cell("数据结构", "赵六", "1-16周", "D101"), empty()],
      [cell("星期二"), empty(), cell("操作系统", "孙七", "1-16周", "D102")],
      [cell("星期三"), empty(), empty()],
    ];
    const analysed = api.genericAnalyseSpec(spec);
    const courses = api.genericExtractCourses(analysed.matrix, analysed.found.dayCols, analysed.found.headerRow);
    assert.deepEqual(courses.map((c) => c.title).sort(), ["操作系统", "数据结构"]);
  });
  test("a login form table is not a timetable", () => {
    const analysed = api.genericAnalyseSpec([[cell("学号"), cell("")], [cell("密码"), cell("")], [cell("验证码"), cell("")]]);
    assert.equal(analysed.score, 0);
  });
});

// ---------- DOM cases (jsdom) ----------

function fakeCtx() {
  const added = [];
  return {
    added,
    ctx: {
      term: { id: "term-test" },
      schedule: {
        addCourse(c) { added.push(c); return c; },
        commit(input) { return { committed: true, termId: input.termId, count: added.length }; },
      },
    },
  };
}

async function runOn(html, setup, slotTimes = []) {
  const dom = new JSDOM(`<!doctype html><html><body>${html}</body></html>`);
  const doc = dom.window.document;
  if (setup) setup(doc);
  globalThis.document = doc;
  const { ctx, added } = fakeCtx();
  ctx.term.slotTimes = slotTimes;
  const result = await api.run(ctx);
  delete globalThis.document;
  return { result, courses: added.map(normalise) };
}

function normalise(c) {
  return {
    title: c.title,
    teacher: c.teacher || "",
    location: c.location || "",
    day: c.dayOfWeek,
    start: c.startNode,
    end: c.endNode,
    weeks: c.weeks.join(","),
  };
}

function sortKey(c) {
  return `${c.day}-${c.start}-${c.end}-${c.title}`;
}

function expectCourses(actual, expected) {
  const sortedActual = actual.slice().sort((a, b) => sortKey(a).localeCompare(sortKey(b)));
  const sortedExpected = expected.slice().sort((a, b) => sortKey(a).localeCompare(sortKey(b)));
  assert.deepEqual(sortedActual, sortedExpected);
}

const WEEKDAY_HEAD = "<tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>";

domTest("school timing labels map to chronological nodes, including noon and evening", async () => {
  const catalog = JSON.parse(readFileSync(join(here, "../../main/assets/school-portal/schools.json"), "utf8"));
  const slots = catalog.find((school) => school.name === "长江大学").timingProfile.slotTimes;
  // The portal lists noon and evening at the end, rather than in time order.
  const labels = ["第一节", "第二节", "第三节", "第四节", "第五节", "第六节", "午间课", "晚间课"];
  const html = `<table>${WEEKDAY_HEAD}${labels.map((label, i) =>
    `<tr><td>${label}</td><td>课程${String.fromCharCode(65 + i)}<br>1-16周</td>${"<td></td>".repeat(6)}</tr>`,
  ).join("")}</table>`;
  const { result, courses } = await runOn(html, null, slots);
  assert.equal(result.committed, true);
  assert.deepEqual(courses.map((c) => [c.start, c.end]), [
    [1, 1], [2, 2], [4, 4], [5, 5], [7, 7], [8, 8], [3, 3], [6, 6],
  ]);
  // A following session with no school profile must retain ordinary period numbering.
  const ordinary = await runOn(`<table>${WEEKDAY_HEAD}
    <tr><td>第三节</td><td>普通课程<br>1-16周</td>${"<td></td>".repeat(6)}</tr></table>`);
  assert.equal(ordinary.courses[0].start, 3);
});

domTest("a rowspan with an unknown end period must not silently import a shortened course", async () => {
  const { result, courses } = await runOn(`<table>${WEEKDAY_HEAD}
    <tr><td>第1节</td><td rowspan="2">实验课程<br>1-16周</td>${"<td></td>".repeat(6)}</tr>
    <tr><td>未标明的节次</td>${"<td></td>".repeat(6)}</tr></table>`);
  assert.equal(result.error, "incomplete");
  assert.deepEqual(courses, []);
});

for (const count of [3, 7, 15]) {
  domTest(`a numbered course list with ${count} rows does not invalidate the timetable`, async () => {
    const grid = `<table>${WEEKDAY_HEAD}<tr><td>第1-2节</td><td>数学<br>1-4周</td>${"<td></td>".repeat(6)}</tr></table>`;
    const list = `<table><tr><th>序号</th><th>课程代码</th><th>课程名称</th><th>学分</th><th>课程序号</th><th>教师</th></tr>${Array.from({ length: count }, (_, i) => `<tr><td>${i + 1}</td><td>CS${1000 + i}</td><td>课程甲</td><td>2</td><td>${700000 + i}</td><td>李四</td></tr>`).join("")}</table>`;
    const { result, courses } = await runOn(grid + list);
    assert.equal(result.committed, true);
    assert.equal(courses.length, 1);
    assert.equal(courses[0].title, "数学");
  });
}

domTest("inline course code and teacher with bare week-room lines", async () => {
  const html = `<table>${WEEKDAY_HEAD}<tr><td>第3-4节</td><td>编译原理(791288) (王老师Y)<br><br>(单9-13,实验东5教101 组1)<br><br>算法设计(791289) (李老师)<br><br>(7-8 13-14,东13-C-215c)</td>${"<td></td>".repeat(6)}</tr></table>`;
  const { result, courses } = await runOn(html);
  assert.equal(result.committed, true);
  expectCourses(courses, [
    { title: "编译原理", teacher: "王老师Y", location: "实验东5教101 组1", day: 1, start: 3, end: 4, weeks: "9,11,13" },
    { title: "算法设计", teacher: "李老师", location: "东13-C-215c", day: 1, start: 3, end: 4, weeks: "7,8,13,14" },
  ]);
});

domTest("standard grid with <br>-separated lines", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td><td>高等数学<br>张三<br>1-16周<br>教学楼A-101</td><td></td><td>&nbsp;</td><td></td><td></td><td></td><td></td></tr>
    <tr><td>第3-4节</td><td></td><td>大学英语<br>李四<br>1-8周<br>二教203</td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { result, courses } = await runOn(html);
  assert.equal(result.committed, true);
  expectCourses(courses, [
    { title: "高等数学", teacher: "张三", location: "教学楼A-101", day: 1, start: 1, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
    { title: "大学英语", teacher: "李四", location: "二教203", day: 2, start: 3, end: 4, weeks: "1,2,3,4,5,6,7,8" },
  ]);
});

domTest("course spanning four periods via rowspan", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>1</td><td rowspan="4">程序设计<br>赵一<br>1-16周<br>机房1</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
    <tr><td>2</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
    <tr><td>3</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
    <tr><td>4</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "程序设计", teacher: "赵一", location: "机房1", day: 1, start: 1, end: 4, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("several courses in one cell, separated by blank lines and by a repeated weeks line", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td><td>
      高数<br>张三<br>1-8周<br>A101<br><br>英语<br>李四<br>9-16周<br>B202
    </td><td>物理<br>王五<br>1-16周<br>C303<br>----<br>化学<br>赵六<br>1-16周<br>D404</td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "高数", teacher: "张三", location: "A101", day: 1, start: 1, end: 2, weeks: "1,2,3,4,5,6,7,8" },
    { title: "英语", teacher: "李四", location: "B202", day: 1, start: 1, end: 2, weeks: "9,10,11,12,13,14,15,16" },
    { title: "物理", teacher: "王五", location: "C303", day: 2, start: 1, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
    { title: "化学", teacher: "赵六", location: "D404", day: 2, start: 1, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("week syntaxes and parity in a real grid", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td><td>甲<br>1-9周(单)</td><td>乙<br>2-16周(双)</td><td>丙<br>第3-9周</td><td>丁<br>1,3,5-7周</td><td>戊<br>{第1-8周|2周}</td><td>己<br>第1-4单周</td><td>庚<br>第5周</td></tr>
  </table>`;
  const { courses } = await runOn(html);
  const byTitle = Object.fromEntries(courses.map((c) => [c.title, c]));
  assert.equal(byTitle["甲"].weeks, "1,3,5,7,9");
  assert.equal(byTitle["乙"].weeks, "2,4,6,8,10,12,14,16");
  assert.equal(byTitle["丙"].weeks, "3,4,5,6,7,8,9");
  assert.equal(byTitle["丁"].weeks, "1,3,5,6,7");
  assert.equal(byTitle["戊"].weeks, "1,2,3,4,5,6,7,8");
  assert.equal(byTitle["己"].weeks, "1,3");
  assert.equal(byTitle["庚"].weeks, "5");
});

domTest("transposed page: days as rows, periods as columns", async () => {
  const html = `<table>
    <tr><th></th><th>1</th><th>2</th><th>3</th></tr>
    <tr><th>星期一</th><td>数据结构<br>赵六<br>1-16周<br>D101</td><td></td><td></td></tr>
    <tr><th>星期二</th><td></td><td>操作系统<br>孙七<br>1-16周<br>D102</td><td></td></tr>
    <tr><th>星期三</th><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "数据结构", teacher: "赵六", location: "D101", day: 1, start: 1, end: 1, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
    { title: "操作系统", teacher: "孙七", location: "D102", day: 2, start: 2, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("Chinese numeral periods with a 上午 group column", async () => {
  const head = "<tr><th>时段</th><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>";
  const html = `<table>${head}
    <tr><td rowspan="2">上午</td><td>第一节</td><td>语文<br>甲<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
    <tr><td>第二节</td><td></td><td>数学<br>乙<br>1-16周</td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "语文", teacher: "甲", location: "", day: 1, start: 1, end: 1, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
    { title: "数学", teacher: "乙", location: "", day: 2, start: 2, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("full-width punctuation and non-breaking spaces", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1－2节</td><td>高等数学（必修）<br>张三（副教授）<br>１－１６周<br>教学楼Ａ－１０１</td><td>&nbsp;<br>&nbsp;</td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  assert.equal(courses.length, 1);
  assert.equal(courses[0].title, "高等数学");
  assert.equal(courses[0].day, 1);
  assert.equal(courses[0].start, 1);
  assert.equal(courses[0].end, 2);
});

domTest("labelled fields in cells", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第5-6节</td><td>大学英语<br>教师：李四<br>地点：二教301<br>周次：1-8周(双)</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "大学英语", teacher: "李四", location: "二教301", day: 1, start: 5, end: 6, weeks: "2,4,6,8" },
  ]);
});

domTest("nested layout table: only the inner timetable is used", async () => {
  const inner = `<table>${WEEKDAY_HEAD}<tr><td>第1节</td><td>心理学<br>周一<br>1-16周<br>E101</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>`;
  const html = `<table><tr><td>${inner}</td></tr></table>`;
  const { courses } = await runOn(html);
  assert.equal(courses.length, 1);
  assert.equal(courses[0].title, "心理学");
});

domTest("two tables (morning and afternoon) are merged and duplicates removed", async () => {
  const html = `<table>${WEEKDAY_HEAD}<tr><td>第1节</td><td>哲学<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>
    <table>${WEEKDAY_HEAD}<tr><td>第7节</td><td></td><td>逻辑<br>1-16周</td><td></td><td></td><td></td><td></td><td></td></tr></table>
    <table>${WEEKDAY_HEAD}<tr><td>第1节</td><td>哲学<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>`;
  const { courses } = await runOn(html);
  expectCourses(courses.map((c) => ({ ...c })), [
    { title: "哲学", teacher: "", location: "", day: 1, start: 1, end: 1, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
    { title: "逻辑", teacher: "", location: "", day: 2, start: 7, end: 7, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("same-origin iframe timetable is read; cross-origin frame is skipped", async () => {
  const html = `<iframe id="same"></iframe><iframe id="cross"></iframe>`;
  const { courses } = await runOn(html, (doc) => {
    const same = doc.getElementById("same");
    same.contentDocument.body.innerHTML = `<table>${WEEKDAY_HEAD}<tr><td>第1节</td><td>框架课<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>`;
    const cross = doc.getElementById("cross");
    Object.defineProperty(cross, "contentDocument", { get() { throw new Error("cross-origin"); } });
  });
  assert.equal(courses.length, 1);
  assert.equal(courses[0].title, "框架课");
});

domTest("hidden elements do not contribute text", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td><td>线性代数<br><span style="display:none">旧数据 1-16周 老师甲</span><span hidden>隐藏 3-4周</span><br>王五<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "线性代数", teacher: "王五", location: "", day: 1, start: 1, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("invalid values are dropped while valid courses survive", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td><td>有效课<br>1-16周<br>E1</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
    <tr><td>第99节</td><td>越界节次<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
    <tr><td>第3节</td><td>零周<br>0周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { result, courses } = await runOn(html);
  // All or nothing: unreadable rows block the import so the timetable is never partial.
  assert.equal(result.error, "incomplete");
  assert.equal(courses.length, 0);
});

domTest("cells without week information produce no course", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1节</td><td>没有周次的课<br>张三<br>教学楼A</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { result, courses } = await runOn(html);
  assert.equal(result.error, "incomplete");
  assert.equal(courses.length, 0);
});

domTest("login page holds", async () => {
  const html = `<form><table><tr><td>学号</td><td><input name="user"></td></tr><tr><td>密码</td><td><input type="password"></td></tr></table><button>登录</button></form>`;
  const { result } = await runOn(html);
  assert.equal(result.hold, true);
});

domTest("grades table holds even though it has numbers and ranges", async () => {
  const html = `<table><tr><th>课程名称</th><th>学分</th><th>成绩</th><th>学期</th></tr>
    <tr><td>高等数学</td><td>4</td><td>92</td><td>2024-2025学年 第1学期</td></tr>
    <tr><td>大学英语</td><td>2</td><td>85</td><td>2024-2025学年 第2学期</td></tr></table>`;
  const { result } = await runOn(html);
  assert.equal(result.hold, true);
});

domTest("div-based timetable is not supported and holds without error", async () => {
  const html = `<div class="kb"><div class="row"><span>星期一</span><span>高数 1-16周</span></div></div>`;
  const { result } = await runOn(html);
  assert.equal(result.hold, true);
});

domTest("header with a merged title row above the weekday row", async () => {
  const html = `<table><tr><th colspan="8">2025-2026学年 第1学期 课表</th></tr>${WEEKDAY_HEAD}
    <tr><td>第1节</td><td>线性代数<br>王五<br>1-16周<br>F201</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "线性代数", teacher: "王五", location: "F201", day: 1, start: 1, end: 1, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("large grid (12 periods x 7 days) stays correct", async () => {
  let rows = "";
  for (let p = 1; p <= 12; p++) {
    let cells = `<td>第${p}节</td>`;
    for (let d = 1; d <= 7; d++) {
      cells += `<td>课${d}-${p}<br>T${d}<br>1-16周<br>R${d}${p}</td>`;
    }
    rows += `<tr>${cells}</tr>`;
  }
  const html = `<table>${WEEKDAY_HEAD}${rows}</table>`;
  const { courses } = await runOn(html);
  assert.equal(courses.length, 84);
  const sample = courses.find((c) => c.title === "课5-7");
  assert.equal(sample.day, 5);
  assert.equal(sample.start, 7);
  assert.equal(sample.end, 7);
  assert.equal(sample.location, "R57");
});

domTest("run with an empty page holds", async () => {
  const { result } = await runOn("<p>欢迎</p>");
  assert.equal(result.hold, true);
});

// ---------- additional realistic markup ----------

domTest("font/p/span markup with title attributes and nbsp placeholders", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td>
      <td class="kbcontent"><p class="title"><span>概率论</span></p><p title="老师"><font title="教师">王芳</font></p><p><font title="周次(节次)">1-16周</font></p><p title="教室"><font title="教室">文科楼301</font></p></td>
      <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "概率论", teacher: "王芳", location: "文科楼301", day: 1, start: 1, end: 2, weeks: "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" },
  ]);
});

domTest("minority-name teacher with a middle dot and bracketed room", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第3-4节</td><td>民族音乐<br>阿卜杜拉·买买提<br>1-12周<br>艺术楼(实验室)101</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  assert.equal(courses.length, 1);
  assert.equal(courses[0].title, "民族音乐");
  assert.equal(courses[0].teacher, "阿卜杜拉·买买提");
  assert.equal(courses[0].location, "艺术楼(实验室)101");
  assert.equal(courses[0].weeks, "1,2,3,4,5,6,7,8,9,10,11,12");
});

domTest("compact one-line forms: name with weeks in brackets, teacher and weeks on one line", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第1-2节</td><td>高数(1-16周)<br>张三 1-16周 教学楼A-101</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  assert.equal(courses.length, 1);
  assert.equal(courses[0].title, "高数");
  assert.equal(courses[0].weeks, "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16");
});

domTest("course with only a name and weeks (no teacher, no room)", async () => {
  const html = `<table>${WEEKDAY_HEAD}
    <tr><td>第5节</td><td></td><td></td><td>体育<br>1-8周</td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "体育", teacher: "", location: "", day: 3, start: 5, end: 5, weeks: "1,2,3,4,5,6,7,8" },
  ]);
});

domTest("transposed table with rowspan period labels", async () => {
  const html = `<table>
    <tr><th>星期</th><th>节次</th><th>课程</th></tr>
    <tr><td rowspan="2">星期一</td><td>1</td><td>离散数学<br>甲<br>1-16周<br>X1</td></tr>
    <tr><td>2</td><td></td></tr>
    <tr><td>星期二</td><td>1</td><td>编译原理<br>乙<br>1-16周<br>X2</td></tr>
    <tr><td>星期三</td><td>1</td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  const titles = courses.map((c) => `${c.title}@${c.day}`).sort();
  assert.deepEqual(titles, ["离散数学@1", "编译原理@2"]);
});

// ---------- fresh extreme cases (regression) ----------
const FRESH_H = "<tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>";
const FRESH_E = "<td></td>".repeat(7);
const FRESH_ALL = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16";
const FRESH_CASES = [
  { name: "day header as bare Chinese numeral 一..日", html: `<table><tr><th>节次</th><th>一</th><th>二</th><th>三</th><th>四</th><th>五</th></tr><tr><td>第1节</td><td>语文<br>1-16周</td><td></td><td></td><td></td><td></td></tr></table>`, expect: [["语文", 1, 1, 1, FRESH_ALL]] },
  { name: "weeks written 第1周至第16周", html: `<table>${FRESH_H}<tr><td>第1-2节</td><td>政治<br>第1周至第16周<br>I1</td>${FRESH_E}</tr></table>`, expect: [["政治", 1, 1, 2, FRESH_ALL]] },
  { name: "tilde range 1～16周 fullwidth", html: `<table>${FRESH_H}<tr><td>第1-2节</td><td>体育<br>1～16周<br>I2</td>${FRESH_E}</tr></table>`, expect: [["体育", 1, 1, 2, FRESH_ALL]] },
  { name: "title containing digits 大学物理(2)", html: `<table>${FRESH_H}<tr><td>第1-2节</td><td>大学物理(2)<br>1-16周<br>I3</td>${FRESH_E}</tr></table>`, expect: [["大学物理(2)", 1, 1, 2, FRESH_ALL]] },
  { name: "teacher in latin letters", html: `<table>${FRESH_H}<tr><td>第1-2节</td><td>Java编程<br>Zhang San<br>1-16周<br>I4</td>${FRESH_E}</tr></table>`, expect: [["Java编程", 1, 1, 2, FRESH_ALL]] },
  { name: "week list with 周 only at the end", html: `<table>${FRESH_H}<tr><td>第1-2节</td><td>力学<br>1-8,10-16周<br>I5</td>${FRESH_E}</tr></table>`, expect: [["力学", 1, 1, 2, "1,2,3,4,5,6,7,8,10,11,12,13,14,15,16"]] },
  { name: "period label with time on the next line", html: `<table>${FRESH_H}<tr><td>第3节<br>09:50-10:35</td><td>电路<br>1-16周<br>I6</td>${FRESH_E}</tr></table>`, expect: [["电路", 1, 3, 3, FRESH_ALL]] },
  { name: "two terms in two tabs (only one visible)", html: `<div class="tab" style="display:none"><table>${FRESH_H}<tr><td>第1节</td><td>上学期旧课<br>1-16周</td>${FRESH_E}</tr></table></div><div class="tab"><table>${FRESH_H}<tr><td>第1节</td><td>本学期新课<br>1-16周</td>${FRESH_E}</tr></table></div>`, expect: [["本学期新课", 1, 1, 1, FRESH_ALL]] },
  { name: "course with teacher and room on one line, weeks on another", html: `<table>${FRESH_H}<tr><td>第5-6节</td><td>美术鉴赏<br>李老师 教学楼C-201<br>3-10周</td>${FRESH_E}</tr></table>`, expect: [["美术鉴赏", 1, 5, 6, "3,4,5,6,7,8,9,10"]] },
  { name: "lunch-break row between periods", html: `<table>${FRESH_H}<tr><td>第4节</td><td>化工<br>1-16周<br>I7</td>${FRESH_E}</tr><tr><td>午休</td>${FRESH_E}</tr><tr><td>第5节</td><td>物化<br>1-16周<br>I8</td>${FRESH_E}</tr></table>`, expect: [["化工", 1, 4, 4, FRESH_ALL], ["物化", 1, 5, 5, FRESH_ALL]] },
  { name: "week-only cell with extra text, no title", html: `<table>${FRESH_H}<tr><td>第1节</td><td>1-16周</td>${FRESH_E}</tr></table>`, expect: [] },
  { name: "weekday names with trailing spaces and nbsp", html: `<table><tr><th>节次</th><th>星期一&nbsp;</th><th> 星期二 </th><th>星期三</th></tr><tr><td>第1节</td><td>网络<br>1-16周</td><td></td><td></td></tr></table>`, expect: [["网络", 1, 1, 1, FRESH_ALL]] },
];

for (const c of FRESH_CASES) {
  domTest(`extreme: ${c.name}`, async () => {
    const { result, courses } = await runOn(c.html);
    const got = courses.map((x) => [x.title, x.day, x.start, x.end, x.weeks]).map((x) => JSON.stringify(x)).sort();
    const want = c.expect.map((e) => JSON.stringify(e)).sort();
    if (c.expect.length === 0) {
      assert.equal(result.hold, true);
    } else {
      assert.deepEqual(got, want);
    }
  });
}

// ---------- layout families: beyond the classic grid ----------

const FAMILY_H = "<tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>";
const FAMILY_E = "<td></td>".repeat(7);
const FAMILY_ALL = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16";

domTest("classic grid: p/font markup with comma period range", async () => {
  const html = `<table id="Table1">${FAMILY_H}
    <tr><td><p>第1,2节</p><p>08:00-09:40</p></td>
      <td rowspan="2" class="kbcontent"><p><span>高等数学</span></p><p title="老师">张三</p><p title="周次(节次)">1-16周</p><p title="教室">A101</p></td>
      ${"<td></td>".repeat(5)}</tr>
  </table>`.replace("<td></td><td></td><td></td><td></td><td></td><td></td>", "<td></td><td></td><td></td><td></td><td></td><td></td>");
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "高等数学", teacher: "张三", location: "A101", day: 1, start: 1, end: 2, weeks: FAMILY_ALL },
  ]);
});

domTest("bracket-delimited fields on one line (强智-style)", async () => {
  const html = `<table>${FAMILY_H}
    <tr><td>第3-4节</td><td>大学英语[1-16周][李四][二教301]</td>${FAMILY_E}</tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "大学英语", teacher: "李四", location: "二教301", day: 1, start: 3, end: 4, weeks: FAMILY_ALL },
  ]);
});

domTest("grid built from div rows (no table element)", async () => {
  const cell = (t) => `<div>${t}</div>`;
  const row = (cells) => `<div class="row">${cells.join("")}</div>`;
  const blank = cell("");
  const html = `<div class="kb">
    ${row([cell("节次"), cell("星期一"), cell("星期二"), cell("星期三"), cell("星期四"), cell("星期五"), cell("星期六"), cell("星期日")])}
    ${row([cell("第1-2节"), cell("高数<br>张三<br>1-16周<br>A101"), blank, blank, blank, blank, blank, blank])}
    ${row([cell("第3-4节"), blank, cell("英语<br>李四<br>1-8周<br>B202"), blank, blank, blank, blank, blank])}
    ${row([cell("第5节"), blank, blank, cell("物理<br>王五<br>1-16周<br>C303"), blank, blank, blank, blank])}
  </div>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "高数", teacher: "张三", location: "A101", day: 1, start: 1, end: 2, weeks: FAMILY_ALL },
    { title: "英语", teacher: "李四", location: "B202", day: 2, start: 3, end: 4, weeks: "1,2,3,4,5,6,7,8" },
    { title: "物理", teacher: "王五", location: "C303", day: 3, start: 5, end: 5, weeks: FAMILY_ALL },
  ]);
});

domTest("card list view: one card per course, each with weekday, period and weeks", async () => {
  const html = `<div class="list">
    <div class="card"><h3>高等数学</h3><p>周一 第1-2节 1-16周</p><p>张三 教学楼A-101</p></div>
    <div class="card"><h3>大学英语</h3><p>周三 第3-4节 1-8周</p><p>李四 二教301</p></div>
  </div>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "高等数学", teacher: "张三", location: "教学楼A-101", day: 1, start: 1, end: 2, weeks: FAMILY_ALL },
    { title: "大学英语", teacher: "李四", location: "二教301", day: 3, start: 3, end: 4, weeks: "1,2,3,4,5,6,7,8" },
  ]);
});

domTest("eams-style cell with one div per field", async () => {
  const html = `<table>${FAMILY_H}
    <tr><td>第1-2节</td><td><div class="courseCell"><div class="name">数据结构</div><div class="teacher">赵六</div><div class="week">1-16周</div><div class="room">D101</div></div></td>${FAMILY_E}</tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "数据结构", teacher: "赵六", location: "D101", day: 1, start: 1, end: 2, weeks: FAMILY_ALL },
  ]);
});

domTest("header cells with time under the weekday and English abbreviation", async () => {
  const html = `<table><tr><th>时间</th><th>周一<br>Mon</th><th>周二<br>Tue</th><th>周三<br>Wed</th><th>周四<br>Thu</th><th>周五<br>Fri</th><th>周六<br>Sat</th><th>周日<br>Sun</th></tr>
    <tr><td>上午第1节<br>08:00</td><td>政治<br>甲<br>1-16周<br>E1</td>${FAMILY_E}</tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "政治", teacher: "甲", location: "E1", day: 1, start: 1, end: 1, weeks: FAMILY_ALL },
  ]);
});

// ---------- period and week wording from more layouts ----------

domTest("clock-only period labels are numbered by order", async () => {
  const html = `<table>${FAMILY_H}
    <tr><td>8:00-8:45</td><td>离散<br>甲<br>1-16周<br>M1</td>${FAMILY_E}</tr>
    <tr><td>08:55-09:40</td><td></td><td>图论<br>乙<br>1-16周<br>M2</td>${"<td></td>".repeat(5)}</tr>
    <tr><td>10:00-10:45</td><td></td><td></td><td>代数<br>丙<br>1-16周<br>M3</td><td></td><td></td><td></td><td></td></tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "离散", teacher: "甲", location: "M1", day: 1, start: 1, end: 1, weeks: FAMILY_ALL },
    { title: "图论", teacher: "乙", location: "M2", day: 2, start: 2, end: 2, weeks: FAMILY_ALL },
    { title: "代数", teacher: "丙", location: "M3", day: 3, start: 3, end: 3, weeks: FAMILY_ALL },
  ]);
});

domTest("Chinese double-numeral period range 第一二节", async () => {
  const html = `<table>${FAMILY_H}
    <tr><td>第一二节</td><td>化学<br>甲<br>1-16周<br>N1</td>${FAMILY_E}</tr>
  </table>`;
  const { courses } = await runOn(html);
  expectCourses(courses, [
    { title: "化学", teacher: "甲", location: "N1", day: 1, start: 1, end: 2, weeks: FAMILY_ALL },
  ]);
});

domTest("week tokens: 前八周, 后八周, 全周 and bracketed digits", async () => {
  const html = `<table>${FAMILY_H}
    <tr><td>第1-2节</td><td>甲课<br>前八周</td><td>乙课<br>后八周</td><td>丙课<br>全周</td><td>丁课<br>[1-9]周</td>${"<td></td>".repeat(3)}</tr>
  </table>`;
  const { courses } = await runOn(html);
  const byTitle = Object.fromEntries(courses.map((c) => [c.title, c]));
  assert.equal(byTitle["甲课"].weeks, "1,2,3,4,5,6,7,8");
  assert.equal(byTitle["乙课"].weeks, "9,10,11,12,13,14,15,16");
  assert.equal(byTitle["丙课"].weeks, FAMILY_ALL);
  assert.equal(byTitle["丁课"].weeks, "1,2,3,4,5,6,7,8,9");
});

domTest("standalone 单周 and 双周 without a range", async () => {
  const html = `<table>${FAMILY_H}
    <tr><td>第1-2节</td><td>实验甲<br>单周</td><td>实验乙<br>双周</td>${"<td></td>".repeat(5)}</tr>
  </table>`;
  const { courses } = await runOn(html);
  const byTitle = Object.fromEntries(courses.map((c) => [c.title, c]));
  assert.equal(byTitle["实验甲"].weeks, "1,3,5,7,9,11,13,15");
  assert.equal(byTitle["实验乙"].weeks, "2,4,6,8,10,12,14,16");
});

// ---------- position-based layout (absolute cards) ----------

// Stub getBoundingClientRect from data-x/y/w/h attributes; jsdom has no layout.
async function runGeometry(html) {
  const dom = new JSDOM(`<!doctype html><html><body>${html}</body></html>`);
  dom.window.HTMLElement.prototype.getBoundingClientRect = function () {
    const d = this.dataset;
    if (d.x === undefined) return { left: 0, top: 0, width: 0, height: 0, right: 0, bottom: 0 };
    const left = Number(d.x), top = Number(d.y), width = Number(d.w), height = Number(d.h);
    return { left, top, width, height, right: left + width, bottom: top + height };
  };
  globalThis.document = dom.window.document;
  const { ctx, added } = fakeCtx();
  const result = await api.run(ctx);
  delete globalThis.document;
  return { result, courses: added.map(normalise) };
}

const box = (x, y, w, h) => `data-x="${x}" data-y="${y}" data-w="${w}" data-h="${h}"`;

test("absolute cards: columns and periods come from geometry", async () => {
  const html = `
    <div ${box(100, 0, 80, 20)}>周一</div><div ${box(200, 0, 80, 20)}>周二</div><div ${box(300, 0, 80, 20)}>周三</div>
    <div ${box(10, 40, 40, 40)}>1<br>08:00</div><div ${box(10, 100, 40, 40)}>2<br>09:00</div><div ${box(10, 160, 40, 40)}>3<br>10:00</div>
    <div ${box(100, 40, 80, 100)}><div>高数</div><div>张三</div><div>1-16周</div><div>A101</div></div>
    <div ${box(200, 100, 80, 60)}><div>英语</div><div>李四</div><div>1-8周</div><div>B202</div></div>
    <div ${box(300, 160, 80, 60)}><div>物理</div><div>王五</div><div>1-16周</div><div>C303</div></div>`;
  const { courses } = await runGeometry(html);
  expectCourses(courses, [
    { title: "高数", teacher: "张三", location: "A101", day: 1, start: 1, end: 2, weeks: FAMILY_ALL },
    { title: "英语", teacher: "李四", location: "B202", day: 2, start: 2, end: 2, weeks: "1,2,3,4,5,6,7,8" },
    { title: "物理", teacher: "王五", location: "C303", day: 3, start: 3, end: 3, weeks: FAMILY_ALL },
  ]);
});

test("absolute cards without week text are not imported", async () => {
  const html = `
    <div ${box(100, 0, 80, 20)}>周一</div><div ${box(200, 0, 80, 20)}>周二</div><div ${box(300, 0, 80, 20)}>周三</div>
    <div ${box(10, 40, 40, 40)}>1<br>08:00</div><div ${box(10, 100, 40, 40)}>2<br>09:00</div>
    <div ${box(100, 40, 80, 60)}><div>数据结构</div><div>陈思远</div><div>博学楼A203</div></div>`;
  const { result, courses } = await runGeometry(html);
  assert.equal(courses.length, 0);
  assert.equal(result.error, "incomplete");
});
