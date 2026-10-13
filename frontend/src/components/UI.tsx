import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function Heading({
  eyebrow = "SDET–BATCH 16",
  title,
  detail,
}: {
  eyebrow?: string;
  title: string;
  detail: string;
}) {
  return (
    <div className="heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="intro">{detail}</p>
      </div>
      <span className="semester">Fall 2026</span>
    </div>
  );
}
export function Badge({
  children,
  tone = "",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={`badge ${tone}`}>{children}</span>;
}
export function Stat({
  label,
  value,
  suffix,
  note,
}: {
  label: string;
  value: ReactNode;
  suffix?: string;
  note?: string;
}) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <strong>
        {value}
        {suffix && <small>{suffix}</small>}
      </strong>
      {note && <span className="stat-note">{note}</span>}
    </div>
  );
}
export function NotFound({ assignment = false }: { assignment?: boolean }) {
  return (
    <>
      <h1>{assignment ? "Assignment" : "Page"} not found</h1>
      <Link className="button" to="/demo/assignments">
        Back to assignments
      </Link>
    </>
  );
}
