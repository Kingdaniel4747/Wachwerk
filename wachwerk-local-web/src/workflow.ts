export type HabitHistory = { completedDates: string[]; missedDates: string[] };
export function changeHabitResult<T extends HabitHistory>(habit: T, date: string, failed: boolean): T {
  const key = failed ? "missedDates" : "completedDates";
  const other = failed ? "completedDates" : "missedDates";
  return { ...habit, [key]: habit[key].includes(date) ? habit[key].filter(d => d !== date) : [...habit[key], date], [other]: habit[other].filter(d => d !== date) };
}
export function calendarState(date: string, today: string, hasEntries: boolean, complete: boolean, failed: boolean) {
  return date > today ? "future" : !hasEntries ? "empty" : complete ? "complete" : failed || date < today ? "missed" : "empty";
}
export function clampDial(value: number, min: number, max: number) { return Math.max(min, Math.min(max, Math.round(value))); }
export function angleDelta(next: number, previous: number) {
  let delta = next - previous;
  if (delta > 180) delta -= 360;
  if (delta < -180) delta += 360;
  return delta;
}
export function scopeFlag(scope: "instant" | "limits" | "windows") { return scope === "limits" ? "limitsEnabled" : scope === "windows" ? "windowsEnabled" : "enabled"; }
