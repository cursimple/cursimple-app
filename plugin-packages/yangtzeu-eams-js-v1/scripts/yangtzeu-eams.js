const eamsSlotNodeByLabel = {
  "第一节": 1,
  "第二节": 2,
  "第三节": 4,
  "第四节": 5,
  "第五节": 7,
  "第六节": 8,
  "午间课": 3,
  "晚间课": 6,
};

const defaultSlotNodeByIndex = {
  0: 1,
  1: 2,
  2: 4,
  3: 5,
  4: 7,
  5: 8,
  6: 3,
  7: 6,
};

function extractMeta(input) {
  const html = input.context?.courseHome || input.html || "";
  const weekValues = [];
  for (const match of html.matchAll(/<option value="(\d+)">第\d+周<\/option>/g)) {
    weekValues.push(Number(match[1]));
  }
  const maxWeek = Math.max(...weekValues);
  if (!Number.isFinite(maxWeek)) {
    throw new Error("未找到教学周上限");
  }
  return {
    semesterId: firstGroup(html, /semesterCalendar\(\{[^}]*value:"([^"]+)"/, "未找到 semester.id"),
    ids: firstGroup(html, /bg\.form\.addInput\(form,"ids","([^"]+)"\)/, "未找到学生 ids"),
    projectId: firstGroupOrNull(html, /project\.id=([^&']+)/) || "1",
    maxWeek,
  };
}

function buildSchedule(input) {
  const meta = input.context?.courseMeta || input.meta;
  const detailHtml = input.context?.courseDetail || input.detailHtml || "";
  if (!meta) {
    throw new Error("未找到 EAMS 元数据");
  }
  const unitCount = Number(firstGroupOrNull(detailHtml, /var unitCount = (\d+);/)) || 8;
  const teacherMap = parseTeacherMap(detailHtml);
  const activities = parseActivities(detailHtml, unitCount, meta.maxWeek, teacherMap);
  const grouped = {};
  for (const course of activities) {
    const day = String(course.time.dayOfWeek);
    grouped[day] = grouped[day] || [];
    grouped[day].push(course);
  }
  return {
    termId: input.termId || meta.semesterId,
    updatedAt: input.updatedAt || input.nowIso,
    dailySchedules: Object.keys(grouped)
      .map(Number)
      .sort((a, b) => a - b)
      .map((dayOfWeek) => ({
        dayOfWeek,
        courses: grouped[String(dayOfWeek)].sort(compareCourse),
      })),
  };
}

function parseTeacherMap(detailHtml) {
  const result = {};
  const rowPattern = /taskTable\.action\?lesson\.id=\d+".*?>(\d+)<\/a>\s*<\/td><td>([^<]*)<\/td>/gs;
  for (const match of detailHtml.matchAll(rowPattern)) {
    result[match[1]] = match[2].trim();
  }
  return result;
}

function parseActivities(detailHtml, unitCount, maxWeek, teacherMap) {
  const slotNodeMap = parseSlotNodeMap(detailHtml, unitCount);
  const blockPattern = /var teachers = \[(.*?)];.*?activity = new TaskActivity\((.*?)\);\s*((?:index\s*=\s*\d+\s*\*\s*unitCount\s*\+\s*\d+\s*;\s*table\d+\.activities\[index]\[table\d+\.activities\[index]\.length]=activity;\s*)+)/gs;
  const courses = [];
  let position = 0;
  for (const match of detailHtml.matchAll(blockPattern)) {
    const teacherBlock = match[1];
    const args = match[2];
    const indexBlock = match[3];
    const literals = [];
    for (const literal of args.matchAll(/"((?:\\.|[^"])*)"/g)) {
      literals.push(decodeJsString(literal[1]));
    }
    if (literals.length < 5) {
      throw new Error("TaskActivity 参数不足，无法解析课表");
    }
    const taskToken = literals[0];
    const rawCourseLabel = literals[1];
    const location = literals[3];
    const validWeeks = literals[4];
    const sequence = extractSequence(rawCourseLabel) || extractSequence(taskToken) || `activity-${position}`;
    const teacher = (firstGroupOrNull(teacherBlock, /name:"([^"]+)"/) || teacherMap[sequence] || "").trim();
    const indices = [];
    for (const indexMatch of indexBlock.matchAll(/index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+)\s*;/g)) {
      indices.push({
        dayIndex: Number(indexMatch[1]),
        slotIndex: Number(indexMatch[2]),
      });
    }
    if (indices.length === 0) {
      throw new Error("未找到课表位置索引");
    }
    const dayOfWeek = indices[0].dayIndex + 1;
    const nodes = indices
      .map((entry) => resolveSlotNode(entry.slotIndex, unitCount, slotNodeMap))
      .sort((a, b) => a - b);
    const startNode = nodes[0];
    const endNode = nodes[nodes.length - 1];
    const title = normalizeCourseTitle(rawCourseLabel);
    courses.push({
      id: stableCourseId(sequence, dayOfWeek, startNode, endNode, title, teacher, location, validWeeks),
      title,
      teacher,
      location,
      weeks: parseWeeks(validWeeks, maxWeek),
      category: "course",
      time: {
        dayOfWeek,
        startNode,
        endNode,
      },
    });
    position += 1;
  }
  const seen = {};
  return courses.filter((course) => {
    if (seen[course.id]) {
      return false;
    }
    seen[course.id] = true;
    return true;
  });
}

function parseSlotNodeMap(detailHtml, unitCount) {
  const result = {};
  const rowPattern = /<tr\b[^>]*>(.*?)<\/tr>/gs;
  for (const match of detailHtml.matchAll(rowPattern)) {
    const rowHtml = match[1] || "";
    const labelMatch = /<td\b[^>]*>\s*(?:<font\b[^>]*>)?\s*([^<]+?)\s*(?:<\/font>)?\s*<\/td>/s.exec(rowHtml);
    if (!labelMatch) {
      continue;
    }
    const node = eamsSlotNodeByLabel[normalizeHtmlText(labelMatch[1])];
    if (!node) {
      continue;
    }
    const slotMatch = /id=["']TD(\d+)_\d+["']/.exec(rowHtml);
    if (!slotMatch) {
      continue;
    }
    result[Number(slotMatch[1]) % unitCount] = node;
  }
  return result;
}

function resolveSlotNode(slotIndex, unitCount, slotNodeMap) {
  if (slotNodeMap[slotIndex]) {
    return slotNodeMap[slotIndex];
  }
  if (unitCount === 8 && defaultSlotNodeByIndex[slotIndex]) {
    return defaultSlotNodeByIndex[slotIndex];
  }
  throw new Error(`未找到 EAMS 节次索引映射: unitCount=${unitCount}, slotIndex=${slotIndex}`);
}

function parseWeeks(validWeeks, maxWeek) {
  const weeks = [];
  for (let index = 1; index <= maxWeek && index < validWeeks.length; index += 1) {
    if (validWeeks.charAt(index) === "1") {
      weeks.push(index);
    }
  }
  return weeks;
}

function normalizeCourseTitle(rawCourseLabel) {
  return rawCourseLabel.replace(/\(\d+\)$/, "").trim();
}

function extractSequence(raw) {
  return firstGroupOrNull(raw, /\((\d+)\)$/);
}

function decodeJsString(value) {
  return value
    .replace(/\\"/g, "\"")
    .replace(/\\'/g, "'")
    .replace(/\\n/g, "\n")
    .replace(/\\r/g, "\r")
    .replace(/\\\\/g, "\\")
    .trim();
}

function normalizeHtmlText(value) {
  return value.replace(/<[^>]+>/g, "").replace(/&nbsp;/g, " ").trim();
}

function stableCourseId(sequence, dayOfWeek, startNode, endNode, title, teacher, location, validWeeks) {
  return `${sequence}-${dayOfWeek}-${startNode}-${endNode}-${hash32([title, teacher, location, validWeeks].join("|"))}`;
}

function hash32(value) {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function compareCourse(left, right) {
  return left.time.startNode - right.time.startNode ||
    left.time.endNode - right.time.endNode ||
    left.title.localeCompare(right.title);
}

function firstGroup(value, regex, message) {
  const result = firstGroupOrNull(value, regex);
  if (!result) {
    throw new Error(message);
  }
  return result;
}

function firstGroupOrNull(value, regex) {
  const match = regex.exec(value);
  return match ? match[1] : null;
}
