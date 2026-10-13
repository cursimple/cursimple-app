import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { formBindingData, formProvider } from '@kit.FormKit';
import { buffer } from '@kit.ArkTS';
import { Note, CourseEvent, parseNotes, parseCalendar, mergeNotes } from './Transfer';

interface FormRef { id: string; name: string; }
interface Snapshot { notes: Note[]; events: CourseEvent[]; forms: FormRef[]; }
export class CardData {
  heading: string = '';
  title: string = '';
  detail: string = '';
  count: string = '';
  secondTitle: string = '';
  secondDetail: string = '';
}

/** Private application storage is shared by the app and its FormExtensionAbility. */
export class CardStore {
  private context: common.Context;
  constructor(context: common.Context) { this.context = context; }
  private path(): string { return this.context.filesDir + '/cursimple-cards.json'; }
  read(): Snapshot {
    try { return JSON.parse(fs.readTextSync(this.path())) as Snapshot; }
    catch (_) { return { notes: [], events: [], forms: [] }; }
  }
  private write(value: Snapshot): void {
    const temporary = this.path() + '.tmp';
    const file = fs.openSync(temporary, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
    try { fs.writeSync(file.fd, JSON.stringify(value)); } finally { fs.closeSync(file); }
    fs.renameSync(temporary, this.path());
  }
  addForm(id: string, name: string): void {
    const snapshot = this.read();
    snapshot.forms = snapshot.forms.filter((form: FormRef) => form.id !== id);
    snapshot.forms.push({ id: id, name: name }); this.write(snapshot);
  }
  removeForm(id: string): void {
    const snapshot = this.read(); snapshot.forms = snapshot.forms.filter((form: FormRef) => form.id !== id); this.write(snapshot);
  }
  async importFile(uri: string, notes: boolean): Promise<number> {
    const file = fs.openSync(uri, fs.OpenMode.READ_ONLY);
    let text = '';
    try {
      const size = fs.statSync(file.fd).size;
      if (size > 8 * 1024 * 1024) throw new Error('file_too_large');
      const contents = new ArrayBuffer(size);
      const bytes = fs.readSync(file.fd, contents);
      if (bytes !== size) throw new Error('incomplete_read');
      text = buffer.from(contents, 0, bytes).toString();
    } finally { fs.closeSync(file); }
    const snapshot = this.read();
    let count = 0;
    if (notes) {
      const merged = mergeNotes(snapshot.notes, parseNotes(text)); count = merged.length - snapshot.notes.length; snapshot.notes = merged;
    } else { const events = parseCalendar(text); count = events.length; snapshot.events = events; }
    this.write(snapshot);
    await this.refreshAll(); return count;
  }
  data(name: string): CardData {
    const result = new CardData();
    const strings = this.context.resourceManager;
    const snapshot = this.read();
    if (name === 'notes') {
      const active = snapshot.notes.filter((note: Note) => !note.completed).sort((a: Note, b: Note) => {
        if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
        const ranks = ['none', 'low', 'medium', 'high']; return ranks.indexOf(b.priority) - ranks.indexOf(a.priority);
      });
      result.heading = strings.getStringByNameSync('notes_card'); result.count = active.length.toString();
      result.title = active[0]?.title || strings.getStringByNameSync('empty_notes');
      result.detail = active[0] ? this.noteDetail(active[0]) : strings.getStringByNameSync('import_hint');
      result.secondTitle = active[1]?.title || ''; result.secondDetail = active[1] ? this.noteDetail(active[1]) : '';
    } else {
      const now = Date.now(); const today = new Date(now).toDateString();
      const courses = snapshot.events.filter((event: CourseEvent) => event.end > now && new Date(event.start).toDateString() === today);
      result.heading = strings.getStringByNameSync('courses_card'); result.count = courses.length.toString();
      result.title = courses[0]?.title || strings.getStringByNameSync('empty_courses');
      result.detail = courses[0] ? this.courseDetail(courses[0]) : strings.getStringByNameSync('import_hint');
      result.secondTitle = courses[1]?.title || ''; result.secondDetail = courses[1] ? this.courseDetail(courses[1]) : '';
    }
    return result;
  }
  private noteDetail(note: Note): string {
    const pieces: string[] = [];
    if (note.pinned) pieces.push(this.context.resourceManager.getStringByNameSync('pinned'));
    if (note.priority === 'high') pieces.push(this.context.resourceManager.getStringByNameSync('priority_high'));
    if (note.priority === 'medium') pieces.push(this.context.resourceManager.getStringByNameSync('priority_medium'));
    if (note.priority === 'low') pieces.push(this.context.resourceManager.getStringByNameSync('priority_low'));
    if (note.courseTitle) pieces.push(note.courseTitle);
    return pieces.join(' · ');
  }
  private clock(at: number): string {
    const date = new Date(at); return date.getHours().toString().padStart(2, '0') + ':' + date.getMinutes().toString().padStart(2, '0');
  }
  private courseDetail(event: CourseEvent): string { return this.clock(event.start) + '–' + this.clock(event.end) + (event.location ? ' · ' + event.location : ''); }
  async refreshAll(): Promise<void> {
    const forms = this.read().forms;
    for (const form of forms) {
      try { await formProvider.updateForm(form.id, formBindingData.createFormBindingData(this.data(form.name))); }
      catch (_) { /* A removed or temporarily unavailable form is retried on its next update. */ }
    }
  }
}
