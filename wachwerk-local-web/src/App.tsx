"use client";

import type { FormEvent, PointerEvent as ReactPointerEvent } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useSheetSwipe } from "./useSheetSwipe";
import NumberField from "./NumberField";
import { changeHabitResult, calendarState, scopeFlag } from "./workflow";

type Screen = "home" | "alarms" | "coach" | "todos" | "blocker" | "settings" | "qr";
type CoachTab = "analysis" | "rhythm";
type Challenge = "shake" | "snake" | "hold" | "qr" | "nfc";
type CheckinState = "great" | "late" | "miss";
type StandbyWidget = "clock" | "alarm" | "calendar" | "rhythm" | "cycles" | "sleep" | "todos" | "focus";
type SheetKind = "alarm" | "todo" | "settings" | "choose";
type Todo = { id: string; text: string; done: boolean; createdAt: number; completedAt?: number; reminderAt?: string };
type Habit = { id: string; text: string; completedDates: string[]; missedDates: string[]; createdAt: number };
type Alarm = {
  id: string;
  time: string;
  date: string;
  label: string;
  days: number[];
  challenge: Challenge;
  enabled: boolean;
  gentleWake: boolean;
  gentleMinutes: number;
  sound: string;
  source: "manual" | "cycle";
  createdAt: number;
  qrToken: string;
  nfcToken: string;
  shakeCount: number;
  holdSeconds: number;
  snakeSeconds: number;
  snoozeEnabled: boolean;
  snoozeMinutes: number;
  snoozeAggressive: boolean;
  snoozeMinimumMinutes: number;
  plannedSleep?: string;
};
type PendingWake = {
  eventId: string;
  alarmId: string;
  label: string;
  plannedTime: string;
  firedAt: number;
  snoozes: number;
  plannedSleep?: string;
};
type Checkin = PendingWake & { state: CheckinState; checkedAt: number; plannedSleep: string; sleepMinutes: number };
type Settings = {
  name: string;
  alarmNfcToken: string;
  keySetupDismissed: boolean;
  qrVerified: boolean;
  bedtime: string;
  remindersOn: boolean;
  nagMode: "fixed" | "urgent";
  reminderInterval: number;
  reminderMinimumInterval: number;
  sleepDetectMinutes: number;
  reminderMessage: string;
  morningDelay: number;
  morningBlockEnabled: boolean;
  morningBlockMinutes: number;
  morningBlockPackages: string[];
  defaultChallenge: Challenge;
  gentleWake: boolean;
  gentleMinutes: number;
  sound: string;
  shakeCount: number;
  holdSeconds: number;
  snakeSeconds: number;
  snoozeEnabled: boolean;
  snoozeMinutes: number;
  snoozeAggressive: boolean;
  snoozeMinimumMinutes: number;
  cycleMinutes: number;
  fallAsleepMinutes: number;
  defaultAlarmDay: "today" | "tomorrow";
  appFont: "modern" | "rounded" | "classic";
  palette: "classic" | "solar" | "dusk";
  focusWorkMinutes: number;
  focusBreakMinutes: number;
  focusRounds: number;
  focusPackages: string[];
  focusSilenceNotifications: boolean;
  consistencyMode: "habits" | "todos" | "both";
};
type AlarmDraft = Omit<Alarm, "id" | "createdAt" | "qrToken"> & { id?: string };
type AppWindow = { start: string; end: string };
type BlockerScope = "instant" | "limits" | "windows";
type BlockerMethod = "nfc" | "qr" | "password";
type BlockerState = { methods: Partial<Record<BlockerScope, BlockerMethod>>; hasPasswords: Partial<Record<BlockerScope, boolean>>; enabled: boolean; limitsEnabled: boolean; windowsEnabled: boolean; packages: string[]; nfcToken: string; method: "nfc" | "qr" | "password"; qrToken: string; limits: Record<string, number>; windows: Record<string, AppWindow>; hasPassword: boolean; limitReminderEnabled: boolean; limitReminderMinutes: number };
type InstalledApp = { packageName: string; label: string; icon?: string };
type FocusState = { active: boolean; ringing: boolean; phase: "work" | "break"; workMinutes: number; breakMinutes: number; rounds: number; round: number; endAt: number; silenceNotifications?: boolean };
type PermissionState = { liveSupported?: boolean; liveEnabled?: boolean; notifications: boolean; exact: boolean; fullScreen: boolean; camera: boolean; dnd: boolean };

declare global {
  interface Window {
    WachwerkAndroid?: {
      syncAlarms: (json: string) => void;
      syncBedtime: (enabled: boolean, time: string, interval: number, minimumInterval: number, sleepDetectMinutes: number, mode: string, message: string) => void;
      syncTodos?: (json: string) => void;
      syncSettings?: (json: string) => void;
      getNativeState?: () => string;
      openRingingAlarm?: () => void;
      getAlarmPermissionState?: () => string;
      completeMorningCheck?: (eventId: string, state: string) => void;
      enterStandby?: () => void;
      exitStandby?: () => void;
      getQrMatrix?: (content: string) => string;
      printCurrentPage?: () => void;
      chooseAlarmSound?: () => void;
      previewAlarmSound?: (sound: string) => void;
      stopAlarmSoundPreview?: () => void;
      enrollNfcTag?: (purpose: string) => void;
      scanBlockerTag?: (token: string, scope: string) => void;
      scanBlockerQr?: (token: string, scope: string) => void;
      getWakeKeyState?: () => string;
      verifyAlarmQr?: () => void;
      hasNfc?: () => boolean;
      requestCameraPermission?: () => void;
      openExactAlarmSettings?: () => void;
      openFullScreenSettings?: () => void;
      openLiveNotificationSettings?: () => void;
      openNotificationSettings?: () => void;
      openNotificationPolicySettings?: () => void;
      getInstalledApps?: () => string;
      getBlockerState?: () => string;
      getAppUsage?: () => string;
      getFocusTimerState?: () => string;
      startFocusTimer?: (workMinutes: number, breakMinutes: number, rounds: number, packagesJson: string, silenceNotifications: boolean) => void;
      cancelFocusTimer?: () => void;
      syncAppBlocker?: (json: string) => void;
      setBlockerPassword?: (password: string, scope: string) => boolean;
      toggleBlockerPassword?: (password: string, scope: string) => boolean;
      isAccessibilityEnabled?: () => boolean;
      hasUsageAccess?: () => boolean;
      openAccessibilitySettings?: () => void;
      openUsageAccessSettings?: () => void;
    };
  }
}

const STORAGE_KEY = "wachwerk-local-v3";
const SCHEMA_KEY = "wachwerk-schema";
const initialSettings: Settings = {
  name: "",
  alarmNfcToken: "",
  keySetupDismissed: false,
  qrVerified: false,
  bedtime: "22:00",
  remindersOn: false,
  nagMode: "fixed",
  reminderInterval: 5,
  reminderMinimumInterval: 1,
  sleepDetectMinutes: 60,
  reminderMessage: "Zeit, das Handy wegzulegen und schlafen zu gehen.",
  morningDelay: 60,
  morningBlockEnabled: false,
  morningBlockMinutes: 20,
  morningBlockPackages: [],
  defaultChallenge: "shake",
  gentleWake: false,
  gentleMinutes: 15,
  sound: "Systemstandard",
  shakeCount: 12,
  holdSeconds: 8,
  snakeSeconds: 10,
  snoozeEnabled: true,
  snoozeMinutes: 10,
  snoozeAggressive: false,
  snoozeMinimumMinutes: 1,
  cycleMinutes: 90,
  fallAsleepMinutes: 15,
  defaultAlarmDay: "tomorrow",
  appFont: "modern",
  palette: "classic",
  focusWorkMinutes: 45,
  focusBreakMinutes: 5,
  focusRounds: 1,
  focusPackages: [],
  focusSilenceNotifications: false,
  consistencyMode: "habits",
};
const navItems: { id: Screen; icon: string; label: string }[] = [
  { id: "home", icon: "⌂", label: "Start" },
  { id: "alarms", icon: "◴", label: "Wecker" },
  { id: "coach", icon: "⌁", label: "Coach" },
  { id: "todos", icon: "✓", label: "To-dos" },
  { id: "blocker", icon: "◈", label: "Blocker" },
];
const weekdayNames = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"];
const challengeNames: Record<Challenge, string> = {
  shake: "Handy schütteln",
  snake: "Schlange verfolgen",
  hold: "Display halten",
  qr: "QR-Code scannen",
  nfc: "NFC-Tag scannen",
};
const standbyWidgetNames: Record<StandbyWidget, string> = {
  clock: "Uhr",
  alarm: "Nächster Wecker",
  calendar: "Kalender",
  rhythm: "Aufsteh-Erfolg",
  cycles: "Schlafbedarf",
  sleep: "Schlafenszeit",
  todos: "Aufgaben",
  focus: "Fokus-Timer",
};

function uid(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
function pad(value: number) { return String(value).padStart(2, "0"); }
function localDate(date = new Date()) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`; }
function tomorrow() { const date = new Date(); date.setDate(date.getDate() + 1); return localDate(date); }
function localDateTimeValue(date = new Date()) { return `${localDate(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`; }
function formatMinutes(total: number) {
  const normalized = ((Math.round(total) % 1440) + 1440) % 1440;
  return `${pad(Math.floor(normalized / 60))}:${pad(normalized % 60)}`;
}
function timeToMinutes(value: string) {
  const [hours, minutes] = value.split(":").map(Number);
  return (Number.isFinite(hours) ? hours : 0) * 60 + (Number.isFinite(minutes) ? minutes : 0);
}
function durationBetween(start: string, end: string) {
  let minutes = timeToMinutes(end) - timeToMinutes(start);
  if (minutes <= 0) minutes += 1440;
  return minutes;
}
function formatDuration(minutes: number) {
  if (!minutes) return "–";
  const hours = Math.floor(minutes / 60);
  const rest = Math.round(minutes % 60);
  return `${hours}h ${pad(rest)}`;
}
function soundLabel(sound: string) {
  return sound.startsWith("custom:") ? sound.slice(7).replace(/^[0-9]+-/, "") : sound;
}
function formatCountdown(milliseconds: number) {
  const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
  return `${pad(Math.floor(seconds / 60))}:${pad(seconds % 60)}`;
}
function isStandbyWidget(value: unknown): value is StandbyWidget {
  return typeof value === "string" && value in standbyWidgetNames;
}
function formatDays(alarm: Alarm | AlarmDraft) {
  if (!alarm.days.length) return alarm.date ? new Date(`${alarm.date}T12:00:00`).toLocaleDateString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit" }) : "Einmalig";
  if (alarm.days.join(",") === "1,2,3,4,5") return "Mo – Fr";
  if (alarm.days.join(",") === "0,6") return "Sa · So";
  return alarm.days.map(day => weekdayNames[day]).join(" · ");
}
function nextOccurrence(alarm: Alarm, from = new Date()) {
  if (!alarm.enabled) return null;
  const [hour, minute] = alarm.time.split(":").map(Number);
  if (!alarm.days.length) {
    const date = new Date(`${alarm.date || localDate(from)}T${alarm.time}:00`);
    return date.getTime() > from.getTime() ? date : null;
  }
  for (let offset = 0; offset < 8; offset++) {
    const candidate = new Date(from);
    candidate.setDate(candidate.getDate() + offset);
    candidate.setHours(hour, minute, 0, 0);
    if (candidate.getTime() > from.getTime() && alarm.days.includes(candidate.getDay())) return candidate;
  }
  return null;
}
function defaultDraft(settings: Settings): AlarmDraft {
  return {
    time: "07:00", date: settings.defaultAlarmDay === "today" ? localDate() : tomorrow(), label: "Aufstehen", days: [], challenge: settings.defaultChallenge,
    enabled: true, gentleWake: settings.gentleWake, gentleMinutes: settings.gentleMinutes,
    sound: settings.sound, source: "manual", nfcToken: settings.alarmNfcToken, shakeCount: settings.shakeCount,
    holdSeconds: settings.holdSeconds, snakeSeconds: settings.snakeSeconds,
    snoozeEnabled: settings.snoozeEnabled, snoozeMinutes: settings.snoozeMinutes,
    snoozeAggressive: settings.snoozeAggressive, snoozeMinimumMinutes: settings.snoozeMinimumMinutes,
  };
}
function qrCell(row: number, col: number, seed: string) {
  const finder = (top: number, left: number) => {
    const r = row - top, c = col - left;
    if (r < 0 || c < 0 || r > 6 || c > 6) return false;
    return r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4);
  };
  if (finder(0, 0) || finder(0, 14) || finder(14, 0)) return true;
  const salt = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return (row * 7 + col * 11 + row * col * 3 + salt) % 13 < 5;
}
function Toggle({ on, onClick, label }: { on: boolean; onClick: () => void; label: string }) {
  return <button type="button" aria-label={label} aria-pressed={on} className={`toggle ${on ? "on" : ""}`} onClick={event => { event.stopPropagation(); onClick(); }}><span /></button>;
}
function TrashIcon() { return <svg aria-hidden="true" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 10v7M14 10v7" /></svg>; }
function ScreenHeader({ eyebrow, title, action, onAction }: { eyebrow: string; title: string; action?: string; onAction?: () => void }) {
  return <header className="screen-header"><div><span className="overline">{eyebrow}</span><h2>{title}</h2></div>{action && <button type="button" className="round-action" onClick={onAction} aria-label={action}>{action}</button>}</header>;
}
function QRCode({ seed = "wachwerk", matrix }: { seed?: string; matrix?: { size: number; bits: string } | null }) {
  const size = matrix?.size || 21;
  return <div className="qr-code" style={{ gridTemplateColumns: `repeat(${size}, 1fr)` }} aria-label="Persönlicher Wachwerk-QR-Code">{Array.from({ length: size * size }, (_, index) => {
    const row = Math.floor(index / size), col = index % size;
    const dark = matrix?.bits ? matrix.bits[index] === "1" : qrCell(row, col, seed);
    return <i key={index} className={dark ? "dark-cell" : ""} />;
  })}</div>;
}
function AppIcon({ app, compact = false }: { app: InstalledApp; compact?: boolean }) {
  return app.icon
    ? <img className={`app-icon ${compact ? "compact" : ""}`} src={app.icon} alt="" />
    : <span className={`app-letter ${compact ? "compact" : ""}`}>{app.label.slice(0, 1).toUpperCase()}</span>;
}
function TimePicker({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label className="time-picker"><span aria-hidden="true">{value}</span><i aria-hidden="true">◷</i><input className="time-native-input" aria-label={label} type="time" required value={value} onChange={event => onChange(event.target.value)} /></label>;
}
export default function App() {
  const [screen, setScreen] = useState<Screen>("home");
  const [coachTab, setCoachTab] = useState<CoachTab>("analysis");
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [todos, setTodos] = useState<Todo[]>([]);
  const [habits, setHabits] = useState<Habit[]>([]);
  const [checkins, setCheckins] = useState<Checkin[]>([]);
  const [pendingWake, setPendingWake] = useState<PendingWake | null>(null);
  const [settings, setSettings] = useState<Settings>(initialSettings);
  const [todoTab, setTodoTab] = useState<"open" | "done" | "habits">("open");
  const [todoModal, setTodoModal] = useState(false);
  const [chooseTodo, setChooseTodo] = useState(false);
  const [todoModalMode, setTodoModalMode] = useState<"todo" | "habit">("todo");
  const [todoText, setTodoText] = useState("");
  const [todoReminder, setTodoReminder] = useState("");
  const [alarmDraft, setAlarmDraft] = useState<AlarmDraft | null>(null);
  const [cycleMode, setCycleMode] = useState<"wake" | "sleep">("wake");
  const [cycleTime, setCycleTime] = useState("07:00");
  const [selectedCycle, setSelectedCycle] = useState(5);
  const [nightMode, setNightMode] = useState(false);
  const [isLandscape, setIsLandscape] = useState(() => window.matchMedia("(orientation: landscape)").matches);
  const [standbyEditing, setStandbyEditing] = useState<"left" | "right" | null>(null);
  const [standbyClock, setStandbyClock] = useState<"digital" | "analog">("digital");
  const [standbyFont, setStandbyFont] = useState<"apple" | "soft" | "mono">("apple");
  const [standbyTone, setStandbyTone] = useState<"blue" | "amber" | "mint" | "rose">("blue");
  const [standbyLeft, setStandbyLeft] = useState<StandbyWidget>("clock");
  const [standbyRight, setStandbyRight] = useState<StandbyWidget>("calendar");
  const [now, setNow] = useState(new Date());
  const [hydrated, setHydrated] = useState(false);
  const [toast, setToast] = useState("");
  const [alarmSlide, setAlarmSlide] = useState(0);
  const [nativeCompleted, setNativeCompleted] = useState<string[]>([]);
  const [qrMatrix, setQrMatrix] = useState<{ size: number; bits: string } | null>(null);
  const [sheetClosing, setSheetClosing] = useState(false);
  const [blocker, setBlocker] = useState<BlockerState>({ methods: {}, hasPasswords: {}, enabled: false, limitsEnabled: false, windowsEnabled: false, packages: [], nfcToken: "", method: "nfc", qrToken: "wachwerk-personal-code", limits: {}, windows: {}, hasPassword: false, limitReminderEnabled: false, limitReminderMinutes: 10 });
  const [blockerPassword, setBlockerPassword] = useState("");
  const [focusState, setFocusState] = useState<FocusState>({ active: false, ringing: false, phase: "work", workMinutes: 45, breakMinutes: 5, rounds: 1, round: 1, endAt: 0 });
  const [permissions, setPermissions] = useState<PermissionState>({ notifications: true, exact: true, fullScreen: true, camera: true, dnd: false });
  const [installedApps, setInstalledApps] = useState<InstalledApp[]>([]);
  const [appSearch, setAppSearch] = useState("");
  const [focusAppSearch, setFocusAppSearch] = useState("");
  const [blockerTab, setBlockerTab] = useState<"instant" | "limits" | "windows">("instant");
  const [morningTab, setMorningTab] = useState(false);
  const [morningBlock, setMorningBlock] = useState<{until: number; packages: string[]}>({until: 0, packages: []});
  const [alarmRinging, setAlarmRinging] = useState(false);
  const [appUsage, setAppUsage] = useState<Record<string, number>>({});
  const [accessibilityEnabled, setAccessibilityEnabled] = useState(false);
  const [usageAccessEnabled, setUsageAccessEnabled] = useState(false);
  const [soundPreviewing, setSoundPreviewing] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const nfcTargetRef = useRef<"alarm" | "blocker">("alarm");
  const standbyPressRef = useRef<{ timer: number | null; long: boolean }>({ timer: null, long: false });

  useSheetSwipe(Boolean(alarmDraft || todoModal || chooseTodo || screen === "settings"), '.modal-sheet[data-sheet-kind]:not([data-sheet-kind="number"])', sheet => closeSheet(sheet.dataset.sheetKind as SheetKind));
  useEffect(() => { document.documentElement.dataset.palette = settings.palette || "classic"; }, [settings.palette]);
  const applyNativeState = (raw?: string | null) => {
    if (!raw) return;
    try {
      const state = JSON.parse(raw) as { pendingWake?: PendingWake | null; completedOneTimeIds?: string[]; openMorningCheck?: boolean; morningBlock?: {until: number; packages: string[]}; alarmRinging?: boolean };
      if (state.morningBlock) setMorningBlock(state.morningBlock);
      setAlarmRinging(Boolean(state.alarmRinging));
      if (state.pendingWake) { setPendingWake(state.pendingWake); if (state.openMorningCheck) setScreen("home"); }
      if (state.completedOneTimeIds?.length) setNativeCompleted(state.completedOneTimeIds);
    } catch { /* A malformed platform state must never block the local UI. */ }
  };

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    try {
      if (window.localStorage.getItem(SCHEMA_KEY) !== "3") {
        window.localStorage.clear();
        window.localStorage.setItem(SCHEMA_KEY, "3");
      }
      const saved = window.localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const data = JSON.parse(saved) as Partial<{ alarms: Alarm[]; todos: Todo[]; habits: Habit[]; checkins: Checkin[]; settings: Settings; blocker: BlockerState; standby: { clock: "digital" | "analog"; font?: "apple" | "soft" | "mono"; tone: "blue" | "amber" | "mint" | "rose"; left: StandbyWidget; right: StandbyWidget } }>;
        const mergedSettings = { ...initialSettings, ...(data.settings ?? {}) };
        if (!mergedSettings.alarmNfcToken) mergedSettings.alarmNfcToken = data.alarms?.find(alarm => alarm.nfcToken)?.nfcToken || data.blocker?.nfcToken || "";
        setSettings(mergedSettings);
        setAlarms((data.alarms ?? []).map(alarm => ({ ...alarm, nfcToken: alarm.nfcToken ?? "", shakeCount: alarm.shakeCount ?? mergedSettings.shakeCount,
          holdSeconds: alarm.holdSeconds ?? mergedSettings.holdSeconds, snakeSeconds: alarm.snakeSeconds ?? mergedSettings.snakeSeconds,
          snoozeEnabled: alarm.snoozeEnabled ?? mergedSettings.snoozeEnabled, snoozeMinutes: alarm.snoozeMinutes ?? mergedSettings.snoozeMinutes,
          snoozeAggressive: alarm.snoozeAggressive ?? mergedSettings.snoozeAggressive,
          snoozeMinimumMinutes: alarm.snoozeMinimumMinutes ?? mergedSettings.snoozeMinimumMinutes })));
        setTodos((data.todos ?? []).map(todo => ({ ...todo, reminderAt: todo.reminderAt ?? "", completedAt: todo.completedAt ?? (todo.done ? todo.createdAt : undefined) }))); setHabits((data.habits ?? []).map(habit => ({ ...habit, completedDates: habit.completedDates ?? [], missedDates: habit.missedDates ?? [] }))); setCheckins(data.checkins ?? []);
        if (data.blocker) setBlocker(current => ({ ...current, ...data.blocker, limits: data.blocker!.limits ?? {}, windows: data.blocker!.windows ?? {} }));
        if (data.standby) {
          setStandbyClock(data.standby.clock); setStandbyTone(data.standby.tone);
          if (data.standby.font) setStandbyFont(data.standby.font);
          if (isStandbyWidget(data.standby.left)) setStandbyLeft(data.standby.left);
          if (isStandbyWidget(data.standby.right)) setStandbyRight(data.standby.right);
        }
      }
      const wakeKey = window.WachwerkAndroid?.getWakeKeyState?.();
      if (wakeKey) { const key = JSON.parse(wakeKey); setSettings(current => ({ ...current, alarmNfcToken: key.nfcToken || current.alarmNfcToken, qrVerified: key.qrVerified || current.qrVerified })); }
      applyNativeState(window.WachwerkAndroid?.getNativeState?.());
      const nativeBlocker = window.WachwerkAndroid?.getBlockerState?.();
      if (nativeBlocker) setBlocker(current => ({ ...current, ...(JSON.parse(nativeBlocker) as Partial<BlockerState>) }));
      const nativeFocus = window.WachwerkAndroid?.getFocusTimerState?.();
      if (nativeFocus) setFocusState(current => ({ ...current, ...(JSON.parse(nativeFocus) as Partial<FocusState>) }));
      const nativePermissions = window.WachwerkAndroid?.getAlarmPermissionState?.();
      if (nativePermissions) setPermissions(current => ({ ...current, ...(JSON.parse(nativePermissions) as Partial<PermissionState>) }));
      const nativeApps = window.WachwerkAndroid?.getInstalledApps?.();
      if (nativeApps) setInstalledApps(JSON.parse(nativeApps) as InstalledApp[]);
    } catch { /* Fresh state remains usable if device storage was corrupted. */ }
    setHydrated(true);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => applyNativeState(JSON.stringify((event as CustomEvent).detail));
    window.addEventListener("wachwerk-native-state", listener);
    return () => window.removeEventListener("wachwerk-native-state", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<{ id?: string; name?: string }>).detail;
      if (!detail?.id) return;
      setSettings(current => ({ ...current, sound: detail.id! }));
      showToast(`Alarmton „${detail.name || soundLabel(detail.id)}“ ausgewählt`);
    };
    window.addEventListener("wachwerk-custom-sound", listener);
    return () => window.removeEventListener("wachwerk-custom-sound", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<{ purpose?: string; token?: string; label?: string }>).detail;
      if (!detail?.token) return;
      setSettings(current => ({ ...current, alarmNfcToken: detail.token!, keySetupDismissed: true }));
      const target = detail.purpose === "blocker" ? "blocker" : nfcTargetRef.current;
      if (target === "alarm") setAlarmDraft(current => current ? { ...current, challenge: "nfc", nfcToken: detail.token! } : current);
      else setBlocker(current => ({ ...current, nfcToken: detail.token! }));
      showToast(detail.label || "NFC-Tag erfolgreich angelernt");
    };
    window.addEventListener("wachwerk-nfc-enrolled", listener);
    return () => window.removeEventListener("wachwerk-nfc-enrolled", listener);
  }, []);
  useEffect(() => {
    const listener = () => setSettings(current => ({ ...current, qrVerified: true }));
    window.addEventListener("wachwerk-qr-verified", listener);
    return () => window.removeEventListener("wachwerk-qr-verified", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<Partial<BlockerState>>).detail;
      setBlocker(current => ({ ...current, ...detail }));
      if (detail.nfcToken) setSettings(current => current.alarmNfcToken ? current : ({ ...current, alarmNfcToken: detail.nfcToken! }));
    };
    window.addEventListener("wachwerk-blocker-state", listener);
    return () => window.removeEventListener("wachwerk-blocker-state", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => setFocusState(current => ({ ...current, ...(event as CustomEvent<Partial<FocusState>>).detail }));
    window.addEventListener("wachwerk-focus-state", listener);
    return () => window.removeEventListener("wachwerk-focus-state", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => setPermissions(current => ({ ...current, ...(event as CustomEvent<Partial<PermissionState>>).detail }));
    window.addEventListener("wachwerk-permission-state", listener);
    return () => window.removeEventListener("wachwerk-permission-state", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => setInstalledApps((event as CustomEvent<InstalledApp[]>).detail ?? []);
    window.addEventListener("wachwerk-installed-apps", listener);
    return () => window.removeEventListener("wachwerk-installed-apps", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<{ enabled?: boolean; usageEnabled?: boolean }>).detail;
      setAccessibilityEnabled(Boolean(detail?.enabled)); setUsageAccessEnabled(Boolean(detail?.usageEnabled));
    };
    window.addEventListener("wachwerk-accessibility-state", listener);
    return () => window.removeEventListener("wachwerk-accessibility-state", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => setSoundPreviewing(Boolean((event as CustomEvent<{ playing?: boolean }>).detail?.playing));
    window.addEventListener("wachwerk-sound-preview", listener);
    return () => window.removeEventListener("wachwerk-sound-preview", listener);
  }, []);
  useEffect(() => {
    const listener = (event: Event) => {
      const requested = (event as CustomEvent<{ screen?: Screen }>).detail?.screen;
      if (requested && navItems.some(item => item.id === requested)) setScreen(requested);
    };
    window.addEventListener("wachwerk-open-screen", listener);
    return () => window.removeEventListener("wachwerk-open-screen", listener);
  }, []);
  useEffect(() => {
    if (!hydrated) return;
    const today = localDate();
    setAlarms(current => current.filter(alarm => alarm.days.length || (!nativeCompleted.includes(alarm.id) && (!alarm.date || alarm.date >= today))));
  }, [hydrated, nativeCompleted]);
  useEffect(() => {
    if (!hydrated) return;
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ alarms, todos, habits, checkins, settings, blocker, standby: { clock: standbyClock, font: standbyFont, tone: standbyTone, left: standbyLeft, right: standbyRight } }));
    try { window.WachwerkAndroid?.syncAlarms(JSON.stringify(alarms)); } catch { /* Browser preview */ }
    try { window.WachwerkAndroid?.syncBedtime(settings.remindersOn, settings.bedtime, settings.reminderInterval, settings.reminderMinimumInterval, settings.sleepDetectMinutes, settings.nagMode, settings.reminderMessage); } catch { /* Browser preview */ }
    try { window.WachwerkAndroid?.syncTodos?.(JSON.stringify(todos)); } catch { /* Browser preview */ }
    try { window.WachwerkAndroid?.syncSettings?.(JSON.stringify(settings)); } catch { /* Browser preview */ }
    try { window.WachwerkAndroid?.syncAppBlocker?.(JSON.stringify(blocker)); } catch { /* Browser preview */ }
  }, [hydrated, alarms, todos, habits, checkins, settings, blocker, standbyClock, standbyFont, standbyTone, standbyLeft, standbyRight]);
  useEffect(() => { scrollRef.current?.scrollTo({ top: 0, behavior: "smooth" }); }, [screen, coachTab]);
  useEffect(() => {
    const orientation = window.matchMedia("(orientation: landscape)");
    const update = () => setIsLandscape(orientation.matches);
    update(); orientation.addEventListener("change", update);
    return () => orientation.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    if (screen !== "qr" && alarmDraft?.challenge !== "qr") return;
    const token = "wachwerk-personal-code";
    try {
      const matrix = window.WachwerkAndroid?.getQrMatrix?.(token);
      if (matrix) setQrMatrix(JSON.parse(matrix) as { size: number; bits: string });
    } catch { setQrMatrix(null); }
  }, [screen, alarmDraft?.challenge]);
  useEffect(() => {
    if (screen !== "blocker") return;
    try {
      const apps = window.WachwerkAndroid?.getInstalledApps?.();
      if (apps) setInstalledApps(JSON.parse(apps) as InstalledApp[]);
      setAccessibilityEnabled(window.WachwerkAndroid?.isAccessibilityEnabled?.() ?? false);
      setUsageAccessEnabled(window.WachwerkAndroid?.hasUsageAccess?.() ?? false);
    } catch { setInstalledApps([]); }
    const refreshUsage = () => {
      try {
        const usage = window.WachwerkAndroid?.getAppUsage?.();
        if (usage) setAppUsage(JSON.parse(usage) as Record<string, number>);
      } catch { setAppUsage({}); }
    };
    refreshUsage();
    const timer = window.setInterval(refreshUsage, 5_000);
    return () => window.clearInterval(timer);
  }, [screen]);
  useEffect(() => {
    if (!isLandscape || nightMode || alarmDraft || todoModal || chooseTodo || screen === "settings") return;
    const enterWhenIdle = () => { if (document.querySelector(".modal-backdrop")) { timer = window.setTimeout(enterWhenIdle, 20_000); return; } setNightMode(true); };
    let timer = window.setTimeout(enterWhenIdle, 20_000);
    const reset = () => { window.clearTimeout(timer); timer = window.setTimeout(enterWhenIdle, 20_000); };
    document.addEventListener("pointerdown", reset, true); document.addEventListener("keydown", reset, true);
    return () => { window.clearTimeout(timer); document.removeEventListener("pointerdown", reset, true); document.removeEventListener("keydown", reset, true); };
  }, [nightMode, isLandscape, alarmDraft, todoModal, chooseTodo, screen]);

  useEffect(() => {
    if (!focusState.active || !window.WachwerkAndroid?.getFocusTimerState) return;
    const refresh = () => {
      try {
        const next = JSON.parse(window.WachwerkAndroid!.getFocusTimerState!()) as FocusState;
        setFocusState(current => JSON.stringify(current) === JSON.stringify(next) ? current : next);
      } catch { /* Keep the last confirmed native timer during an interrupted read. */ }
    };
    const timer = window.setInterval(refresh, 1000);
    return () => window.clearInterval(timer);
  }, [focusState.active]);

  const upcoming = useMemo(() => alarms.map(alarm => ({ alarm, date: nextOccurrence(alarm, now) })).filter(item => item.date).sort((a, b) => itemTime(a.date) - itemTime(b.date)), [alarms, now]);
  const analysis = useMemo(() => {
    if (!checkins.length) return { need: 450, success: 0, confidence: 0, direct: 0, late: 0, miss: 0, averageSnoozes: 0, spread: 0 };
    const recent = checkins.slice(-21);
    const samples = recent.map((entry, index) => ({
      value: Math.max(330, Math.min(660, entry.sleepMinutes + (entry.state === "miss" ? 35 : entry.state === "late" ? 15 : 0) + entry.snoozes * 4)),
      weight: 1 + index / Math.max(1, recent.length - 1),
    }));
    const weight = samples.reduce((sum, sample) => sum + sample.weight, 0);
    const rawNeed = samples.reduce((sum, sample) => sum + sample.value * sample.weight, 0) / weight;
    const need = Math.round(rawNeed / 5) * 5;
    const spread = Math.sqrt(samples.reduce((sum, sample) => sum + Math.pow(sample.value - rawNeed, 2), 0) / samples.length);
    const direct = checkins.filter(entry => entry.state === "great").length, late = checkins.filter(entry => entry.state === "late").length, miss = checkins.filter(entry => entry.state === "miss").length;
    const sampleConfidence = Math.min(90, recent.length / 14 * 90);
    const consistency = Math.max(35, 100 - spread * 1.4);
    return { need, success: Math.round(direct / checkins.length * 100), confidence: Math.round(sampleConfidence * .75 + consistency * .25), direct, late, miss, averageSnoozes: checkins.reduce((sum, entry) => sum + entry.snoozes, 0) / checkins.length, spread };
  }, [checkins]);
  const learnedBedtime = formatMinutes(timeToMinutes(cycleTime) - analysis.need - settings.fallAsleepMinutes);
  const cycleSuggestions = useMemo(() => {
    const base = cycleMode === "sleep" ? now.getHours() * 60 + now.getMinutes() : timeToMinutes(cycleTime);
    return [4, 5, 6].map(cycles => ({ cycles, time: formatMinutes(cycleMode === "sleep" ? base + cycles * settings.cycleMinutes + settings.fallAsleepMinutes : base - cycles * settings.cycleMinutes - settings.fallAsleepMinutes) }));
  }, [cycleMode, cycleTime, now, settings.cycleMinutes, settings.fallAsleepMinutes]);
  const visibleTodos = todoTab === "habits" ? [] : todos.filter(todo => todo.done === (todoTab === "done"));
  const filteredApps = installedApps.filter(app => `${app.label} ${app.packageName}`.toLocaleLowerCase("de-DE").includes(appSearch.trim().toLocaleLowerCase("de-DE")));

  function itemTime(date: Date | null) { return date?.getTime() ?? Number.MAX_SAFE_INTEGER; }
  function showToast(message: string) { setToast(message); window.setTimeout(() => setToast(""), 2400); }
  function resetSheet() { setSheetClosing(false); }
  function openNewAlarm(time = "07:00", source: "manual" | "cycle" = "manual") { resetSheet(); setAlarmDraft({ ...defaultDraft(settings), time, source }); }
  function closeSheet(kind: SheetKind) {
    if (sheetClosing) return;
    setSheetClosing(true);
    window.setTimeout(() => {
      if (kind === "alarm") setAlarmDraft(null);
      else if (kind === "settings") setScreen("home");
      else if (kind === "choose") setChooseTodo(false);
      else setTodoModal(false);
      resetSheet();
    }, 220);
  }
  function chooseAlarmSound() {
    if (window.WachwerkAndroid?.chooseAlarmSound) window.WachwerkAndroid.chooseAlarmSound();
    else showToast("Eigene Audiodateien können in der APK ausgewählt werden");
  }
  function previewAlarmSound() {
    if (soundPreviewing) {
      window.WachwerkAndroid?.stopAlarmSoundPreview?.();
      setSoundPreviewing(false);
      showToast("Tonvorschau beendet");
      return;
    }
    if (window.WachwerkAndroid?.previewAlarmSound) window.WachwerkAndroid.previewAlarmSound(settings.sound);
    else window.setTimeout(() => setSoundPreviewing(false), 3_000);
    setSoundPreviewing(true);
  }
  function enrollNfc(target: "alarm" | "blocker") {
    nfcTargetRef.current = target;
    if (window.WachwerkAndroid?.enrollNfcTag) window.WachwerkAndroid.enrollNfcTag(target);
    else showToast("NFC-Tags können in der installierten APK angelernt werden");
  }
  function toggleBlockerWithKey() {
    const scope = blockerTab;
    const flag = scopeFlag(scope);
    const count = scope === "limits" ? Object.keys(blocker.limits).length : scope === "windows" ? Object.keys(blocker.windows).length : blocker.packages.length;
    if (!blocker[flag] && !accessibilityEnabled && window.WachwerkAndroid) { window.WachwerkAndroid.openAccessibilitySettings?.(); showToast("Erlaube zuerst den App-Blocker-Zugriff"); return; }
    if (scope === "limits" && !usageAccessEnabled && window.WachwerkAndroid) { window.WachwerkAndroid.openUsageAccessSettings?.(); return; }
    if (!blocker[flag] && !count) { showToast("Wähle zuerst mindestens eine App aus"); return; }
    if ((blocker.methods[blockerTab] ?? blocker.method) === "nfc") {
      if (window.WachwerkAndroid?.scanBlockerTag) window.WachwerkAndroid.scanBlockerTag(blocker.nfcToken, scope);
      else setBlocker(current => ({ ...current, [flag]: !current[flag] }));
      return;
    }
    if ((blocker.methods[blockerTab] ?? blocker.method) === "qr") {
      if (window.WachwerkAndroid?.scanBlockerQr) window.WachwerkAndroid.scanBlockerQr(blocker.qrToken, scope);
      else setBlocker(current => ({ ...current, [flag]: !current[flag] }));
      return;
    }
    if (!(blocker.hasPasswords[blockerTab] ?? blocker.hasPassword)) { showToast("Lege zuerst ein Passwort fest"); return; }
    if (!blockerPassword) { showToast("Gib dein Passwort ein"); return; }
    const accepted = window.WachwerkAndroid?.toggleBlockerPassword
      ? window.WachwerkAndroid.toggleBlockerPassword(blockerPassword, scope) : true;
    if (!accepted) { setBlockerPassword(""); showToast("Passwort ist falsch"); return; }
    setBlocker(current => ({ ...current, [flag]: !current[flag] }));
    setBlockerPassword(""); showToast(blocker[flag] ? "Sperre aufgehoben" : "Sperre aktiviert");
  }
  function saveBlockerPassword() {
    if (blocker[scopeFlag(blockerTab)]) { showToast("Entsperre erst diesen Bereich, bevor du seinen Schlüssel änderst"); return; }
    if (blockerPassword.length < 4) { showToast("Das Passwort braucht mindestens 4 Zeichen"); return; }
    const saved = window.WachwerkAndroid?.setBlockerPassword ? window.WachwerkAndroid.setBlockerPassword(blockerPassword, blockerTab) : true;
    if (!saved) { showToast("Passwort konnte nicht gespeichert werden"); return; }
    setBlocker(current => ({ ...current, hasPasswords: { ...current.hasPasswords, [blockerTab]: true } })); setBlockerPassword(""); showToast("Passwort lokal gespeichert");
  }
  function startFocusTimer() {
    if (settings.focusPackages.length && !accessibilityEnabled) { window.WachwerkAndroid?.openAccessibilitySettings?.(); showToast("Aktiviere zuerst den App-Blocker-Zugriff"); return; }
    if (settings.focusSilenceNotifications && !permissions.dnd) { window.WachwerkAndroid?.openNotificationPolicySettings?.(); showToast("Erlaube zuerst den Nicht-stören-Zugriff"); return; }
    window.WachwerkAndroid?.startFocusTimer?.(settings.focusWorkMinutes, settings.focusBreakMinutes, settings.focusRounds, JSON.stringify(settings.focusPackages), settings.focusSilenceNotifications);
    setFocusState({ active: true, ringing: false, phase: "work", workMinutes: settings.focusWorkMinutes, breakMinutes: settings.focusBreakMinutes, rounds: settings.focusRounds, round: 1, endAt: Date.now() + settings.focusWorkMinutes * 60_000, silenceNotifications: settings.focusSilenceNotifications });
    enterStandby();
  }
  function cancelFocusTimer() {
    window.WachwerkAndroid?.cancelFocusTimer?.();
    setFocusState(current => ({ ...current, active: false, ringing: false, endAt: 0 })); showToast("Fokus-Timer beendet");
  }
  function saveAlarm(event: FormEvent) {
    event.preventDefault(); if (!alarmDraft) return;
    if (alarmDraft.challenge === "nfc" && !alarmDraft.nfcToken) { showToast("Bitte zuerst einen NFC-Tag anlernen"); return; }
    if (!alarmDraft.days.length && new Date(`${alarmDraft.date}T${alarmDraft.time}:00`).getTime() <= Date.now()) { showToast("Diese Uhrzeit liegt bereits in der Vergangenheit"); return; }
    const saved: Alarm = { ...alarmDraft, id: alarmDraft.id ?? uid("alarm"), createdAt: Date.now(), qrToken: "wachwerk-personal-code" };
    setAlarms(current => alarmDraft.id ? current.map(item => item.id === alarmDraft.id ? saved : item) : [...current, saved]);
    setAlarmDraft(null); showToast(alarmDraft.id ? "Wecker aktualisiert" : `Wecker für ${saved.time} gespeichert`);
  }
  function deleteAlarm(id: string) { setAlarms(current => current.filter(alarm => alarm.id !== id)); setAlarmDraft(null); showToast("Wecker gelöscht"); }
  function addTodo(event: FormEvent) {
    event.preventDefault();
    const text = todoText.trim();
    if (!text) return;
    if (todoModalMode === "habit") {
      setHabits(current => [{ id: uid("habit"), text, completedDates: [], missedDates: [], createdAt: Date.now() }, ...current]);
      showToast("Habit hinzugefügt");
    } else {
      setTodos(current => [{ id: uid("todo"), text, done: false, createdAt: Date.now(), reminderAt: todoReminder }, ...current]);
      showToast(todoReminder ? "Aufgabe mit Erinnerung hinzugefügt" : "Aufgabe hinzugefügt");
    }
    setTodoText(""); setTodoReminder(""); setTodoModal(false);
  }
  function openTodoComposer(mode: "todo" | "habit") { resetSheet(); setTodoText(""); setTodoReminder(""); setTodoModalMode(mode); setTodoModal(true); }
  function toggleHabit(id: string, failed = false) {
    const today = localDate();
    setHabits(current => current.map(habit => habit.id !== id ? habit : changeHabitResult(habit, today, failed)));
  }
  function habitStreak(habit: Habit) {
    let streak = 0;
    const cursor = new Date();
    while (habit.completedDates.includes(localDate(cursor))) { streak++; cursor.setDate(cursor.getDate() - 1); }
    return streak;
  }
  function setAppLimit(packageName: string, minutes: number) {
    if (blocker.limitsEnabled) { showToast("Limits erst mit deinem Schlüssel entsperren"); return; }
    setBlocker(current => {
      const limits = { ...current.limits };
      if (minutes > 0) limits[packageName] = minutes; else delete limits[packageName];
      return { ...current, limits };
    });
    showToast(minutes > 0 ? `Tageslimit auf ${minutes} Minuten gesetzt` : "Tageslimit entfernt");
  }
  function setAppWindow(packageName: string, window: AppWindow | null) {
    if (blocker.windowsEnabled) { showToast("Uhrzeiten erst mit deinem Schlüssel entsperren"); return; }
    setBlocker(current => {
      const windows = { ...current.windows };
      if (window) windows[packageName] = window; else delete windows[packageName];
      return { ...current, windows };
    });
    showToast(window ? `Nutzungszeit ${window.start}–${window.end} gespeichert` : "Nutzungsfenster entfernt");
  }
  function logCheckin(state: CheckinState) {
    if (!pendingWake) return;
    const plannedSleep = pendingWake.plannedSleep || settings.bedtime;
    const sleepMinutes = durationBetween(plannedSleep, pendingWake.plannedTime);
    setCheckins(current => [...current.filter(entry => entry.eventId !== pendingWake.eventId), { ...pendingWake, state, checkedAt: Date.now(), plannedSleep, sleepMinutes }]);
    window.WachwerkAndroid?.completeMorningCheck?.(pendingWake.eventId, state);
    setPendingWake(null); showToast("Morgencheck gespeichert");
  }
  function enterStandby() { setStandbyEditing(null); setNightMode(true); window.WachwerkAndroid?.enterStandby?.(); }
  function exitStandby() { setStandbyEditing(null); setNightMode(false); window.WachwerkAndroid?.exitStandby?.(); }
  function beginStandbyPress(side: "left" | "right") {
    if (standbyPressRef.current.timer) window.clearTimeout(standbyPressRef.current.timer);
    standbyPressRef.current.long = false;
    standbyPressRef.current.timer = window.setTimeout(() => {
      standbyPressRef.current.long = true;
      setStandbyEditing(side);
      if (navigator.vibrate) navigator.vibrate(35);
    }, 620);
  }
  function endStandbyPress() {
    if (standbyPressRef.current.timer) window.clearTimeout(standbyPressRef.current.timer);
    standbyPressRef.current.timer = null;
  }
  function handleStandbyTap(event: ReactPointerEvent<HTMLDivElement>) {
    if ((event.target as HTMLElement).closest(".standby-panel")) return;
    if (standbyPressRef.current.long) { standbyPressRef.current.long = false; return; }
    if (standbyEditing) setStandbyEditing(null); else exitStandby();
  }
  function selectCycle(cycles: number) { setSelectedCycle(cycles); showToast(`${cycles} Zyklen ausgewählt`); }
  function applyCycle() {
    const choice = cycleSuggestions.find(item => item.cycles === selectedCycle) ?? cycleSuggestions[1];
    if (cycleMode === "sleep") {
      const wakeDate = new Date(now.getTime() + (selectedCycle * settings.cycleMinutes + settings.fallAsleepMinutes) * 60_000);
      setAlarmDraft({ ...defaultDraft(settings), time: choice.time, date: localDate(wakeDate), source: "cycle", plannedSleep: `${pad(now.getHours())}:${pad(now.getMinutes())}` });
    }
    else {
      const [hours, minutes] = cycleTime.split(":").map(Number);
      const wakeDate = new Date(now);
      wakeDate.setHours(hours, minutes, 0, 0);
      if (wakeDate.getTime() <= now.getTime()) wakeDate.setDate(wakeDate.getDate() + 1);
      resetSheet();
      setAlarmDraft({ ...defaultDraft(settings), time: cycleTime, date: localDate(wakeDate), label: "Zyklus-Wecker", source: "cycle", plannedSleep: choice.time });
    }
  }

  function renderHome() {
    const next = upcoming[0];
    return <>
      <header className="app-header"><div><span className="overline">{now.toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" }).toUpperCase()}</span><h2>Guten Morgen{settings.name.trim() ? `, ${settings.name.trim()}` : ""}.</h2></div><button type="button" className="settings-button" aria-label="Einstellungen" onClick={() => { resetSheet(); setScreen("settings"); }}>⚙</button></header>
      {!settings.keySetupDismissed && !settings.alarmNfcToken && <section className="settings-card key-setup"><span className="overline">EINMAL EINRICHTEN</span><h3>Dein NFC-Schlüssel</h3><p>Lerne deinen Tag einmal an. Für neue Wecker bleibt er danach gespeichert.</p><button type="button" className="primary-button" onClick={() => enrollNfc("alarm")}>NFC-Tag anlernen</button><button type="button" className="secondary-button" onClick={() => setSettings(current => ({ ...current, keySetupDismissed: true }))}>Später</button></section>}
      <section className="alarm-carousel-wrap">
        {upcoming.length ? <><div className="alarm-carousel" onScroll={event => setAlarmSlide(Math.round(event.currentTarget.scrollLeft / event.currentTarget.clientWidth))}>{upcoming.map(({ alarm, date }) => <article className="hero-card" key={alarm.id} onClick={() => setAlarmDraft({ ...alarm })}><div className="moon-orbit"><span>☾</span></div><div><span className="overline">NÄCHSTER WECKER</span><div className="alarm-time">{alarm.time}</div><p>{date?.toLocaleDateString("de-DE", { weekday: "long" })} · {challengeNames[alarm.challenge]}</p></div><Toggle on={alarm.enabled} label="Wecker umschalten" onClick={() => setAlarms(current => current.map(item => item.id === alarm.id ? { ...item, enabled: !item.enabled } : item))} /></article>)}</div><div className="carousel-dots">{upcoming.map((_, index) => <i key={index} className={alarmSlide === index ? "active" : ""} />)}</div></> : <article className="hero-card empty-hero"><div className="moon-orbit"><span>☾</span></div><div><span className="overline">NÄCHSTER WECKER</span><h3>Noch keiner gestellt</h3><p>Erstelle deinen ersten Wecker im Wecker-Tab.</p></div><button type="button" className="small-primary" onClick={() => { setScreen("alarms"); openNewAlarm(); }}>＋</button></article>}
      </section>
      <div className="quick-row home-quick-row"><button type="button" className="wide-quick" onClick={enterStandby}><span>◐</span><div><strong>Standby-Modus</strong><small>Uhr, Kalender und Widgets im Querformat</small></div></button></div>
      <div className="section-title"><h3>Dein Rhythmus</h3><button type="button" onClick={() => { setScreen("coach"); setCoachTab("analysis"); }}>Auswertung →</button></div>
      <div className="bento-grid"><button type="button" className="metric-card mint" onClick={() => { setScreen("coach"); setCoachTab("analysis"); }}><span className="card-icon">↗</span><strong>{checkins.length ? `${analysis.success}%` : "–"}</strong><p>{checkins.length ? "direkt aufgestanden" : "noch keine Daten"}</p></button><button type="button" className="metric-card dark" onClick={() => { setScreen("coach"); setCoachTab("rhythm"); }}><span className="card-icon">◴</span><strong>{checkins.length ? formatDuration(analysis.need) : "–"}</strong><p>{checkins.length ? "geschätzter Schlafbedarf" : "lernt mit jedem Check"}</p></button></div>
      <section className={`morning-check ${pendingWake ? "ready" : ""}`}><div><span className="overline">MORGENCHECK</span><h3>{pendingWake ? `Wie lief das Aufstehen um ${pendingWake.plannedTime}?` : "Wie lief das Aufstehen?"}</h3><p>{pendingWake ? "Deine Antwort verbessert die persönliche Berechnung." : "Erscheint nach einem wirklich beendeten Wecker."}</p></div><div className="mood-row two">{([{ id: "great", icon: "↑", label: "Direkt auf" }, { id: "miss", icon: "×", label: "Verschlafen" }] as const).map(item => <button type="button" key={item.id} disabled={!pendingWake} onClick={() => logCheckin(item.id)}><span>{item.icon}</span><small>{item.label}</small></button>)}</div></section>
      <section className="home-todo-preview"><div className="home-todo-title"><div><span className="overline">HEUTE</span><h3>Deine To-dos</h3></div><button type="button" onClick={() => setScreen("todos")}>Alle ansehen →</button></div>{todos.filter(todo => !todo.done).slice(0, 3).length ? todos.filter(todo => !todo.done).slice(0, 3).map(todo => <button type="button" key={todo.id} className="home-todo-row" onClick={() => toggleTodo(todo.id)}><i /> <span>{todo.text}</span></button>) : <button type="button" className="home-todo-empty" onClick={() => { setScreen("todos"); setTodoTab("open"); }}>Alles erledigt · neue Aufgabe hinzufügen</button>}<div className="habit-summary"><span>{habits.filter(habit => !habit.completedDates.includes(localDate()) && !habit.missedDates.includes(localDate())).length}</span><small>Habits heute noch offen</small></div></section>
    </>;
  }

  function renderFocusTimer() {
    const focusApps = installedApps.filter(app => `${app.label} ${app.packageName}`.toLocaleLowerCase("de-DE").includes(focusAppSearch.trim().toLocaleLowerCase("de-DE")));
    return <section className={`focus-timer-card ${focusState.active ? "active" : ""}`}>
      <div className="focus-heading"><div><span className="overline">DEEP FOCUS</span><h3>{focusState.active ? (focusState.phase === "work" ? "Du bist im Fokus" : "Erholung läuft") : "Fokus-Session planen"}</h3></div><span className="focus-icon">◎</span></div>
      {focusState.active ? <>
        <div className="focus-live-pill"><i /><span>{focusState.phase === "work" ? "FOKUS" : "PAUSE"}</span><strong>{focusState.ringing ? "00:00" : formatCountdown(focusState.endAt - now.getTime())}</strong></div>
        <div className="focus-running"><span>Runde {focusState.round} von {focusState.rounds}</span></div>
        <div className="focus-protection-summary"><span>{settings.focusPackages.length ? `◈ ${settings.focusPackages.length} Apps gesperrt` : "Apps bleiben frei"}</span><span>{focusState.silenceNotifications ? "☾ Benachrichtigungen still" : "Benachrichtigungen aktiv"}</span></div>
        <button type="button" className="secondary-button focus-stop" onClick={cancelFocusTimer}>Session beenden</button>
      </> : <>
        <p>Lege deinen Rhythmus fest und schalte Ablenkungen für die Fokusphase gezielt aus.</p>
        <div className="focus-values"><NumberField className="focus-number" label="Fokuszeit" value={settings.focusWorkMinutes} min={1} max={1440} onCommit={value => setSettings(current => ({ ...current, focusWorkMinutes: value }))} /><NumberField className="focus-number" label="Pausenzeit" value={settings.focusBreakMinutes} min={1} max={1440} onCommit={value => setSettings(current => ({ ...current, focusBreakMinutes: value }))} /><NumberField className="focus-number" label="Runden" value={settings.focusRounds} min={1} max={99} unit="" onCommit={value => setSettings(current => ({ ...current, focusRounds: value }))} /></div>
        <div className="focus-option"><div><strong>Benachrichtigungen stillschalten</strong><small>Aktiviert während Fokus den Android-Modus „Nicht stören“</small></div><Toggle on={settings.focusSilenceNotifications} label="Benachrichtigungen im Fokus still" onClick={() => setSettings(current => ({ ...current, focusSilenceNotifications: !current.focusSilenceNotifications }))} /></div>
        {settings.focusSilenceNotifications && !permissions.dnd && <button type="button" className="permission-button" onClick={() => window.WachwerkAndroid?.openNotificationPolicySettings?.()}>Nicht-stören-Zugriff erlauben</button>}
        <details className="focus-app-picker"><summary><span><strong>Apps während Fokus sperren</strong><small>{settings.focusPackages.length ? `${settings.focusPackages.length} ausgewählt` : "Optional"}</small></span></summary><label className="app-search"><span>⌕</span><input type="search" placeholder="Apps suchen …" value={focusAppSearch} onChange={event => setFocusAppSearch(event.target.value)} /></label><div>{focusApps.length ? focusApps.map(app => { const selected = settings.focusPackages.includes(app.packageName); return <button type="button" key={app.packageName} className={selected ? "selected" : ""} onClick={() => setSettings(current => ({ ...current, focusPackages: selected ? current.focusPackages.filter(item => item !== app.packageName) : [...current.focusPackages, app.packageName] }))}><AppIcon app={app} compact /><strong>{app.label}</strong><i>{selected ? "✓" : ""}</i></button>; }) : <p>App-Liste wird vorbereitet …</p>}</div></details>
        <button type="button" className="primary-button focus-start" onClick={startFocusTimer}>Session starten</button>
      </>}
    </section>;
  }

  function renderAlarms() {
    return <><ScreenHeader eyebrow="AUFSTEHEN" title="Deine Wecker" action="＋" onAction={() => openNewAlarm()} />
      <section className="alarm-cycle-panel"><div className="cycle-card quick-cycle-card"><span className="overline">SCHNELLPLANER</span><h3>{cycleMode === "wake" ? "Wann willst du aufstehen?" : "Du gehst jetzt schlafen"}</h3><div className="choice-row"><button type="button" className={cycleMode === "wake" ? "active" : ""} onClick={() => setCycleMode("wake")}>Aufstehen um</button><button type="button" className={cycleMode === "sleep" ? "active" : ""} onClick={() => setCycleMode("sleep")}>Ich schlafe jetzt</button></div>{cycleMode === "wake" ? <TimePicker label="Gewünschte Aufstehzeit" value={cycleTime} onChange={setCycleTime} /> : <div className="current-sleep-time"><strong>{pad(now.getHours())}:{pad(now.getMinutes())}</strong><span>aktuelle Uhrzeit · nicht veränderbar</span></div>}<p className="science-note">Orientierung mit {settings.cycleMinutes} Minuten pro Zyklus plus {settings.fallAsleepMinutes} Minuten Einschlafzeit. Beides kannst du in den Einstellungen ändern.</p></div>
        <div className="quick-suggestions"><div className="quick-suggestion-title"><strong>{cycleMode === "wake" ? "Passende Einschlafzeiten" : "Passende Weckzeiten"}</strong><span>4–6 Zyklen</span></div><div className="suggestion-list">{cycleSuggestions.map(item => <button type="button" className={selectedCycle === item.cycles ? "selected" : ""} key={item.cycles} onClick={() => selectCycle(item.cycles)}><div><strong>{item.time}</strong><span>{item.cycles} Zyklen · {formatDuration(item.cycles * settings.cycleMinutes)}</span></div><em>{Math.abs(item.cycles * settings.cycleMinutes - analysis.need) <= Math.max(20, settings.cycleMinutes / 2) ? "empfohlen" : `${item.cycles} Zyklen`}</em></button>)}</div><button type="button" className="cycle-apply" onClick={applyCycle}>{cycleMode === "wake" ? `Wecker für ${cycleTime} erstellen` : "Daraus Wecker erstellen"}</button></div></section>
      {renderFocusTimer()}
      <div className="section-title alarm-section-title"><h3>Gespeicherte Wecker</h3><span>{alarms.length || "keine"}</span></div>
      <section className="alarm-list">{alarms.length ? alarms.map(alarm => <article className={`alarm-item ${alarm.enabled ? "enabled" : ""}`} key={alarm.id} onClick={() => setAlarmDraft({ ...alarm })}><div className="alarm-top"><strong>{alarm.time}</strong><Toggle on={alarm.enabled} label={`${alarm.label} umschalten`} onClick={() => setAlarms(current => current.map(item => item.id === alarm.id ? { ...item, enabled: !item.enabled } : item))} /></div><h3>{alarm.label}</h3><p>{formatDays(alarm)}</p><div className="challenge-pill"><span>◆</span>{challengeNames[alarm.challenge]}</div><div className="alarm-actions"><button type="button" onClick={event => { event.stopPropagation(); setAlarmDraft({ ...alarm }); }}>Bearbeiten</button><button type="button" className="danger" onClick={event => { event.stopPropagation(); deleteAlarm(alarm.id); }}>Löschen</button></div></article>) : <div className="empty-state"><span>◴</span><h3>Noch keine Wecker</h3><p>Tippe oben auf Plus, um deinen ersten Wecker zu erstellen.</p></div>}</section>
    </>;
  }

  function renderCoach() {
    const recent = [...checkins].sort((a, b) => b.checkedAt - a.checkedAt).slice(0, 7).reverse();
    return <><ScreenHeader eyebrow="SCHLAFCOACH" title="Deine Auswertung" /><div className="segmented-control coach-tabs"><button type="button" className={coachTab === "analysis" ? "active" : ""} onClick={() => setCoachTab("analysis")}>Analyse</button><button type="button" className={coachTab === "rhythm" ? "active" : ""} onClick={() => setCoachTab("rhythm")}>Rhythmus</button></div>
      {coachTab === "analysis" ? <>{checkins.length ? <><section className="sleep-target-card"><span className="overline">DEINE GELERNTE SCHLAFZEIT</span><div><strong>{learnedBedtime}</strong><small>ins Bett bei Aufstehen um {cycleTime}</small></div><p>Enthält {formatDuration(analysis.need)} geschätzten Schlafbedarf plus {settings.fallAsleepMinutes} Minuten Einschlafzeit.</p><div className="confidence"><i style={{ width: `${analysis.confidence}%` }} /></div><em>{analysis.confidence}% Sicherheit aus {Math.min(checkins.length, 21)} Versuchen</em></section><div className="stats-grid"><article><strong>{analysis.success}%</strong><span>direkt auf</span></article><article><strong>{analysis.miss}</strong><span>verschlafen</span></article><article><strong>{formatDuration(analysis.need)}</strong><span>gelernter Bedarf</span></article><article><strong>{analysis.averageSnoozes.toFixed(1)}</strong><span>Ø Snoozes</span></article></div><section className="week-card"><div><span className="overline">LETZTE VERSUCHE</span><h3>Zyklen und echte Morgen</h3></div><div className="week-bars">{recent.map(entry => <div key={entry.eventId}><i className={entry.state} style={{ height: entry.state === "great" ? "88%" : "28%" }} /><span>{new Date(entry.checkedAt).toLocaleDateString("de-DE", { weekday: "short" })}</span></div>)}</div></section></> : <div className="empty-state coach-empty"><span>⌁</span><h3>Die Analyse beginnt mit deinem ersten Wecker</h3><p>Probiere Zyklus-Wecker oder eigene Schlafzeiten aus. Nach jedem Morgen lernt Wachwerk aus „Direkt auf“ oder „Verschlafen“.</p></div>}</> : <><section className="rhythm-card"><span className="overline">DER LERNALGORITHMUS</span><h3>Jeder echte Versuch verändert deine Empfehlung</h3><ol><li><strong>Schlafdauer</strong><span>Geplante Einschlafzeit und Weckzeit ergeben die getestete Dauer.</span></li><li><strong>Ergebnis</strong><span>Verschlafen und Snoozes erhöhen den geschätzten Bedarf; direktes Aufstehen bestätigt ihn.</span></li><li><strong>Gewichtung</strong><span>Neuere Versuche zählen stärker. Schwankende Ergebnisse senken die angezeigte Sicherheit.</span></li></ol></section><section className="recommendation-card"><span className="overline">NÄCHSTER VERSUCH</span><h3>{checkins.length ? `${learnedBedtime} ins Bett` : "Erst Daten sammeln"}</h3><p>{checkins.length ? `Für ${cycleTime} Uhr Aufstehen empfiehlt Wachwerk aktuell ${learnedBedtime}. Weitere Morgen machen die Schätzung genauer.` : "Stelle einen Wecker und beantworte danach den Morgencheck."}</p></section></>}
    </>;
  }

  function toggleTodo(id: string) {
    setTodos(current => current.map(item => item.id !== id ? item : { ...item, done: !item.done, completedAt: item.done ? undefined : Date.now() }));
  }

  function renderHabitRow(habit: Habit, removable = false) {
    const today = localDate(), doneToday = habit.completedDates.includes(today), failed = habit.missedDates.includes(today);
    return <article key={habit.id} className={doneToday ? "done-today" : failed ? "failed-today" : ""}><button type="button" className="habit-check habit-success" aria-label={`${habit.text}: heute geschafft`} aria-pressed={doneToday} onClick={() => toggleHabit(habit.id)}>{doneToday ? "✓" : ""}</button><div className="habit-copy"><strong>{habit.text}</strong><small>{doneToday ? "Heute geschafft" : failed ? "Heute nicht geschafft" : "Heute noch offen"} · Serie {habitStreak(habit)} Tage</small></div>{removable && <button type="button" className="todo-delete" aria-label="Habit vollständig löschen" onClick={() => { setHabits(current => current.filter(item => item.id !== habit.id)); showToast("Habit entfernt"); }}><TrashIcon /></button>}<button type="button" className="habit-check habit-failure" aria-label={`${habit.text}: heute nicht geschafft`} aria-pressed={failed} onClick={() => toggleHabit(habit.id, true)}>{failed ? "×" : ""}</button></article>;
  }

  function renderTodos() {
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const monthDays = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    const leading = (monthStart.getDay() + 6) % 7;
    const heatmap = Array.from({ length: leading + monthDays }, (_, index) => {
      if (index < leading) return null;
      const day = index - leading + 1, date = new Date(now.getFullYear(), now.getMonth(), day), key = localDate(date), future = date.getTime() > new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
      const eligibleHabits = habits.filter(habit => habit.createdAt <= new Date(now.getFullYear(), now.getMonth(), day, 23, 59).getTime());
      const dayTodos = todos.filter(todo => localDate(new Date(todo.createdAt)) === key);
      const countHabits = settings.consistencyMode !== "todos", countTodos = settings.consistencyMode !== "habits";
      const hasEntries = (countHabits && eligibleHabits.length > 0) || (countTodos && dayTodos.length > 0);
      const complete = (!countHabits || eligibleHabits.every(habit => habit.completedDates.includes(key))) && (!countTodos || dayTodos.every(todo => todo.done));
      const failed = countHabits && eligibleHabits.some(habit => habit.missedDates.includes(key));
      return { day, key, state: calendarState(key, localDate(now), hasEntries, complete, failed) };
    });
    const openCount = todos.filter(item => !item.done).length + habits.filter(habit => !habit.completedDates.includes(localDate()) && !habit.missedDates.includes(localDate())).length;
    const openHabits = habits.filter(habit => !habit.completedDates.includes(localDate()) && !habit.missedDates.includes(localDate()));
    return <><ScreenHeader eyebrow="FOKUS" title="Deine Aufgaben" action="＋" onAction={() => { resetSheet(); setChooseTodo(true); }} /><section className="consistency-card"><div><span className="overline">KONSTANZ</span><h3>{now.toLocaleDateString("de-DE", { month: "long", year: "numeric" })}</h3></div><div className="consistency-weekdays">{["M","D","M","D","F","S","S"].map((day,index) => <span key={index}>{day}</span>)}</div><div className="consistency-grid">{heatmap.map((day,index) => day ? <i key={day.key} className={day.state} title={`${day.key}: ${day.state === "complete" ? "geschafft" : day.state === "missed" ? "nicht geschafft" : "keine Einträge"}`}>{day.day}</i> : <i key={`blank-${index}`} className="blank" />)}</div><div className="consistency-legend"><span><i className="complete" /> geschafft</span><span><i className="missed" /> nicht geschafft</span></div></section><div className="segmented-control todo-tabs"><button type="button" className={todoTab === "open" ? "active" : ""} onClick={() => setTodoTab("open")}>Offen · {openCount}</button><button type="button" className={todoTab === "habits" ? "active" : ""} onClick={() => setTodoTab("habits")}>Habits · {habits.length}</button><button type="button" className={todoTab === "done" ? "active" : ""} onClick={() => setTodoTab("done")}>Erledigt · {todos.filter(item => item.done).length}</button></div>{todoTab === "open" && <><section className="open-habits">{openHabits.map(habit => renderHabitRow(habit))}</section><section className="todo-list">{todos.filter(todo => !todo.done).length ? todos.filter(todo => !todo.done).map(todo => <article key={todo.id}><button type="button" className="todo-check" onClick={() => toggleTodo(todo.id)} /><div className="todo-copy"><span>{todo.text}</span>{todo.reminderAt && <small>◴ {new Date(todo.reminderAt).toLocaleString("de-DE", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</small>}</div><button type="button" className="todo-delete" aria-label="Aufgabe löschen" onClick={() => setTodos(current => current.filter(item => item.id !== todo.id))}><TrashIcon /></button></article>) : !openHabits.length && <div className="empty-state"><span>✓</span><h3>Alles frei</h3></div>}</section></>}{todoTab === "habits" && <section className="habit-list">{habits.length ? habits.map(habit => renderHabitRow(habit, true)) : <div className="empty-state"><span>↻</span><h3>Noch keine Habits</h3><p>Über Plus legst du ein tägliches Ziel an.</p></div>}</section>}{todoTab === "done" && <section className="todo-list">{todos.filter(todo => todo.done).length ? todos.filter(todo => todo.done).map(todo => <article key={todo.id} className="done"><button type="button" className="todo-check" onClick={() => toggleTodo(todo.id)}>✓</button><div className="todo-copy"><span>{todo.text}</span></div><button type="button" className="todo-delete" aria-label="Aufgabe löschen" onClick={() => setTodos(current => current.filter(item => item.id !== todo.id))}><TrashIcon /></button></article>) : <div className="empty-state"><span>✓</span><h3>Noch nichts erledigt</h3></div>}</section>}</>;
  }

  function renderSettings() {
    const numberSetting = (id: string, label: string, value: number, update: (value: number) => void, min = 1, max = 1440, unit = "Min.") => <NumberField id={id} label={label} value={value} onCommit={update} min={min} max={max} unit={unit} />;
    return <><ScreenHeader eyebrow="WACHWERK" title="Einstellungen" /><section className="settings-card"><label className="field-label" htmlFor="profile-name">Dein Name</label><input id="profile-name" type="text" placeholder="Optional" value={settings.name} onChange={event => setSettings(current => ({ ...current, name: event.target.value }))} /><label className="field-label" htmlFor="app-font">Schrift in der App</label><select id="app-font" value={settings.appFont} onChange={event => setSettings(current => ({ ...current, appFont: event.target.value as Settings["appFont"] }))}><option value="modern">Modern</option><option value="rounded">Weich & rund</option><option value="classic">Klassisch</option></select><label className="field-label">Farbpalette</label><div className="palette-options" role="group" aria-label="Farbpalette">{([{ id: "classic", name: "Original", colors: ["#06131f", "#c9dcf8", "#9bf5b1", "#ffd347"] }, { id: "solar", name: "Sonnenwärme", colors: ["#003049", "#d62828", "#f77f00", "#fcbf49", "#eae2b7"] }, { id: "dusk", name: "Abendruhe", colors: ["#191629", "#c2b2f1", "#90cdb7", "#edd9bc"] }] as const).map(palette => <button type="button" key={palette.id} aria-pressed={settings.palette === palette.id} className={settings.palette === palette.id ? "selected" : ""} onClick={() => setSettings(current => ({ ...current, palette: palette.id }))}><span className="palette-swatches">{palette.colors.map(color => <i key={color} style={{ background: color }} />)}</span><strong>{palette.name}</strong><span className="palette-check">{settings.palette === palette.id ? "✓" : ""}</span></button>)}</div></section>
      <section className="settings-card"><div className="switch-row"><div><strong>Schlafenszeit-Erinnerungen</strong><small>Bleiben bei eingeschaltetem Bildschirm hartnäckig aktiv</small></div><Toggle on={settings.remindersOn} label="Schlafenszeit-Erinnerungen" onClick={() => setSettings(current => ({ ...current, remindersOn: !current.remindersOn }))} /></div><label className="field-label">Ab wann soll Ruhe sein?</label><TimePicker label="Schlafenszeit" value={settings.bedtime} onChange={value => setSettings(current => ({ ...current, bedtime: value }))} /><div className="choice-row"><button type="button" className={settings.nagMode === "fixed" ? "active" : ""} onClick={() => setSettings(current => ({ ...current, nagMode: "fixed" }))}>Gleichmäßig</button><button type="button" className={settings.nagMode === "urgent" ? "active" : ""} onClick={() => setSettings(current => ({ ...current, nagMode: "urgent" }))}>Immer kürzer</button></div>{numberSetting("interval", settings.nagMode === "fixed" ? "Erinnerung alle" : "Erster Abstand", settings.reminderInterval, value => setSettings(current => ({ ...current, reminderInterval: value, reminderMinimumInterval: Math.min(current.reminderMinimumInterval, value) })), 1)}{settings.nagMode === "urgent" && numberSetting("minimum-interval", "Kleinster Abstand", settings.reminderMinimumInterval, value => setSettings(current => ({ ...current, reminderMinimumInterval: Math.min(value, current.reminderInterval) })), 1)}{numberSetting("sleep-detect", "Als Schlaf erkannt nach", settings.sleepDetectMinutes, value => setSettings(current => ({ ...current, sleepDetectMinutes: value })), 5, 720)}<label className="field-label" htmlFor="message">Nachricht</label><textarea id="message" rows={3} value={settings.reminderMessage} onChange={event => setSettings(current => ({ ...current, reminderMessage: event.target.value }))} /><p className="field-help">Ist der Bildschirm so lange aus, gilt das Handy als weggelegt. Dann endet die Erinnerung bis zur nächsten eingestellten Schlafenszeit. Ein klingelnder Wecker beendet sie ebenfalls.</p></section>
      <section className="settings-card"><span className="overline">NACH DEM AUFSTEHEN</span>{numberSetting("morning-delay", "Morgencheck nach", settings.morningDelay, value => setSettings(current => ({ ...current, morningDelay: value })), 0)}<p className="field-help">0 Minuten bedeutet sofort. Beim Antippen öffnet sich genau der Check für den letzten Wecker.</p></section>
      <section className="settings-card"><span className="overline">SCHLAFBERECHNUNG</span>{numberSetting("cycle-minutes", "Minuten je Schlafzyklus", settings.cycleMinutes, value => setSettings(current => ({ ...current, cycleMinutes: value })), 30, 240)}{numberSetting("sleep-onset", "Einschlafzeit", settings.fallAsleepMinutes, value => setSettings(current => ({ ...current, fallAsleepMinutes: value })), 0, 180)}<p className="field-help">Diese Werte werden im Schnellplaner verwendet und sind jederzeit änderbar.</p></section>
      <section className="settings-card"><span className="overline">ERFOLGSANZEIGE IM KALENDER</span><label className="field-label" htmlFor="consistency-mode">Wann soll ein Tag grün werden?</label><select id="consistency-mode" value={settings.consistencyMode} onChange={event => setSettings(current => ({ ...current, consistencyMode: event.target.value as Settings["consistencyMode"] }))}><option value="habits">Alle Habits geschafft</option><option value="todos">Alle To-dos geschafft</option><option value="both">Alle Habits und To-dos geschafft</option></select><p className="field-help">Standardmäßig bewertet Wachwerk nur deine täglichen Habits.</p></section>
      {permissions.liveSupported && !permissions.liveEnabled && <section className="settings-card"><h3>Fokus als Live-Anzeige</h3><p className="field-help">Erlaube Live-Benachrichtigungen, damit Android den Timer zusätzlich neben der Uhr anzeigen kann.</p><button type="button" className="secondary-button" onClick={() => window.WachwerkAndroid?.openLiveNotificationSettings?.()}>Live-Anzeige erlauben</button></section>}
      <section className="settings-card"><span className="overline">APP-ZEIT VERBLEIBEND</span><div className="switch-row"><div><strong>Restzeit regelmäßig melden</strong><small>Nur für Apps mit aktivem Tageslimit</small></div><Toggle on={blocker.limitReminderEnabled} label="App-Restzeit erinnern" onClick={() => setBlocker(current => ({ ...current, limitReminderEnabled: !current.limitReminderEnabled }))} /></div>{blocker.limitReminderEnabled && <NumberField label="Erinnerung alle" value={blocker.limitReminderMinutes} min={1} max={1440} onCommit={value => setBlocker(current => ({ ...current, limitReminderMinutes: value }))} />}<p className="field-help">Der Abstand zählt die tatsächlich genutzte Zeit. Wachwerk nennt dir dabei die noch übrigen Minuten.</p></section>
      <section className="settings-card"><span className="overline">AUFWACH-SCHLÜSSEL</span><h3>{settings.alarmNfcToken ? "NFC-Tag gespeichert" : "NFC-Tag einmal anlernen"}</h3><p className="field-help">Ein gespeicherter Tag steht für alle neuen NFC-Wecker bereit.</p><button type="button" className="secondary-button" onClick={() => enrollNfc("alarm")}>{settings.alarmNfcToken ? "Anderen Tag einrichten" : "Tag anlernen"}</button></section>
      <section className="settings-card"><span className="overline">STANDARD FÜR NEUE WECKER</span><label className="field-label" htmlFor="default-day">Vorausgewähltes Datum</label><select id="default-day" value={settings.defaultAlarmDay} onChange={event => setSettings(current => ({ ...current, defaultAlarmDay: event.target.value as Settings["defaultAlarmDay"] }))}><option value="today">Heute</option><option value="tomorrow">Morgen</option></select><label className="field-label" htmlFor="default-challenge">Aufwach-Aufgabe</label><select id="default-challenge" value={settings.defaultChallenge} onChange={event => setSettings(current => ({ ...current, defaultChallenge: event.target.value as Challenge }))}>{Object.entries(challengeNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><div className="number-grid">{numberSetting("shake-count", "Schüttelbewegungen", settings.shakeCount, value => setSettings(current => ({ ...current, shakeCount: value })), 3, 200, "×")}{numberSetting("hold-seconds", "Display halten", settings.holdSeconds, value => setSettings(current => ({ ...current, holdSeconds: value })), 3, 120, "Sek.")}{numberSetting("snake-seconds", "Schlange folgen", settings.snakeSeconds, value => setSettings(current => ({ ...current, snakeSeconds: value })), 3, 120, "Sek.")}</div><div className="switch-row"><div><strong>Snooze anbieten</strong><small>Kann bei jedem Wecker einzeln geändert werden</small></div><Toggle on={settings.snoozeEnabled} label="Snooze" onClick={() => setSettings(current => ({ ...current, snoozeEnabled: !current.snoozeEnabled }))} /></div>{settings.snoozeEnabled && <><div className="number-grid">{numberSetting("snooze-minutes", "Erste Snooze-Zeit", settings.snoozeMinutes, value => setSettings(current => ({ ...current, snoozeMinutes: value, snoozeMinimumMinutes: Math.min(current.snoozeMinimumMinutes, value) })), 1)}{settings.snoozeAggressive && numberSetting("snooze-minimum", "Kleinste Snooze-Zeit", settings.snoozeMinimumMinutes, value => setSettings(current => ({ ...current, snoozeMinimumMinutes: Math.min(value, current.snoozeMinutes) })), 1)}</div><div className="switch-row"><div><strong>Snooze wird aggressiver</strong><small>Der Abstand halbiert sich bis zum Minimum</small></div><Toggle on={settings.snoozeAggressive} label="Aggressiver Snooze" onClick={() => setSettings(current => ({ ...current, snoozeAggressive: !current.snoozeAggressive }))} /></div></>}<label className="field-label" htmlFor="default-sound">Alarmton</label><select id="default-sound" value={settings.sound} onChange={event => setSettings(current => ({ ...current, sound: event.target.value }))}>{settings.sound.startsWith("custom:") && <option value={settings.sound}>{soundLabel(settings.sound)}</option>}<option>Systemstandard</option><option>Sanft</option><option>Klar</option><option>Kräftig</option></select><div className="dual-actions"><button type="button" className={`secondary-button ${soundPreviewing ? "preview-stop" : ""}`} onClick={previewAlarmSound}>{soundPreviewing ? "■ Stoppen" : "▶ Anhören"}</button><button type="button" className="file-picker-button" onClick={chooseAlarmSound}>＋ Eigene Datei</button></div>{soundPreviewing && <div className="sound-preview-status"><i /><span>Tonvorschau läuft</span><button type="button" onClick={previewAlarmSound}>Beenden</button></div>}<p className="field-help">Eine eigene Datei wird lokal in Wachwerk kopiert und funktioniert danach offline.</p><div className="switch-row"><div><strong>Sanftes Licht</strong><small>Ganzer Bildschirm wird warmweiß und immer heller</small></div><Toggle on={settings.gentleWake} label="Sanftes Licht" onClick={() => setSettings(current => ({ ...current, gentleWake: !current.gentleWake }))} /></div>{settings.gentleWake && numberSetting("gentle-minutes", "Licht startet vorher", settings.gentleMinutes, value => setSettings(current => ({ ...current, gentleMinutes: value })), 1, 180)}</section>
      {(!permissions.notifications || !permissions.exact || !permissions.fullScreen || !permissions.camera) && <section className="settings-card"><span className="overline">NOCH OFFENE BERECHTIGUNGEN</span>{!permissions.notifications && <div className="switch-row"><div><strong>Benachrichtigungen erlauben</strong><small>Für Wecker, Timer und Erinnerungen</small></div><button type="button" className="secondary-button compact" onClick={() => window.WachwerkAndroid?.openNotificationSettings?.()}>Öffnen</button></div>}{!permissions.exact && <div className="switch-row"><div><strong>Alarme & Erinnerungen</strong><small>Damit Wecker sekundengenau klingeln</small></div><button type="button" className="secondary-button compact" onClick={() => window.WachwerkAndroid?.openExactAlarmSettings?.()}>Öffnen</button></div>}{!permissions.fullScreen && <div className="switch-row"><div><strong>Vollbild-Wecker</strong><small>Damit der ausgeschaltete Bildschirm aufwacht</small></div><button type="button" className="secondary-button compact" onClick={() => window.WachwerkAndroid?.openFullScreenSettings?.()}>Öffnen</button></div>}{!permissions.camera && <div className="switch-row"><div><strong>Kamera für QR-Aufgabe</strong><small>Wird ausschließlich beim Scannen benutzt</small></div><button type="button" className="secondary-button compact" onClick={() => window.WachwerkAndroid?.requestCameraPermission?.()}>Erlauben</button></div>}<p className="field-help">Sobald eine Berechtigung erteilt ist, verschwindet ihr Hinweis automatisch.</p></section>}

    </>;
  }

  function renderBlockerHeader() {
    return <><ScreenHeader eyebrow="FOKUS" title="App-Blocker" /><div className="segmented-control blocker-tabs four">
      {(["instant", "limits", "windows"] as const).map((tab, index) => <button type="button" key={tab} className={!morningTab && blockerTab === tab ? "active" : ""} onClick={() => { setMorningTab(false); setBlockerTab(tab); setBlockerPassword(""); }}>{["Direkt", "Limits", "Uhrzeiten"][index]}</button>)}
      <button type="button" className={morningTab ? "active" : ""} onClick={() => { setMorningTab(true); setBlockerPassword(""); }}>Morgen</button>
    </div></>;
  }
  function renderMorningBlocker() {
    const remaining = Math.max(0, Math.ceil((morningBlock.until - now.getTime()) / 1000));
    return <>{renderBlockerHeader()}
      {remaining > 0 && <section className="blocker-hero active morning-active"><span className="overline">DEIN MORGEN GEHÖRT DIR</span><strong>{Math.floor(remaining / 60).toString().padStart(2, "0")}:{(remaining % 60).toString().padStart(2, "0")}</strong><p>{morningBlock.packages.length} Apps warten noch. Danach endet nur die Morgensperre automatisch.</p></section>}
      <section className="settings-card morning-config"><span className="overline">NACH DEM AUFSTEHEN</span>
        <div className="switch-row"><div><strong>Handy-Pause am Morgen</strong><small>Startet nach dem Ausschalten eines Weckers</small></div><Toggle on={settings.morningBlockEnabled} label="Morgensperre aktivieren" onClick={() => {
          if (!settings.morningBlockEnabled && !settings.morningBlockPackages.length) { showToast("Wähle unten zuerst deine Apps aus"); return; }
          if (!settings.morningBlockEnabled && !accessibilityEnabled && window.WachwerkAndroid) { window.WachwerkAndroid.openAccessibilitySettings?.(); showToast("Aktiviere zuerst den App-Blocker-Zugriff"); return; }
          setSettings(current => ({...current, morningBlockEnabled: !current.morningBlockEnabled}));
        }} /></div>
        <NumberField label="Dauer in Minuten" value={settings.morningBlockMinutes} min={1} max={1440} onCommit={value => setSettings(current => ({...current, morningBlockMinutes: value}))} />
        <p className="field-help">Erst wenn du die Aufwachaufgabe schaffst – nicht beim Snoozen. Nur gewählte Apps werden gesperrt; Wetter, Nachrichten und alle anderen bleiben erreichbar.</p>
        {remaining > 0 && <p className="field-help">Änderungen hier gelten für den nächsten Wecker. Die laufende Morgensperre bleibt bis zum Ende bestehen.</p>}
      </section>
      {!accessibilityEnabled && <section className="settings-card"><h3>App-Blocker-Zugriff</h3><button type="button" className="secondary-button" onClick={() => window.WachwerkAndroid?.openAccessibilitySettings?.()}>Bedienungshilfe öffnen</button></section>}
      <div className="section-title blocker-list-title"><h3>Diese Apps warten</h3><span>{settings.morningBlockPackages.length} gewählt</span></div>
      <label className="app-search"><span>⌕</span><input type="search" aria-label="Morgen-Apps durchsuchen" placeholder="Apps suchen …" value={appSearch} onChange={event => setAppSearch(event.target.value)} /></label>
      <section className="app-picker">{filteredApps.map(app => {
        const selected = settings.morningBlockPackages.includes(app.packageName);
        return <button type="button" key={app.packageName} className={selected ? "selected" : ""} onClick={() => setSettings(current => ({...current, morningBlockPackages: selected ? current.morningBlockPackages.filter(pkg => pkg !== app.packageName) : [...current.morningBlockPackages, app.packageName]}))}>
          <AppIcon app={app} /><span><strong>{app.label}</strong><small>{app.packageName}</small></span><i>{selected ? "✓" : ""}</i>
        </button>;
      })}{!filteredApps.length && <div className="empty-state"><span>☀</span><h3>{appSearch ? "Keine passenden Apps" : "Apps werden vorbereitet"}</h3></div>}</section>
    </>;
  }
  function renderBlocker() {
    if (morningTab) return renderMorningBlocker();
    const scopeActive = blockerTab === "limits" ? blocker.limitsEnabled : blockerTab === "windows" ? blocker.windowsEnabled : blocker.enabled;
    const scopeName = blockerTab === "limits" ? "Tageslimits" : blockerTab === "windows" ? "Uhrzeiten" : "Apps";
    const keyName = (blocker.methods[blockerTab] ?? blocker.method) === "nfc" ? "NFC-Tag" : (blocker.methods[blockerTab] ?? blocker.method) === "qr" ? "QR-Code" : "Passwort";
    const needsAccessibility = !accessibilityEnabled;
    const needsUsage = blockerTab === "limits" && !usageAccessEnabled;
    const accessCard = needsAccessibility || needsUsage ? <section className="settings-card blocker-access-card"><span className="overline">NOCH ERFORDERLICHER ANDROID-ZUGRIFF</span>{needsAccessibility && <><div className="switch-row"><div><strong>App-Blocker aktivieren</strong><small>Erkennt, welche App gerade geöffnet wird</small></div><i className="status-dot" /></div><button type="button" className="secondary-button" onClick={() => window.WachwerkAndroid?.openAccessibilitySettings?.()}>Bedienungshilfe öffnen</button></>}{needsUsage && <><div className="switch-row permission-row"><div><strong>Genaue Nutzungszeit erlauben</strong><small>Notwendig für korrekte Tageslimits</small></div><i className="status-dot" /></div><button type="button" className="secondary-button" onClick={() => window.WachwerkAndroid?.openUsageAccessSettings?.()}>Nutzungsdatenzugriff öffnen</button></>}<p className="field-help">Sobald alles erlaubt ist, verschwindet dieser Hinweis automatisch. Alle Messwerte bleiben lokal.</p></section> : null;
    const search = <label className="app-search"><span>⌕</span><input type="search" aria-label="Apps durchsuchen" placeholder="Apps suchen …" value={appSearch} onChange={event => setAppSearch(event.target.value)} />{appSearch && <button type="button" aria-label="Suche leeren" onClick={() => setAppSearch("")}>×</button>}</label>;
    const keyCard = <section className="settings-card blocker-key-card"><span className="overline">{scopeName.toUpperCase()} · SCHLÜSSEL</span><div className="choice-row three"><button type="button" disabled={scopeActive} className={(blocker.methods[blockerTab] ?? blocker.method) === "nfc" ? "active" : ""} onClick={() => setBlocker(current => ({ ...current, methods: { ...current.methods, [blockerTab]: "nfc" } }))}>NFC-Tag</button><button type="button" disabled={scopeActive} className={(blocker.methods[blockerTab] ?? blocker.method) === "qr" ? "active" : ""} onClick={() => setBlocker(current => ({ ...current, methods: { ...current.methods, [blockerTab]: "qr" } }))}>QR-Code</button><button type="button" disabled={scopeActive} className={(blocker.methods[blockerTab] ?? blocker.method) === "password" ? "active" : ""} onClick={() => setBlocker(current => ({ ...current, methods: { ...current.methods, [blockerTab]: "password" } }))}>Passwort</button></div>{(blocker.methods[blockerTab] ?? blocker.method) === "nfc" ? <p className="field-help key-auto-help">Beim ersten Verwenden merkt sich Wachwerk automatisch den NFC-Tag, den du anhältst. Der Tag wird nicht beschrieben.</p> : (blocker.methods[blockerTab] ?? blocker.method) === "qr" ? <><p className="field-help key-auto-help">Dein persönlicher QR-Code ist bereits bereit und muss nicht angelernt werden.</p><button type="button" className="file-picker-button" onClick={() => setScreen("qr")}>QR-Code anzeigen und drucken</button></> : <><h3>{(blocker.hasPasswords[blockerTab] ?? blocker.hasPassword) ? "Passwort ist eingerichtet" : "Lokales Passwort festlegen"}</h3><p className="field-help">Mindestens vier Zeichen. Es wird nur als Prüfsumme und nie im Klartext gespeichert.</p><div className="password-row"><input type="password" autoComplete="new-password" placeholder={(blocker.hasPasswords[blockerTab] ?? blocker.hasPassword) ? "Passwort" : "Neues Passwort"} value={blockerPassword} onChange={event => setBlockerPassword(event.target.value)} /><button type="button" className="file-picker-button" disabled={scopeActive} onClick={saveBlockerPassword}>{(blocker.hasPasswords[blockerTab] ?? blocker.hasPassword) ? "Ändern" : "Speichern"}</button></div></>}</section>;
    return <>{renderBlockerHeader()}{keyCard}<section className={`blocker-hero ${scopeActive ? "active" : ""}`}><span className="overline">STATUS</span><strong>{`${scopeName} ${scopeActive ? "gesperrt" : "nicht gesperrt"}`}</strong><p>{blockerTab !== "instant" ? (scopeActive ? "Diese Regeln sind aktiv und geschützt. Entsperre sie mit deinem Schlüssel, um sie zu ändern oder auszuschalten." : "Lege unten deine Regeln fest und aktiviere sie anschließend mit deinem Schlüssel.") : scopeActive ? "Deine ausgewählten Apps bleiben bis zur Freigabe gesperrt." : "Wähle unten die Apps und sperre sie mit deinem Schlüssel."}</p>{(blocker.methods[blockerTab] ?? blocker.method) === "password" && <input className="blocker-password-action" type="password" autoComplete="current-password" placeholder="Passwort" value={blockerPassword} onChange={event => setBlockerPassword(event.target.value)} />}<button type="button" className="primary-button" onClick={toggleBlockerWithKey}>{scopeActive ? `Mit ${keyName} entsperren` : `Mit ${keyName} sperren`}</button></section>{blockerTab === "instant" && <>{accessCard}<div className="section-title blocker-list-title"><h3>Zu sperrende Apps</h3><span>{blocker.packages.length} gewählt</span></div>{search}<section className="app-picker">{filteredApps.length ? filteredApps.map(app => { const selected = blocker.packages.includes(app.packageName); return <button type="button" key={app.packageName} disabled={blocker.enabled} className={selected ? "selected" : ""} onClick={() => setBlocker(current => ({ ...current, packages: selected ? current.packages.filter(item => item !== app.packageName) : [...current.packages, app.packageName] }))}><AppIcon app={app} /><span><strong>{app.label}</strong><small>{app.packageName}</small></span><i>{selected ? "✓" : ""}</i></button>; }) : <div className="empty-state"><span>◈</span><h3>Apps werden vorbereitet</h3></div>}</section></>}{blockerTab === "limits" && <>{accessCard}<div className="section-title blocker-list-title"><h3>Tägliche App-Limits</h3><span>{Object.keys(blocker.limits).length} gesetzt</span></div>{search}<section className="limit-picker">{filteredApps.length ? filteredApps.map(app => { const limit = blocker.limits[app.packageName] ?? 0; const usedSeconds = appUsage[app.packageName] ?? 0; const usedMinutes = Math.floor(usedSeconds / 60); const progress = limit ? Math.min(100, Math.round(usedSeconds / (limit * 60) * 100)) : 0; return <article key={app.packageName} className={limit ? "limited" : ""}><AppIcon app={app} /><div className="limit-copy"><strong>{app.label}</strong><small>{limit ? `${usedMinutes} von ${limit} Min. heute` : "Kein Tageslimit"}</small>{limit > 0 && <div className="limit-progress"><i style={{ width: `${progress}%` }} /></div>}</div><NumberField className="limit-custom" disabled={blocker.limitsEnabled} label="" value={limit} min={0} max={1440} onCommit={value => setAppLimit(app.packageName, value)} /></article>; }) : <div className="empty-state"><span>⌛</span><h3>Apps werden vorbereitet</h3></div>}</section></>}{blockerTab === "windows" && <>{accessCard}<div className="section-title blocker-list-title"><h3>Erlaubte Nutzungszeiten</h3><span>{Object.keys(blocker.windows).length} gesetzt</span></div>{search}<section className="window-picker">{filteredApps.length ? filteredApps.map(app => { const window = blocker.windows[app.packageName]; return <article key={app.packageName} className={window ? "active" : ""}><div className="window-app"><AppIcon app={app} /><div><strong>{app.label}</strong><small>{window ? `Erlaubt von ${window.start} bis ${window.end}` : "Keine Uhrzeitbeschränkung"}</small></div><Toggle on={Boolean(window)} label={`Zeitfenster für ${app.label}`} onClick={() => setAppWindow(app.packageName, window ? null : { start: "12:00", end: "18:00" })} /></div>{window && <fieldset className="window-times" disabled={blocker.windowsEnabled}><label>Von<input type="time" value={window.start} onChange={event => setAppWindow(app.packageName, { ...window, start: event.target.value })} /></label><span>bis</span><label>Bis<input type="time" value={window.end} onChange={event => setAppWindow(app.packageName, { ...window, end: event.target.value })} /></label></fieldset>}</article>; }) : <div className="empty-state"><span>◷</span><h3>Apps werden vorbereitet</h3></div>}</section></>}</>;
  }

  function renderQR() {
    const token = "wachwerk-personal-code";
    return <><ScreenHeader eyebrow="PERSÖNLICHER SCHLÜSSEL" title="Dein QR-Code" action="Zurück" onAction={() => setScreen("home")} /><section className="qr-page-card"><QRCode seed={token} matrix={qrMatrix} /><h3>Häng mich in einen anderen Raum</h3><p>Dieser persönliche Code kann einen QR-Wecker beenden und den App-Blocker sperren oder entsperren.</p><button type="button" className="primary-button" onClick={() => { if (window.WachwerkAndroid?.printCurrentPage) window.WachwerkAndroid.printCurrentPage(); else window.print(); }}>Als PDF drucken</button><button type="button" className="permission-button" onClick={() => window.WachwerkAndroid?.requestCameraPermission?.()}>Kamera-Berechtigung prüfen</button></section></>;
  }

  function renderAlarmModal() {
    if (!alarmDraft) return null;
    const recurring = alarmDraft.days.length > 0;
    return <div className={`modal-backdrop ${sheetClosing ? "closing" : ""}`} role="presentation" onPointerDown={event => { if (event.target === event.currentTarget) closeSheet("alarm"); }}>
      <form data-sheet-kind="alarm" className={`modal-sheet alarm-modal-sheet ${sheetClosing ? "closing" : ""}`} role="dialog" aria-modal="true" aria-label="Wecker bearbeiten" onSubmit={saveAlarm}>
        <div className="modal-handle" aria-label="Nach unten ziehen zum Schließen"><i /></div>
        <div className="modal-title compact-title"><div><span className="overline">{alarmDraft.id ? "WECKER BEARBEITEN" : "NEUER WECKER"}</span></div></div>
        <TimePicker label="Weckzeit" value={alarmDraft.time} onChange={value => setAlarmDraft(current => current ? { ...current, time: value } : current)} />
        <label className="field-label" htmlFor="alarm-label">Bezeichnung</label><input id="alarm-label" type="text" value={alarmDraft.label} onChange={event => setAlarmDraft(current => current ? { ...current, label: event.target.value } : current)} />
        <div className="choice-row"><button type="button" className={!recurring ? "active" : ""} onClick={() => setAlarmDraft(current => current ? { ...current, days: [], date: current.date || (settings.defaultAlarmDay === "today" ? localDate() : tomorrow()) } : current)}>Einmalig</button><button type="button" className={recurring ? "active" : ""} onClick={() => setAlarmDraft(current => current ? { ...current, days: [1,2,3,4,5], date: "" } : current)}>Wiederholen</button></div>
        {recurring ? <div className="weekday-row">{[1,2,3,4,5,6,0].map(day => <button type="button" key={day} className={alarmDraft.days.includes(day) ? "active" : ""} onClick={() => setAlarmDraft(current => current ? { ...current, days: current.days.includes(day) ? current.days.filter(value => value !== day) : [...current.days, day].sort() } : current)}>{weekdayNames[day].slice(0,1)}</button>)}</div> : <><label className="field-label" htmlFor="alarm-date">Datum</label><input id="alarm-date" type="date" min={localDate()} value={alarmDraft.date} onChange={event => setAlarmDraft(current => current ? { ...current, date: event.target.value } : current)} /></>}
        <label className="field-label" htmlFor="alarm-challenge">So beweise ich, dass ich wach bin</label><select id="alarm-challenge" value={alarmDraft.challenge} onChange={event => setAlarmDraft(current => current ? { ...current, challenge: event.target.value as Challenge } : current)}>{Object.entries(challengeNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        {alarmDraft.challenge === "shake" && <NumberField label="Kräftige Bewegungen" value={alarmDraft.shakeCount} min={3} max={200} unit="×" onCommit={value => setAlarmDraft(current => current ? { ...current, shakeCount: value } : current)} />}
        {alarmDraft.challenge === "hold" && <NumberField label="Display halten" value={alarmDraft.holdSeconds} min={3} max={120} unit="Sek." onCommit={value => setAlarmDraft(current => current ? { ...current, holdSeconds: value } : current)} />}
        {alarmDraft.challenge === "snake" && <NumberField label="Schlange verfolgen" value={alarmDraft.snakeSeconds} min={3} max={120} unit="Sek." onCommit={value => setAlarmDraft(current => current ? { ...current, snakeSeconds: value } : current)} />}
        {alarmDraft.challenge === "nfc" && <section className={`nfc-enroll ${alarmDraft.nfcToken ? "ready" : ""}`}><div><strong>{alarmDraft.nfcToken ? "NFC-Tag bereit" : "NFC-Tag vorbereiten"}</strong><small>{alarmDraft.nfcToken ? "Nur dieser Tag beendet den Wecker." : "Halte den gewünschten Tag kurz an die Rückseite. Der Tag wird nicht beschrieben."}</small></div>{!alarmDraft.nfcToken && <button type="button" onClick={() => enrollNfc("alarm")}>Einmal anlernen</button>}</section>}
        {alarmDraft.challenge === "qr" && <section className="qr-alarm-preview qr-inline-card"><div><strong>Dein persönlicher QR-Code</strong><small>Dieser Code bleibt gleich. Prüfe einmal, dass dein Ausdruck scanbar ist, bevor du ihn als Weckschlüssel verwendest.</small></div><QRCode seed="wachwerk-personal-code" matrix={qrMatrix} />{!settings.qrVerified ? <button type="button" className="secondary-button" onClick={() => window.WachwerkAndroid?.verifyAlarmQr?.()}>Ausdruck einmal prüfen</button> : <small>✓ QR-Code schon erfolgreich geprüft</small>}<button type="button" className="file-picker-button" onClick={() => { if (window.WachwerkAndroid?.printCurrentPage) window.WachwerkAndroid.printCurrentPage(); else window.print(); }}>Als PDF drucken</button></section>}
        <div className="switch-row"><div><strong>Snooze</strong><small>{alarmDraft.snoozeEnabled ? `${alarmDraft.snoozeMinutes} Minuten${alarmDraft.snoozeAggressive ? " · wird kürzer" : ""}` : "Für diesen Wecker ausgeschaltet"}</small></div><Toggle on={alarmDraft.snoozeEnabled} label="Snooze" onClick={() => setAlarmDraft(current => current ? { ...current, snoozeEnabled: !current.snoozeEnabled } : current)} /></div>
        {alarmDraft.snoozeEnabled && <><NumberField label="Erste Snooze-Zeit" value={alarmDraft.snoozeMinutes} min={1} max={1440} onCommit={value => setAlarmDraft(current => current ? { ...current, snoozeMinutes: value, snoozeMinimumMinutes: Math.min(current.snoozeMinimumMinutes, value) } : current)} /><div className="switch-row"><div><strong>Abstände verkürzen</strong><small>Bei jedem Snooze aggressiver</small></div><Toggle on={alarmDraft.snoozeAggressive} label="Snooze verkürzen" onClick={() => setAlarmDraft(current => current ? { ...current, snoozeAggressive: !current.snoozeAggressive } : current)} /></div>{alarmDraft.snoozeAggressive && <NumberField label="Kleinste Snooze-Zeit" value={alarmDraft.snoozeMinimumMinutes} min={1} max={alarmDraft.snoozeMinutes} onCommit={value => setAlarmDraft(current => current ? { ...current, snoozeMinimumMinutes: value } : current)} />}</>}
        <label className="field-label" htmlFor="alarm-sound">Alarmton</label><select id="alarm-sound" value={alarmDraft.sound} onChange={event => setAlarmDraft(current => current ? { ...current, sound: event.target.value } : current)}>{alarmDraft.sound.startsWith("custom:") && <option value={alarmDraft.sound}>{soundLabel(alarmDraft.sound)}</option>}{settings.sound.startsWith("custom:") && settings.sound !== alarmDraft.sound && <option value={settings.sound}>{soundLabel(settings.sound)}</option>}<option>Systemstandard</option><option>Sanft</option><option>Klar</option><option>Kräftig</option></select><p className="field-help">Eigene Audiodateien verwaltest du in den Einstellungen.</p>
        <div className="switch-row"><div><strong>Sanftes Licht</strong><small>{alarmDraft.gentleMinutes} Minuten vorher</small></div><Toggle on={alarmDraft.gentleWake} label="Sanftes Licht" onClick={() => setAlarmDraft(current => current ? { ...current, gentleWake: !current.gentleWake } : current)} /></div>{alarmDraft.gentleWake && <NumberField label="Licht startet vorher" value={alarmDraft.gentleMinutes} min={1} max={180} onCommit={value => setAlarmDraft(current => current ? { ...current, gentleMinutes: value } : current)} />}
        <button type="submit" className="primary-button">{alarmDraft.id ? "Änderungen speichern" : "Wecker speichern"}</button>{alarmDraft.id && <button type="button" className="delete-button" onClick={() => deleteAlarm(alarmDraft.id!)}>Wecker löschen</button>}
      </form>
    </div>;
  }
  function renderTodoModal() {
    if (!todoModal) return null;
    return <div className={`modal-backdrop ${sheetClosing ? "closing" : ""}`} role="presentation" onPointerDown={event => { if (event.target === event.currentTarget) closeSheet("todo"); }}><form data-sheet-kind="todo" className={`modal-sheet compact-modal ${sheetClosing ? "closing" : ""}`} role="dialog" aria-modal="true" aria-label="Aufgabe hinzufügen" onSubmit={addTodo}><div className="modal-handle" aria-label="Nach unten ziehen zum Schließen"><i /></div><div className="modal-title"><div><span className="overline">{todoModalMode === "habit" ? "NEUES HABIT" : "NEUE AUFGABE"}</span><h2>{todoModalMode === "habit" ? "Was willst du täglich schaffen?" : "Was steht an?"}</h2></div></div><input autoFocus aria-label={todoModalMode === "habit" ? "Habit" : "Aufgabe"} type="text" placeholder={todoModalMode === "habit" ? "z. B. 10 Liegestütze" : "Aufgabe eingeben"} value={todoText} onChange={event => setTodoText(event.target.value)} />{todoModalMode === "todo" && <><label className="field-label" htmlFor="todo-reminder">Erinnerung (optional)</label><label className="reminder-picker"><span>{todoReminder ? new Date(todoReminder).toLocaleString("de-DE", { day:"2-digit", month:"2-digit", year:"numeric", hour:"2-digit", minute:"2-digit" }) : "Datum und Uhrzeit wählen"}<i>◷</i></span><input id="todo-reminder" aria-label="Erinnerung: Datum und Uhrzeit wählen" type="datetime-local" min={localDateTimeValue()} value={todoReminder} onChange={event => setTodoReminder(event.target.value)} /></label>{todoReminder && <button type="button" className="text-button" onClick={() => setTodoReminder("")}>Erinnerung entfernen</button>}<p className="field-help">Wenn du eine Zeit einträgst, erinnert dich Wachwerk lokal per Benachrichtigung.</p></>}{todoModalMode === "habit" && <p className="field-help habit-modal-help">Das Ziel erscheint jeden Tag neu. Deine vergangenen Haken bleiben für den Verlauf gespeichert.</p>}<button type="submit" className="primary-button">Hinzufügen</button></form></div>;
  }
  function renderExtraSheets() {
    if (screen !== "settings" && !chooseTodo) return null;
    const kind: SheetKind = chooseTodo ? "choose" : "settings";
    return <div className={`modal-backdrop ${sheetClosing ? "closing" : ""}`} onPointerDown={event => { if (event.target === event.currentTarget) closeSheet(kind); }}><section role="dialog" aria-modal="true" aria-label={chooseTodo ? "Was möchtest du hinzufügen?" : "Einstellungen"} data-sheet-kind={kind} className={`modal-sheet ${chooseTodo ? "composer-choice" : "settings-sheet"} ${sheetClosing ? "closing" : ""}`}><div className="modal-handle" aria-label="Nach unten ziehen zum Schließen"><i /></div>{chooseTodo ? <><span className="overline">HINZUFÜGEN</span><h2>Was möchtest du anlegen?</h2><button type="button" className="composer-option" onClick={() => {setChooseTodo(false);openTodoComposer("todo");}}><span>✓</span><div><strong>To-do</strong><small>Eine einzelne Aufgabe</small></div><i>→</i></button><button type="button" className="composer-option" onClick={() => {setChooseTodo(false);openTodoComposer("habit");}}><span>↻</span><div><strong>Habit</strong><small>Dein tägliches Ziel</small></div><i>→</i></button></> : renderSettings()}</section></div>;
  }
  function renderStandbyWidget(widget: StandbyWidget) {
    if (widget === "clock") return <div className="standby-clock-widget">{standbyClock === "digital" ? <><strong>{pad(now.getHours())}:{pad(now.getMinutes())}</strong><span>{now.toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" })}</span></> : <><div className="analog-clock"><i className="hour" style={{ transform: `rotate(${(now.getHours() % 12) * 30 + now.getMinutes() / 2}deg)` }} /><i className="minute" style={{ transform: `rotate(${now.getMinutes() * 6}deg)` }} /><i className="dot" /></div><span>{now.toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" })}</span></>}</div>;
    if (widget === "calendar") {
      const first = new Date(now.getFullYear(), now.getMonth(), 1);
      const leading = (first.getDay() + 6) % 7;
      const days = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
      return <div className="standby-calendar"><header><span>{now.toLocaleDateString("de-DE", { month: "long" }).toUpperCase()}</span><small>{now.getFullYear()}</small></header><div className="calendar-grid"><b>MO</b><b>DI</b><b>MI</b><b>DO</b><b>FR</b><b>SA</b><b>SO</b>{Array.from({ length: leading }, (_, index) => <i key={`blank-${index}`} />)}{Array.from({ length: days }, (_, index) => <i key={index + 1} className={index + 1 === now.getDate() ? "today" : ""}>{index + 1}</i>)}</div></div>;
    }
    if (widget === "alarm") return <div className="standby-metric"><span>NÄCHSTER WECKER</span><strong>{upcoming[0]?.alarm.time ?? "–:–"}</strong><small>{upcoming[0]?.alarm.label ?? "Kein Wecker aktiv"}</small></div>;
    if (widget === "rhythm") return <div className="standby-metric"><span>AUFSTEH-ERFOLG</span><strong>{checkins.length ? `${analysis.success}%` : "–"}</strong><small>direkt aufgestanden</small></div>;
    if (widget === "cycles") return <div className="standby-metric"><span>SCHLAFBEDARF</span><strong>{checkins.length ? formatDuration(analysis.need) : "–"}</strong><small>{analysis.confidence}% Sicherheit</small></div>;
    if (widget === "sleep") return <div className="standby-metric"><span>SCHLAFENSZEIT</span><strong>{settings.bedtime}</strong><small>{settings.remindersOn ? "Erinnerung aktiv" : "Erinnerung aus"}</small></div>;
    if (widget === "focus") return <div className={`standby-focus-widget ${focusState.active ? "active" : ""}`}><span>{focusState.active ? (focusState.phase === "work" ? "FOKUS" : "PAUSE") : "FOKUS-TIMER"}</span><strong>{focusState.active ? (focusState.ringing ? "00:00" : formatCountdown(focusState.endAt - now.getTime())) : "–:––"}</strong><small>{focusState.active ? `Runde ${focusState.round} von ${focusState.rounds}` : "Keine Session aktiv"}</small></div>;
    return <div className="standby-metric"><span>AUFGABEN</span><strong>{todos.filter(item => !item.done).length}</strong><small>noch offen</small></div>;
  }

  if (nightMode) {
    const selectedWidget = standbyEditing === "right" ? standbyRight : standbyLeft;
    const focusInCard = standbyLeft === "focus" || standbyRight === "focus";
    return <div className={`standby-screen tone-${standbyTone} standby-font-${standbyFont}`} onPointerUp={handleStandbyTap}>{focusState.active && !focusInCard && <div className="standby-focus-island"><i /><span>{focusState.phase === "work" ? "Fokus" : "Pause"}</span><strong>{focusState.ringing ? "00:00" : formatCountdown(focusState.endAt - now.getTime())}</strong><em>Runde {focusState.round}/{focusState.rounds}</em></div>}<div className="standby-grid"><article className={`standby-card ${standbyEditing === "left" ? "editing" : ""}`} onPointerDown={() => beginStandbyPress("left")} onPointerUp={endStandbyPress} onPointerCancel={endStandbyPress}>{renderStandbyWidget(standbyLeft)}</article><article className={`standby-card ${standbyEditing === "right" ? "editing" : ""}`} onPointerDown={() => beginStandbyPress("right")} onPointerUp={endStandbyPress} onPointerCancel={endStandbyPress}>{renderStandbyWidget(standbyRight)}</article></div>{standbyEditing && <section className="standby-panel" onPointerUp={event => event.stopPropagation()}><div className="standby-panel-title"><h3>{standbyEditing === "left" ? "Linke Seite" : "Rechte Seite"}</h3><small>Außen tippen speichert</small></div><label>Widget<select value={selectedWidget} onChange={event => standbyEditing === "left" ? setStandbyLeft(event.target.value as StandbyWidget) : setStandbyRight(event.target.value as StandbyWidget)}>{(Object.entries(standbyWidgetNames) as [StandbyWidget, string][]).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>{selectedWidget === "clock" && <label>Uhr-Stil<select value={standbyClock} onChange={event => setStandbyClock(event.target.value as "digital" | "analog")}><option value="digital">Digital</option><option value="analog">Analog</option></select></label>}{selectedWidget === "clock" && <label>Uhr-Schrift<select value={standbyFont} onChange={event => setStandbyFont(event.target.value as typeof standbyFont)}><option value="apple">Apple-artig</option><option value="soft">Weich</option><option value="mono">Monospace</option></select></label>}<div className="tone-field"><span>Farbe</span><div className="tone-row">{(["blue","amber","mint","rose"] as const).map(tone => <button type="button" aria-label={tone} key={tone} className={`tone-${tone} ${standbyTone === tone ? "active" : ""}`} onClick={() => setStandbyTone(tone)} />)}</div></div></section>}<small className="standby-hint">Gedrückt halten: Seite bearbeiten · Antippen: zurück</small></div>;
  }

  return <div className="app-shell"><main className={`app-root app-font-${settings.appFont}`}><div className={`app-screen screen-${screen}`} ref={scrollRef}>{alarmRinging && <button className="ringing-banner" type="button" onClick={() => window.WachwerkAndroid?.openRingingAlarm?.()}><span>◷</span><span><strong>Dein Wecker klingelt</strong><small>Zur Aufwachaufgabe</small></span></button>}{(screen === "home" || screen === "settings") && renderHome()}{screen === "alarms" && renderAlarms()}{screen === "coach" && renderCoach()}{screen === "todos" && renderTodos()}{screen === "blocker" && renderBlocker()}{screen === "qr" && renderQR()}</div><nav className="bottom-nav">{navItems.map(item => <button type="button" key={item.id} className={screen === item.id ? "active" : ""} onClick={() => setScreen(item.id)}><span>{item.icon}</span><small>{item.label}</small></button>)}</nav>{renderAlarmModal()}{renderTodoModal()}{renderExtraSheets()}{toast && <div className="toast">{toast}</div>}</main></div>;
}
