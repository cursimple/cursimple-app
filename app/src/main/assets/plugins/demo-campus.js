function login(ctx, input) {
  if (!input.username || !input.password) {
    throw new Error("用户名或密码为空");
  }
  Host.log("login => " + input.username + " @ " + ctx.baseUrl);
  return {
    token: "demo-token-" + input.username,
    user: input.username
  };
}

function fetchSchedule(ctx, session, term) {
  Host.log("fetchSchedule => " + session.user + ", term=" + term.termId);
  const now = Host.nowIso();
  return {
    termId: term.termId || ctx.termId || "2026-spring",
    updatedAt: now,
    courses: [
      { id: "c1", title: "高等数学", teacher: "张老师", location: "A101", dayOfWeek: 1, startNode: 1, endNode: 2, weeks: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] },
      { id: "c2", title: "数据结构", teacher: "李老师", location: "B203", dayOfWeek: 1, startNode: 3, endNode: 4, weeks: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] },
      { id: "c3", title: "操作系统", teacher: "王老师", location: "C305", dayOfWeek: 3, startNode: 5, endNode: 6, weeks: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] },
      { id: "c4", title: "英语", teacher: "陈老师", location: "D110", dayOfWeek: 5, startNode: 7, endNode: 8, weeks: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] }
    ]
  };
}

function normalize(raw) {
  const grouped = {};
  (raw.courses || []).forEach((course) => {
    if (!grouped[course.dayOfWeek]) {
      grouped[course.dayOfWeek] = [];
    }
    grouped[course.dayOfWeek].push({
      id: course.id,
      title: course.title,
      teacher: course.teacher || "",
      location: course.location || "",
      weeks: course.weeks || [],
      time: {
        dayOfWeek: course.dayOfWeek,
        startNode: course.startNode,
        endNode: course.endNode
      }
    });
  });

  const dailySchedules = Object.keys(grouped)
    .map((day) => Number(day))
    .sort((a, b) => a - b)
    .map((day) => ({
      dayOfWeek: day,
      courses: grouped[day]
    }));

  return {
    termId: raw.termId,
    updatedAt: raw.updatedAt,
    dailySchedules: dailySchedules
  };
}

