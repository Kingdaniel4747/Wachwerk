import test from 'node:test';
import assert from 'node:assert/strict';
import { changeHabitResult, calendarState, clampDial, angleDelta, scopeFlag } from '../src/workflow.ts';

test('Habit: offen → geschafft → nicht geschafft → offen; Verlauf bleibt erhalten', () => {
  const old = { completedDates:['2026-08-31'], missedDates:[], text:'Sport' };
  const done = changeHabitResult(old,'2026-09-01',false);
  assert.deepEqual(done.completedDates,['2026-08-31','2026-09-01']);
  const missed = changeHabitResult(done,'2026-09-01',true);
  assert.deepEqual(missed.completedDates,['2026-08-31']);
  assert.deepEqual(missed.missedDates,['2026-09-01']);
  const reset = changeHabitResult(missed,'2026-09-01',true);
  assert.deepEqual(reset.missedDates,[]);
  assert.deepEqual(old.completedDates,['2026-08-31']);
  assert.equal(reset.text,'Sport');
});
test('Ein neuer Tag beginnt offen; ein Misserfolg verändert keine anderen Tage', () => {
  const result=changeHabitResult({completedDates:['2026-08-31'],missedDates:['2026-08-30']},'2026-09-01',true);
  assert.deepEqual(result.missedDates,['2026-08-30','2026-09-01']);
  assert.equal(result.missedDates.includes('2026-09-02'),false);
});
test('Kalender trennt offen, nicht geschafft, vollständig geschafft und Zukunft', () => {
  assert.equal(calendarState('2026-09-01','2026-09-01',true,false,false),'empty');
  assert.equal(calendarState('2026-09-01','2026-09-01',true,false,true),'missed');
  assert.equal(calendarState('2026-09-01','2026-09-01',true,true,false),'complete');
  assert.equal(calendarState('2026-08-31','2026-09-01',true,false,false),'missed');
  assert.equal(calendarState('2026-08-31','2026-09-01',false,false,false),'empty');
  assert.equal(calendarState('2026-09-02','2026-09-01',true,true,false),'future');
});
test('Drehrad überquert Null in beide Richtungen ohne Zahlensprung', () => {
  assert.equal(angleDelta(-179,179),2);
  assert.equal(angleDelta(179,-179),-2);
  assert.equal(angleDelta(42,36),6);
  assert.equal(clampDial(-1,0,1440),0);
  assert.equal(clampDial(1441,1,1440),1440);
  assert.equal(clampDial(3.8,1,99),4);
});
test('Jeder Sperr-Tab verwendet seinen eigenen Aktivierungszustand', () => {
  assert.equal(scopeFlag('instant'),'enabled');
  assert.equal(scopeFlag('limits'),'limitsEnabled');
  assert.equal(scopeFlag('windows'),'windowsEnabled');
});
