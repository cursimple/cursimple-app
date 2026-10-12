import {readFileSync} from 'node:fs';
import {createContext, runInContext} from 'node:vm';
import assert from 'node:assert/strict';
const [hold,forced]=process.argv.slice(2).map(path=>readFileSync(path,'utf8'));
let clock=0;
class FakeDate extends Date { static now(){return clock;} }
const create=()=>{
 const context=createContext({Date:FakeDate,URL,TextEncoder,console:{log(){},warn(){},error(){}},location:{href:'https://courses.example/eams'}});
 context.window=context;
 return context;
};
const invoke=(script,context)=>JSON.parse(runInContext(script,context));
const flush=()=>new Promise(resolve=>setImmediate(resolve));

// The plugin reports it needs the user; automatic retries must stop instead of looping.
const state=create();
assert.equal(invoke(hold,state).started,true);await flush();
assert.equal(state.calls,1);
assert.equal(state.__CURSIMPLE_PLUGIN_HOLD_KEYS['test-session@https://courses.example/eams'],true);
clock=2000;assert.equal(invoke(hold,state).skipped,true);
clock=60000;assert.equal(invoke(hold,state).skipped,true);
assert.equal(state.calls,1);

// Pressing upload runs the plugin again with the manual flag and releases the hold.
assert.equal(invoke(forced,state).started,true);await flush();
assert.equal(state.calls,2);
assert.equal(state.manualSeen,true);
assert.equal(state.__CURSIMPLE_PLUGIN_HOLD_KEYS['test-session@https://courses.example/eams'],undefined);
assert.equal(JSON.parse(state.__CURSIMPLE_SCHEDULE_DRAFT_JSON).courses.length,1);

// A plugin may raise the hold again on a later page; retries stop the same way.
const again=create();
assert.equal(invoke(hold,again).started,true);await flush();
clock+=2000;assert.equal(invoke(hold,again).skipped,true);
assert.equal(again.calls,1);
console.log('Plugin hold stops automatic retries and manual upload releases it.');
