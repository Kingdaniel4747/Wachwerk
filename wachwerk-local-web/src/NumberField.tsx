import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useSheetSwipe } from "./useSheetSwipe";
import { angleDelta, clampDial } from "./workflow";

export default function NumberField({ id, label, value, onCommit, min = 0, max = 1440, unit = "Min.", className = "", disabled = false }: { id?: string; label: string; value: number; onCommit: (value: number) => void; min?: number; max?: number; unit?: string; className?: string; disabled?: boolean }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value);
  const [closing, setClosing] = useState(false);
  const drag = useRef<{ angle: number; remainder: number } | null>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);
  const change = (delta: number) => setDraft(old => clampDial(old + delta, min, max));
  function close(save = true) {
    if (closing) return;
    if (save) onCommit(draft);
    setClosing(true);
    window.setTimeout(() => { setOpen(false); setClosing(false); trigger.current?.focus(); }, 230);
  }
  useSheetSwipe(open, '[data-sheet-kind="number"]', () => close());
  useEffect(() => {
    if (!open) return;
    dialog.current?.focus();
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") { event.stopPropagation(); close(false); } };
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [open]);
  const angle = (element: HTMLElement, x: number, y: number) => { const r = element.getBoundingClientRect(); return Math.atan2(y-r.top-r.height/2, x-r.left-r.width/2)*180/Math.PI; };
  return <div className={`number-field ${className}`}><span>{label}</span><button ref={trigger} id={id} type="button" className="number-trigger" disabled={disabled} aria-label={`${label || "Wert"}: ${value} ${unit}, ändern`} onClick={() => { setDraft(value); setOpen(true); }}><strong>{value}</strong><small>{unit}</small></button>{open && createPortal(<div className={`modal-backdrop rotary-backdrop ${closing ? "closing" : ""}`} onPointerDown={e => { if (e.target === e.currentTarget) close(); }}><div ref={dialog} data-sheet-kind="number" tabIndex={-1} role="dialog" aria-modal="true" aria-label={label || "Wert wählen"} className={`modal-sheet rotary-sheet ${closing ? "closing" : ""}`} onKeyDown={e => { if(e.key === "Tab") { const buttons = e.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled),[tabindex="0"]'); const first=buttons[0], last=buttons[buttons.length-1]; if(e.shiftKey && document.activeElement === first){e.preventDefault();last?.focus();} else if(!e.shiftKey && document.activeElement === last){e.preventDefault();first?.focus();} } }}><div className="modal-handle" role="button" tabIndex={0} aria-label="Speichern und schließen" onKeyDown={e => { if (e.key === "Enter" || e.key === " ") close(); }}><i /></div><h2>{label || "Wert wählen"}</h2><p>Drehen · außerhalb tippen speichert</p><div className="rotary-dial" role="slider" tabIndex={0} aria-label={label || "Wert"} aria-valuenow={draft} aria-valuemin={min} aria-valuemax={max} aria-valuetext={`${draft} ${unit}`} onKeyDown={e => {if(["ArrowUp","ArrowRight","ArrowDown","ArrowLeft","PageUp","PageDown","Home","End"].includes(e.key)){e.preventDefault();if(e.key==="Home")setDraft(min);else if(e.key==="End")setDraft(max);else change(e.key==="PageUp"?10:e.key==="PageDown"?-10:["ArrowUp","ArrowRight"].includes(e.key)?1:-1);}}} onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId);drag.current={angle:angle(e.currentTarget,e.clientX,e.clientY),remainder:0}; }} onPointerMove={e => {if(!drag.current)return;const next=angle(e.currentTarget,e.clientX,e.clientY);const delta=angleDelta(next,drag.current.angle);drag.current.angle=next;drag.current.remainder+=delta;const steps=Math.trunc(drag.current.remainder/6);if(steps){drag.current.remainder-=steps*6;change(steps);}}} onPointerUp={() => {drag.current=null;}} onPointerCancel={() => {drag.current=null;}}><div className="dial-ticks" style={{transform:`rotate(${draft*6}deg)`}}>{Array.from({length:60},(_,i)=><i key={i} style={{transform:`rotate(${i*6}deg)`}} className={i%5===0?"major":""}/>)}</div><span className="dial-marker"/><div className="dial-value"><strong key={draft}>{draft}</strong><small>{unit}</small></div></div></div></div>,document.body)}</div>;
}
