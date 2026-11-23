// Generic timetable extraction for school portal pages.
// Runs inside the signed-in page when the user taps upload. Returns { hold: true }
// when no timetable grid is recognised, so the manual upload action stays available.
// Pure helpers take plain data so they can be tested without a DOM.

const GENERIC_MAX_COURSES = 300;
const GENERIC_MAX_PERIOD = 16;
const GENERIC_MAX_WEEK = 40;
const GENERIC_MAX_FRAME_DEPTH = 3;

// Course-like blocks that could not be read (no title, weeks or periods). Any of them blocks the import.
let genericSkipped = 0;
let genericSlotRanges = new Map();

const GENERIC_CN_NUMBERS = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10, 十一: 11, 十二: 12, 十三: 13, 十四: 14, 十五: 15, 十六: 16 };
const GENERIC_DAY_CN = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 日: 7, 天: 7, 七: 7 };
const GENERIC_DAY_EN = { mon: 1, tue: 2, wed: 3, thu: 4, fri: 5, sat: 6, sun: 7 };
const GENERIC_BLOCK_TAGS = ["div", "p", "li", "tr", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article"];
const GENERIC_SKIP_TAGS = ["script", "style", "noscript", "template", "button", "select", "option"];
// Course type words that decorate titles; they are not part of the course name.
const GENERIC_TYPE_WORDS = /必修|选修|限选|任选|公选|自选|公共课|专业课|基础课|通识|核心|实验课|理论课|实践课|考查|考试|考查课|考试课|重修|辅修|双学位|二专|双语|全英|全英文|研讨|网络课|网课|MOOC|线上|线下|翻转课堂|分项|课时|学分/;

// Normalise full-width punctuation and spacing so one set of patterns covers both.
function genericNorm(text) {
  return String(text || "")
    .replace(/[\u200b-\u200d\ufeff]/g, "")
    .replace(/[０-９]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0xfee0))
    .replace(/[　 ]/g, " ")
    .replace(/[（［【〔〖「『]/g, "(")
    .replace(/[）］】〕〗」』]/g, ")")
    .replace(/：/g, ":")
    .replace(/[，、]/g, ",")
    .replace(/[•·．]/g, "·")
    .replace(/(\d)\s*[－—–～~至到_]\s*(\d)/g, "$1-$2")
    .replace(/\s+/g, " ")
    .trim();
}

function genericDayOf(text) {
  const firstLine = String(text || "").split("\n").map((l) => genericNorm(l).replace(/\s/g, "")).find((l) => l) || "";
  const en = firstLine.match(/^(mon|tue|wed|thu|fri|sat|sun)[a-z]*$/i);
  if (en) return GENERIC_DAY_EN[en[1].toLowerCase()];
  const t = firstLine.replace(/\(?(mon|tue|wed|thu|fri|sat|sun)[a-z]*\)?$/i, "").replace(/\(?[(（].*$/, "");
  if (!t || t.length > 6) return null;
  let m = t.match(/^(?:星期|周|礼拜)([一二三四五六日天七])$/);
  if (m) return GENERIC_DAY_CN[m[1]];
  m = t.match(/^(?:星期|周|礼拜)([1-7])$/);
  if (m) return Number(m[1]);
  m = t.toLowerCase().match(/^(mon|tue|wed|thu|fri|sat|sun)/);
  if (m) return GENERIC_DAY_EN[m[1]];
  return null;
}

function genericRange(a, b) {
  const start = Math.min(a, b);
  const end = Math.max(a, b);
  if (start < 1 || end > GENERIC_MAX_PERIOD) return null;
  return { start, end };
}

// Period label cell: "1", "01", "第1节", "1-2节", "第一节", "3-4". The first matching line wins.
// Rows labelled only with clock times ("08:00-08:45") are numbered by their order of appearance.
// Period range for a label: a period label, or a clock time mapped through the time ordinals.
function genericLabelRange(text, ordinals) {
  for (const line of String(text || "").split("\n")) {
    const named = genericSlotRanges.get(genericNorm(line));
    if (named) return named;
  }
  const direct = genericPeriodLabel(text);
  if (direct) return direct;
  const t = String(text || "").match(/\d{1,2}:\d{2}/);
  if (t && ordinals && ordinals.has(t[0])) return genericRange(ordinals.get(t[0]), ordinals.get(t[0]));
  return null;
}

function genericTimeOrdinals(labels) {
  const starts = [...new Set(labels.filter((t) => /\d{1,2}:\d{2}/.test(t)).map((t) => t.match(/\d{1,2}:\d{2}/)[0]))];
  const minutes = (t) => { const [h, m] = t.split(":").map(Number); return h * 60 + m; };
  starts.sort((a, b) => minutes(a) - minutes(b));
  return new Map(starts.map((t, i) => [t, i + 1]));
}

function genericPeriodLabel(text) {
  for (const raw of String(text || "").split("\n")) {
    const line = genericNorm(raw).replace(/\s/g, "").replace(/^(上午|下午|晚上|中午|早上)/, "");
    if (!line || line.length > 12) continue;
    let m = line.match(/^第?0?(\d{1,2})[-~,、]0?(\d{1,2})节?$/);
    if (m) return genericRange(Number(m[1]), Number(m[2]));
    m = line.match(/^第?0?(\d{1,2})节?$/);
    if (m) return genericRange(Number(m[1]), Number(m[1]));
    // Compact double period e.g. "0102节" -> 1-2, "0304节" -> 3-4
    m = line.match(/^0(\d)0(\d)节?$/);
    if (m) return genericRange(Number(m[1]), Number(m[2]));
    m = line.match(/^第?([一二三四五六七八九十]{1,3})节?$/);
    if (m && GENERIC_CN_NUMBERS[m[1]]) return genericRange(GENERIC_CN_NUMBERS[m[1]], GENERIC_CN_NUMBERS[m[1]]);
    m = line.match(/^第?([一二三四五六七八九十])([一二三四五六七八九十])节?$/);
    if (m && GENERIC_CN_NUMBERS[m[1]] && GENERIC_CN_NUMBERS[m[2]]) return genericRange(GENERIC_CN_NUMBERS[m[1]], GENERIC_CN_NUMBERS[m[2]]);
    m = line.match(/^第?([一二三四五六七八九十]{1,2})[-~,、至到]第?([一二三四五六七八九十]{1,2})节?$/);
    if (m && GENERIC_CN_NUMBERS[m[1]] && GENERIC_CN_NUMBERS[m[2]]) return genericRange(GENERIC_CN_NUMBERS[m[1]], GENERIC_CN_NUMBERS[m[2]]);
  }
  return null;
}

// Period range inside free text, e.g. "1-2节", "第5节", "第3-4节", "[01-02节]", "节次:1-2".
function genericPeriodsIn(text) {
  const norm = genericNorm(text);
  // Compact double period like "0506节", "0102节", "0304节", "0708节", "0910节"
  let m = norm.match(/\b(?:0(\d)0(\d)|(\d{2})(\d{2}))节?\b/);
  if (m) {
    const s = Number(m[1] || m[3]);
    const e = Number(m[2] || m[4]);
    return genericRange(s, e);
  }
  m = norm.match(/第?\s*0?(\d{1,2})\s*(?:[-~,、]\s*0?(\d{1,2}))\s*节/);
  if (m) return genericRange(Number(m[1]), Number(m[2]));
  m = norm.match(/\[\s*0?(\d{1,2})\s*[-~,、]\s*0?(\d{1,2})\s*节?\s*\]/);
  if (m) return genericRange(Number(m[1]), Number(m[2]));
  m = norm.match(/(?:节次|大节|小节)\s*[:：]?\s*0?(\d{1,2})\s*(?:[-~,、]\s*0?(\d{1,2}))/);
  if (m) return genericRange(Number(m[1]), Number(m[2]));
  m = norm.match(/第?\s*0?(\d{1,2})\s*节/);
  if (m) return genericRange(Number(m[1]), Number(m[1]));
  m = norm.match(/(?:节次|大节|小节)\s*[:：]?\s*0?(\d{1,2})/);
  if (m) return genericRange(Number(m[1]), Number(m[1]));
  m = norm.match(/第?\s*([一二三四五六七八九十]{1,2})\s*(?:[-~,、至到]\s*第?\s*([一二三四五六七八九十]{1,2}))\s*节/);
  if (m && GENERIC_CN_NUMBERS[m[1]] && GENERIC_CN_NUMBERS[m[2]]) return genericRange(GENERIC_CN_NUMBERS[m[1]], GENERIC_CN_NUMBERS[m[2]]);
  return null;
}

function genericCnOrInt(text) {
  return /^\d+$/.test(text) ? Number(text) : (GENERIC_CN_NUMBERS[text] || 0);
}

// Week ranges and lists: "1-16周", "第3-9周", "1,3,5-7周", "1-9周(单)", "2-8周 双", "单周".
// Parity: 单 keeps odd weeks, 双 keeps even weeks; it may sit before 周 or in brackets after it.
function genericWeeksIn(text) {
  const weeks = new Set();
  const raw = genericNorm(text);

  // Binary bitmap representation e.g. "11111111111111110000" (16 consecutive 1s)
  const bitm = raw.match(/\b([01]{12,30})\b/);
  if (bitm && bitm[1].includes("1")) {
    const bits = bitm[1];
    for (let i = 0; i < bits.length && i < GENERIC_MAX_WEEK; i++) {
      if (bits[i] === "1") weeks.add(i + 1);
    }
    if (weeks.size > 0) return [...weeks].sort((a, b) => a - b);
  }

  const src = raw.replace(/[{}]/g, " ")
    // Strip repetitions like "(每周2次)" or "(3次/周)"
    .replace(/\([^\(\)]*?(?:每周\s*\d+\s*次|\d+\s*次\s*\/\s*周)[^\(\)]*?\)/g, "")
    // "[1-9]周" or "(1-16周)" or "{1-16周}" written with brackets around the numbers.
    .replace(/[\[(（](\d[\d\s,\-]*)周[\])）]/g, "$1周")
    .replace(/\[(\d[\d\s,\-]*)\]\s*周/g, "$1周")
    // "周次: 1-16" or "[周次]1-16, 18-20"
    .replace(/(?:\[周次\]|周次\s*[:：])\s*([0-9\s,\-]+)(?![0-9\s,\-]*周)/g, "$1周")
    // Leading parity e.g. "单周 1-15", "双周: 2-16"
    .replace(/(单|双)\s*周\s*[:：]?\s*(\d{1,2}(?:\s*-\s*\d{1,2})?)/g, "$2$1周")
    // "前八周" / "后八周" / "全周" / "全学期": the first or last eight weeks, or the whole term.
    .replace(/前\s*([一二三四五六七八九十\d]+)\s*周/g, (m, n) => `1-${genericCnOrInt(n)}周`)
    .replace(/后\s*([一二三四五六七八九十\d]+)\s*周/g, (m, n) => `${16 - genericCnOrInt(n) + 1}-16周`)
    .replace(/(?:全\s*周|全学期|整学期)/g, "1-16周")
    // Standalone "单周" / "双周" with no range: odd or even weeks of a 16-week term.
    .replace(/(^|[^\d-])(单|双)周/g, (m, pre, parity) => `${pre}1-16${parity}周`)
    // "2-16(双周)" / "1-16(周)": parity wording in brackets directly after the list.
    .replace(/(\d{1,2}(?:\s*-\s*\d{1,2})?)\s*\(\s*(单|双)?\s*周?\s*\)/g, "$1$2周")
    // "第1周至第16周": a range written with 周 on both ends.
    .replace(/第?\s*(\d{1,2})\s*周\s*(?:至|到|-)\s*第?\s*(\d{1,2})\s*周/g, "$1-$2周")
    // "1-8周,10-17周" -> "1-8,10-17周"
    .replace(/周\s*,\s*(\d)/g, ",$1")
    // Slashes or pluses between week digits: "1/3/5/7周" or "1+3+5周"
    .replace(/(\d{1,2})[+/](\d{1,2})/g, "$1,$2");

  const re = /(\d{1,2}(?:\s*-\s*\d{1,2})?(?:\s*,\s*\d{1,2}(?:\s*-\s*\d{1,2})?)*)\s*(单|双)?\s*(?:周|週)\s*(?:[(\[]?\s*(单|双)\s*[)\]]?)?/g;
  let m;
  while ((m = re.exec(src)) !== null) {
    const parity = m[2] || m[3] || "";
    for (const token of m[1].split(",")) {
      const range = token.match(/(\d{1,2})\s*-\s*(\d{1,2})/);
      const from = range ? Number(range[1]) : Number(token.trim());
      const to = range ? Number(range[2]) : from;
      for (let w = Math.min(from, to); w <= Math.max(from, to); w++) {
        if (w < 1 || w > GENERIC_MAX_WEEK) continue;
        if (parity === "单" && w % 2 === 0) continue;
        if (parity === "双" && w % 2 === 1) continue;
        weeks.add(w);
      }
    }
  }
  return [...weeks].sort((a, b) => a - b);
}

// Labels need a colon, bracket or a space after them, so "教学楼A-101" is not read as a label.
const GENERIC_FIELD_LABELS = [
  { key: "title", re: /^(?:\[\s*(?:课程名称|课程名|课程)\s*\]|(?:课程名称|课程名|课程)(?:\s*[:：]|\s+))(.+)$/ },
  { key: "teacher", re: /^(?:\[\s*(?:任课教师|授课教师|教师|老师)\s*\]|(?:任课教师|授课教师|教师|老师)(?:\s*[:：]|\s+))(.+)$/ },
  { key: "location", re: /^(?:\[\s*(?:上课地点|地点|教室|上课教室)\s*\]|(?:上课地点|地点|教室|上课教室)(?:\s*[:：]|\s+))(.+)$/ },
  { key: "weeks", re: /^(?:\[\s*(?:周次|上课周次|周数)\s*\]|(?:周次|上课周次|周数)(?:\s*[:：]|\s+))(.+)$/ },
  { key: "periods", re: /^(?:\[\s*(?:节次|上课节次)\s*\]|(?:节次|上课节次)(?:\s*[:：]|\s+))(.+)$/ },
];

// Bare week list in brackets followed by the room, with no 周 suffix: "(1-15,东13-C-215c)".
// The comma is required so a course code such as "(791288)" is never read as weeks.
function genericBareWeekRoom(line) {
  // Week list items may be separated by commas or spaces: "(1-15,…)", "(7-8 13-14,…)".
  const m = genericNorm(line).match(/^[(（\[]\s*((?:单|双)?\s*\d{1,2}(?:\s*-\s*\d{1,2})?(?:(?:\s*[,，]\s*|\s+)\d{1,2}(?:\s*-\s*\d{1,2})?)*)\s*[,，]\s*([^)）\]]+?)\s*[)）\]]$/);
  if (!m) return null;
  // A leading 单/双 applies to the whole list, e.g. "(单9-13,…)" keeps odd weeks only.
  const parity = m[1].match(/^(单|双)/);
  const body = m[1].replace(/^(单|双)\s*/, "").replace(/\s*-\s*/g, "-").replace(/\s+/g, ",").replace(/，/g, ",");
  const suffix = parity ? parity[1] : "";
  const weeks = genericWeeksIn(`${body}${suffix}周`);
  if (!weeks.length) return null;
  return { weekText: `${body}${suffix}`, room: m[2].trim(), weeks };
}

// Weeks carried by a line, either with 周 or in the bare bracketed form.
function genericLineWeeks(line) {
  const weeks = genericWeeksIn(line);
  if (weeks.length) return weeks;
  const bare = genericBareWeekRoom(line);
  return bare ? bare.weeks : [];
}

function genericIsLocation(line) {
  if (genericWeeksIn(line).length) return false;
  // Bracketed text is usually a course code or type ("计算机(791288)"), so it does not make a line a room.
  const outside = line.replace(/[(（\[][^)）\]]*[)）\]]/g, " ").trim();
  if (!outside) return false;
  // Words like "大学体育", "物理实验" are course names, not locations. Only match real facility terms.
  return /楼|室|馆|场|校区|教室|栋|排|区|园|堂|剧场|大厅|场地|池|中心|主楼|二教|一教|三教|四教|五教|公教|逸夫|文理|机电|理科|工科|生化|多媒体|语音|画室|琴房|田径场|操场|体育馆|体育场|网球场|游泳池|游泳馆|机房|实验室|\d{2,}|[A-Za-z]\d|\d[A-Za-z]|[A-Za-z]-[0-9]|#\d/.test(outside);
}

// Remove week and period expressions, keeping any other wording on the same line.
function genericStripSpans(line) {
  return genericNorm(line)
    .replace(/\d{1,2}(?:\s*-\s*\d{1,2})?(?:\s*,\s*\d{1,2}(?:\s*-\s*\d{1,2})?)*\s*(?:单|双)?\s*(?:周|週)\s*(?:[(\[]?\s*(?:单|双)\s*[)\]]?)?/g, " ")
    .replace(/第?\s*\d{1,2}\s*(?:-\s*\d{1,2})?\s*节/g, " ")
    .replace(/\b0\d0\d节\b/g, " ")
    .replace(/(单|双)周|全周|[前后]\s*[一二三四五六七八九十\d]+\s*周/g, " ")
    .replace(/[()\[\]]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function genericCleanTeacher(line) {
  return genericNorm(line)
    .replace(/[(（\[][^)）\]]*(?:教授|讲师|助教|实验师|工程师|研究员|教员|外教|博导|硕导|老师)[^)）\]]*[)）\]]/g, "")
    .replace(/\s*[/／]\s*(?:教授|副教授|讲师|助教|高工|工程师|实验师|研究员|教员|外教|老师)/g, "")
    .replace(/[()\[\]]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function genericIsName(line) {
  const clean = genericCleanTeacher(line);
  if (/^[\u4e00-\u9fa5\u00b7·]{2,8}$/.test(clean)) return true;
  if (/^[A-Za-z\s.·]{2,20}$/.test(clean)) return true;
  // Multiple teachers separated by slash or comma e.g. "张三/李四"
  if (/^[\u4e00-\u9fa5]{2,4}(?:[/,，\s]+[\u4e00-\u9fa5]{2,4})+$/.test(clean)) return true;
  return false;
}

function genericIsMeta(line) {
  return /周|节/.test(line) && /\d/.test(line);
}

// Split a cell's lines into course blocks at blank lines and separator rules.
function genericBlocks(lines) {
  const blocks = [];
  let current = [];
  const hasWeeks = (block) => block.some((l) => genericLineWeeks(l).length > 0);
  for (const raw of lines) {
    const line = String(raw || "").trim();
    if (/^([-_=*#~]|─|━|═){3,}$/.test(line)) {
      if (current.length) blocks.push(current);
      current = [];
    } else if (!line) {
      // A blank line ends a course only once the course has its weeks; before that it is noise.
      if (hasWeeks(current)) {
        blocks.push(current);
        current = [];
      }
    } else {
      current.push(line);
    }
  }
  if (current.length) blocks.push(current);
  // Two weeks lines in one block mean two courses; split before the second course's title.
  const result = [];
  for (const block of blocks) result.push(...genericSplitRepeatedWeeks(block));
  return result;
}

function genericSplitRepeatedWeeks(lines) {
  const weekIdx = [];
  lines.forEach((line, i) => {
    if (genericLineWeeks(line).length && !GENERIC_FIELD_LABELS.some((l) => l.key === "weeks" && l.re.test(line))) weekIdx.push(i);
  });
  if (weekIdx.length < 2) return [lines];
  const w1 = weekIdx[0];
  const w2 = weekIdx[1];
  let k = w1 + 1;
  while (k < w2 && genericIsLocation(lines[k])) k++;
  if (k >= w2) return [lines];
  return [lines.slice(0, k), ...genericSplitRepeatedWeeks(lines.slice(k))];
}

function genericStripTypeWords(title) {
  return title
    // Strip leading course code e.g. "[COMP1001]", "(B0101)", "TX001: "
    .replace(/^\[\s*[A-Za-z0-9_-]{3,}\s*\]\s*/, "")
    .replace(/^\(\s*[A-Za-z0-9_-]{3,}\s*\)\s*/, "")
    .replace(/^[A-Za-z0-9_-]{4,}\s*[:：]\s*/, "")
    // A trailing course code such as "(791288)" is an identifier, not part of the course name.
    .replace(/[(（]\s*\d{4,}\s*[)）]\s*$/, "")
    // Strip credit/hours like "[3.0学分]", "(3学分)", "[32学时]"
    .replace(/[(（\[]\s*\d+(?:\.\d+)?\s*(?:学分|学时|分)\s*[)）\]]/g, "")
    // Strip class/section like "(01班)", "(1班)"
    .replace(/[(（\[]\s*\d+班\s*[)）\]]/g, "")
    .replace(/\((?:[^()]*?)\)/g, (m) => (GENERIC_TYPE_WORDS.test(m) ? "" : m))
    .replace(/\[(?:[^\[\]]*?)\]/g, (m) => (GENERIC_TYPE_WORDS.test(m) ? "" : m))
    .replace(/\s+/g, " ")
    .trim();
}

// Turn one course block into a course, or null when title, weeks or periods are missing.
function genericCourseFromBlock(lines, fallbackRange, recordSkip = true) {
  const clean = lines.map((l) => genericNorm(l)).filter(Boolean);
  if (!clean.length) return null;
  let labelledTitle = "";
  let teacher = "";
  let location = "";
  let periods = null;
  const weekLines = [];
  const free = [];
  for (const line of clean) {
    let labelled = false;
    for (const label of GENERIC_FIELD_LABELS) {
      const m = line.match(label.re);
      if (!m) continue;
      labelled = true;
      const value = m[1].trim();
      if (label.key === "title" && !labelledTitle) labelledTitle = value;
      else if (label.key === "teacher" && !teacher) teacher = genericCleanTeacher(value);
      else if (label.key === "location" && !location) location = value;
      else if (label.key === "weeks") weekLines.push(/周/.test(value) ? value : value + "周");
      else if (label.key === "periods") periods = genericPeriodsIn(/节/.test(value) ? value : value + "节") || periods;
      break;
    }
    if (labelled) continue;
    const bare = genericBareWeekRoom(line);
    if (bare) {
      weekLines.push(`${bare.weekText}周`);
      free.push(bare.room);
      continue;
    }
    if (genericWeeksIn(line).length) weekLines.push(line);
    const p = genericPeriodsIn(line);
    if (p) periods = p;
    if (/^\d+$/.test(line)) continue;
    if (genericIsMeta(line) || genericWeeksIn(line).length) {
      const rest = genericStripSpans(line);
      if (rest) free.push(...rest.split(" ").filter((t) => /[\u4e00-\u9fa5A-Za-z]/.test(t)));
      continue;
    }
    // "张三 教学楼A-101": a name and a room written on one line.
    const parts = line.split(" ");
    if (parts.length >= 2 && genericIsLocation(parts[parts.length - 1]) && !genericIsLocation(parts[0])) {
      free.push(parts.slice(0, -1).join(" "), parts[parts.length - 1]);
      continue;
    }
    free.push(line);
  }
  const titleLine = labelledTitle || free.find((l) => !genericIsLocation(l)) || free[0];
  if (!titleLine) {
    if (recordSkip) genericSkipped++;
    return null;
  }
  // Some grids put the teacher after the course code on the title line.
  const inlineTeacher = titleLine.match(/^(.*?)\(\d{4,}\)\s*\(([^()]+)\)\s*$/);
  if (inlineTeacher && !teacher) teacher = inlineTeacher[2].trim();
  const title = genericStripTypeWords((inlineTeacher ? inlineTeacher[1] : titleLine).replace(/\(\s*\)$/, "").trim());
  if (!title) {
    if (recordSkip) genericSkipped++;
    return null;
  }
  const rest = free.filter((l) => l !== titleLine);
  if (!teacher) {
    // Prefer a plain Chinese name; otherwise any short line that does not look like a room.
    const candidate = rest.find((l) => genericIsName(l)) || rest.find((l) => !genericIsLocation(l) && l.length <= 12);
    if (candidate) teacher = genericCleanTeacher(candidate);
  }
  if (!location) {
    // Rooms usually come after the teacher, so take the last room-like line.
    const rooms = rest.filter((l) => l !== teacher && genericIsLocation(l));
    if (rooms.length) location = rooms[rooms.length - 1];
  }
  const weeks = genericWeeksIn(weekLines.join(" "));
  if (!weeks.length) {
    if (recordSkip) genericSkipped++;
    return null;
  }
  const range = periods || fallbackRange;
  if (!range) {
    if (recordSkip) genericSkipped++;
    return null;
  }
  return { title, teacher, location, weeks, startNode: range.start, endNode: range.end };
}

// spec: rows of cells, each { lines, rowspan, colspan }. Spans share one object across the grid.
function genericBuildMatrix(specRows) {
  const matrix = [];
  specRows.forEach((row, r) => {
    matrix[r] = matrix[r] || [];
    let c = 0;
    row.forEach((cell) => {
      while (matrix[r][c]) c++;
      const rowspan = Math.max(1, cell.rowspan || 1);
      const colspan = Math.max(1, cell.colspan || 1);
      const obj = { lines: cell.lines || [], rowspan, colspan, row: r, col: c };
      for (let i = 0; i < rowspan; i++) {
        matrix[r + i] = matrix[r + i] || [];
        for (let j = 0; j < colspan; j++) matrix[r + i][c + j] = obj;
      }
      c += colspan;
    });
  });
  return matrix;
}

function genericTranspose(matrix) {
  const width = Math.max(0, ...matrix.map((row) => row.length));
  const out = [];
  for (let c = 0; c < width; c++) out.push(matrix.map((row) => row[c] || null));
  return out;
}

// Header row with at least three weekday cells. Bare digits count only when they run 1..n in order.
function genericDayColumns(matrix) {
  for (let r = 0; r < Math.min(matrix.length, 4); r++) {
    const dayCols = new Map();
    const digitCols = [];
    (matrix[r] || []).forEach((cell, c) => {
      if (!cell) return;
      const text = cell.lines.join("");
      const day = genericDayOf(text);
      if (day && !dayCols.has(c)) dayCols.set(c, day);
      if (/^[1-7]$/.test(genericNorm(text).replace(/\s/g, ""))) digitCols.push({ c, d: Number(text.trim()) });
    });
    if (dayCols.size < 3 && digitCols.length >= 3) {
      const texts = (matrix[r] || []).filter(Boolean).map((cell) => genericNorm(cell.lines.join("")));
      const numberedList = texts.some((text) => /^(?:序号|编号|序|No\.?|Index)$/i.test(text)) ||
        texts.some((text) => /^\d+$/.test(text) && (Number(text) < 1 || Number(text) > 7));
      const sequential = digitCols.every((x, i) => x.d === i + 1);
      if (sequential && !numberedList) digitCols.forEach((x) => dayCols.set(x.c, x.d));
    }
    // Bare 一..日 headers, accepted only when they run in order (avoids matching body text).
    if (dayCols.size < 3) {
      const cnCols = [];
      (matrix[r] || []).forEach((cell, c) => {
        if (!cell) return;
        const t = genericNorm(cell.lines.join("")).replace(/\s/g, "");
        if (t in GENERIC_DAY_CN) cnCols.push({ c, d: GENERIC_DAY_CN[t] });
      });
      if (cnCols.length >= 3 && cnCols.every((x, i) => x.d === i + 1)) cnCols.forEach((x) => dayCols.set(x.c, x.d));
    }
    if (dayCols.size >= 3) return { dayCols, headerRow: r };
  }
  return null;
}

function genericScoreMatrix(matrix) {
  const found = genericDayColumns(matrix);
  if (!found) return { score: 0, found: null };
  let courseCells = 0;
  let periodRows = 0;
  const seen = new Set();
  for (let r = found.headerRow + 1; r < matrix.length; r++) {
    const row = matrix[r] || [];
    if (row.some((cell) => cell && genericPeriodLabel(cell.lines.join("\n")))) periodRows++;
    for (const cell of row) {
      if (!cell || seen.has(cell)) continue;
      seen.add(cell);
      if (genericWeeksIn(cell.lines.join(" ")).length) courseCells++;
    }
  }
  if (courseCells < 1) return { score: 0, found: null };
  return { score: found.dayCols.size * 3 + courseCells * 2 + periodRows, found };
}

// Try normal and transposed orientation; return the better one as a matrix in normal orientation.
function genericAnalyseSpec(specRows) {
  const matrix = genericBuildMatrix(specRows);
  const normal = genericScoreMatrix(matrix);
  const flipped = genericTranspose(matrix);
  const transposed = genericScoreMatrix(flipped);
  if (transposed.score > normal.score) return { score: transposed.score, matrix: flipped, found: transposed.found };
  return { score: normal.score, matrix, found: normal.found };
}

// Long layout: each row carries its weekday and period, and the course sits in its own column or across row columns.
function genericAnalyseLong(matrix) {
  const width = Math.max(0, ...matrix.map((row) => row.length));
  const distinct = (c, test) => new Set(matrix.map((row) => row[c]).filter((cell) => cell && test(cell))).size;
  let dayCol = -1;
  let dayCount = 0;
  for (let c = 0; c < width; c++) {
    const n = distinct(c, (cell) => genericDayOf(cell.lines.join("")) !== null);
    if (n > dayCount) { dayCount = n; dayCol = c; }
  }
  if (dayCount < 1) return { score: 0, courses: [] };
  let periodCol = -1;
  let periodCount = 0;
  let weekCol = -1;
  let weekCount = 0;
  for (let c = 0; c < width; c++) {
    if (c === dayCol) continue;
    const labels = distinct(c, (cell) => genericPeriodLabel(cell.lines.join("\n")) !== null || /\d{1,2}:\d{2}/.test(cell.lines.join(" ")));
    if (labels > periodCount) { periodCount = labels; periodCol = c; }
    const weeks = distinct(c, (cell) => genericWeeksIn(cell.lines.join(" ")).length > 0);
    if (weeks > weekCount) { weekCount = weeks; weekCol = c; }
  }
  if (periodCount < 1 || weekCount < 1 || weekCol === periodCol) return { score: 0, courses: [] };
  const courses = [];
  const processed = new Set();
  const allLabels = matrix.map((row) => (row[periodCol] ? row[periodCol].lines.join("\n") : ""));
  const ordinals = genericTimeOrdinals(allLabels);
  const labelRange = (r) => (matrix[r] && matrix[r][periodCol] ? genericLabelRange(matrix[r][periodCol].lines.join("\n"), ordinals) : null);
  for (let r = 0; r < matrix.length; r++) {
    const dayCell = matrix[r] && matrix[r][dayCol];
    const day = dayCell ? genericDayOf(dayCell.lines.join("")) : null;
    if (!day) continue;
    const first = labelRange(r);
    const fallback = first || null;

    // Combine lines from the course/week columns in this row (ignoring day and period columns)
    const rowLines = [];
    (matrix[r] || []).forEach((cell, c) => {
      if (c === dayCol || c === periodCol || !cell) return;
      // Skip pure index numbers like "1", "2"
      const t = cell.lines.join("").trim();
      if (/^\d{1,3}$/.test(t) && c < dayCol && c < weekCol) return;
      // Skip credit numbers like "3.0"
      if (/^\d(?:\.\d)?$/.test(t)) return;
      rowLines.push(...cell.lines);
    });

    const blockKey = `${day}|${r}|${rowLines.join(";")}`;
    if (processed.has(blockKey)) continue;
    processed.add(blockKey);

    for (const block of genericBlocks(rowLines)) {
      const course = genericCourseFromBlock(block, fallback, false);
      if (course) courses.push({ ...course, dayOfWeek: day });
    }
  }
  return { score: dayCount * 3 + weekCount * 2 + periodCount, courses };
}

function genericExtractCourses(matrix, dayCols, headerRow) {
  const body = [];
  for (let r = headerRow + 1; r < matrix.length; r++) body.push(r);
  const width = Math.max(0, ...matrix.map((row) => row.length));
  // Period column: the non-day column whose cells read as period labels most often.
  let labelCol = -1;
  let bestHits = 0;
  for (let c = 0; c < width; c++) {
    if (dayCols.has(c)) continue;
    let hits = 0;
    for (const r of body) {
      const cell = matrix[r] && matrix[r][c];
      if (cell && (genericPeriodLabel(cell.lines.join("\n")) || /\d{1,2}:\d{2}/.test(cell.lines.join(" ")))) hits++;
    }
    if (hits > bestHits) {
      bestHits = hits;
      labelCol = c;
    }
  }
  if (labelCol < 0) return [];
  const ordinals = genericTimeOrdinals(body.map((r) => (matrix[r] && matrix[r][labelCol] ? matrix[r][labelCol].lines.join("\n") : "")));
  const rangeAt = (r) => {
    const cell = matrix[r] && matrix[r][labelCol];
    return cell ? genericLabelRange(cell.lines.join("\n"), ordinals) : null;
  };
  const out = [];
  const seenKeys = new Set();
  for (const [col, day] of dayCols) {
    const processed = new Set();
    for (const r of body) {
      const cell = matrix[r] && matrix[r][col];
      if (!cell || processed.has(cell)) continue;
      processed.add(cell);
      // A cell spanning several day columns is a whole-week notice, not one day's course.
      if (cell.colspan > 1 && [...dayCols.keys()].filter((c) => c >= cell.col && c < cell.col + cell.colspan).length > 1) continue;
      const first = rangeAt(cell.row);
      const lastRow = Math.min(matrix.length - 1, cell.row + cell.rowspan - 1);
      const last = rangeAt(lastRow);
      const fallback = first && last ? { start: first.start, end: last.end } :
        (first && first.end > first.start ? first : null);
      for (const block of genericBlocks(cell.lines)) {
        const course = genericCourseFromBlock(block, fallback);
        if (!course) continue;
        const key = [day, course.startNode, course.endNode, course.title, course.location, course.weeks.join(",")].join("|");
        if (seenKeys.has(key)) continue;
        seenKeys.add(key);
        out.push({ ...course, dayOfWeek: day });
      }
    }
  }
  return out.slice(0, GENERIC_MAX_COURSES);
}

// Host draft rules: anything outside these ranges would fail the whole import, so drop it here.
function genericValidCourse(c) {
  return !!c && typeof c.title === "string" && c.title.trim().length > 0 &&
    Number.isInteger(c.dayOfWeek) && c.dayOfWeek >= 1 && c.dayOfWeek <= 7 &&
    Number.isInteger(c.startNode) && c.startNode >= 1 && c.startNode <= 32 &&
    Number.isInteger(c.endNode) && c.endNode >= c.startNode && c.endNode <= 32 &&
    Array.isArray(c.weeks) && c.weeks.length > 0 && c.weeks.every((w) => Number.isInteger(w) && w >= 1 && w <= 60);
}

// ---- DOM layer (page context only) ----

// Read a cell's text by walking nodes, so <br>, block elements and hidden nodes behave the same
// in every WebView. Blank lines mark course separators.
function genericCellLines(cell) {
  const lines = [];
  let buffer = "";
  const flush = (isBreak) => {
    const text = genericNorm(buffer);
    buffer = "";
    if (text) lines.push(text);
    else if (isBreak) lines.push("");
  };
  const cellTitle = cell && cell.getAttribute && (cell.getAttribute("title") || cell.getAttribute("data-original-title"));
  if (cellTitle && (cellTitle.includes("周") || cellTitle.includes("节")) && cellTitle.includes("\n")) {
    const rawInner = (cell.textContent || "").trim();
    const titleLines = cellTitle.split(/\r?\n/).map(genericNorm).filter(Boolean);
    if (titleLines.length >= 2 && rawInner.length < cellTitle.length * 0.7) {
      return titleLines;
    }
  }
  const walk = (node) => {
    if (node.nodeType === 3) {
      buffer += node.nodeValue;
      return;
    }
    if (node.nodeType !== 1) return;
    const tag = String(node.tagName || "").toLowerCase();
    if (GENERIC_SKIP_TAGS.includes(tag)) return;
    if (node.hasAttribute && node.hasAttribute("hidden")) return;
    if (/display\s*:\s*none/i.test((node.getAttribute && node.getAttribute("style")) || "")) return;
    if (tag === "br") {
      flush(true);
      return;
    }
    if (tag === "hr") {
      flush(false);
      lines.push("");
      return;
    }
    const block = GENERIC_BLOCK_TAGS.includes(tag);
    if (block) flush(false);
    for (const child of Array.from(node.childNodes)) walk(child);
    if (block) flush(false);
  };
  walk(cell);
  flush(false);
  while (lines.length && lines[0] === "") lines.shift();
  while (lines.length && lines[lines.length - 1] === "") lines.pop();
  return lines;
}

function genericTableSpec(table) {
  return Array.from(table.rows).map((row) => Array.from(row.cells).map((cell) => ({
    lines: genericCellLines(cell),
    rowspan: cell.rowSpan || 1,
    colspan: cell.colSpan || 1,
  })));
}

// A table is hidden when it or an ancestor is hidden, e.g. the inactive tab of a term switcher.
function genericIsHidden(el) {
  const view = el.ownerDocument && el.ownerDocument.defaultView;
  for (let node = el; node && node.nodeType === 1; node = node.parentElement) {
    if (node.hasAttribute && node.hasAttribute("hidden")) return true;
    if (view && view.getComputedStyle && view.getComputedStyle(node).display === "none") return true;
  }
  return false;
}

function genericDocuments(doc, depth = 0, out = []) {
  out.push(doc);
  if (depth >= GENERIC_MAX_FRAME_DEPTH) return out;
  doc.querySelectorAll("iframe, frame").forEach((frame) => {
    try {
      const inner = frame.contentDocument;
      if (inner) genericDocuments(inner, depth + 1, out);
    } catch (error) {
      // Cross-origin frames cannot be read; skip them.
    }
  });
  return out;
}

// Grid built from <div> rows: a container whose children are rows, each row holding one child per column.
function genericDivGridSpecs(doc) {
  const specs = [];
  for (const parent of doc.querySelectorAll("div, section, main, ul, article")) {
    const rows = Array.from(parent.children).filter((el) => el.children.length >= 6 && el.children.length <= 14 && !el.querySelector("table"));
    if (rows.length < 3) continue;
    const widths = rows.map((row) => row.children.length);
    const mode = widths.sort((a, b) => a - b)[Math.floor(widths.length / 2)];
    const usable = rows.filter((row) => row.children.length === mode);
    if (usable.length < 3) continue;
    specs.push(usable.map((row) => Array.from(row.children).map((cell) => ({
      lines: genericCellLines(cell), rowspan: 1, colspan: 1,
    }))));
  }
  return specs;
}

// Card/list view: the smallest elements whose text carries a weekday, a period and weeks.
function genericCardCourses(doc) {
  const out = [];
  const dayWord = /(星期|周|礼拜)[一二三四五六日天七1-7]|\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\b/i;
  const matches = (el) => {
    const text = genericNorm(el.textContent || "");
    return dayWord.test(text) && genericPeriodsIn(text) && genericWeeksIn(text).length > 0;
  };
  // Smallest matching elements first, then climb to the outermost element that still holds one course.
  const minimal = Array.from(doc.querySelectorAll("div, li, section, article, tr, p")).filter((el) =>
    matches(el) && !Array.from(el.querySelectorAll("div, li, section, article, tr, p")).some(matches));
  const countIn = (node) => minimal.filter((m) => node.contains(m)).length;
  const roots = new Set();
  for (const el of minimal) {
    let node = el;
    while (node.parentElement && countIn(node.parentElement) === 1) node = node.parentElement;
    roots.add(node);
  }
  for (const el of roots) {
    const lines = genericCellLines(el);
    let day = null;
    const cleaned = lines.map((line) => {
      const m = line.match(dayWord);
      if (m && day === null) {
        day = genericDayOf(m[0]);
        return line.replace(dayWord, " ");
      }
      return line;
    });
    if (!day) continue;
    const course = genericCourseFromBlock(cleaned.filter(Boolean), null);
    if (course) out.push({ ...course, dayOfWeek: day });
  }
  return out;
}

// Position-based layout: cards are absolutely placed, so columns and rows come from element geometry.
// Needs real layout (getBoundingClientRect); returns nothing when the page has no usable geometry.
function genericRect(el) {
  const r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
  if (!r || !(r.width > 0) || !(r.height > 0)) return null;
  return { x: r.left, y: r.top, w: r.width, h: r.height, cx: r.left + r.width / 2, cy: r.top + r.height / 2 };
}

function genericGeometryCourses(doc) {
  const items = [];
  doc.querySelectorAll("body *").forEach((el) => {
    const rect = genericRect(el);
    if (!rect) return;
    const lines = genericCellLines(el).filter(Boolean);
    if (!lines.length || lines.join("").length > 200) return;
    items.push({ el, rect, lines, first: lines[0] });
  });
  // Weekday headers: one short element per column, in left-to-right order.
  const cnDay = (t) => (t in GENERIC_DAY_CN ? GENERIC_DAY_CN[t] : null);
  const headers = items.filter((it) => it.lines.join("").length <= 12 && (genericDayOf(it.first) || cnDay(genericNorm(it.first).replace(/\s/g, ""))));
  const columns = [];
  headers.sort((a, b) => a.rect.cx - b.rect.cx).forEach((h) => {
    const day = genericDayOf(h.first) || cnDay(genericNorm(h.first).replace(/\s/g, ""));
    const last = columns[columns.length - 1];
    if (last && Math.abs(last.cx - h.rect.cx) < 20) return;
    columns.push({ cx: h.rect.cx, day, el: h.el, top: h.rect.y });
  });
  if (columns.length < 3) return [];
  const colW = (columns[columns.length - 1].cx - columns[0].cx) / Math.max(1, columns.length - 1);
  const headerEls = new Set(headers.map((h) => h.el));
  // Period labels: a left-hand column of period numbers or clock times.
  const labelItems = items.filter((it) => !headerEls.has(it.el) && it.lines.length <= 4 &&
    (genericPeriodLabel(it.lines.join("\n")) || /\d{1,2}:\d{2}/.test(it.lines.join(" "))));
  const groups = [];
  labelItems.sort((a, b) => a.rect.cx - b.rect.cx).forEach((it) => {
    const g = groups.find((x) => Math.abs(x.cx - it.rect.cx) < 40);
    if (g) g.items.push(it); else groups.push({ cx: it.rect.cx, items: [it] });
  });
  groups.sort((a, b) => b.items.length - a.items.length || a.cx - b.cx);
  if (!groups.length || groups[0].items.length < 2) return [];
  const labels = groups[0].items.slice().sort((a, b) => a.rect.cy - b.rect.cy);
  const ordinals = genericTimeOrdinals(labels.map((l) => l.lines.join("\n")));
  const bands = labels.map((l) => ({ cy: l.rect.cy, range: genericLabelRange(l.lines.join("\n"), ordinals) })).filter((b) => b.range);
  if (bands.length < 2) return [];
  const labelEls = new Set(labels.map((l) => l.el));
  const colOf = (cx) => {
    let best = null;
    columns.forEach((c) => { if (Math.abs(c.cx - cx) <= colW / 2 && (!best || Math.abs(c.cx - cx) < Math.abs(best.cx - cx))) best = c; });
    return best;
  };
  // Card roots: leaves inside a column, climbed to the outermost element that still stays in that column.
  const roots = new Map();
  items.forEach((it) => {
    if (headerEls.has(it.el) || labelEls.has(it.el)) return;
    const col = colOf(it.rect.cx);
    if (!col || it.rect.cy < bands[0].cy - colW || it.rect.cy > bands[bands.length - 1].cy + colW) return;
    let node = it.el;
    while (node.parentElement && node.parentElement.tagName !== "BODY") {
      const parentRect = genericRect(node.parentElement);
      if (!parentRect || Math.abs(parentRect.cx - col.cx) > colW / 2 || parentRect.w > colW * 1.3) break;
      if (Array.from(node.parentElement.querySelectorAll("*")).some((x) => headerEls.has(x) || labelEls.has(x))) break;
      node = node.parentElement;
    }
    roots.set(node, col);
  });
  const out = [];
  roots.forEach((col, el) => {
    const rect = genericRect(el);
    if (!rect) return;
    const lines = genericCellLines(el).filter((l) => l && !/\d{1,2}:\d{2}/.test(l));
    // Periods covered by the card: label bands whose centre lies inside the card's vertical span.
    const covered = bands.filter((b) => b.cy >= rect.y && b.cy <= rect.y + rect.h);
    const near = bands.reduce((a, b) => (Math.abs(b.cy - rect.cy) < Math.abs(a.cy - rect.cy) ? b : a));
    const range = covered.length
      ? { start: covered[0].range.start, end: covered[covered.length - 1].range.end }
      : near.range;
    const course = genericCourseFromBlock(lines, range);
    if (course && col.day) out.push({ ...course, dayOfWeek: col.day });
  });
  return out;
}

// Only labelled dates or dated teaching weeks can anchor a term.
function genericTermStartIn(text) {
  const value = genericNorm(text);
  const isoDate = (year, month, day) => {
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    return date.getUTCFullYear() === Number(year) &&
      date.getUTCMonth() + 1 === Number(month) && date.getUTCDate() === Number(day) ? date : null;
  };
  const candidates = new Set();
  for (const match of value.matchAll(/(?:开学日期|学期开始日期|学期起始日期|开课日期)\s*[:：]?\s*(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})日?/g)) {
    const date = isoDate(match[1], match[2], match[3]);
    if (date) candidates.add(date.toISOString().slice(0, 10));
  }
  // Explicit school dates take priority over week annotations.
  if (candidates.size) return candidates.size === 1 ? [...candidates][0] : null;
  for (const match of value.matchAll(/第\s*(\d{1,2})\s*周[ \t:：]*(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})日?/g)) {
    const week = Number(match[1]);
    const date = isoDate(match[2], match[3], match[4]);
    if (!date || week < 1 || week > 30) continue;
    date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7) - (week - 1) * 7);
    candidates.add(date.toISOString().slice(0, 10));
  }
  return candidates.size === 1 ? [...candidates][0] : null;
}

export async function run(ctx) {
  genericSkipped = 0;
  genericSlotRanges = new Map();
  for (const slot of (ctx.term && ctx.term.slotTimes) || []) {
    const label = genericNorm(slot.label);
    if (label && Number.isInteger(slot.startNode) && Number.isInteger(slot.endNode) &&
        slot.startNode >= 1 && slot.endNode >= slot.startNode && slot.endNode <= 32) {
      genericSlotRanges.set(label, { start: slot.startNode, end: slot.endNode });
    }
  }
  const merged = [];
  const seen = new Set();
  let structure = false;
  const addAll = (courses) => {
    for (const course of courses) {
      if (!genericValidCourse(course)) continue;
      const key = [course.dayOfWeek, course.startNode, course.endNode, course.title, course.location, course.weeks.join(",")].join("|");
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push(course);
    }
  };
  const docs = genericDocuments(document);
  for (const doc of docs) {
    for (const table of doc.querySelectorAll("table")) {
      // Layout tables wrap the real timetable; only innermost tables are candidates.
      if (table.querySelector("table")) continue;
      if (genericIsHidden(table)) continue;
      const spec = genericTableSpec(table);
      const analysed = genericAnalyseSpec(spec);
      const gridScore = analysed.score;
      // A weekday header means this is a timetable even when none of its courses are readable yet.
      const matrix = genericBuildMatrix(spec);
      const headerProbe = genericDayColumns(matrix) || genericDayColumns(genericTranspose(matrix));
      if (headerProbe) structure = true;
      let gridCourses = [];
      if (gridScore > 0) {
        gridCourses = genericExtractCourses(analysed.matrix, analysed.found.dayCols, analysed.found.headerRow);
      } else if (headerProbe) {
        const probeMatrix = genericDayColumns(matrix) ? matrix : genericTranspose(matrix);
        const probe = genericDayColumns(probeMatrix);
        gridCourses = genericExtractCourses(probeMatrix, probe.dayCols, probe.headerRow);
      }
      const long = genericAnalyseLong(matrix);
      if (gridScore > 0 || long.score > 0) structure = true;
      // Pick whichever layout actually yields courses; a higher heuristic score alone is not enough.
      addAll(long.courses.length > gridCourses.length ? long.courses : gridCourses);
    }
  }
  if (!merged.length && genericSkipped === 0) {
    for (const doc of docs) {
      for (const spec of genericDivGridSpecs(doc)) {
        const analysed = genericAnalyseSpec(spec);
        if (analysed.score <= 0) continue;
        structure = true;
        addAll(genericExtractCourses(analysed.matrix, analysed.found.dayCols, analysed.found.headerRow));
      }
    }
  }
  if (!merged.length && genericSkipped === 0) {
    for (const doc of docs) {
      const before = genericSkipped;
      const found = [...genericGeometryCourses(doc), ...genericCardCourses(doc)];
      if (found.length || genericSkipped > before) structure = true;
      addAll(found);
    }
  }
  // All or nothing: a timetable with unreadable courses is not imported, because it would be incomplete.
  if (genericSkipped > 0) return { hold: true, error: "incomplete", count: genericSkipped };
  const courses = merged.slice(0, GENERIC_MAX_COURSES);
  if (!courses.length) return { hold: true, error: structure ? "no_courses" : "no_table" };
  for (const course of courses) {
    ctx.schedule.addCourse({
      title: course.title,
      teacher: course.teacher,
      location: course.location,
      dayOfWeek: course.dayOfWeek,
      startNode: course.startNode,
      endNode: course.endNode,
      weeks: course.weeks,
    });
  }
  const dates = [...new Set(docs.map((doc) => genericTermStartIn(doc.body ? genericCellLines(doc.body).join("\n") : ""))
    .filter(Boolean))];
  return ctx.schedule.commit({
    termId: ctx.term && ctx.term.id ? ctx.term.id : "",
    termStartDate: dates.length === 1 ? dates[0] : null,
  });
}
