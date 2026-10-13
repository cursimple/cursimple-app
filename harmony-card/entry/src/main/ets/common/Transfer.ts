export interface Note {
  id: string;
  courseTitle: string;
  title: string;
  body: string;
  priority: string;
  pinned: boolean;
  completed: boolean;
  dueAt?: string;
  sharedOriginId?: string;
}
export interface NotesBundle { format: string; version: number; notes: Note[]; }
export interface CourseEvent { id: string; title: string; location: string; start: number; end: number; }

/** The same portable file used by Android; no sender timetable IDs are trusted. */
export function parseNotes(text: string): Note[] {
  if (text.length > 8 * 1024 * 1024) throw new Error('file_too_large');
  const value = JSON.parse(text) as NotesBundle;
  if (value.format !== 'cursimple.notes' || value.version !== 1 || !Array.isArray(value.notes) || value.notes.length > 2000) throw new Error('invalid_notes');
  value.notes.forEach((n: Note) => {
    if (typeof n.id !== 'string' || !n.id || n.id.length > 256 || typeof n.body !== 'string' || n.body.length > 500000 || typeof n.title !== 'string' || n.title.length > 2000 || typeof n.courseTitle !== 'string') throw new Error('invalid_note');
  });
  return value.notes;
}

function noteKey(note: Note): string {
  return JSON.stringify([note.sharedOriginId || note.id, note.courseTitle.trim().toLowerCase(), note.title, note.body, note.priority || 'none', !!note.pinned, !!note.completed, note.dueAt || '']);
}
export function mergeNotes(existing: Note[], incoming: Note[]): Note[] {
  const result: Note[] = existing.slice();
  incoming.forEach((note: Note) => {
    if (!result.some((old: Note) => noteKey(old) === noteKey(note))) result.push(note);
  });
  return result;
}

function unescapeText(value: string): string {
  return value.replace(/\\([nN,;\\])/g, (_: string, ch: string) => ch.toLowerCase() === 'n' ? '\n' : ch);
}
function dateValue(value: string, key: string): number {
  const m = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z?)$/.exec(value);
  if (!m) throw new Error('invalid_time');
  const year = Number(m[1]), month = Number(m[2]) - 1, day = Number(m[3]), hour = Number(m[4]), minute = Number(m[5]), second = Number(m[6]);
  if (m[7] === 'Z') return Date.UTC(year, month, day, hour, minute, second);
  if (/TZID=(Asia\/Shanghai|Asia\/Taipei|Asia\/Hong_Kong)/.test(key)) return Date.UTC(year, month, day, hour - 8, minute, second);
  if (/TZID=(UTC|Etc\/UTC)/.test(key)) return Date.UTC(year, month, day, hour, minute, second);
  if (key.includes('TZID=')) throw new Error('unsupported_timezone');
  return new Date(year, month, day, hour, minute, second).getTime();
}

/** Supports CurSimple's weekly calendar export, including excluded and moved occurrences. */
export function parseCalendar(text: string): CourseEvent[] {
  if (text.length > 8 * 1024 * 1024 || !text.includes('BEGIN:VCALENDAR')) throw new Error('invalid_calendar');
  const lines = text.replace(/\r?\n[ \t]/g, '').split(/\r?\n/);
  const result: CourseEvent[] = [];
  let inside = false, title = '', location = '', id = '', start = 0, end = 0, count = 1, until = Number.MAX_SAFE_INTEGER;
  let excluded: number[] = [];
  lines.forEach((line: string) => {
    if (line === 'BEGIN:VEVENT') { inside = true; title = ''; location = ''; id = ''; start = 0; end = 0; count = 1; until = Number.MAX_SAFE_INTEGER; excluded = []; return; }
    if (!inside) return;
    if (line === 'END:VEVENT') {
      if (!start || end <= start || !title) throw new Error('invalid_event');
      for (let index = 0; index < count; index++) {
        const at = start + index * 7 * 86400000;
        if (at > until) break;
        if (!excluded.includes(at)) result.push({ id: id + '/' + at, title: title, location: location, start: at, end: at + end - start });
        if (result.length > 5000) throw new Error('too_many_events');
      }
      inside = false; return;
    }
    const colon = line.indexOf(':'); if (colon < 0) return;
    const key = line.substring(0, colon), value = line.substring(colon + 1), name = key.split(';')[0];
    if (name === 'SUMMARY') title = unescapeText(value);
    if (name === 'LOCATION') location = unescapeText(value);
    if (name === 'UID') id = value;
    if (name === 'DTSTART') start = dateValue(value, key);
    if (name === 'DTEND') end = dateValue(value, key);
    if (name === 'EXDATE') excluded = value.split(',').map((v: string) => dateValue(v, key));
    if (name === 'RRULE') {
      if (!value.includes('FREQ=WEEKLY') || (/INTERVAL=(\d+)/.exec(value)?.[1] || '1') !== '1') throw new Error('unsupported_recurrence');
      const limit = /COUNT=(\d+)/.exec(value); count = limit ? Number(limit[1]) : 104;
      if (count < 1 || count > 104) throw new Error('invalid_recurrence');
      const bound = /UNTIL=([^;]+)/.exec(value); if (bound) until = dateValue(bound[1], 'UNTIL');
    }
  });
  if (inside) throw new Error('incomplete_calendar');
  return result.sort((a: CourseEvent, b: CourseEvent) => a.start - b.start);
}
