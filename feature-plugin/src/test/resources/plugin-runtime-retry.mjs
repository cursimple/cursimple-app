import {readFileSync} from 'node:fs';
import {createContext, runInContext} from 'node:vm';
import assert from 'node:assert/strict';
const [normal,forced,legacy]=process.argv.slice(2).map(path=>readFileSync(path,'utf8'));
let clock=0;
class FakeDate extends Date { static now(){return clock;} }
const create=()=>{
 const context=createContext({Date:FakeDate,URL,TextEncoder,console:{log(){},warn(){},error(){}},location:{href:'https://courses.example/eams'}});
 context.window=context;
 return context;
};
const invoke=(script,context)=>JSON.parse(runInContext(script,context));
const flush=()=>new Promise(resolve=>setImmediate(resolve));

const state=create();
assert.equal(invoke(normal,state).started,true);await flush();
assert.equal(state.calls,1);assert.equal(state.__CURSIMPLE_SCHEDULE_DRAFT_JSON,undefined);
clock=1000;assert.equal(invoke(normal,state).skipped,true);assert.equal(state.calls,1);
clock=2000;state.hold=true;state.ready=true;
assert.equal(invoke(normal,state).started,true);await flush();
assert.equal(invoke(forced,state).skipped,true);assert.equal(state.calls,2);
state.release();await flush();
assert.equal(JSON.parse(state.__CURSIMPLE_SCHEDULE_DRAFT_JSON).courses.length,1);
assert.equal(invoke(normal,state).skipped,true);assert.equal(state.calls,2);
// A manual retry cannot upload the draft left by the previous execution.
assert.equal(invoke(forced,state).started,true);await flush();
assert.equal(state.__CURSIMPLE_SCHEDULE_DRAFT_JSON,undefined);
assert.equal(state.calls,3);state.release();await flush();
assert.equal(JSON.parse(state.__CURSIMPLE_SCHEDULE_DRAFT_JSON).courses.length,1);

const failure=create();failure.ready=true;failure.fail=true;
assert.equal(invoke(normal,failure).started,true);await flush();
assert.equal(failure.__CURSIMPLE_PLUGIN_RUNTIME_ERROR,'test failure');
failure.fail=false;assert.equal(invoke(forced,failure).started,true);await flush();
assert.equal(JSON.parse(failure.__CURSIMPLE_SCHEDULE_DRAFT_JSON).courses.length,1);

const optedOut=create();
invoke(legacy,optedOut);await flush();clock+=10000;
assert.equal(invoke(legacy,optedOut).skipped,true);assert.equal(optedOut.calls,1);
console.log('Waiting page retry, throttling, concurrent upload, failure recovery and legacy opt-out passed.');
