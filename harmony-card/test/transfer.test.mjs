import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseCalendar,parseNotes,mergeNotes} from '../entry/src/main/ets/common/Transfer.ts';
const note={id:'origin',courseTitle:'高等数学',title:'复习',body:'正文',priority:'high',pinned:true,completed:false};
test('portable notes preserve real content and deduplicate imports',()=>{
 const notes=parseNotes(JSON.stringify({format:'cursimple.notes',version:1,notes:[note]}));
 assert.equal(notes[0].courseTitle,'高等数学');assert.equal(mergeNotes(notes,notes).length,1);
 assert.throws(()=>parseNotes('{"notes":[]}'));
});
test('calendar reads actual local time, weekly exclusions and moved one-off events',()=>{
 const text='BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:math\r\nSUMMARY:高等数学\r\nLOCATION:A101\r\nDTSTART;TZID=Asia/Shanghai:20260303T080000\r\nDTEND;TZID=Asia/Shanghai:20260303T094000\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\nEXDATE;TZID=Asia/Shanghai:20260310T080000\r\nEND:VEVENT\r\nEND:VCALENDAR';
 const events=parseCalendar(text);assert.equal(events.length,2);
 assert.equal(events[0].start,Date.UTC(2026,2,3,0));assert.equal(events[1].start,Date.UTC(2026,2,17,0));
 assert.equal(events[0].location,'A101');
});
test('unsupported calendar rules do not produce wrong dates',()=>{
 assert.throws(()=>parseCalendar('BEGIN:VCALENDAR\nBEGIN:VEVENT\nRRULE:FREQ=MONTHLY\nEND:VEVENT\nEND:VCALENDAR'));
});
