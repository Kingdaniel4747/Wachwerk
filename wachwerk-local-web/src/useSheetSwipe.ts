import { useEffect, useRef } from "react";

// Start a close gesture only at the scroll boundary (or on the enlarged handle).
// A gesture which started as scrolling never turns into dismissal halfway through.
export function useSheetSwipe(enabled: boolean, selector: string, dismiss: (sheet: HTMLElement) => void) {
  const callback = useRef(dismiss);
  callback.current = dismiss;
  useEffect(() => {
    if (!enabled) return;
    let gesture: { sheet: HTMLElement; x: number; y: number; distance: number; dragging: boolean } | null = null;
    const start = (target: EventTarget | null, x: number, y: number) => {
      const element = target instanceof Element ? target : null;
      const sheet = element?.closest<HTMLElement>(".modal-sheet");
      if (!sheet?.matches(selector) || sheet.classList.contains("closing")) return;
      const handle = element?.closest(".modal-handle,.modal-title,.screen-header,.rotary-sheet h2,.composer-choice > h2");
      if (!handle && (sheet.scrollTop > 8 || element?.closest("button,input,select,textarea,a,[role=slider],.rotary-dial"))) return;
      gesture = { sheet, x, y, distance: 0, dragging: false };
    };
    const move = (x: number, y: number, event: Event) => {
      if (!gesture) return;
      const dy = y - gesture.y, dx = Math.abs(x - gesture.x);
      if (!gesture.dragging) {
        if (dy < -8 || dx > Math.max(12, Math.abs(dy))) { gesture = null; return; }
        if (dy < 14) return;
        gesture.dragging = true;
        gesture.sheet.classList.add("dragging");
      }
      if (event.cancelable) event.preventDefault();
      gesture.distance = Math.max(0, dy);
      gesture.sheet.style.transform = `translate3d(0,${gesture.distance}px,0)`;
    };
    const finish = (cancelled = false) => {
      if (!gesture) return;
      const { sheet, distance, dragging } = gesture;
      gesture = null;
      sheet.classList.remove("dragging");
      if (dragging && !cancelled && distance >= 80) callback.current(sheet);
      else sheet.style.removeProperty("transform");
    };
    const touchStart = (e: TouchEvent) => { if (e.touches.length === 1) start(e.target, e.touches[0].clientX, e.touches[0].clientY); else finish(true); };
    const touchMove = (e: TouchEvent) => { if (e.touches.length === 1) move(e.touches[0].clientX, e.touches[0].clientY, e); };
    const touchEnd = () => finish();
    const cancel = () => finish(true);
    const mouseStart = (e: PointerEvent) => { if (e.pointerType === "mouse" && e.button === 0) start(e.target, e.clientX, e.clientY); };
    const mouseMove = (e: PointerEvent) => { if (e.pointerType === "mouse") move(e.clientX, e.clientY, e); };
    const mouseEnd = (e: PointerEvent) => { if (e.pointerType === "mouse") finish(); };
    document.addEventListener("touchstart", touchStart, { passive: true });
    document.addEventListener("touchmove", touchMove, { passive: false });
    document.addEventListener("touchend", touchEnd);
    document.addEventListener("touchcancel", cancel);
    document.addEventListener("pointerdown", mouseStart);
    document.addEventListener("pointermove", mouseMove);
    document.addEventListener("pointerup", mouseEnd);
    return () => {
      finish(true);
      document.removeEventListener("touchstart", touchStart);
      document.removeEventListener("touchmove", touchMove);
      document.removeEventListener("touchend", touchEnd);
      document.removeEventListener("touchcancel", cancel);
      document.removeEventListener("pointerdown", mouseStart);
      document.removeEventListener("pointermove", mouseMove);
      document.removeEventListener("pointerup", mouseEnd);
    };
  }, [enabled, selector]);
}
