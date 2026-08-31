/* eslint-disable */
/* Uni Hi — alle Screens & UI-Primitives */

const { useState, useEffect, useRef } = React;

/* ── ICONS ───────────────────────────────────────────────────────── */
const PATHS = {
  home:     "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10",
  calendar: "M3 4h18a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M16 2v4 M8 2v4 M3 10h18",
  utensils: "M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2 M7 2v20 M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3zm0 0v7",
  book:     "M4 19.5A2.5 2.5 0 0 1 6.5 17H20 M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z",
  award:    "M12 15 A7 7 0 1 0 12 1 A7 7 0 0 0 12 15 M8.21 13.89L7 23l5-3 5 3-1.21-9.12",
  bell:     "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0",
  clock:    "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 6v6l4 2",
  mapPin:   "M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
  users:    "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75",
  alert:    "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 8v4 M12 16h.01",
  chevR:    "M9 18l6-6-6-6",
  chevL:    "M15 18l-6-6 6-6",
  check:    "M20 6L9 17l-5-5",
  plus:     "M12 5v14 M5 12h14",
  mail:     "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z M22 6l-10 7L2 6",
  film:     "M3 3h18v18H3z M7 3v18 M17 3v18 M3 8h4 M3 16h4 M17 8h4 M17 16h4 M3 12h18",
  list:     "M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01",
  search:   "M21 21l-6-6 M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0z",
  inbox:    "M22 12h-6l-2 3h-4l-2-3H2 M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
  star:     "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77 5.82 21.02 7 14.14 2 9.27l6.91-1.01L12 2z",
  trash:    "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
  play:     "M5 3l14 9-14 9V3z",
  user:     "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
  card:     "M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M2 10h20",
  map:      "M9 4l-6 3v13l6-3 6 3 6-3V4l-6 3-6-3z M9 4v13 M15 7v13",
  settings: "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
  grip:     "M9 4h.01 M9 9h.01 M9 14h.01 M9 19h.01 M15 4h.01 M15 9h.01 M15 14h.01 M15 19h.01",
  signOut:  "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9",
  building: "M3 21h18 M5 21V7l8-4v18 M19 21V11l-6-4",
  qr:       "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h2v2h-2z M18 14h3 M14 18h2v3 M19 18v3",
  gradCap:  "M22 10v6 M2 10l10-5 10 5-10 5z M6 12v5c0 1.6 3 3 6 3s6-1.4 6-3v-5",
  dumbbell: "M6 4v16 M18 4v16 M2 8v8 M22 8v8 M6 12h12",
  chart:    "M3 3v18h18 M7 14l4-4 4 4 5-7",
};

/* ── ALL TABS CATALOG ─────────────────────────────────────────────── */
const TAB_CATALOG = [
  { id:'home',        label:'Start',       icon:'home' },
  { id:'stundenplan', label:'Stundenplan', icon:'calendar' },
  { id:'mensa',       label:'Mensa',       icon:'utensils' },
  { id:'bibliothek',  label:'Bibliothek',  icon:'book' },
  { id:'kurse',       label:'Kurse',       icon:'award' },
  { id:'mail',        label:'Mail',        icon:'mail' },
  { id:'todo',        label:'Aufgaben',    icon:'list' },
  { id:'kino',        label:'Uni Kino',    icon:'film' },
  { id:'campus',      label:'Campus',      icon:'map' },
  { id:'profil',      label:'Profil',      icon:'user' },
  { id:'mensacard',   label:'Mensa-Karte', icon:'card' },
  { id:'lerngruppen', label:'Lerngruppen', icon:'users' },
  { id:'klausuren',   label:'Klausuren',   icon:'gradCap' },
  { id:'push',        label:'Benachrichtigungen', icon:'bell' },
  { id:'sport',       label:'Hochschulsport',     icon:'dumbbell' },
  { id:'noten',       label:'Notenübersicht',  icon:'chart' },
];

function Icon({ name, size=22, color='currentColor', fill='none', sw=2 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">
      {(PATHS[name]||'').split(' M').map((seg, i) => <path key={i} d={i===0?seg:'M'+seg} />)}
    </svg>
  );
}

function StarFill({ size=12, color }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill={color} stroke="none">
    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
  </svg>;
}

/* ── STATUS BAR ─────────────────────────────────────────────────── */
function StatusBar({ C }) {
  return (
    <div style={{ position:'relative', height:54, padding:'14px 28px 0', display:'flex', alignItems:'center', justifyContent:'space-between', background:C.surface, flexShrink:0, zIndex:2 }}>
      <span style={{ fontSize:15, fontWeight:700, color:C.text }}>9:41</span>
      <div style={{ width:88, height:28, background:C.text, borderRadius:14, position:'absolute', left:'50%', transform:'translateX(-50%)', top:10 }}></div>
      <div style={{ display:'flex', alignItems:'center', gap:6 }}>
        <svg width={15} height={13} viewBox="0 0 15 13" fill={C.text}>
          <rect x="0" y="8" width="3" height="5" rx="1"/>
          <rect x="4" y="5" width="3" height="8" rx="1"/>
          <rect x="8" y="2" width="3" height="11" rx="1"/>
          <rect x="12" y="0" width="3" height="13" rx="1"/>
        </svg>
        <svg width={15} height={13} viewBox="0 0 24 24" fill="none" stroke={C.text} strokeWidth={2.5} strokeLinecap="round">
          <path d="M5 12.55a11 11 0 0 1 14.08 0"/>
          <path d="M1.42 9a16 16 0 0 1 21.16 0"/>
          <path d="M8.53 16.11a6 6 0 0 1 6.95 0"/>
          <path d="M12 20h.01"/>
        </svg>
        <svg width={25} height={13} viewBox="0 0 25 13" fill="none">
          <rect x="0.5" y="0.5" width="21" height="12" rx="3.5" stroke={C.text} strokeWidth="1.2"/>
          <rect x="22" y="4" width="3" height="5" rx="1.5" fill={C.text}/>
          <rect x="2" y="2" width="16" height="9" rx="2" fill={C.text}/>
        </svg>
      </div>
    </div>
  );
}

/* ── BOTTOM NAV ──────────────────────────────────────────────────── */
function BottomNav({ activeTab, setActiveTab, C, navTabs }) {
  const tabs = (navTabs || ['home','stundenplan','mensa','bibliothek','kurse'])
    .map(id => TAB_CATALOG.find(t => t.id === id))
    .filter(Boolean);
  return (
    <div style={{ height:83, background:C.surface, borderTop:`1px solid ${C.border}`, display:'flex', alignItems:'flex-start', paddingTop:10, flexShrink:0 }}>
      {tabs.map(t => {
        const active = activeTab === t.id;
        return (
          <div key={t.id} className="tap" onClick={() => setActiveTab(t.id)}
            style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:3 }}>
            <div style={{ width:36, height:32, display:'flex', alignItems:'center', justifyContent:'center', borderRadius:10, background: active ? C.primaryLight : 'transparent', transition:'background 0.2s' }}>
              <Icon name={t.icon} size={20} color={active ? C.primary : C.textMuted} sw={active?2.4:2} />
            </div>
            <span style={{ fontSize:10, fontWeight: active?700:500, color: active?C.primary:C.textMuted }}>{t.label}</span>
          </div>
        );
      })}
    </div>
  );
}

/* ── COMMON ──────────────────────────────────────────────────────── */
function SectionLabel({ label, C, action, onAction }) {
  return (
    <div style={{ display:'flex', justifyContent:'space-between', alignItems:'baseline', marginBottom:10 }}>
      <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{label}</p>
      {action && <p className="tap" onClick={onAction} style={{ fontSize:12, fontWeight:600, color:C.primary }}>{action}</p>}
    </div>
  );
}

function SubHeader({ title, subtitle, onBack, C, color, bg, action }) {
  return (
    <div style={{ background: bg || C.surface, padding:'20px 22px', borderBottom:`1px solid ${C.border}` }}>
      <div className="tap" onClick={onBack} style={{ display:'inline-flex', alignItems:'center', gap:5, marginBottom:14, fontSize:13, fontWeight:600, color: color || C.textMuted }}>
        <Icon name="chevL" size={16} color={color || C.textMuted} />
        Zurück
      </div>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-end' }}>
        <div>
          <h1 style={{ fontSize:22, fontWeight:800, color: color || C.text, lineHeight:1.15 }}>{title}</h1>
          {subtitle && <p style={{ fontSize:12, color: color || C.textMuted, opacity: color?0.7:1, marginTop:3, fontWeight:500 }}>{subtitle}</p>}
        </div>
        {action}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* HOME SCREEN                                                         */
/* ══════════════════════════════════════════════════════════════════ */
function HomeScreen({ C, CC, R, tweaks, goto, schedule, news, todos, kino, mails, notifications }) {
  const today = schedule['Mo'];
  const unreadMail = mails.filter(m => m.unread).length;
  const unreadPush = (notifications || []).filter(n => n.unread).length;
  const openTodos = todos.filter(t => !t.done).length;
  const upcomingKino = kino.slice(0, 4);
  const previewTodos = todos.filter(t => !t.done).slice(0, 3);

  return (
    <div style={{ paddingBottom:24 }}>
      {/* Header */}
      <div style={{ background:C.surface, padding:'18px 22px 22px', borderRadius:`0 0 ${R.big}px ${R.big}px`, marginBottom:18 }}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start' }}>
          <div>
            <p style={{ fontSize:11, color:C.textMuted, fontWeight:700, letterSpacing:'0.06em', marginBottom:5 }}>MONTAG · 18. MAI 2026</p>
            <h1 style={{ fontSize:25, fontWeight:800, color:C.text, lineHeight:1.15 }}>
              {tweaks.greeting},{'\u00A0'}<span style={{ color:C.primary }}>{tweaks.studentName}!</span>
            </h1>
          </div>
          <div style={{ display:'flex', gap:8 }}>
            <div className="tap" onClick={() => goto('profil')} style={{ width:44, height:44, borderRadius:R.tile, background:C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center', fontSize:15, fontWeight:800, color:C.primary }}>
              {tweaks.studentName.slice(0,2).toUpperCase()}
            </div>
            <div className="tap" onClick={() => goto('push')} style={{ position:'relative', width:44, height:44, borderRadius:R.tile, background:C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center' }}>
              <Icon name="bell" size={20} color={C.primary} />
              {unreadPush > 0 && (
                <span style={{ position:'absolute', top:-4, right:-4, minWidth:18, height:18, padding:'0 5px', background:C.red, color:'white', fontSize:10, fontWeight:800, borderRadius:9, display:'flex', alignItems:'center', justifyContent:'center', border:`2px solid ${C.surface}` }}>{unreadPush}</span>
              )}
            </div>
          </div>
        </div>
        <div style={{ marginTop:18, background:C.primary, borderRadius:R.card, padding:'14px 18px', display:'flex', gap:14, alignItems:'center' }}>
          <div style={{ width:44, height:44, background:'rgba(255,255,255,0.18)', borderRadius:R.tile, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <Icon name="clock" size={22} color="white" />
          </div>
          <div>
            <p style={{ fontSize:10, fontWeight:700, color:'rgba(255,255,255,0.65)', letterSpacing:'0.06em', marginBottom:3 }}>NÄCHSTE VORLESUNG · IN 47 MIN</p>
            <p style={{ fontSize:15, fontWeight:700, color:'white' }}>Lineare Algebra</p>
            <p style={{ fontSize:12, color:'rgba(255,255,255,0.72)', marginTop:2 }}>8:00 Uhr · Raum B 201</p>
          </div>
        </div>
      </div>

      {/* Quick tiles */}
      <div style={{ padding:'0 18px', marginBottom:18 }}>
        <SectionLabel label="Schnellzugriff" C={C} />
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:10 }}>
          <QuickCard icon="utensils" label="Mensa heute"   sub="5 Gerichte verfügbar"     color={C.amber}   bg={C.amberLight}   C={C} R={R} onClick={() => goto('mensa')} />
          <QuickCard icon="book"     label="Bibliothek"    sub="4 von 6 Räumen frei"      color={C.green}   bg={C.greenLight}   C={C} R={R} onClick={() => goto('bibliothek')} />
          <QuickCard icon="mail"     label="Mails"         sub={`${unreadMail} ungelesen`} color={C.primary} bg={C.primaryLight} C={C} R={R} onClick={() => goto('mail')} badge={unreadMail} />
          <QuickCard icon="list"     label="Aufgaben"      sub={`${openTodos} offen`}      color={C.purple}  bg={C.purpleLight}  C={C} R={R} onClick={() => goto('todo')} />
        </div>
      </div>

      {/* Heute */}
      <div style={{ padding:'0 18px', marginBottom:18 }}>
        <SectionLabel label="Heute" C={C} action="Alle anzeigen" onAction={() => goto('stundenplan')} />
        {today.map(course => {
          const col = CC[course.id];
          return (
            <div key={course.id} className="tap" onClick={() => goto('stundenplan')} style={{ background:C.surface, borderRadius:R.card, padding:'13px 15px', marginBottom:8, display:'flex', gap:12, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ width:4, height:44, background:col.dot, borderRadius:4, flexShrink:0 }}></div>
              <div style={{ flex:1 }}>
                <div style={{ display:'flex', justifyContent:'space-between' }}>
                  <p style={{ fontSize:14, fontWeight:700, color:C.text }}>{course.name}</p>
                  <p style={{ fontSize:13, fontWeight:700, color:col.fg }}>{course.start}:00</p>
                </div>
                <p style={{ fontSize:12, color:C.textMuted, marginTop:2 }}>Raum {course.room} · {course.prof}</p>
              </div>
            </div>
          );
        })}
      </div>

      {/* Uni Kino */}
      {tweaks.showKino && (
        <div style={{ marginBottom:18 }}>
          <div style={{ padding:'0 18px' }}>
            <SectionLabel label="Uni Kino" C={C} action="Programm" onAction={() => goto('kino')} />
          </div>
          <div style={{ display:'flex', gap:10, overflowX:'auto', padding:'0 18px 6px', scrollSnapType:'x mandatory' }} className="scroll-area">
            {upcomingKino.map(film => (
              <div key={film.id} className="tap" onClick={() => goto('kino')} style={{ flexShrink:0, width:160, scrollSnapAlign:'start' }}>
                <div style={{ width:160, height:200, borderRadius:R.card, background:`linear-gradient(150deg, ${film.color} 0%, oklch(from ${film.color} calc(l - 0.18) c h) 100%)`, padding:'14px', display:'flex', flexDirection:'column', justifyContent:'space-between', position:'relative', overflow:'hidden' }}>
                  <span style={{ alignSelf:'flex-start', fontSize:10, fontWeight:700, padding:'3px 8px', background:'rgba(255,255,255,0.22)', color:'white', borderRadius:6, backdropFilter:'blur(4px)' }}>{film.genre}</span>
                  <div>
                    <p style={{ fontSize:14, fontWeight:800, color:'white', lineHeight:1.15, marginBottom:4 }}>{film.title}</p>
                    <p style={{ fontSize:10, color:'rgba(255,255,255,0.78)', fontWeight:600 }}>{film.date}</p>
                  </div>
                  <div style={{ position:'absolute', top:-30, right:-30, width:80, height:80, borderRadius:'50%', background:'rgba(255,255,255,0.07)' }}></div>
                </div>
                <p style={{ fontSize:11, color:C.textMuted, marginTop:8, fontWeight:600 }}>{film.time} · {film.room}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Offene Aufgaben Preview */}
      {tweaks.showTodo && previewTodos.length > 0 && (
        <div style={{ padding:'0 18px', marginBottom:18 }}>
          <SectionLabel label="Offene Aufgaben" C={C} action="Alle" onAction={() => goto('todo')} />
          <div style={{ background:C.surface, borderRadius:R.card, padding:'4px 0', boxShadow:`0 2px 10px ${C.shadow}` }}>
            {previewTodos.map((todo, i) => {
              const col = todo.courseId ? CC[todo.courseId] : null;
              return (
                <div key={todo.id} className="tap" onClick={() => goto('todo')} style={{ display:'flex', alignItems:'center', gap:12, padding:'12px 15px', borderBottom: i < previewTodos.length - 1 ? `1px solid ${C.border}` : 'none' }}>
                  <div style={{ width:20, height:20, borderRadius:'50%', border:`2px solid ${col ? col.dot : C.textMuted}`, flexShrink:0 }}></div>
                  <div style={{ flex:1 }}>
                    <p style={{ fontSize:13, fontWeight:600, color:C.text }}>{todo.title}</p>
                    {todo.due && <p style={{ fontSize:11, color: todo.dueColor === 'red' ? C.red : todo.dueColor === 'amber' ? C.amber : C.textMuted, marginTop:2, fontWeight:600 }}>{todo.due}</p>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* News */}
      {tweaks.showNews && (
        <div style={{ padding:'0 18px' }}>
          <SectionLabel label="Neuigkeiten" C={C} />
          {news.map(item => (
            <div key={item.id} style={{ background:C.surface, borderRadius:R.card, padding:'13px 15px', marginBottom:8, boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ display:'flex', gap:8, alignItems:'flex-start' }}>
                {item.urgent && <div style={{ flexShrink:0, marginTop:1 }}><Icon name="alert" size={15} color={C.red} /></div>}
                <div style={{ flex:1 }}>
                  <div style={{ display:'flex', justifyContent:'space-between', gap:8 }}>
                    <p style={{ fontSize:13, fontWeight:700, color:C.text, flex:1 }}>{item.title}</p>
                    <p style={{ fontSize:10, color:C.textMuted, flexShrink:0, fontWeight:600 }}>{item.date}</p>
                  </div>
                  <p style={{ fontSize:12, color:C.textMuted, marginTop:3, lineHeight:1.45 }}>{item.desc}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function QuickCard({ icon, label, sub, color, bg, C, R, onClick, badge }) {
  return (
    <div className="tap" onClick={onClick} style={{ background:bg, borderRadius:R.card, padding:'14px', position:'relative' }}>
      <div style={{ width:38, height:38, background:C.surface, borderRadius:R.tile, display:'flex', alignItems:'center', justifyContent:'center', marginBottom:10, boxShadow:`0 2px 8px ${color}44` }}>
        <Icon name={icon} size={18} color={color} />
      </div>
      <p style={{ fontSize:13, fontWeight:700, color:C.text, marginBottom:2 }}>{label}</p>
      <p style={{ fontSize:11, color:C.textMuted }}>{sub}</p>
      {badge > 0 && (
        <span style={{ position:'absolute', top:10, right:12, minWidth:18, height:18, padding:'0 5px', background:color, color:'white', fontSize:10, fontWeight:800, borderRadius:9, display:'flex', alignItems:'center', justifyContent:'center' }}>{badge}</span>
      )}
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* STUNDENPLAN — Tag/Woche/Monat                                       */
/* ══════════════════════════════════════════════════════════════════ */
function StundenplanScreen({ C, CC, R, schedule }) {
  const [view, setView] = useState('tag');

  return (
    <div>
      <div style={{ background:C.surface, padding:'18px 22px 0', borderBottom:`1px solid ${C.border}` }}>
        <h1 style={{ fontSize:22, fontWeight:800, color:C.text, marginBottom:14 }}>Stundenplan</h1>
        {/* View tabs */}
        <div style={{ display:'flex', gap:4, background:C.surfaceAlt, padding:4, borderRadius:R.tile, marginBottom:14 }}>
          {[['tag','Tag'],['woche','Woche'],['monat','Monat']].map(([id, lbl]) => (
            <div key={id} className="tap" onClick={() => setView(id)} style={{ flex:1, textAlign:'center', padding:'7px 0', borderRadius:R.tile - 4, background: view === id ? C.surface : 'transparent', fontSize:12, fontWeight:700, color: view === id ? C.text : C.textMuted, boxShadow: view === id ? `0 2px 6px ${C.shadow}` : 'none', transition:'all 0.18s' }}>{lbl}</div>
          ))}
        </div>
      </div>
      {view === 'tag'   && <DayView C={C} CC={CC} R={R} schedule={schedule} />}
      {view === 'woche' && <WeekView C={C} CC={CC} R={R} schedule={schedule} />}
      {view === 'monat' && <MonthView C={C} CC={CC} R={R} schedule={schedule} />}
    </div>
  );
}

function DayView({ C, CC, R, schedule }) {
  const [day, setDay] = useState('Mo');
  const days = ['Mo','Di','Mi','Do','Fr'];
  const dates = { Mo:'18', Di:'19', Mi:'20', Do:'21', Fr:'22' };
  const courses = schedule[day] || [];
  const PX = 60; const START = 8; const END = 18;
  const slots = [8,10,12,14,16,18];

  return (
    <div>
      <div style={{ background:C.surface, padding:'0 22px 14px', borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', gap:4 }}>
          {days.map(d => {
            const active = day === d;
            const isToday = d === 'Mo';
            return (
              <div key={d} className="tap" onClick={() => setDay(d)}
                style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:4, padding:'8px 2px', borderRadius:R.tile, background: active ? C.primary : 'transparent' }}>
                <span style={{ fontSize:10, fontWeight:600, color: active ? 'rgba(255,255,255,0.72)' : C.textMuted }}>{d}</span>
                <span style={{ fontSize:17, fontWeight:800, color: active ? 'white' : isToday ? C.primary : C.text }}>{dates[d]}</span>
                <div style={{ width:4, height:4, borderRadius:2, background: isToday && !active ? C.primary : 'transparent' }}></div>
              </div>
            );
          })}
        </div>
      </div>
      <div style={{ padding:'18px 16px 28px' }}>
        {courses.length === 0 ? (
          <div style={{ textAlign:'center', padding:'64px 0', color:C.textMuted }}>
            <div style={{ fontSize:38, marginBottom:12, opacity:0.3 }}>—</div>
            <p style={{ fontSize:15, fontWeight:700 }}>Keine Veranstaltungen</p>
            <p style={{ fontSize:12, marginTop:5, opacity:0.7 }}>Freier Tag!</p>
          </div>
        ) : (
          <div style={{ display:'flex', gap:10 }}>
            <div style={{ width:36, position:'relative', height:(END-START)*PX, flexShrink:0 }}>
              {slots.map(h => (
                <div key={h} style={{ position:'absolute', top:(h-START)*PX - 7, right:0, fontSize:10, fontWeight:600, color:C.textMuted, textAlign:'right', width:36 }}>{h}:00</div>
              ))}
            </div>
            <div style={{ flex:1, position:'relative', height:(END-START)*PX }}>
              {slots.map(h => (
                <div key={h} style={{ position:'absolute', top:(h-START)*PX, left:0, right:0, height:1, background:C.border }} />
              ))}
              {courses.map(c => {
                const col = CC[c.id];
                const top = (c.start-START)*PX + 4;
                const ht  = (c.end-c.start)*PX - 8;
                return (
                  <div key={c.id} style={{ position:'absolute', top, left:0, right:0, height:ht, background:col.bg, borderRadius:R.card - 4, borderLeft:`4px solid ${col.dot}`, padding:'11px 13px', boxShadow:`0 3px 12px ${col.dot}33` }}>
                    <p style={{ fontSize:13, fontWeight:700, color:col.fg }}>{c.name}</p>
                    <p style={{ fontSize:11, color:col.fg, opacity:0.75, marginTop:2 }}>{c.start}:00 – {c.end}:00 · {c.room}</p>
                    <p style={{ fontSize:11, color:col.fg, opacity:0.6, marginTop:2 }}>{c.prof}</p>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function WeekView({ C, CC, R, schedule }) {
  const days = ['Mo','Di','Mi','Do','Fr'];
  const dates = { Mo:'18', Di:'19', Mi:'20', Do:'21', Fr:'22' };
  const PX = 40; const START = 8; const END = 18;
  const slots = [8,10,12,14,16,18];

  return (
    <div style={{ padding:'14px 14px 28px' }}>
      <div style={{ display:'flex', gap:4 }}>
        <div style={{ width:24, flexShrink:0, position:'relative', height:(END-START)*PX + 24, paddingTop:24 }}>
          {slots.map(h => (
            <div key={h} style={{ position:'absolute', top:(h-START)*PX + 24 - 5, right:0, fontSize:9, fontWeight:600, color:C.textMuted, textAlign:'right', width:24 }}>{h}</div>
          ))}
        </div>
        {days.map(d => {
          const isToday = d === 'Mo';
          const courses = schedule[d] || [];
          return (
            <div key={d} style={{ flex:1 }}>
              <div style={{ display:'flex', flexDirection:'column', alignItems:'center', gap:2, paddingBottom:6 }}>
                <span style={{ fontSize:9, fontWeight:700, color: isToday ? C.primary : C.textMuted, letterSpacing:'0.04em' }}>{d}</span>
                <div style={{ width:24, height:24, borderRadius:12, background: isToday ? C.primary : 'transparent', display:'flex', alignItems:'center', justifyContent:'center' }}>
                  <span style={{ fontSize:12, fontWeight:800, color: isToday ? 'white' : C.text }}>{dates[d]}</span>
                </div>
              </div>
              <div style={{ position:'relative', height:(END-START)*PX, background:C.surface, borderRadius:R.tile, overflow:'hidden' }}>
                {slots.map(h => (
                  <div key={h} style={{ position:'absolute', top:(h-START)*PX, left:0, right:0, height:1, background:C.border, opacity:0.6 }} />
                ))}
                {courses.map(c => {
                  const col = CC[c.id];
                  const top = (c.start-START)*PX + 2;
                  const ht  = (c.end-c.start)*PX - 4;
                  return (
                    <div key={c.id} style={{ position:'absolute', top, left:2, right:2, height:ht, background:col.bg, borderRadius:6, borderLeft:`3px solid ${col.dot}`, padding:'5px 6px', overflow:'hidden' }}>
                      <p style={{ fontSize:9, fontWeight:700, color:col.fg, lineHeight:1.15 }}>{c.name.split(' ')[0]}</p>
                      <p style={{ fontSize:8, color:col.fg, opacity:0.7, marginTop:1 }}>{c.room}</p>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function MonthView({ C, CC, R, schedule }) {
  // May 2026: 31 days. May 1 = Friday. So calendar starts with Mo Apr 27.
  const weeks = [
    ['27','28','29','30',  '1',  '2',  '3'],
    [ '4', '5', '6', '7',  '8',  '9', '10'],
    ['11','12','13','14', '15', '16', '17'],
    ['18','19','20','21', '22', '23', '24'],
    ['25','26','27','28', '29', '30', '31'],
  ];
  const otherMonth = new Set(['27','28','29','30']); // April (we'd need positional logic; here only the first row)
  // dayKey for the schedule map by weekday index
  const wkKey = ['Mo','Di','Mi','Do','Fr','Sa','So'];
  const isInMonth = (week, idx) => !(week === 0 && idx < 4);

  // Find days with classes — for simplicity highlight Mon-Fri of "May" weeks 11-22 etc.
  const today = '18';
  const [selected, setSelected] = useState('18');

  // Compute schedule for selected day (Mo/Di/.. of week 18-22 → real schedule)
  const selectedWeekIdx = weeks.findIndex(w => w.includes(selected));
  const selectedIdx = selectedWeekIdx >= 0 ? weeks[selectedWeekIdx].indexOf(selected) : -1;
  const selectedKey = selectedIdx >= 0 && selectedIdx < 5 ? wkKey[selectedIdx] : null;
  const selectedCourses = (selectedKey && isInMonth(selectedWeekIdx, selectedIdx)) ? (schedule[selectedKey] || []) : [];

  return (
    <div style={{ padding:'14px 16px 28px' }}>
      <p style={{ fontSize:13, fontWeight:700, color:C.text, textAlign:'center', marginBottom:12 }}>Mai 2026</p>
      {/* Weekday header */}
      <div style={{ display:'grid', gridTemplateColumns:'repeat(7, 1fr)', gap:4, marginBottom:6 }}>
        {wkKey.map(w => (
          <div key={w} style={{ textAlign:'center', fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em' }}>{w}</div>
        ))}
      </div>
      {/* Calendar grid */}
      <div style={{ display:'grid', gridTemplateColumns:'repeat(7, 1fr)', gap:4, marginBottom:14 }}>
        {weeks.flatMap((week, wi) => week.map((d, di) => {
          const inMonth = isInMonth(wi, di);
          const dayKey = di < 5 ? wkKey[di] : null;
          const hasClasses = inMonth && dayKey && schedule[dayKey] && schedule[dayKey].length > 0;
          const isToday = d === today && inMonth;
          const isSelected = d === selected && inMonth;
          const isWeekend = di >= 5;
          return (
            <div key={`${wi}-${di}`} className="tap" onClick={() => inMonth && setSelected(d)}
              style={{ aspectRatio:'1', display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center', gap:3, borderRadius:R.tile - 4, background: isSelected ? C.primary : isToday ? C.primaryLight : 'transparent', opacity: inMonth ? 1 : 0.3, cursor: inMonth ? 'pointer' : 'default', transition:'all 0.15s' }}>
              <span style={{ fontSize:13, fontWeight: isToday || isSelected ? 800 : 600, color: isSelected ? 'white' : isToday ? C.primary : isWeekend ? C.textMuted : C.text }}>{d}</span>
              <div style={{ display:'flex', gap:2 }}>
                {hasClasses && schedule[dayKey].slice(0, 3).map((c, i) => {
                  const col = CC[c.id];
                  return <div key={i} style={{ width:4, height:4, borderRadius:2, background: isSelected ? 'white' : col.dot }}></div>;
                })}
              </div>
            </div>
          );
        }))}
      </div>

      {/* Selected day details */}
      <div style={{ marginTop:8 }}>
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8 }}>
          {selectedKey ? `${selectedKey === 'Mo' ? 'Montag' : selectedKey === 'Di' ? 'Dienstag' : selectedKey === 'Mi' ? 'Mittwoch' : selectedKey === 'Do' ? 'Donnerstag' : 'Freitag'}, ${selected}. MAI` : `WOCHENENDE — ${selected}. MAI`}
        </p>
        {selectedCourses.length === 0 ? (
          <div style={{ background:C.surface, borderRadius:R.card, padding:'18px', textAlign:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
            <p style={{ fontSize:13, color:C.textMuted, fontWeight:600 }}>Keine Veranstaltungen</p>
          </div>
        ) : selectedCourses.map(c => {
          const col = CC[c.id];
          return (
            <div key={c.id} style={{ background:C.surface, borderRadius:R.card, padding:'12px 14px', marginBottom:8, display:'flex', gap:12, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ width:4, height:38, background:col.dot, borderRadius:4, flexShrink:0 }}></div>
              <div style={{ flex:1 }}>
                <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{c.name}</p>
                <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{c.start}:00 – {c.end}:00 · {c.room}</p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* MENSA                                                               */
/* ══════════════════════════════════════════════════════════════════ */
const TAG_COLORS = {
  vegan:       { bg:'oklch(94% 0.08 145)', fg:'oklch(36% 0.18 145)' },
  vegetarisch: { bg:'oklch(95% 0.07 140)', fg:'oklch(38% 0.17 140)' },
  fisch:       { bg:'oklch(93% 0.07 215)', fg:'oklch(38% 0.18 215)' },
};

function MensaScreen({ C, R, menus }) {
  const days = [
    { id:'Mo', label:'Mo', date:'25', full:'Montag, 25. Mai' },
    { id:'Di', label:'Di', date:'26', full:'Dienstag, 26. Mai' },
    { id:'Mi', label:'Mi', date:'27', full:'Mittwoch, 27. Mai' },
    { id:'Do', label:'Do', date:'28', full:'Donnerstag, 28. Mai' },
    { id:'Fr', label:'Fr', date:'29', full:'Freitag, 29. Mai' },
  ];
  const [day, setDay] = useState('Mo');
  const [meal, setMeal] = useState('mittag'); // 'mittag' | 'abend'
  const cats = ['Alle','Hauptgericht','Beilage','Dessert'];
  const [cat, setCat] = useState('Alle');
  const dayMenu = (menus[meal] && menus[meal][day]) || [];
  const items = cat === 'Alle' ? dayMenu : dayMenu.filter(m => m.cat === cat);
  const activeDay = days.find(d => d.id === day);
  const isToday = day === 'Mo';
  const mealTimes = {
    mittag: { hours:'11:30 – 14:30 Uhr', label:'Mittag', icon:'utensils' },
    abend:  { hours:'17:30 – 20:00 Uhr', label:'Abend',  icon:'utensils' },
  };
  const mt = mealTimes[meal];

  return (
    <div>
      <div style={{ background:C.surface, padding:'20px 22px 0', borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:4 }}>
          <h1 style={{ fontSize:22, fontWeight:800, color:C.text }}>Mensa</h1>
          <span style={{ fontSize:10, fontWeight:700, padding:'4px 10px', background: isToday ? C.greenLight : C.surfaceAlt, color: isToday ? C.green : C.textMuted, borderRadius:8, letterSpacing:'0.04em', whiteSpace:'nowrap' }}>
            {isToday ? 'GEÖFFNET' : 'VORSCHAU'}
          </span>
        </div>
        <p style={{ fontSize:12, color:C.textMuted, fontWeight:500 }}>{activeDay.full} · {mt.hours}</p>
        {/* Meal time switch — segmented control */}
        <div style={{ display:'flex', gap:4, background:C.surfaceAlt, padding:4, borderRadius:R.tile, marginTop:14 }}>
          {[['mittag','Mittag','11:30 – 14:30'],['abend','Abend','17:30 – 20:00']].map(([id, lbl, sub]) => (
            <div key={id} className="tap" onClick={() => setMeal(id)} style={{ flex:1, textAlign:'center', padding:'7px 0', borderRadius:R.tile - 4, background: meal === id ? C.surface : 'transparent', boxShadow: meal === id ? `0 2px 6px ${C.shadow}` : 'none', transition:'all 0.18s' }}>
              <p style={{ fontSize:12, fontWeight:800, color: meal === id ? C.text : C.textMuted, lineHeight:1.1 }}>{lbl}</p>
              <p style={{ fontSize:9, color: meal === id ? C.textMuted : C.textMuted, opacity: meal === id ? 1 : 0.6, marginTop:2, fontWeight:600 }}>{sub}</p>
            </div>
          ))}
        </div>
        {/* Day picker */}
        <div style={{ display:'flex', gap:4, marginTop:10 }}>
          {days.map(d => {
            const active = day === d.id;
            const today = d.id === 'Mo';
            return (
              <div key={d.id} className="tap" onClick={() => setDay(d.id)}
                style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:3, padding:'8px 2px', borderRadius:R.tile, background: active ? C.primary : 'transparent' }}>
                <span style={{ fontSize:10, fontWeight:600, color: active ? 'rgba(255,255,255,0.72)' : C.textMuted }}>{d.label}</span>
                <span style={{ fontSize:16, fontWeight:800, color: active ? 'white' : today ? C.primary : C.text }}>{d.date}</span>
                <div style={{ width:4, height:4, borderRadius:2, background: today && !active ? C.primary : 'transparent' }}></div>
              </div>
            );
          })}
        </div>
        {/* Category pills */}
        <div style={{ display:'flex', gap:7, marginTop:10, paddingBottom:16, overflowX:'auto' }} className="scroll-area">
          {cats.map(c => {
            const active = cat === c;
            return (
              <div key={c} className="tap" onClick={() => setCat(c)} style={{ padding:'7px 15px', borderRadius:20, flexShrink:0, background: active ? C.primary : C.surfaceAlt, fontSize:12, fontWeight:600, color: active ? 'white' : C.textMuted, transition:'background 0.18s' }}>{c}</div>
            );
          })}
        </div>
      </div>
      <div style={{ padding:'16px 18px' }}>
        {items.length === 0 ? (
          <div style={{ background:C.surface, borderRadius:R.card, padding:'32px 18px', textAlign:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
            <p style={{ fontSize:14, fontWeight:700, color:C.textMuted }}>Speiseplan noch nicht verfügbar</p>
            <p style={{ fontSize:12, color:C.textMuted, marginTop:5, opacity:0.7 }}>Wird in der Regel 2 Tage vorher veröffentlicht</p>
          </div>
        ) : items.map(item => (
          <div key={item.id} style={{ background:C.surface, borderRadius:R.card, padding:'16px', marginBottom:12, boxShadow:`0 2px 10px ${C.shadow}` }}>
            <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:6 }}>
              <div style={{ flex:1, paddingRight:12 }}>
                <p style={{ fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.06em', marginBottom:4 }}>{item.cat.toUpperCase()}</p>
                <p style={{ fontSize:15, fontWeight:700, color:C.text }}>{item.name}</p>
              </div>
              <p style={{ fontSize:17, fontWeight:800, color:C.primary, flexShrink:0 }}>{item.price}</p>
            </div>
            <p style={{ fontSize:12, color:C.textMuted, marginBottom:10, lineHeight:1.45 }}>{item.desc}</p>
            <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
              <div style={{ display:'flex', gap:5, flexWrap:'wrap' }}>
                {item.tags.map(tag => {
                  const tc = TAG_COLORS[tag] || { bg:C.surfaceAlt, fg:C.textMuted };
                  return <span key={tag} style={{ fontSize:10, fontWeight:700, padding:'3px 8px', background:tc.bg, color:tc.fg, borderRadius:6 }}>{tag}</span>;
                })}
              </div>
              <div style={{ display:'flex', alignItems:'center', gap:4 }}>
                <StarFill size={12} color={C.amber} />
                <span style={{ fontSize:12, fontWeight:700, color:C.text }}>{item.rating}</span>
                <span style={{ fontSize:11, color:C.textMuted }}>({item.votes})</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* BIBLIOTHEK                                                          */
/* ══════════════════════════════════════════════════════════════════ */
const BIB_DAYS = [
  { id:0, label:'Heute',  wk:'Mo', d:'25', m:'Mai', full:'Montag, 25. Mai' },
  { id:1, label:'Morgen', wk:'Di', d:'26', m:'Mai', full:'Dienstag, 26. Mai' },
  { id:2, label:'Mi',     wk:'Mi', d:'27', m:'Mai', full:'Mittwoch, 27. Mai' },
  { id:3, label:'Do',     wk:'Do', d:'28', m:'Mai', full:'Donnerstag, 28. Mai' },
  { id:4, label:'Fr',     wk:'Fr', d:'29', m:'Mai', full:'Freitag, 29. Mai' },
  { id:5, label:'Mo',     wk:'Mo', d:'1',  m:'Jun', full:'Montag, 1. Juni' },
  { id:6, label:'Di',     wk:'Di', d:'2',  m:'Jun', full:'Dienstag, 2. Juni' },
];

function BibliothekScreen({ C, R, rooms, bookings, onBookRoom, onCancelBooking, bibDay, setBibDay }) {
  const STATUS = {
    free:    { label:'Frei',      color:C.green, bg:C.greenLight },
    partial: { label:'Teilweise', color:C.amber, bg:C.amberLight },
    full:    { label:'Belegt',    color:C.red,   bg:C.redLight   },
  };
  const isToday = bibDay === 0;
  const activeDay = BIB_DAYS.find(d => d.id === bibDay);
  const myBookings = bookings.filter(b => b.dayId === bibDay);
  // For non-today days, occupancy is just shown as "Verfügbar" since it's a forecast
  const freeCount = rooms.filter(r => r.status !== 'full').length;
  const totalOcc  = rooms.reduce((a,r) => a+r.occ, 0);

  return (
    <div>
      <div style={{ background:C.surface, padding:'20px 22px 0', borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:4 }}>
          <h1 style={{ fontSize:22, fontWeight:800, color:C.text }}>Bibliothek</h1>
          <span style={{ fontSize:10, fontWeight:700, padding:'4px 10px', background:C.greenLight, color:C.green, borderRadius:8, letterSpacing:'0.04em' }}>GEÖFFNET</span>
        </div>
        <p style={{ fontSize:12, color:C.textMuted, fontWeight:500 }}>{activeDay.full} · 8:00 – 22:00 Uhr</p>

        {/* Day picker — root level */}
        <div style={{ display:'flex', gap:6, marginTop:14, paddingBottom:14, overflowX:'auto' }} className="scroll-area">
          {BIB_DAYS.map(d => {
            const active = bibDay === d.id;
            const today = d.id === 0;
            const hasBooking = bookings.some(b => b.dayId === d.id);
            return (
              <div key={d.id} className="tap" onClick={() => setBibDay(d.id)}
                style={{ flexShrink:0, minWidth:56, padding:'8px 6px', borderRadius:R.tile, background: active ? C.primary : C.surfaceAlt, textAlign:'center', transition:'background 0.18s', position:'relative' }}>
                <p style={{ fontSize:9, fontWeight:700, color: active ? 'rgba(255,255,255,0.7)' : today ? C.primary : C.textMuted, letterSpacing:'0.04em' }}>{d.label.toUpperCase().slice(0, 5)}</p>
                <p style={{ fontSize:16, fontWeight:800, color: active ? 'white' : today ? C.primary : C.text, lineHeight:1.1, marginTop:3 }}>{d.d}</p>
                {hasBooking && !active && <div style={{ position:'absolute', bottom:5, left:'50%', transform:'translateX(-50%)', width:5, height:5, borderRadius:3, background:C.primary }}></div>}
                {hasBooking && active && <div style={{ position:'absolute', bottom:5, left:'50%', transform:'translateX(-50%)', width:5, height:5, borderRadius:3, background:'white' }}></div>}
              </div>
            );
          })}
        </div>
      </div>

      <div style={{ padding:'16px 18px 28px' }}>

        {/* My bookings for this day */}
        {myBookings.length > 0 && (
          <>
            <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10, paddingLeft:4 }}>MEINE BUCHUNGEN</p>
            <div style={{ display:'flex', flexDirection:'column', gap:8, marginBottom:18 }}>
              {myBookings.map(b => (
                <div key={b.id} style={{ background:C.primary, borderRadius:R.card, padding:'14px 16px', display:'flex', alignItems:'center', gap:12, position:'relative', overflow:'hidden', boxShadow:`0 4px 14px ${C.shadow}` }}>
                  <div style={{ position:'absolute', top:-20, right:-20, width:80, height:80, borderRadius:'50%', background:'rgba(255,255,255,0.08)' }}></div>
                  <div style={{ width:42, height:42, borderRadius:R.tile - 2, background:'rgba(255,255,255,0.18)', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, position:'relative' }}>
                    <Icon name="check" size={20} color="white" sw={2.6} />
                  </div>
                  <div style={{ flex:1, minWidth:0, position:'relative' }}>
                    <p style={{ fontSize:14, fontWeight:800, color:'white' }}>{b.roomName}</p>
                    <p style={{ fontSize:11, color:'rgba(255,255,255,0.8)', marginTop:3, fontWeight:600 }}>{b.startTime} – {b.endTime} · {b.duration}</p>
                  </div>
                  <span className="tap" onClick={() => onCancelBooking(b.id)} style={{ fontSize:10, fontWeight:700, padding:'5px 10px', background:'rgba(255,255,255,0.18)', color:'white', borderRadius:8, whiteSpace:'nowrap', position:'relative' }}>Stornieren</span>
                </div>
              ))}
            </div>
          </>
        )}

        {/* Stats — only when today */}
        {isToday && (
          <div style={{ background:C.surface, borderRadius:R.card, padding:'14px 20px', display:'flex', justifyContent:'space-around', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}` }}>
            {[['Verfügbar', freeCount, C.primary],['Räume', rooms.length, C.text],['Personen', totalOcc, C.text]].map(([lbl,val,col]) => (
              <div key={lbl} style={{ textAlign:'center' }}>
                <p style={{ fontSize:23, fontWeight:800, color:col, lineHeight:1 }}>{val}</p>
                <p style={{ fontSize:11, color:C.textMuted, fontWeight:600, marginTop:3 }}>{lbl}</p>
              </div>
            ))}
          </div>
        )}

        {/* Rooms */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10, paddingLeft:4 }}>{isToday ? 'RÄUME · LIVE-AUSLASTUNG' : 'RÄUME · VERFÜGBARKEIT'}</p>
        {rooms.map(room => {
          const si = STATUS[room.status];
          const pct = Math.round(room.occ / room.cap * 100);
          const barColor = room.status==='full' ? C.red : room.status==='partial' ? C.amber : C.green;
          const myBookedHere = myBookings.find(b => b.roomId === room.id);
          return (
            <div key={room.id} className={!myBookedHere && room.bookable ? 'tap' : ''} onClick={() => !myBookedHere && room.bookable && onBookRoom(room)}
              style={{ background:C.surface, borderRadius:R.card, padding:'14px 16px', marginBottom:10, boxShadow:`0 2px 10px ${C.shadow}`, border: myBookedHere ? `2px solid ${C.primary}` : 'none' }}>
              <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom: isToday ? 10 : 6, gap:10 }}>
                <div style={{ flex:1, minWidth:0 }}>
                  <div style={{ display:'flex', alignItems:'center', gap:8 }}>
                    <p style={{ fontSize:15, fontWeight:700, color:C.text }}>{room.name}</p>
                    {myBookedHere && <span style={{ fontSize:9, fontWeight:800, padding:'2px 7px', background:C.primary, color:'white', borderRadius:5, letterSpacing:'0.04em' }}>GEBUCHT</span>}
                  </div>
                  <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{room.floor} · {room.cap} Plätze</p>
                </div>
                <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end', gap:5, flexShrink:0 }}>
                  {isToday && <span style={{ fontSize:10, fontWeight:700, padding:'4px 10px', background:si.bg, color:si.color, borderRadius:8, whiteSpace:'nowrap' }}>{si.label}</span>}
                  {!myBookedHere && room.bookable && (
                    <span style={{ fontSize:10, fontWeight:700, padding:'4px 10px', background:C.primaryLight, color:C.primary, borderRadius:8, whiteSpace:'nowrap', display:'flex', alignItems:'center', gap:4 }}>
                      Buchen
                      <Icon name="chevR" size={10} color={C.primary} sw={2.4} />
                    </span>
                  )}
                </div>
              </div>
              {isToday && (
                <>
                  <div style={{ height:6, background:C.surfaceAlt, borderRadius:3, overflow:'hidden', marginBottom:5 }}>
                    <div style={{ height:'100%', width:`${pct}%`, background:barColor, borderRadius:3, transition:'width 0.6s ease' }}></div>
                  </div>
                  <p style={{ fontSize:11, color:C.textMuted }}>{room.occ} von {room.cap} belegt{room.until ? ` · Frei bis ${room.until}` : ''}</p>
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* BIBLIOTHEK — RAUM BUCHEN                                            */
/* ══════════════════════════════════════════════════════════════════ */
function LibraryBookingScreen({ C, R, room, day, onConfirm, onBack }) {
  // 8:00 – 20:00 in 30-min slots
  const slots = [];
  for (let h = 8; h < 20; h++) {
    slots.push({ start: `${String(h).padStart(2,'0')}:00`, end: `${String(h).padStart(2,'0')}:30` });
    slots.push({ start: `${String(h).padStart(2,'0')}:30`, end: `${String(h+1).padStart(2,'0')}:00` });
  }
  const activeDay = day || BIB_DAYS[0];
  const [selected, setSelected] = useState([]);
  const [confirmed, setConfirmed] = useState(false);

  // Deterministic taken slots
  const isTaken = (idx) => {
    const seed = (activeDay.id * 7 + (room.id || 1)) * 31;
    const h = (idx * 2654435761 + seed) >>> 0;
    return (h % 100) < 30;
  };
  const currentSlotIdx = activeDay.id === 0 ? 4 : -1;

  const toggleSlot = (idx) => {
    if (isTaken(idx) || idx < currentSlotIdx) return;
    if (selected.includes(idx)) {
      const next = selected.filter(i => i !== idx);
      if (next.length === 0) { setSelected([]); return; }
      const sorted = [...next].sort((a,b) => a-b);
      const result = [sorted[0]];
      for (let i = 1; i < sorted.length; i++) {
        if (sorted[i] === result[result.length - 1] + 1) result.push(sorted[i]);
        else break;
      }
      setSelected(result);
      return;
    }
    if (selected.length === 0) { setSelected([idx]); return; }
    const sorted = [...selected].sort((a,b) => a-b);
    const min = sorted[0], max = sorted[sorted.length - 1];
    if (idx === min - 1 || idx === max + 1) {
      const next = [...sorted, idx];
      const lo = Math.min(...next), hi = Math.max(...next);
      for (let i = lo; i <= hi; i++) if (isTaken(i) || i < currentSlotIdx) return;
      setSelected(next);
    } else {
      setSelected([idx]);
    }
  };

  const sortedSel = [...selected].sort((a,b) => a-b);
  const startSlot = sortedSel[0];
  const endSlot   = sortedSel[sortedSel.length - 1];
  const durationMin = selected.length * 30;
  const hours = Math.floor(durationMin / 60);
  const mins  = durationMin % 60;
  const durLbl = durationMin === 0 ? '–' : `${hours > 0 ? `${hours}h` : ''}${mins > 0 ? ` ${mins} Min` : ''}`.trim();
  const timeRange = startSlot != null ? `${slots[startSlot].start} – ${slots[endSlot].end}` : null;

  if (confirmed) {
    return (
      <div>
        <SubHeader title="Reservierung" onBack={onBack} C={C} />
        <div style={{ padding:'40px 22px', textAlign:'center' }}>
          <div style={{ width:96, height:96, borderRadius:48, background:C.greenLight, display:'flex', alignItems:'center', justifyContent:'center', margin:'0 auto 18px' }}>
            <Icon name="check" size={48} color={C.green} sw={2.8} />
          </div>
          <p style={{ fontSize:22, fontWeight:800, color:C.text, marginBottom:6 }}>Raum gebucht</p>
          <p style={{ fontSize:13, color:C.textMuted, lineHeight:1.5, marginBottom:24 }}>Du findest die Reservierung jetzt in der Bibliothek-Übersicht.</p>
          <div style={{ background:C.surface, borderRadius:R.card, padding:'18px', textAlign:'left', boxShadow:`0 4px 16px ${C.shadow}`, marginBottom:18 }}>
            <p style={{ fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em' }}>RESERVIERUNG</p>
            <p style={{ fontSize:17, fontWeight:800, color:C.text, marginTop:6 }}>{room.name}</p>
            <p style={{ fontSize:12, color:C.textMuted, marginTop:3 }}>{room.floor} · {room.cap} Plätze</p>
            <div style={{ height:1, background:C.border, margin:'14px 0' }}></div>
            <div style={{ display:'flex', justifyContent:'space-between', marginBottom:8 }}>
              <span style={{ fontSize:12, color:C.textMuted, fontWeight:600 }}>Tag</span>
              <span style={{ fontSize:13, fontWeight:800, color:C.text }}>{activeDay.wk}, {activeDay.d}. {activeDay.m}</span>
            </div>
            <div style={{ display:'flex', justifyContent:'space-between', marginBottom:8 }}>
              <span style={{ fontSize:12, color:C.textMuted, fontWeight:600 }}>Uhrzeit</span>
              <span style={{ fontSize:13, fontWeight:800, color:C.text }}>{timeRange}</span>
            </div>
            <div style={{ display:'flex', justifyContent:'space-between' }}>
              <span style={{ fontSize:12, color:C.textMuted, fontWeight:600 }}>Dauer</span>
              <span style={{ fontSize:13, fontWeight:800, color:C.text }}>{durLbl}</span>
            </div>
          </div>
          <button onClick={() => { onConfirm && onConfirm({
            id: Date.now(),
            roomId: room.id,
            roomName: room.name,
            dayId: activeDay.id,
            dayLabel: activeDay.wk + ', ' + activeDay.d + '. ' + activeDay.m,
            startTime: slots[startSlot].start,
            endTime: slots[endSlot].end,
            duration: durLbl,
          }); }} style={{ width:'100%', padding:'14px', borderRadius:R.tile, background:C.primary, color:'white', border:'none', fontWeight:800, fontSize:13, fontFamily:'inherit', cursor:'pointer' }}>Fertig</button>
        </div>
      </div>
    );
  }

  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <SubHeader title={room.name} subtitle={`${activeDay.wk}, ${activeDay.d}. ${activeDay.m} · ${room.cap} Plätze`} onBack={onBack} C={C} />

      <div style={{ flex:1, overflowY:'auto', padding:'18px 18px 130px' }} className="scroll-area">
        {/* Hint */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:6 }}>ZEITRAUM WÄHLEN</p>
        <p style={{ fontSize:11, color:C.textMuted, lineHeight:1.45, marginBottom:14 }}>
          Tippe auf einen freien Slot. Weitere angrenzende Slots verlängern die Buchung.
        </p>

        {/* Legend */}
        <div style={{ display:'flex', gap:14, marginBottom:14, flexWrap:'wrap' }}>
          {[['Frei',C.greenLight, C.green],['Gewählt',C.primary, 'white'],['Belegt',C.surfaceAlt, C.textMuted]].map(([lbl, bg, dotCol]) => (
            <div key={lbl} style={{ display:'flex', alignItems:'center', gap:6 }}>
              <div style={{ width:12, height:12, borderRadius:4, background:bg, border: lbl==='Belegt' ? `1px solid ${C.border}` : 'none' }}></div>
              <span style={{ fontSize:11, fontWeight:600, color:C.textMuted }}>{lbl}</span>
            </div>
          ))}
        </div>

        {/* Slot grid — 3 columns */}
        <div style={{ display:'grid', gridTemplateColumns:'repeat(3, 1fr)', gap:6 }}>
          {slots.map((slot, i) => {
            const taken = isTaken(i);
            const past = i < currentSlotIdx;
            const sel = selected.includes(i);
            const disabled = taken || past;
            return (
              <div key={i} className={disabled ? '' : 'tap'} onClick={() => toggleSlot(i)}
                style={{
                  height:48,
                  borderRadius:R.tile - 4,
                  background: sel ? C.primary : taken ? C.surfaceAlt : past ? 'transparent' : C.greenLight,
                  border: past && !taken ? `1px dashed ${C.border}` : sel ? `1px solid ${C.primary}` : `1px solid transparent`,
                  display:'flex',
                  alignItems:'center',
                  justifyContent:'center',
                  flexDirection:'column',
                  gap:1,
                  opacity: disabled && !sel ? (past ? 0.4 : 0.65) : 1,
                  cursor: disabled ? 'not-allowed' : 'pointer',
                  transition:'background 0.15s, border 0.15s',
                }}>
                <p style={{ fontSize:13, fontWeight:800, color: sel ? 'white' : taken ? C.textMuted : past ? C.textMuted : C.green, lineHeight:1 }}>{slot.start}</p>
                <p style={{ fontSize:9, fontWeight:600, color: sel ? 'rgba(255,255,255,0.7)' : taken ? C.textMuted : past ? C.textMuted : C.green, opacity: sel ? 1 : 0.7, lineHeight:1 }}>
                  {taken ? 'belegt' : past ? 'vorbei' : `bis ${slot.end}`}
                </p>
              </div>
            );
          })}
        </div>

        <p style={{ fontSize:10, color:C.textMuted, marginTop:14, textAlign:'center', fontWeight:600 }}>Bibliothek geöffnet 8:00 – 22:00 · Buchung bis 20:00</p>
      </div>

      {/* Sticky bottom CTA */}
      {selected.length > 0 && (
        <div style={{ position:'absolute', bottom:0, left:0, right:0, background:C.surface, borderTop:`1px solid ${C.border}`, padding:'14px 18px', boxShadow:`0 -4px 16px ${C.shadow}`, display:'flex', justifyContent:'space-between', alignItems:'center', gap:12 }}>
          <div style={{ minWidth:0 }}>
            <p style={{ fontSize:15, fontWeight:800, color:C.text }}>{timeRange}</p>
            <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{durLbl} · {activeDay.wk}, {activeDay.d}. {activeDay.m}</p>
          </div>
          <button onClick={() => setConfirmed(true)}
            style={{ padding:'12px 22px', borderRadius:R.tile, background:C.primary, color:'white', border:'none', fontWeight:800, fontSize:13, fontFamily:'inherit', cursor:'pointer', whiteSpace:'nowrap' }}>
            Buchen
          </button>
        </div>
      )}
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* KURSE                                                               */
/* ══════════════════════════════════════════════════════════════════ */
function KurseScreen({ C, CC, R, courses, pastSemesters, currentSemester }) {
  // Build full semester list: past semesters first (oldest → newest), then current
  const currentSem = {
    id: 'current',
    label: `${(pastSemesters?.length || 0) + 1}. Semester`,
    semester: currentSemester,
    isCurrent: true,
    courses: courses,
  };
  const allSems = [...(pastSemesters || []), currentSem];
  const [semIdx, setSemIdx] = useState(allSems.length - 1);
  const [sel, setSel] = useState(null);

  const activeSem = allSems[semIdx];
  const isCurrent = !!activeSem.isCurrent;
  const semCourses = activeSem.courses;
  const totalLP = semCourses.reduce((a,c) => a + c.credits, 0);

  // GPA for past semesters
  const gpa = !isCurrent
    ? (semCourses.reduce((a,c) => a + parseFloat(c.grade.replace(',','.')) * c.credits, 0) / totalLP).toFixed(2).replace('.', ',')
    : null;

  /* ── Course Detail ───────────────────────────────────────────────── */
  if (sel) {
    const course = semCourses.find(c => c.id === sel);
    const colKey = isCurrent ? course.id : course.colorRef;
    const col = CC[colKey] || CC.linalg;
    return (
      <div>
        <SubHeader title={course.name} subtitle={course.prof} onBack={() => setSel(null)} C={C} color={col.fg} bg={col.bg} />
        <div style={{ padding:'18px', display:'flex', flexDirection:'column', gap:10 }}>
          {[
            ['Leistungspunkte', `${course.credits} LP`],
            ['Semester', activeSem.semester],
            isCurrent
              ? ['Nächste Prüfung', course.nextExam || '–']
              : ['Eingetragen am', course.gradedOn],
          ].map(([l,v]) => (
            <div key={l} style={{ background:C.surface, borderRadius:R.tile, padding:'13px 16px', display:'flex', justifyContent:'space-between', boxShadow:`0 2px 8px ${C.shadow}` }}>
              <p style={{ fontSize:13, color:C.textMuted, fontWeight:500 }}>{l}</p>
              <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{v}</p>
            </div>
          ))}

          {isCurrent ? (
            <>
              <div style={{ background:C.surface, borderRadius:R.tile, padding:'14px 16px', boxShadow:`0 2px 8px ${C.shadow}` }}>
                <p style={{ fontSize:12, fontWeight:600, color:C.textMuted, marginBottom:9 }}>Kursfortschritt</p>
                <div style={{ height:8, background:C.surfaceAlt, borderRadius:4, overflow:'hidden', marginBottom:6 }}>
                  <div style={{ height:'100%', width:`${course.progress*100}%`, background:col.dot, borderRadius:4, transition:'width 0.9s ease' }}></div>
                </div>
                <p style={{ fontSize:12, color:C.textMuted }}>{Math.round(course.progress*100)}% der Vorlesungen besucht</p>
              </div>

              <div style={{ background:C.surface, borderRadius:R.tile, padding:'16px', marginTop:4, boxShadow:`0 2px 8px ${C.shadow}` }}>
                <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em', marginBottom:8 }}>NOTENSTATUS</p>
                <div style={{ display:'flex', alignItems:'center', gap:14 }}>
                  <div style={{ width:48, height:48, borderRadius:R.tile - 4, background:col.bg, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                    <Icon name="clock" size={22} color={col.dot} />
                  </div>
                  <div style={{ flex:1 }}>
                    <p style={{ fontSize:14, fontWeight:700, color:C.text }}>Note steht noch aus</p>
                    <p style={{ fontSize:12, color:C.textMuted, marginTop:2 }}>{course.nextExam ? `Endklausur am ${course.nextExam}` : 'Bewertung am Semesterende'}</p>
                  </div>
                </div>
                <p style={{ fontSize:11, color:C.textMuted, marginTop:12, lineHeight:1.5, paddingTop:12, borderTop:`1px solid ${C.border}` }}>
                  Du erhältst eine Benachrichtigung, sobald deine Endnote eingetragen wird.
                </p>
              </div>
            </>
          ) : (
            <div style={{ background:C.surface, borderRadius:R.tile, padding:'16px', marginTop:4, boxShadow:`0 2px 8px ${C.shadow}` }}>
              <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em', marginBottom:10 }}>ENDNOTE</p>
              <div style={{ display:'flex', alignItems:'center', gap:14 }}>
                <div style={{ width:64, height:64, borderRadius:R.tile - 2, background:col.bg, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                  <span style={{ fontSize:24, fontWeight:800, color:col.fg, letterSpacing:'-0.02em' }}>{course.grade}</span>
                </div>
                <div style={{ flex:1 }}>
                  <p style={{ fontSize:14, fontWeight:700, color:C.text }}>Bestanden</p>
                  <p style={{ fontSize:12, color:C.textMuted, marginTop:2 }}>{course.credits} LP gutgeschrieben</p>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  /* ── Semester Switcher ───────────────────────────────────────────── */
  const goPrev = () => semIdx > 0 && setSemIdx(semIdx - 1);
  const goNext = () => semIdx < allSems.length - 1 && setSemIdx(semIdx + 1);
  const canPrev = semIdx > 0;
  const canNext = semIdx < allSems.length - 1;

  return (
    <div>
      <div style={{ background:C.surface, padding:'20px 22px 0', borderBottom:`1px solid ${C.border}` }}>
        <h1 style={{ fontSize:22, fontWeight:800, color:C.text }}>Meine Kurse</h1>

        {/* Semester switcher */}
        <div style={{ display:'flex', alignItems:'center', gap:10, marginTop:14, marginBottom:6 }}>
          <div className="tap" onClick={goPrev}
            style={{ width:34, height:34, borderRadius:17, background: canPrev ? C.surfaceAlt : 'transparent', display:'flex', alignItems:'center', justifyContent:'center', opacity: canPrev ? 1 : 0.3, flexShrink:0 }}>
            <Icon name="chevL" size={18} color={C.text} />
          </div>
          <div style={{ flex:1, textAlign:'center' }}>
            <div style={{ display:'flex', alignItems:'center', justifyContent:'center', gap:6 }}>
              <p style={{ fontSize:14, fontWeight:800, color:C.text }}>{activeSem.label}</p>
              {isCurrent && (
                <span style={{ fontSize:9, fontWeight:800, letterSpacing:'0.06em', color:C.primary, background:C.primaryLight, padding:'2px 6px', borderRadius:4 }}>AKTUELL</span>
              )}
            </div>
            <p style={{ fontSize:11, color:C.textMuted, fontWeight:600, marginTop:2 }}>{activeSem.semester} · {totalLP} LP</p>
          </div>
          <div className="tap" onClick={goNext}
            style={{ width:34, height:34, borderRadius:17, background: canNext ? C.surfaceAlt : 'transparent', display:'flex', alignItems:'center', justifyContent:'center', opacity: canNext ? 1 : 0.3, flexShrink:0 }}>
            <Icon name="chevR" size={18} color={C.text} />
          </div>
        </div>

        {/* Pagination dots */}
        <div style={{ display:'flex', justifyContent:'center', gap:5, paddingBottom:14 }}>
          {allSems.map((s, i) => (
            <div key={s.id} className="tap" onClick={() => setSemIdx(i)}
              style={{ width: i === semIdx ? 16 : 5, height:5, borderRadius:3, background: i === semIdx ? C.primary : C.border, transition:'width 0.18s, background 0.18s' }}></div>
          ))}
        </div>
      </div>

      <div style={{ padding:'16px 18px' }}>
        {semCourses.map(course => {
          const colKey = isCurrent ? course.id : course.colorRef;
          const col = CC[colKey] || CC.linalg;
          return (
            <div key={course.id} className="tap" onClick={() => setSel(course.id)}
              style={{ background:C.surface, borderRadius:R.card, padding:'15px', marginBottom:10, display:'flex', gap:13, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ width:48, height:48, background:col.bg, borderRadius:R.tile, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                {isCurrent
                  ? <Icon name="book" size={22} color={col.dot} />
                  : <span style={{ fontSize:18, fontWeight:800, color:col.fg, letterSpacing:'-0.02em' }}>{course.grade}</span>
                }
              </div>
              <div style={{ flex:1 }}>
                <p style={{ fontSize:14, fontWeight:700, color:C.text, marginBottom:2 }}>{course.name}</p>
                <p style={{ fontSize:12, color:C.textMuted }}>{course.prof}</p>
                {isCurrent ? (
                  <div style={{ marginTop:8, height:4, background:C.surfaceAlt, borderRadius:2, overflow:'hidden' }}>
                    <div style={{ height:'100%', width:`${course.progress*100}%`, background:col.dot, borderRadius:2 }}></div>
                  </div>
                ) : (
                  <p style={{ fontSize:11, color:C.textMuted, marginTop:6 }}>Bestanden · {course.gradedOn}</p>
                )}
              </div>
              <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end', gap:8, flexShrink:0 }}>
                <span style={{ fontSize:11, fontWeight:700, color:C.textMuted, whiteSpace:'nowrap' }}>{course.credits} LP</span>
                <Icon name="chevR" size={16} color={C.textMuted} />
              </div>
            </div>
          );
        })}

        {/* Summary footer */}
        <div style={{ background:C.primaryLight, borderRadius:R.card, padding:'16px', marginTop:4, display:'flex', justifyContent:'space-around' }}>
          {(isCurrent
            ? [['Kurse', semCourses.length], ['LP gesamt', totalLP], ['Semester', semIdx + 1]]
            : [['Module', semCourses.length], ['LP gesamt', totalLP], ['Ø Note', gpa]]
          ).map(([l,v]) => (
            <div key={l} style={{ textAlign:'center' }}>
              <p style={{ fontSize:24, fontWeight:800, color:C.primary, lineHeight:1 }}>{v}</p>
              <p style={{ fontSize:10, color:C.primary, opacity:0.65, fontWeight:600, marginTop:4 }}>{l}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* MAIL                                                                */
/* ══════════════════════════════════════════════════════════════════ */
function MailScreen({ C, CC, R, mails, onBack, onUpdateMail }) {
  const [folder, setFolder] = useState('inbox');
  const [open, setOpen] = useState(null);

  if (open !== null) {
    const mail = mails.find(m => m.id === open);
    const col = mail.courseId ? CC[mail.courseId] : null;
    return (
      <div>
        <SubHeader title={mail.subject} subtitle={mail.from} onBack={() => setOpen(null)} C={C} />
        <div style={{ padding:'18px' }}>
          <div style={{ display:'flex', gap:10, alignItems:'center', marginBottom:18 }}>
            <div style={{ width:42, height:42, borderRadius:21, background: col ? col.bg : C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
              <span style={{ fontSize:15, fontWeight:800, color: col ? col.dot : C.primary }}>{mail.from.split(' ').map(s => s[0]).slice(0, 2).join('')}</span>
            </div>
            <div style={{ flex:1 }}>
              <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{mail.from}</p>
              <p style={{ fontSize:11, color:C.textMuted, marginTop:1 }}>{mail.time} · an mich</p>
            </div>
            <div className="tap" onClick={() => onUpdateMail(mail.id, { starred: !mail.starred })}>
              <Icon name="star" size={20} color={mail.starred ? C.amber : C.textMuted} fill={mail.starred ? C.amber : 'none'} />
            </div>
          </div>
          <div style={{ background:C.surface, borderRadius:R.card, padding:'18px', boxShadow:`0 2px 10px ${C.shadow}`, fontSize:13, color:C.text, lineHeight:1.6 }}>
            <p style={{ marginBottom:10 }}>{mail.snippet}</p>
            <p style={{ marginBottom:10 }}>Bei Fragen bitte direkt antworten.</p>
            <p style={{ color:C.textMuted, fontSize:12 }}>Viele Grüße<br/>{mail.from}</p>
          </div>
          <div style={{ display:'flex', gap:10, marginTop:18 }}>
            <button style={{ flex:1, padding:'12px', borderRadius:R.tile, background:C.primary, color:'white', border:'none', fontWeight:700, fontSize:13, fontFamily:'inherit', cursor:'pointer' }}>Antworten</button>
            <button style={{ padding:'12px 16px', borderRadius:R.tile, background:C.surface, color:C.textMuted, border:`1px solid ${C.border}`, fontWeight:700, fontSize:13, fontFamily:'inherit', cursor:'pointer', display:'flex', alignItems:'center' }}>
              <Icon name="trash" size={16} color={C.textMuted} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  const filtered = folder === 'starred' ? mails.filter(m => m.starred) : mails;
  const unread = mails.filter(m => m.unread).length;
  return (
    <div>
      <SubHeader title="Posteingang" subtitle={`${unread} ungelesen · ${mails.length} insgesamt`} onBack={onBack} C={C}
        action={<div style={{ width:36, height:36, borderRadius:R.tile, background:C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center' }}><Icon name="search" size={18} color={C.primary} /></div>} />
      <div style={{ background:C.surface, padding:'10px 18px 14px', borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', gap:6 }}>
          {[['inbox','Posteingang'],['starred','Markiert']].map(([id, lbl]) => {
            const active = folder === id;
            return (
              <div key={id} className="tap" onClick={() => setFolder(id)} style={{ padding:'6px 13px', borderRadius:14, background: active ? C.primaryLight : C.surfaceAlt, fontSize:11, fontWeight:700, color: active ? C.primary : C.textMuted }}>{lbl}</div>
            );
          })}
        </div>
      </div>
      <div style={{ padding:'8px 0' }}>
        {filtered.map((mail, i) => {
          const col = mail.courseId ? CC[mail.courseId] : null;
          return (
            <div key={mail.id} className="tap" onClick={() => { setOpen(mail.id); onUpdateMail(mail.id, { unread: false }); }}
              style={{ background:C.surface, padding:'14px 18px', borderBottom: i < filtered.length - 1 ? `1px solid ${C.border}` : 'none', display:'flex', gap:11, alignItems:'flex-start' }}>
              <div style={{ width:38, height:38, borderRadius:19, background: col ? col.bg : C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, position:'relative' }}>
                <span style={{ fontSize:13, fontWeight:800, color: col ? col.dot : C.primary }}>{mail.from.split(' ').map(s => s[0]).slice(0, 2).join('')}</span>
                {mail.unread && <div style={{ position:'absolute', top:-2, left:-2, width:10, height:10, borderRadius:5, background:C.primary, border:`2px solid ${C.surface}` }}></div>}
              </div>
              <div style={{ flex:1, minWidth:0 }}>
                <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', gap:8 }}>
                  <p style={{ fontSize:13, fontWeight: mail.unread ? 800 : 600, color:C.text, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{mail.from}</p>
                  <div style={{ display:'flex', alignItems:'center', gap:6, flexShrink:0 }}>
                    {mail.starred && <StarFill size={11} color={C.amber} />}
                    <p style={{ fontSize:11, color: mail.unread ? C.primary : C.textMuted, fontWeight: mail.unread ? 700 : 500 }}>{mail.time}</p>
                  </div>
                </div>
                <p style={{ fontSize:13, fontWeight: mail.unread ? 700 : 500, color:C.text, marginTop:2 }}>{mail.subject}</p>
                <p style={{ fontSize:12, color:C.textMuted, marginTop:3, lineHeight:1.4, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{mail.snippet}</p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* TODO                                                                */
/* ══════════════════════════════════════════════════════════════════ */
function TodoScreen({ C, CC, R, todos, onToggle, onAdd, onBack }) {
  const [input, setInput] = useState('');
  const open = todos.filter(t => !t.done);
  const done = todos.filter(t => t.done);

  const handleAdd = () => {
    if (!input.trim()) return;
    onAdd(input.trim());
    setInput('');
  };

  return (
    <div>
      <SubHeader title="Aufgaben" subtitle={`${open.length} offen · ${done.length} erledigt`} onBack={onBack} C={C} />
      {/* Add input */}
      <div style={{ padding:'14px 18px', background:C.surface, borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', gap:8, alignItems:'center', background:C.surfaceAlt, borderRadius:R.tile, padding:'4px 4px 4px 14px' }}>
          <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleAdd()}
            placeholder="Neue Aufgabe…"
            style={{ flex:1, padding:'8px 0', background:'transparent', border:'none', outline:'none', fontSize:13, fontFamily:'inherit', color:C.text }} />
          <button onClick={handleAdd} style={{ width:34, height:34, borderRadius:R.tile - 4, background:C.primary, border:'none', display:'flex', alignItems:'center', justifyContent:'center', cursor:'pointer' }}>
            <Icon name="plus" size={18} color="white" sw={2.4} />
          </button>
        </div>
      </div>
      <div style={{ padding:'14px 18px 28px' }}>
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10 }}>OFFEN ({open.length})</p>
        {open.length === 0 && (
          <div style={{ background:C.surface, borderRadius:R.card, padding:'18px', textAlign:'center', boxShadow:`0 2px 10px ${C.shadow}`, marginBottom:18 }}>
            <p style={{ fontSize:13, color:C.textMuted, fontWeight:600 }}>Alles erledigt! 🎉</p>
          </div>
        )}
        {open.map(todo => <TodoItem key={todo.id} todo={todo} C={C} CC={CC} R={R} onToggle={onToggle} />)}
        {done.length > 0 && (
          <>
            <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginTop:18, marginBottom:10 }}>ERLEDIGT ({done.length})</p>
            {done.map(todo => <TodoItem key={todo.id} todo={todo} C={C} CC={CC} R={R} onToggle={onToggle} />)}
          </>
        )}
      </div>
    </div>
  );
}

function TodoItem({ todo, C, CC, R, onToggle }) {
  const col = todo.courseId ? CC[todo.courseId] : null;
  return (
    <div className="tap" onClick={() => onToggle(todo.id)}
      style={{ background:C.surface, borderRadius:R.card, padding:'13px 15px', marginBottom:8, display:'flex', gap:12, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}`, opacity: todo.done ? 0.55 : 1 }}>
      <div style={{ width:22, height:22, borderRadius:11, border:`2px solid ${todo.done ? (col ? col.dot : C.primary) : (col ? col.dot : C.textMuted)}`, background: todo.done ? (col ? col.dot : C.primary) : 'transparent', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, transition:'all 0.18s' }}>
        {todo.done && <Icon name="check" size={13} color="white" sw={3} />}
      </div>
      <div style={{ flex:1 }}>
        <p style={{ fontSize:13, fontWeight:600, color:C.text, textDecoration: todo.done ? 'line-through' : 'none' }}>{todo.title}</p>
        <div style={{ display:'flex', gap:8, marginTop:3, alignItems:'center' }}>
          {col && <span style={{ fontSize:10, fontWeight:700, padding:'2px 7px', background:col.bg, color:col.fg, borderRadius:5 }}>{todo.courseLabel}</span>}
          {todo.due && !todo.done && (
            <span style={{ fontSize:11, color: todo.dueColor === 'red' ? C.red : todo.dueColor === 'amber' ? C.amber : C.textMuted, fontWeight:600, whiteSpace:'nowrap' }}>{todo.due}</span>
          )}
        </div>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* UNI KINO                                                            */
/* ══════════════════════════════════════════════════════════════════ */
function KinoScreen({ C, R, kino, onBack }) {
  const [selected, setSelected] = useState(null);
  if (selected) {
    const film = kino.find(f => f.id === selected);
    return <KinoDetail C={C} R={R} film={film} onBack={() => setSelected(null)} />;
  }
  const featured = kino[0];
  const upcoming = kino.slice(1);
  return (
    <div>
      <SubHeader title="Uni Kino" subtitle="Diese Woche im Audimax & HS 1" onBack={onBack} C={C} />
      {/* Featured */}
      <div style={{ padding:'18px 18px 0' }}>
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10 }}>HEUTE ABEND</p>
        <div className="tap" onClick={() => setSelected(featured.id)} style={{ borderRadius:R.card, overflow:'hidden', position:'relative', height:260, background:`linear-gradient(160deg, ${featured.color} 0%, oklch(from ${featured.color} calc(l - 0.22) c h) 100%)`, padding:'20px', display:'flex', flexDirection:'column', justifyContent:'space-between', boxShadow:`0 8px 24px ${C.shadow}` }}>
          <div style={{ position:'absolute', top:-40, right:-40, width:140, height:140, borderRadius:'50%', background:'rgba(255,255,255,0.07)' }}></div>
          <div style={{ position:'absolute', bottom:-30, left:-30, width:100, height:100, borderRadius:'50%', background:'rgba(255,255,255,0.05)' }}></div>
          <div style={{ display:'flex', gap:8 }}>
            <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', background:'rgba(255,255,255,0.22)', color:'white', borderRadius:6, backdropFilter:'blur(8px)', whiteSpace:'nowrap' }}>{featured.genre}</span>
            <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', background:'rgba(255,255,255,0.22)', color:'white', borderRadius:6, backdropFilter:'blur(8px)', whiteSpace:'nowrap' }}>{featured.dur}</span>
          </div>
          <div style={{ position:'relative' }}>
            <h2 style={{ fontSize:26, fontWeight:800, color:'white', lineHeight:1.1, marginBottom:8 }}>{featured.title}</h2>
            <p style={{ fontSize:12, color:'rgba(255,255,255,0.82)', lineHeight:1.45, marginBottom:14 }}>{featured.desc}</p>
            <div style={{ display:'flex', gap:10, alignItems:'center' }}>
              <button className="tap" onClick={(e) => { e.stopPropagation(); setSelected(featured.id); }} style={{ padding:'10px 16px', background:'white', color:featured.color, border:'none', borderRadius:R.tile, fontSize:12, fontWeight:800, display:'flex', alignItems:'center', gap:6, cursor:'pointer', fontFamily:'inherit' }}>
                <Icon name="play" size={13} color={featured.color} fill={featured.color} />
                Reservieren
              </button>
              <div style={{ display:'flex', flexDirection:'column' }}>
                <p style={{ fontSize:13, fontWeight:700, color:'white' }}>{featured.date} · {featured.time}</p>
                <p style={{ fontSize:11, color:'rgba(255,255,255,0.7)' }}>{featured.room}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
      {/* Upcoming */}
      <div style={{ padding:'22px 18px 28px' }}>
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10 }}>NÄCHSTE VORSTELLUNGEN</p>
        {upcoming.map(film => (
          <div key={film.id} className="tap" onClick={() => setSelected(film.id)} style={{ background:C.surface, borderRadius:R.card, padding:'14px', marginBottom:10, display:'flex', gap:14, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
            <div style={{ width:56, height:72, borderRadius:R.tile - 4, background:`linear-gradient(140deg, ${film.color}, oklch(from ${film.color} calc(l - 0.18) c h))`, flexShrink:0, position:'relative', overflow:'hidden' }}>
              <div style={{ position:'absolute', top:-10, right:-10, width:30, height:30, borderRadius:'50%', background:'rgba(255,255,255,0.1)' }}></div>
              <div style={{ position:'absolute', bottom:6, left:6, right:6 }}>
                <div style={{ height:2, background:'rgba(255,255,255,0.4)', borderRadius:1 }}></div>
              </div>
            </div>
            <div style={{ flex:1 }}>
              <p style={{ fontSize:14, fontWeight:700, color:C.text, marginBottom:2 }}>{film.title}</p>
              <p style={{ fontSize:11, color:C.textMuted, marginBottom:5 }}>{film.genre} · {film.dur}</p>
              <p style={{ fontSize:11, fontWeight:600, color:C.primary }}>{film.date} · {film.time} · {film.room}</p>
            </div>
            <Icon name="chevR" size={16} color={C.textMuted} />
          </div>
        ))}
      </div>
    </div>
  );
}

function KinoDetail({ C, R, film, onBack }) {
  const [selectedSeats, setSelectedSeats] = useState([]);
  const [reserved, setReserved] = useState(false);
  // Seat plan: 8 rows × 10 seats
  const rows = ['A','B','C','D','E','F','G','H'];
  const cols = [1,2,3,4,5,6,7,8,9,10];
  // Deterministic "taken" seats based on film id for stable demo
  const taken = new Set();
  const seedStr = String(film.id);
  let h = 0;
  for (let i = 0; i < seedStr.length; i++) h = (h * 31 + seedStr.charCodeAt(i)) | 0;
  const rng = () => { h = (h * 1103515245 + 12345) | 0; return Math.abs(h) / 2147483648; };
  rows.forEach(r => cols.forEach(c => {
    if (rng() < 0.28) taken.add(`${r}${c}`);
  }));

  const PRICE = 3.50;
  const toggleSeat = (id) => {
    if (taken.has(id)) return;
    setSelectedSeats(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
  };
  const total = selectedSeats.length * PRICE;

  if (reserved) {
    return (
      <div>
        <SubHeader title="Reservierung" onBack={onBack} C={C} />
        <div style={{ padding:'40px 24px', textAlign:'center' }}>
          <div style={{ width:96, height:96, borderRadius:48, background:C.greenLight, display:'flex', alignItems:'center', justifyContent:'center', margin:'0 auto 18px' }}>
            <Icon name="check" size={48} color={C.green} sw={2.8} />
          </div>
          <p style={{ fontSize:22, fontWeight:800, color:C.text, marginBottom:8 }}>Bestätigt!</p>
          <p style={{ fontSize:13, color:C.textMuted, lineHeight:1.5, marginBottom:24 }}>Du erhältst eine Bestätigung per Mail.<br/>Bitte 15 Min. vor Beginn da sein.</p>

          {/* Ticket */}
          <div style={{ background:C.surface, borderRadius:R.card, padding:0, marginBottom:18, boxShadow:`0 4px 16px ${C.shadow}`, overflow:'hidden', textAlign:'left' }}>
            <div style={{ background:`linear-gradient(140deg, ${film.color}, oklch(from ${film.color} calc(l - 0.18) c h))`, padding:'16px 18px' }}>
              <p style={{ fontSize:10, fontWeight:700, color:'rgba(255,255,255,0.7)', letterSpacing:'0.08em' }}>UNI KINO TICKET</p>
              <p style={{ fontSize:18, fontWeight:800, color:'white', marginTop:4 }}>{film.title}</p>
              <p style={{ fontSize:11, color:'rgba(255,255,255,0.8)', marginTop:3 }}>{film.date} · {film.time} · {film.room}</p>
            </div>
            <div style={{ display:'flex', position:'relative' }}>
              <div style={{ position:'absolute', top:-8, left:'40%', width:16, height:16, borderRadius:8, background:C.bg }}></div>
              <div style={{ position:'absolute', top:-8, right:-8, width:16, height:16, borderRadius:8, background:C.bg }}></div>
              <div style={{ flex:1, padding:'14px 18px' }}>
                <p style={{ fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em' }}>SITZE</p>
                <p style={{ fontSize:14, fontWeight:800, color:C.text, marginTop:3 }}>{selectedSeats.sort().join(' · ')}</p>
                <p style={{ fontSize:11, color:C.textMuted, marginTop:8 }}>{selectedSeats.length} × {PRICE.toFixed(2).replace('.', ',')} € = <span style={{ fontWeight:800, color:C.text }}>{total.toFixed(2).replace('.', ',')} €</span></p>
              </div>
              <div style={{ width:80, padding:'14px 12px', borderLeft:`2px dashed ${C.border}`, display:'flex', alignItems:'center', justifyContent:'center' }}>
                <div style={{ width:56, height:56, background:'white', borderRadius:6, display:'grid', gridTemplateColumns:'repeat(7, 1fr)', padding:4, gap:1 }}>
                  {Array.from({length:49}).map((_, i) => {
                    const fill = ((i * 17 + 13) % 7) < 4;
                    return <div key={i} style={{ background: fill ? '#0a0a0a' : 'transparent' }}></div>;
                  })}
                </div>
              </div>
            </div>
          </div>
          <button onClick={onBack} style={{ width:'100%', padding:'14px', borderRadius:R.tile, background:C.primary, color:'white', border:'none', fontWeight:700, fontSize:13, fontFamily:'inherit', cursor:'pointer' }}>Fertig</button>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Hero */}
      <div style={{ position:'relative', height:240, background:`linear-gradient(160deg, ${film.color} 0%, oklch(from ${film.color} calc(l - 0.25) c h) 100%)`, padding:'20px 22px', display:'flex', flexDirection:'column', justifyContent:'space-between' }}>
        <div style={{ position:'absolute', top:-50, right:-50, width:160, height:160, borderRadius:'50%', background:'rgba(255,255,255,0.06)' }}></div>
        <div style={{ position:'absolute', bottom:-40, left:-40, width:120, height:120, borderRadius:'50%', background:'rgba(255,255,255,0.04)' }}></div>
        <div className="tap" onClick={onBack} style={{ position:'relative', display:'inline-flex', alignItems:'center', gap:5, fontSize:13, fontWeight:600, color:'white', alignSelf:'flex-start' }}>
          <Icon name="chevL" size={16} color="white" />
          Zurück
        </div>
        <div style={{ position:'relative' }}>
          <div style={{ display:'flex', gap:6, marginBottom:8 }}>
            <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', background:'rgba(255,255,255,0.22)', color:'white', borderRadius:6, backdropFilter:'blur(8px)' }}>{film.genre}</span>
            <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', background:'rgba(255,255,255,0.22)', color:'white', borderRadius:6, backdropFilter:'blur(8px)' }}>{film.dur}</span>
            {film.fsk && <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', background:'rgba(255,255,255,0.22)', color:'white', borderRadius:6, backdropFilter:'blur(8px)' }}>FSK {film.fsk}</span>}
          </div>
          <h1 style={{ fontSize:28, fontWeight:800, color:'white', lineHeight:1.1, letterSpacing:'-0.01em' }}>{film.title}</h1>
          <div style={{ display:'flex', gap:8, marginTop:8, alignItems:'center' }}>
            <StarFill size={14} color="white" />
            <span style={{ fontSize:13, fontWeight:800, color:'white' }}>{film.rating || '4,6'}</span>
            <span style={{ fontSize:11, color:'rgba(255,255,255,0.7)' }}>({film.votes || '128'} Bewertungen)</span>
          </div>
        </div>
      </div>

      <div style={{ padding:'18px' }}>
        {/* Showing info */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:'14px 16px', marginBottom:14, display:'flex', justifyContent:'space-around', boxShadow:`0 2px 10px ${C.shadow}` }}>
          {[['Datum', film.date],['Zeit', film.time],['Saal', film.room]].map(([l, v]) => (
            <div key={l} style={{ textAlign:'center', flex:1 }}>
              <p style={{ fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em' }}>{l.toUpperCase()}</p>
              <p style={{ fontSize:13, fontWeight:800, color:C.text, marginTop:4 }}>{v}</p>
            </div>
          ))}
        </div>

        {/* Synopsis */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8 }}>HANDLUNG</p>
        <p style={{ fontSize:13, color:C.text, lineHeight:1.55, marginBottom:6 }}>{film.desc}</p>
        {film.longDesc && <p style={{ fontSize:13, color:C.textMuted, lineHeight:1.55, marginBottom:18 }}>{film.longDesc}</p>}

        {/* Cast & Crew */}
        {(film.director || film.cast) && (
          <>
            <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, marginTop:8 }}>CAST & CREW</p>
            <div style={{ background:C.surface, borderRadius:R.card, padding:'4px 0', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}` }}>
              {film.director && (
                <div style={{ display:'flex', justifyContent:'space-between', padding:'10px 15px', borderBottom:`1px solid ${C.border}` }}>
                  <span style={{ fontSize:12, color:C.textMuted, fontWeight:600 }}>Regie</span>
                  <span style={{ fontSize:12, color:C.text, fontWeight:700 }}>{film.director}</span>
                </div>
              )}
              {film.cast && (
                <div style={{ display:'flex', justifyContent:'space-between', padding:'10px 15px', gap:14 }}>
                  <span style={{ fontSize:12, color:C.textMuted, fontWeight:600, flexShrink:0 }}>Cast</span>
                  <span style={{ fontSize:12, color:C.text, fontWeight:700, textAlign:'right' }}>{film.cast}</span>
                </div>
              )}
            </div>
          </>
        )}

        {/* Seat plan */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10 }}>SITZPLATZ WÄHLEN</p>
        <div style={{ background:C.surface, borderRadius:R.card, padding:'16px 14px', marginBottom:14, boxShadow:`0 2px 10px ${C.shadow}` }}>
          {/* Screen */}
          <div style={{ height:14, background:`linear-gradient(to bottom, ${C.surfaceAlt}, transparent)`, margin:'0 18px 6px', borderRadius:'100% 100% 0 0 / 100% 100% 0 0', position:'relative' }}>
            <p style={{ position:'absolute', top:0, left:'50%', transform:'translateX(-50%)', fontSize:9, fontWeight:700, color:C.textMuted, letterSpacing:'0.08em' }}>LEINWAND</p>
          </div>
          <div style={{ display:'flex', flexDirection:'column', gap:4, marginTop:14, alignItems:'center' }}>
            {rows.map(r => (
              <div key={r} style={{ display:'flex', alignItems:'center', gap:4 }}>
                <span style={{ width:14, fontSize:9, fontWeight:700, color:C.textMuted, textAlign:'center' }}>{r}</span>
                {cols.map(c => {
                  const id = `${r}${c}`;
                  const isTaken = taken.has(id);
                  const isSelected = selectedSeats.includes(id);
                  return (
                    <div key={c} className="tap" onClick={() => toggleSeat(id)}
                      style={{ width:18, height:18, borderRadius:'5px 5px 3px 3px',
                        background: isTaken ? C.surfaceAlt : isSelected ? C.primary : C.primaryLight,
                        border: isTaken ? `1px solid ${C.border}` : `1px solid ${isSelected ? C.primary : 'transparent'}`,
                        cursor: isTaken ? 'not-allowed' : 'pointer',
                        opacity: isTaken ? 0.5 : 1,
                        transition:'all 0.12s', marginRight: c === 5 ? 8 : 0 }}>
                    </div>
                  );
                })}
                <span style={{ width:14, fontSize:9, fontWeight:700, color:C.textMuted, textAlign:'center' }}>{r}</span>
              </div>
            ))}
          </div>
          {/* Legend */}
          <div style={{ display:'flex', justifyContent:'center', gap:14, marginTop:14, paddingTop:12, borderTop:`1px solid ${C.border}` }}>
            {[['Frei',C.primaryLight],['Gewählt',C.primary],['Belegt',C.surfaceAlt]].map(([lbl, bg]) => (
              <div key={lbl} style={{ display:'flex', alignItems:'center', gap:5 }}>
                <div style={{ width:11, height:11, borderRadius:'3px 3px 2px 2px', background:bg, border: lbl==='Belegt' ? `1px solid ${C.border}` : 'none' }}></div>
                <span style={{ fontSize:10, fontWeight:600, color:C.textMuted }}>{lbl}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Summary + CTA */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:'14px 16px', marginBottom:14, display:'flex', justifyContent:'space-between', alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
          <div>
            <p style={{ fontSize:11, color:C.textMuted, fontWeight:600 }}>{selectedSeats.length} {selectedSeats.length === 1 ? 'Platz' : 'Plätze'} · {PRICE.toFixed(2).replace('.', ',')} €/Ticket</p>
            <p style={{ fontSize:20, fontWeight:800, color:C.text, marginTop:2, letterSpacing:'-0.01em' }}>{total.toFixed(2).replace('.', ',')} €</p>
          </div>
          {selectedSeats.length > 0 && (
            <p style={{ fontSize:11, color:C.primary, fontWeight:700, maxWidth:140, textAlign:'right', lineHeight:1.3 }}>{selectedSeats.sort().join(' · ')}</p>
          )}
        </div>
        <button
          onClick={() => selectedSeats.length > 0 && setReserved(true)}
          disabled={selectedSeats.length === 0}
          style={{ width:'100%', padding:'14px', borderRadius:R.tile, background: selectedSeats.length > 0 ? C.primary : C.surfaceAlt, color: selectedSeats.length > 0 ? 'white' : C.textMuted, border:'none', fontWeight:800, fontSize:14, fontFamily:'inherit', cursor: selectedSeats.length > 0 ? 'pointer' : 'not-allowed', display:'flex', alignItems:'center', justifyContent:'center', gap:8, marginBottom:24 }}>
          {selectedSeats.length === 0 ? 'Sitzplatz wählen' : 'Jetzt reservieren'}
        </button>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* PROFIL                                                              */
/* ══════════════════════════════════════════════════════════════════ */
function ProfileScreen({ C, CC, R, tweaks, mensaBalance, goto, onBack }) {
  const initials = tweaks.studentName.slice(0,2).toUpperCase();
  return (
    <div>
      <SubHeader title="Profil" onBack={onBack} C={C} />
      <div style={{ padding:'4px 18px 28px' }}>
        {/* Avatar block */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:'22px 18px', marginTop:14, marginBottom:14, textAlign:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
          <div style={{ width:84, height:84, borderRadius:42, margin:'0 auto 12px', background:`linear-gradient(140deg, ${C.primary}, oklch(from ${C.primary} calc(l - 0.18) c h))`, display:'flex', alignItems:'center', justifyContent:'center', fontSize:32, fontWeight:800, color:'white', letterSpacing:'-0.02em' }}>{initials}</div>
          <p style={{ fontSize:18, fontWeight:800, color:C.text }}>{tweaks.studentName} Schneider</p>
          <p style={{ fontSize:12, color:C.textMuted, marginTop:3 }}>Wirtschaftsinformatik · 2. Semester</p>
          <p style={{ fontSize:11, color:C.textMuted, marginTop:8, fontFamily:'ui-monospace, monospace' }}>Matrikelnr. 7349281</p>
        </div>

        {/* Studi-Ausweis */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, paddingLeft:4 }}>STUDIERENDENAUSWEIS</p>
        <div style={{ borderRadius:R.card, padding:'18px', marginBottom:18, background:`linear-gradient(135deg, ${C.primary}, oklch(from ${C.primary} calc(l - 0.22) c h))`, position:'relative', overflow:'hidden', boxShadow:`0 8px 24px ${C.shadow}` }}>
          <div style={{ position:'absolute', top:-30, right:-30, width:120, height:120, borderRadius:'50%', background:'rgba(255,255,255,0.07)' }}></div>
          <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:30, position:'relative' }}>
            <div>
              <p style={{ fontSize:10, fontWeight:700, color:'rgba(255,255,255,0.65)', letterSpacing:'0.08em' }}>UNIVERSITÄT HILDESHEIM</p>
              <p style={{ fontSize:14, fontWeight:700, color:'white', marginTop:4 }}>Semesterticket gültig</p>
              <p style={{ fontSize:10, color:'rgba(255,255,255,0.7)', marginTop:2 }}>01.04.2026 – 30.09.2026</p>
            </div>
            <div style={{ width:48, height:48, background:'white', borderRadius:8, display:'flex', alignItems:'center', justifyContent:'center' }}>
              <Icon name="qr" size={32} color={C.primary} sw={2} />
            </div>
          </div>
          <div style={{ display:'flex', gap:24 }}>
            <div>
              <p style={{ fontSize:9, fontWeight:700, color:'rgba(255,255,255,0.55)', letterSpacing:'0.06em' }}>NAME</p>
              <p style={{ fontSize:13, fontWeight:700, color:'white', marginTop:2 }}>{tweaks.studentName} Schneider</p>
            </div>
            <div>
              <p style={{ fontSize:9, fontWeight:700, color:'rgba(255,255,255,0.55)', letterSpacing:'0.06em' }}>MATRIKEL</p>
              <p style={{ fontSize:13, fontWeight:700, color:'white', marginTop:2, fontFamily:'ui-monospace, monospace' }}>7349281</p>
            </div>
          </div>
          <div style={{ marginTop:14, height:32, borderRadius:4, background:'white', display:'flex', alignItems:'center', justifyContent:'center', gap:1.5, padding:'0 12px' }}>
            {Array.from({length: 38}).map((_, i) => {
              const widths = [1, 2, 1, 3, 1, 2, 1, 1, 2, 3, 1, 2];
              const w = widths[i % widths.length];
              return <div key={i} style={{ width:w, height:'72%', background:'#0a0a0a' }}></div>;
            })}
          </div>
        </div>

        {/* Mensa-Karte balance */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, paddingLeft:4 }}>GUTHABEN</p>
        <div className="tap" onClick={() => goto('mensacard')}
          style={{ background:C.surface, borderRadius:R.card, padding:'16px', marginBottom:18, display:'flex', gap:14, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
          <div style={{ width:46, height:46, borderRadius:R.tile, background:C.amberLight, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <Icon name="card" size={22} color={C.amber} />
          </div>
          <div style={{ flex:1 }}>
            <p style={{ fontSize:13, color:C.textMuted, fontWeight:500 }}>Mensa-Karte</p>
            <p style={{ fontSize:20, fontWeight:800, color:C.text, lineHeight:1.1, marginTop:2 }}>{mensaBalance.toFixed(2).replace('.', ',')} €</p>
          </div>
          <span style={{ fontSize:11, fontWeight:700, padding:'5px 11px', background:C.primaryLight, color:C.primary, borderRadius:8, whiteSpace:'nowrap' }}>Aufladen</span>
        </div>

        {/* Quick links */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, paddingLeft:4 }}>SCHNELLZUGRIFF</p>
        <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}` }}>
          <ProfileRow icon="bell"     label="Benachrichtigungen"   C={C} R={R} onClick={() => goto('push')}        accent={C.red}    bg={C.redLight} />
          <ProfileRow icon="chart"    label="Notenübersicht"        C={C} R={R} onClick={() => goto('noten')}       accent={C.green}  bg={C.greenLight}  divider />
          <ProfileRow icon="users"    label="Lerngruppen"           C={C} R={R} onClick={() => goto('lerngruppen')} accent={C.primary} bg={C.primaryLight} divider />
          <ProfileRow icon="gradCap"  label="Klausurplan"           C={C} R={R} onClick={() => goto('klausuren')}   accent={C.amber}  bg={C.amberLight}  divider />
          <ProfileRow icon="dumbbell" label="Hochschulsport"        C={C} R={R} onClick={() => goto('sport')}       accent={C.green}  bg={C.greenLight}  divider />
          <ProfileRow icon="map"      label="Campus-Plan"          C={C} R={R} onClick={() => goto('campus')}      accent={C.purple} bg={C.purpleLight} divider />
          <ProfileRow icon="film"     label="Uni Kino"              C={C} R={R} onClick={() => goto('kino')}        accent={C.purple} bg={C.purpleLight} divider />
          <ProfileRow icon="list"     label="Aufgaben"              C={C} R={R} onClick={() => goto('todo')}        accent={C.amber}  bg={C.amberLight}  divider />
          <ProfileRow icon="mail"     label="Mails"                 C={C} R={R} onClick={() => goto('mail')}        accent={C.primary} bg={C.primaryLight} divider />
        </div>

        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, paddingLeft:4 }}>EINSTELLUNGEN</p>
        <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', boxShadow:`0 2px 10px ${C.shadow}` }}>
          <ProfileRow icon="grip"     label="Tab-Leiste anpassen"   sub="Wähle deine 5 Lieblings-Tabs" C={C} R={R} onClick={() => goto('navsettings')} accent={C.text} bg={C.surfaceAlt} />
          <ProfileRow icon="settings" label="App-Einstellungen"     sub="Sprache, Benachrichtigungen" C={C} R={R} accent={C.text} bg={C.surfaceAlt} divider />
          <ProfileRow icon="signOut"  label="Abmelden"              C={C} R={R} accent={C.red}  bg={C.redLight}  divider />
        </div>
      </div>
    </div>
  );
}

function ProfileRow({ icon, label, sub, C, R, onClick, accent, bg, divider }) {
  return (
    <div className="tap" onClick={onClick} style={{ display:'flex', alignItems:'center', gap:13, padding:'13px 16px', borderTop: divider ? `1px solid ${C.border}` : 'none' }}>
      <div style={{ width:34, height:34, borderRadius:R.tile - 4, background:bg, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <Icon name={icon} size={16} color={accent} />
      </div>
      <div style={{ flex:1 }}>
        <p style={{ fontSize:13, fontWeight:600, color:C.text }}>{label}</p>
        {sub && <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{sub}</p>}
      </div>
      <Icon name="chevR" size={16} color={C.textMuted} />
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* MENSA-KARTE                                                         */
/* ══════════════════════════════════════════════════════════════════ */
function MensaCardScreen({ C, R, balance, transactions, onTopUp, onBack }) {
  const [amount, setAmount] = useState(10);
  const [scanning, setScanning] = useState(false);
  const [done, setDone] = useState(false);

  const startScan = () => {
    if (scanning || done) return;
    setScanning(true);
    setTimeout(() => {
      setScanning(false);
      setDone(true);
      onTopUp(amount);
      setTimeout(() => setDone(false), 2200);
    }, 1800);
  };

  return (
    <div>
      <SubHeader title="Mensa-Karte" subtitle="Guthaben aufladen & ansehen" onBack={onBack} C={C} />
      <div style={{ padding:'18px' }}>
        {/* Balance hero */}
        <div style={{ borderRadius:R.card, padding:'22px 20px', marginBottom:18, background:`linear-gradient(135deg, ${C.amber}, oklch(from ${C.amber} calc(l - 0.18) c h))`, position:'relative', overflow:'hidden', boxShadow:`0 8px 24px ${C.shadow}` }}>
          <div style={{ position:'absolute', top:-30, right:-30, width:120, height:120, borderRadius:'50%', background:'rgba(255,255,255,0.08)' }}></div>
          <p style={{ fontSize:11, fontWeight:700, color:'rgba(255,255,255,0.75)', letterSpacing:'0.08em' }}>AKTUELLES GUTHABEN</p>
          <p style={{ fontSize:42, fontWeight:800, color:'white', marginTop:6, letterSpacing:'-0.02em' }}>{balance.toFixed(2).replace('.', ',')} €</p>
          <p style={{ fontSize:12, color:'rgba(255,255,255,0.75)', marginTop:6 }}>Karten-Nr. 7349281 · gültig bis 09/27</p>
        </div>

        {/* NFC Scanner */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10 }}>AUFLADEBETRAG</p>
        <div style={{ display:'flex', gap:8, marginBottom:18 }}>
          {[5, 10, 20, 50].map(v => {
            const active = amount === v;
            return (
              <div key={v} className="tap" onClick={() => setAmount(v)} style={{ flex:1, padding:'12px 0', textAlign:'center', borderRadius:R.tile, background: active ? C.primary : C.surface, border: active ? 'none' : `1px solid ${C.border}`, fontSize:13, fontWeight:800, color: active ? 'white' : C.text, boxShadow: active ? 'none' : `0 1px 4px ${C.shadow}` }}>
                {v} €
              </div>
            );
          })}
        </div>

        {/* Reader Visualization */}
        <div onClick={startScan} className="tap" style={{ background:C.surface, borderRadius:R.card, padding:'28px 20px', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}`, textAlign:'center' }}>
          <div style={{ width:130, height:130, margin:'0 auto 14px', borderRadius:65, background: done ? C.greenLight : scanning ? C.amberLight : C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center', position:'relative', transition:'background 0.3s' }}>
            {/* Ripples while scanning */}
            {scanning && [0, 1, 2].map(i => (
              <div key={i} style={{ position:'absolute', inset:0, borderRadius:'50%', border:`2px solid ${C.amber}`, animation:`mc-ripple 1.6s ${i * 0.5}s ease-out infinite`, opacity:0 }}></div>
            ))}
            <Icon name={done ? 'check' : 'card'} size={done ? 56 : 50} color={done ? C.green : scanning ? C.amber : C.primary} sw={done ? 3 : 2} />
          </div>
          <p style={{ fontSize:15, fontWeight:800, color:C.text, marginBottom:4 }}>
            {done ? `${amount},00 € aufgeladen` : scanning ? 'Karte erkannt…' : 'Karte auflegen'}
          </p>
          <p style={{ fontSize:12, color:C.textMuted, lineHeight:1.4 }}>
            {done ? 'Vielen Dank!' : scanning ? 'Bitte nicht bewegen' : 'Halte deine Karte an die Rückseite deines Smartphones'}
          </p>
          <style>{`
            @keyframes mc-ripple {
              0%   { opacity: 0.6; transform: scale(0.9); }
              100% { opacity: 0;   transform: scale(1.5); }
            }
          `}</style>
        </div>

        {/* History */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:10 }}>LETZTE BUCHUNGEN</p>
        <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', boxShadow:`0 2px 10px ${C.shadow}` }}>
          {transactions.map((t, i) => (
            <div key={t.id} style={{ display:'flex', alignItems:'center', gap:12, padding:'12px 15px', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
              <div style={{ width:34, height:34, borderRadius:R.tile - 4, background: t.amount > 0 ? C.greenLight : C.surfaceAlt, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                <Icon name={t.amount > 0 ? 'plus' : 'utensils'} size={15} color={t.amount > 0 ? C.green : C.textMuted} sw={2.4} />
              </div>
              <div style={{ flex:1 }}>
                <p style={{ fontSize:13, fontWeight:600, color:C.text }}>{t.label}</p>
                <p style={{ fontSize:11, color:C.textMuted, marginTop:1 }}>{t.date}</p>
              </div>
              <p style={{ fontSize:14, fontWeight:800, color: t.amount > 0 ? C.green : C.text, whiteSpace:'nowrap' }}>
                {t.amount > 0 ? '+' : ''}{t.amount.toFixed(2).replace('.', ',')} €
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* CAMPUS                                                              */
/* ══════════════════════════════════════════════════════════════════ */
const CAMPUS_BUILDINGS = [
  { id:'A', name:'Hauptgebäude A',     type:'Hörsäle',     x:60,  y:90,  w:130, h:74 },
  { id:'B', name:'Gebäude B',          type:'Seminarräume', x:210, y:75,  w:88,  h:64 },
  { id:'H', name:'Hauptbibliothek',    type:'Bibliothek',  x:80,  y:200, w:110, h:60 },
  { id:'M', name:'Mensa',              type:'Mensa & Café', x:210, y:170, w:90,  h:90 },
  { id:'AU',name:'Audimax',            type:'Veranstaltung', x:60, y:290, w:100, h:54 },
  { id:'S', name:'Sportzentrum',       type:'Sport',       x:185, y:285, w:115, h:62 },
];

function CampusScreen({ C, R, onBack }) {
  const [filter, setFilter] = useState('Alle');
  const filters = ['Alle','Hörsäle','Bibliothek','Mensa & Café','Sport'];
  const filtered = filter === 'Alle' ? CAMPUS_BUILDINGS : CAMPUS_BUILDINGS.filter(b => b.type === filter);
  const TYPE_COLOR = {
    'Hörsäle':       C.primary,
    'Seminarräume':  C.purple,
    'Bibliothek':    C.amber,
    'Mensa & Café':  C.green,
    'Veranstaltung': C.red,
    'Sport':         C.primary,
  };

  return (
    <div>
      <SubHeader title="Campus-Plan" subtitle="Universität Hildesheim · Bühler Campus" onBack={onBack} C={C} />
      <div style={{ padding:'14px 18px 28px' }}>
        {/* SVG Map */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:14, marginBottom:14, boxShadow:`0 2px 10px ${C.shadow}`, position:'relative', overflow:'hidden' }}>
          <svg viewBox="0 0 360 400" style={{ width:'100%', height:'auto', display:'block' }}>
            {/* Grass / background */}
            <rect x="0" y="0" width="360" height="400" fill={C.surfaceAlt} />
            {/* Paths */}
            <path d="M 0 175 L 360 175 M 175 0 L 175 400 M 60 60 L 320 350" stroke={C.border} strokeWidth="14" strokeLinecap="round" opacity="0.6" />
            <path d="M 0 175 L 360 175 M 175 0 L 175 400" stroke="white" strokeWidth="2" strokeDasharray="6 8" opacity="0.5" />
            {/* Trees */}
            {[[25,40],[330,30],[20,210],[340,210],[15,370],[345,370],[345,130]].map(([cx,cy], i) => (
              <g key={i}>
                <circle cx={cx} cy={cy} r="10" fill={C.green} opacity="0.4" />
                <circle cx={cx} cy={cy} r="6" fill={C.green} opacity="0.6" />
              </g>
            ))}
            {/* Buildings */}
            {CAMPUS_BUILDINGS.map(b => {
              const col = TYPE_COLOR[b.type] || C.primary;
              const dimmed = filter !== 'Alle' && b.type !== filter;
              return (
                <g key={b.id} opacity={dimmed ? 0.25 : 1} style={{ transition:'opacity 0.25s' }}>
                  <rect x={b.x} y={b.y} width={b.w} height={b.h} fill={col} rx="6" />
                  <rect x={b.x} y={b.y} width={b.w} height="10" fill="rgba(0,0,0,0.18)" rx="6" />
                  <text x={b.x + b.w/2} y={b.y + b.h/2 + 6} fontSize="22" fontWeight="800" fill="white" textAnchor="middle" fontFamily="Plus Jakarta Sans, sans-serif">{b.id}</text>
                </g>
              );
            })}
            {/* "You are here" pin */}
            <g>
              <circle cx="175" cy="175" r="14" fill={C.red} opacity="0.18" />
              <circle cx="175" cy="175" r="7"  fill={C.red} />
              <circle cx="175" cy="175" r="3"  fill="white" />
            </g>
          </svg>
          <div style={{ position:'absolute', bottom:14, left:14, padding:'5px 10px', background:C.surface, borderRadius:14, boxShadow:`0 2px 8px ${C.shadow}`, display:'flex', alignItems:'center', gap:6 }}>
            <div style={{ width:8, height:8, borderRadius:4, background:C.red }}></div>
            <span style={{ fontSize:10, fontWeight:700, color:C.text }}>Du bist hier</span>
          </div>
        </div>

        {/* Filters */}
        <div style={{ display:'flex', gap:6, marginBottom:14, overflowX:'auto' }} className="scroll-area">
          {filters.map(f => {
            const active = filter === f;
            return (
              <div key={f} className="tap" onClick={() => setFilter(f)} style={{ padding:'6px 13px', borderRadius:18, flexShrink:0, background: active ? C.primary : C.surfaceAlt, fontSize:11, fontWeight:700, color: active ? 'white' : C.textMuted, whiteSpace:'nowrap' }}>{f}</div>
            );
          })}
        </div>

        {/* Building list */}
        {filtered.map(b => {
          const col = TYPE_COLOR[b.type] || C.primary;
          return (
            <div key={b.id} className="tap" style={{ background:C.surface, borderRadius:R.card, padding:'13px 14px', marginBottom:8, display:'flex', gap:13, alignItems:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ width:44, height:44, borderRadius:R.tile - 4, background:col, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, fontSize:16, fontWeight:800, color:'white' }}>{b.id}</div>
              <div style={{ flex:1 }}>
                <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{b.name}</p>
                <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{b.type}</p>
              </div>
              <Icon name="chevR" size={16} color={C.textMuted} />
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* NAV SETTINGS                                                        */
/* ══════════════════════════════════════════════════════════════════ */
function NavSettingsScreen({ C, R, navTabs, onChange, onBack }) {
  const inNav = navTabs.map(id => TAB_CATALOG.find(t => t.id === id)).filter(Boolean);
  const outNav = TAB_CATALOG.filter(t => !navTabs.includes(t.id));

  const move = (id, dir) => {
    const idx = navTabs.indexOf(id);
    const newIdx = idx + dir;
    if (newIdx < 0 || newIdx >= navTabs.length) return;
    const arr = [...navTabs];
    [arr[idx], arr[newIdx]] = [arr[newIdx], arr[idx]];
    onChange(arr);
  };
  const removeTab = (id) => {
    if (navTabs.length <= 2) return;
    onChange(navTabs.filter(x => x !== id));
  };
  const addTab = (id) => {
    if (navTabs.length >= 5) return;
    onChange([...navTabs, id]);
  };
  const reset = () => onChange(['home','stundenplan','mensa','bibliothek','kurse']);

  return (
    <div>
      <SubHeader title="Tab-Leiste anpassen" subtitle={`${navTabs.length} von max. 5 Tabs gewählt`} onBack={onBack} C={C}
        action={<span className="tap" onClick={reset} style={{ fontSize:11, fontWeight:700, color:C.primary, padding:'5px 11px', background:C.primaryLight, borderRadius:8 }}>Zurücksetzen</span>} />

      <div style={{ padding:'14px 18px 28px' }}>
        {/* Preview */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:'14px 8px 10px', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}` }}>
          <p style={{ fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em', textAlign:'center', marginBottom:10 }}>VORSCHAU</p>
          <div style={{ display:'flex', alignItems:'flex-start', borderTop:`1px solid ${C.border}`, paddingTop:10 }}>
            {inNav.map((t, i) => (
              <div key={t.id} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:3 }}>
                <div style={{ width:34, height:30, display:'flex', alignItems:'center', justifyContent:'center', borderRadius:10, background: i === 0 ? C.primaryLight : 'transparent' }}>
                  <Icon name={t.icon} size={18} color={i === 0 ? C.primary : C.textMuted} sw={i === 0 ? 2.4 : 2} />
                </div>
                <span style={{ fontSize:9, fontWeight: i === 0 ? 700 : 500, color: i === 0 ? C.primary : C.textMuted }}>{t.label}</span>
              </div>
            ))}
            {Array.from({length: 5 - inNav.length}).map((_, i) => (
              <div key={`empty-${i}`} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:3, opacity:0.3 }}>
                <div style={{ width:24, height:24, borderRadius:12, border:`2px dashed ${C.textMuted}` }}></div>
                <span style={{ fontSize:9, color:C.textMuted }}>—</span>
              </div>
            ))}
          </div>
        </div>

        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8 }}>IN DER LEISTE ({inNav.length})</p>
        <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}` }}>
          {inNav.map((t, i) => (
            <div key={t.id} style={{ display:'flex', alignItems:'center', gap:11, padding:'12px 13px', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
              <Icon name="grip" size={16} color={C.textMuted} sw={2} />
              <div style={{ width:34, height:34, borderRadius:R.tile - 4, background:C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                <Icon name={t.icon} size={16} color={C.primary} />
              </div>
              <p style={{ flex:1, fontSize:13, fontWeight:600, color:C.text }}>{t.label}</p>
              <div className="tap" onClick={() => move(t.id, -1)} style={{ width:28, height:28, borderRadius:8, background: i === 0 ? 'transparent' : C.surfaceAlt, display:'flex', alignItems:'center', justifyContent:'center', opacity: i === 0 ? 0.25 : 1 }}>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke={C.text} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M18 15l-6-6-6 6"/></svg>
              </div>
              <div className="tap" onClick={() => move(t.id, 1)} style={{ width:28, height:28, borderRadius:8, background: i === inNav.length - 1 ? 'transparent' : C.surfaceAlt, display:'flex', alignItems:'center', justifyContent:'center', opacity: i === inNav.length - 1 ? 0.25 : 1 }}>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke={C.text} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M6 9l6 6 6-6"/></svg>
              </div>
              <div className="tap" onClick={() => removeTab(t.id)} style={{ width:28, height:28, borderRadius:8, background:C.redLight, display:'flex', alignItems:'center', justifyContent:'center', opacity: navTabs.length <= 2 ? 0.3 : 1 }}>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke={C.red} strokeWidth="2.5" strokeLinecap="round"><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </div>
            </div>
          ))}
        </div>

        {outNav.length > 0 && (
          <>
            <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8 }}>AUSGEBLENDET ({outNav.length})</p>
            <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', boxShadow:`0 2px 10px ${C.shadow}` }}>
              {outNav.map((t, i) => (
                <div key={t.id} style={{ display:'flex', alignItems:'center', gap:11, padding:'12px 13px', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <div style={{ width:34, height:34, borderRadius:R.tile - 4, background:C.surfaceAlt, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                    <Icon name={t.icon} size={16} color={C.textMuted} />
                  </div>
                  <p style={{ flex:1, fontSize:13, fontWeight:600, color:C.text }}>{t.label}</p>
                  <div className="tap" onClick={() => addTab(t.id)} style={{ padding:'5px 11px', borderRadius:8, background: navTabs.length >= 5 ? C.surfaceAlt : C.primaryLight, fontSize:11, fontWeight:700, color: navTabs.length >= 5 ? C.textMuted : C.primary, opacity: navTabs.length >= 5 ? 0.5 : 1 }}>
                    Hinzufügen
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* LERNGRUPPEN                                                         */
/* ══════════════════════════════════════════════════════════════════ */
function LerngruppenScreen({ C, CC, R, groups, onToggleJoin, onBack }) {
  const [filter, setFilter] = useState('Alle');
  const myCount = groups.filter(g => g.joined).length;
  const filters = ['Alle', 'Meine Gruppen', 'Offen'];
  const filtered = groups.filter(g => filter === 'Alle' || (filter === 'Meine Gruppen' && g.joined) || (filter === 'Offen' && !g.joined));
  return (
    <div>
      <SubHeader title="Lerngruppen" subtitle={`${myCount} Mitgliedschaften · ${groups.length} insgesamt`} onBack={onBack} C={C}
        action={<div className="tap" style={{ width:36, height:36, borderRadius:R.tile, background:C.primaryLight, display:'flex', alignItems:'center', justifyContent:'center' }}><Icon name="plus" size={18} color={C.primary} sw={2.4} /></div>} />
      <div style={{ background:C.surface, padding:'10px 18px 14px', borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', gap:6 }}>
          {filters.map(f => {
            const active = filter === f;
            return <div key={f} className="tap" onClick={() => setFilter(f)} style={{ padding:'6px 13px', borderRadius:14, background: active ? C.primaryLight : C.surfaceAlt, fontSize:11, fontWeight:700, color: active ? C.primary : C.textMuted, whiteSpace:'nowrap' }}>{f}</div>;
          })}
        </div>
      </div>
      <div style={{ padding:'14px 18px 28px' }}>
        {filtered.map(g => {
          const col = CC[g.courseId] || { bg:C.primaryLight, fg:C.primary, dot:C.primary };
          return (
            <div key={g.id} style={{ background:C.surface, borderRadius:R.card, padding:'15px', marginBottom:10, boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ display:'flex', alignItems:'flex-start', gap:11, marginBottom:12 }}>
                <div style={{ width:42, height:42, borderRadius:R.tile - 2, background:col.bg, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                  <Icon name="users" size={20} color={col.dot} />
                </div>
                <div style={{ flex:1, minWidth:0 }}>
                  <p style={{ fontSize:14, fontWeight:700, color:C.text, lineHeight:1.2 }}>{g.name}</p>
                  <p style={{ fontSize:11, color:col.fg, fontWeight:600, marginTop:3 }}>{g.courseLabel}</p>
                </div>
                <span className="tap" onClick={() => onToggleJoin(g.id)}
                  style={{ fontSize:11, fontWeight:700, padding:'6px 12px', borderRadius:R.tile - 4, background: g.joined ? col.bg : C.primary, color: g.joined ? col.fg : 'white', whiteSpace:'nowrap', flexShrink:0 }}>
                  {g.joined ? 'Mitglied' : 'Beitreten'}
                </span>
              </div>
              {/* Members avatars */}
              <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:10 }}>
                <div style={{ display:'flex' }}>
                  {g.memberAvatars.slice(0, 4).map((init, i) => (
                    <div key={i} style={{ width:26, height:26, borderRadius:13, background:col.bg, border:`2px solid ${C.surface}`, marginLeft: i === 0 ? 0 : -8, display:'flex', alignItems:'center', justifyContent:'center', fontSize:10, fontWeight:800, color:col.fg }}>{init}</div>
                  ))}
                  {g.members > 4 && (
                    <div style={{ width:26, height:26, borderRadius:13, background:C.surfaceAlt, border:`2px solid ${C.surface}`, marginLeft:-8, display:'flex', alignItems:'center', justifyContent:'center', fontSize:9, fontWeight:800, color:C.textMuted }}>+{g.members - 4}</div>
                  )}
                </div>
                <p style={{ fontSize:11, color:C.textMuted, fontWeight:600 }}>{g.members} {g.members === 1 ? 'Mitglied' : 'Mitglieder'}</p>
              </div>
              {/* Next meeting */}
              <div style={{ background:C.surfaceAlt, borderRadius:R.tile - 4, padding:'10px 12px', display:'flex', alignItems:'center', gap:10 }}>
                <Icon name="clock" size={14} color={C.textMuted} sw={2} />
                <div style={{ flex:1, minWidth:0 }}>
                  <p style={{ fontSize:11, fontWeight:700, color:C.text }}>{g.nextMeeting}</p>
                  <p style={{ fontSize:10, color:C.textMuted, marginTop:1 }}>{g.location}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* KLAUSUREN                                                           */
/* ══════════════════════════════════════════════════════════════════ */
function KlausurenScreen({ C, CC, R, exams, onBack }) {
  // exams is array sorted by days-until
  const next = exams[0];
  const nextCol = next ? (CC[next.courseId] || { bg:C.primaryLight, fg:C.primary, dot:C.primary }) : null;
  const totalLP = exams.reduce((a,e) => a + (e.credits || 0), 0);

  return (
    <div>
      <SubHeader title="Klausurplan" subtitle={`${exams.length} Prüfungen · ${totalLP} LP`} onBack={onBack} C={C} />
      <div style={{ padding:'18px' }}>
        {/* Countdown hero */}
        {next && (
          <div style={{ borderRadius:R.card, padding:'20px 18px', marginBottom:18, background:`linear-gradient(135deg, ${nextCol.dot}, oklch(from ${nextCol.dot} calc(l - 0.18) c h))`, position:'relative', overflow:'hidden', boxShadow:`0 8px 24px ${C.shadow}` }}>
            <div style={{ position:'absolute', top:-30, right:-30, width:120, height:120, borderRadius:'50%', background:'rgba(255,255,255,0.08)' }}></div>
            <p style={{ fontSize:10, fontWeight:700, color:'rgba(255,255,255,0.7)', letterSpacing:'0.08em' }}>NÄCHSTE KLAUSUR IN</p>
            <p style={{ fontSize:48, fontWeight:800, color:'white', lineHeight:1, marginTop:4, letterSpacing:'-0.03em' }}>{next.daysUntil} <span style={{ fontSize:20, fontWeight:700, opacity:0.8 }}>Tagen</span></p>
            <p style={{ fontSize:15, fontWeight:700, color:'white', marginTop:14 }}>{next.name}</p>
            <p style={{ fontSize:12, color:'rgba(255,255,255,0.78)', marginTop:3 }}>{next.date} · {next.time} · {next.room}</p>
          </div>
        )}

        {/* Timeline */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:12 }}>ALLE PRÜFUNGEN</p>
        <div style={{ position:'relative', paddingLeft:24 }}>
          <div style={{ position:'absolute', left:5, top:8, bottom:8, width:2, background:C.border }}></div>
          {exams.map((e, i) => {
            const col = CC[e.courseId] || { bg:C.primaryLight, fg:C.primary, dot:C.primary };
            const urgency = e.daysUntil <= 7 ? 'red' : e.daysUntil <= 21 ? 'amber' : 'normal';
            return (
              <div key={e.id} style={{ position:'relative', marginBottom: i === exams.length - 1 ? 0 : 12 }}>
                <div style={{ position:'absolute', left:-23, top:18, width:12, height:12, borderRadius:6, background:col.dot, border:`2px solid ${C.bg}` }}></div>
                <div style={{ background:C.surface, borderRadius:R.card, padding:'13px 15px', boxShadow:`0 2px 10px ${C.shadow}` }}>
                  <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', gap:10, marginBottom:8 }}>
                    <div style={{ flex:1, minWidth:0 }}>
                      <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{e.name}</p>
                      <p style={{ fontSize:11, color:col.fg, fontWeight:600, marginTop:2 }}>{e.prof}</p>
                    </div>
                    <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', borderRadius:8, whiteSpace:'nowrap',
                      background: urgency === 'red' ? C.redLight : urgency === 'amber' ? C.amberLight : C.surfaceAlt,
                      color: urgency === 'red' ? C.red : urgency === 'amber' ? C.amber : C.textMuted }}>
                      in {e.daysUntil} Tg.
                    </span>
                  </div>
                  <div style={{ display:'flex', gap:14, flexWrap:'wrap' }}>
                    <div style={{ display:'flex', alignItems:'center', gap:5 }}>
                      <Icon name="calendar" size={12} color={C.textMuted} sw={2} />
                      <span style={{ fontSize:11, color:C.textMuted, fontWeight:600 }}>{e.date}</span>
                    </div>
                    <div style={{ display:'flex', alignItems:'center', gap:5 }}>
                      <Icon name="clock" size={12} color={C.textMuted} sw={2} />
                      <span style={{ fontSize:11, color:C.textMuted, fontWeight:600 }}>{e.time}</span>
                    </div>
                    <div style={{ display:'flex', alignItems:'center', gap:5 }}>
                      <Icon name="mapPin" size={12} color={C.textMuted} sw={2} />
                      <span style={{ fontSize:11, color:C.textMuted, fontWeight:600 }}>{e.room}</span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* PUSH NOTIFICATIONS CENTER                                           */
/* ══════════════════════════════════════════════════════════════════ */
const NOTIF_TYPE = {
  course:   { icon:'book',     accent:'primary' },
  exam:     { icon:'gradCap',  accent:'red'     },
  deadline: { icon:'clock',    accent:'amber'   },
  mail:     { icon:'mail',     accent:'primary' },
  library:  { icon:'book',     accent:'green'   },
  mensa:    { icon:'utensils', accent:'amber'   },
  kino:     { icon:'film',     accent:'purple'  },
  system:   { icon:'bell',     accent:'textMuted' },
  sport:    { icon:'dumbbell', accent:'green'   },
  grade:    { icon:'chart',    accent:'green'   },
};

function PushScreen({ C, R, notifications, onMarkRead, onMarkAllRead, onClearAll, onBack }) {
  const groups = { Heute:[], Gestern:[], Älter:[] };
  notifications.forEach(n => {
    const key = n.bucket || 'Älter';
    if (!groups[key]) groups[key] = [];
    groups[key].push(n);
  });
  const unread = notifications.filter(n => n.unread).length;
  return (
    <div>
      <SubHeader title="Benachrichtigungen" subtitle={`${unread} ungelesen · ${notifications.length} insgesamt`} onBack={onBack} C={C}
        action={
          <div style={{ display:'flex', gap:6 }}>
            <span className="tap" onClick={onMarkAllRead} style={{ fontSize:11, fontWeight:700, padding:'5px 10px', borderRadius:8, background:C.primaryLight, color:C.primary, whiteSpace:'nowrap' }}>Alle gelesen</span>
          </div>
        } />
      <div style={{ padding:'12px 18px 28px' }}>
        {Object.entries(groups).map(([label, list]) => {
          if (list.length === 0) return null;
          return (
            <div key={label} style={{ marginBottom:14 }}>
              <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, paddingLeft:4 }}>{label.toUpperCase()}</p>
              <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', boxShadow:`0 2px 10px ${C.shadow}` }}>
                {list.map((n, i) => {
                  const type = NOTIF_TYPE[n.type] || NOTIF_TYPE.system;
                  const accentKey = type.accent;
                  const accentColor = C[accentKey] || C.primary;
                  const accentBg    = C[accentKey + 'Light'] || C.primaryLight;
                  return (
                    <div key={n.id} className="tap" onClick={() => onMarkRead(n.id)}
                      style={{ display:'flex', alignItems:'flex-start', gap:11, padding:'13px 14px', borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: n.unread ? C.surfaceAlt : 'transparent', transition:'background 0.18s' }}>
                      <div style={{ width:36, height:36, borderRadius:R.tile - 4, background:accentBg, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0, position:'relative' }}>
                        <Icon name={type.icon} size={16} color={accentColor} />
                        {n.unread && <div style={{ position:'absolute', top:-2, right:-2, width:9, height:9, borderRadius:5, background:accentColor, border:`2px solid ${C.surface}` }}></div>}
                      </div>
                      <div style={{ flex:1, minWidth:0 }}>
                        <div style={{ display:'flex', justifyContent:'space-between', gap:8, alignItems:'baseline' }}>
                          <p style={{ fontSize:13, fontWeight: n.unread ? 800 : 600, color:C.text }}>{n.title}</p>
                          <p style={{ fontSize:10, color:C.textMuted, fontWeight:600, whiteSpace:'nowrap', flexShrink:0 }}>{n.time}</p>
                        </div>
                        <p style={{ fontSize:12, color:C.textMuted, marginTop:3, lineHeight:1.4 }}>{n.body}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
        {notifications.length === 0 && (
          <div style={{ background:C.surface, borderRadius:R.card, padding:'40px 18px', textAlign:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
            <p style={{ fontSize:14, fontWeight:700, color:C.textMuted }}>Keine Benachrichtigungen</p>
            <p style={{ fontSize:12, color:C.textMuted, marginTop:4, opacity:0.7 }}>Du bist auf dem Laufenden!</p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* SPORT                                                               */
/* ══════════════════════════════════════════════════════════════════ */
function SportScreen({ C, R, sports, onToggleBook, onBack }) {
  const cats = ['Alle','Yoga','Bouldern','Ballsport','Cardio','Kraft'];
  const [cat, setCat] = useState('Alle');
  const items = cat === 'Alle' ? sports : sports.filter(s => s.category === cat);
  const booked = sports.filter(s => s.booked).length;

  return (
    <div>
      <SubHeader title="Hochschulsport" subtitle={`${booked} gebuchte Kurse · ${sports.length} im Programm`} onBack={onBack} C={C} />
      <div style={{ background:C.surface, padding:'12px 18px 14px', borderBottom:`1px solid ${C.border}` }}>
        <div style={{ display:'flex', gap:6, overflowX:'auto' }} className="scroll-area">
          {cats.map(c => {
            const active = cat === c;
            return <div key={c} className="tap" onClick={() => setCat(c)} style={{ padding:'6px 13px', borderRadius:18, flexShrink:0, background: active ? C.primary : C.surfaceAlt, fontSize:11, fontWeight:700, color: active ? 'white' : C.textMuted, whiteSpace:'nowrap' }}>{c}</div>;
          })}
        </div>
      </div>
      <div style={{ padding:'14px 18px 28px' }}>
        {items.map(s => {
          const full = s.spots === s.cap;
          const pct = s.spots / s.cap;
          const spotColor = pct >= 1 ? C.red : pct >= 0.7 ? C.amber : C.green;
          return (
            <div key={s.id} style={{ background:C.surface, borderRadius:R.card, padding:'14px', marginBottom:10, boxShadow:`0 2px 10px ${C.shadow}` }}>
              <div style={{ display:'flex', alignItems:'flex-start', gap:12, marginBottom:10 }}>
                <div style={{ width:46, height:46, borderRadius:R.tile - 2, background:`linear-gradient(135deg, ${s.color}, oklch(from ${s.color} calc(l - 0.15) c h))`, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                  <Icon name="dumbbell" size={22} color="white" sw={2.2} />
                </div>
                <div style={{ flex:1, minWidth:0 }}>
                  <p style={{ fontSize:14, fontWeight:700, color:C.text, lineHeight:1.2 }}>{s.name}</p>
                  <p style={{ fontSize:11, color:C.textMuted, marginTop:3 }}>{s.instructor} · {s.category}</p>
                </div>
                <span className="tap" onClick={() => !full && onToggleBook(s.id)}
                  style={{ fontSize:11, fontWeight:700, padding:'7px 13px', borderRadius:R.tile - 4, background: s.booked ? C.greenLight : full ? C.surfaceAlt : C.primary, color: s.booked ? C.green : full ? C.textMuted : 'white', whiteSpace:'nowrap', flexShrink:0, opacity: !s.booked && full ? 0.6 : 1, cursor: full && !s.booked ? 'not-allowed' : 'pointer' }}>
                  {s.booked ? 'Gebucht' : full ? 'Voll' : 'Buchen'}
                </span>
              </div>
              <div style={{ display:'flex', alignItems:'center', gap:14, flexWrap:'wrap', marginBottom:8 }}>
                <div style={{ display:'flex', alignItems:'center', gap:5 }}>
                  <Icon name="calendar" size={12} color={C.textMuted} sw={2} />
                  <span style={{ fontSize:11, fontWeight:600, color:C.textMuted }}>{s.day}</span>
                </div>
                <div style={{ display:'flex', alignItems:'center', gap:5 }}>
                  <Icon name="clock" size={12} color={C.textMuted} sw={2} />
                  <span style={{ fontSize:11, fontWeight:600, color:C.textMuted }}>{s.time}</span>
                </div>
                <div style={{ display:'flex', alignItems:'center', gap:5 }}>
                  <Icon name="mapPin" size={12} color={C.textMuted} sw={2} />
                  <span style={{ fontSize:11, fontWeight:600, color:C.textMuted }}>{s.room}</span>
                </div>
              </div>
              <div style={{ display:'flex', alignItems:'center', gap:8 }}>
                <div style={{ flex:1, height:4, background:C.surfaceAlt, borderRadius:2, overflow:'hidden' }}>
                  <div style={{ width:`${Math.min(pct, 1) * 100}%`, height:'100%', background:spotColor, borderRadius:2, transition:'width 0.6s' }}></div>
                </div>
                <span style={{ fontSize:11, fontWeight:700, color:spotColor, whiteSpace:'nowrap' }}>{s.spots}/{s.cap} belegt</span>
              </div>
            </div>
          );
        })}
        {items.length === 0 && (
          <div style={{ background:C.surface, borderRadius:R.card, padding:'24px', textAlign:'center', boxShadow:`0 2px 10px ${C.shadow}` }}>
            <p style={{ fontSize:13, color:C.textMuted, fontWeight:600 }}>Keine Kurse in dieser Kategorie</p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/* NOTENÜBERSICHT                                                      */
/* ══════════════════════════════════════════════════════════════════ */
function gradeColor(C, g) {
  if (g == null) return C.textMuted;
  const n = typeof g === 'number' ? g : parseFloat(String(g).replace(',', '.'));
  if (isNaN(n)) return C.textMuted;
  if (n <= 1.5) return C.green;
  if (n <= 2.5) return 'oklch(58% 0.15 100)'; // olive
  if (n <= 3.5) return C.amber;
  if (n <= 4.0) return 'oklch(58% 0.16 50)'; // orange
  return C.red;
}

function NotenScreen({ C, CC, R, currentCourses, pastSemesters, totalEctsRequired, gradeNotifEnabled, onToggleGradeNotif, onBack }) {
  const [openSem, setOpenSem] = useState(pastSemesters.length > 0 ? pastSemesters[0].id : null);

  // Compute overall GPA from past semesters
  const allPast = pastSemesters.flatMap(s => s.courses);
  const totalLP = allPast.reduce((a, c) => a + c.credits, 0);
  const weightedSum = allPast.reduce((a, c) => a + parseFloat(c.grade.replace(',', '.')) * c.credits, 0);
  const gpa = totalLP > 0 ? (weightedSum / totalLP) : null;
  const gpaStr = gpa != null ? gpa.toFixed(2).replace('.', ',') : '–';
  const gpaCol = gradeColor(C, gpa);
  const earnedLP = totalLP;
  const ectsPct = Math.min(earnedLP / totalEctsRequired, 1);

  return (
    <div>
      <SubHeader title="Notenübersicht" subtitle="Wirtschaftsinformatik · alle Module" onBack={onBack} C={C} />
      <div style={{ padding:'14px 18px 28px' }}>
        {/* GPA Hero */}
        <div style={{ borderRadius:R.card, padding:'22px 20px', marginBottom:14, background:`linear-gradient(135deg, ${gpaCol}, oklch(from ${gpaCol} calc(l - 0.18) c h))`, position:'relative', overflow:'hidden', boxShadow:`0 8px 24px ${C.shadow}` }}>
          <div style={{ position:'absolute', top:-40, right:-40, width:140, height:140, borderRadius:'50%', background:'rgba(255,255,255,0.07)' }}></div>
          <div style={{ position:'absolute', bottom:-30, left:-30, width:100, height:100, borderRadius:'50%', background:'rgba(255,255,255,0.05)' }}></div>
          <p style={{ fontSize:10, fontWeight:700, color:'rgba(255,255,255,0.7)', letterSpacing:'0.08em', position:'relative' }}>NOTENSCHNITT</p>
          <p style={{ fontSize:54, fontWeight:800, color:'white', lineHeight:1, marginTop:6, letterSpacing:'-0.03em', position:'relative' }}>{gpaStr}</p>
          <p style={{ fontSize:12, color:'rgba(255,255,255,0.78)', marginTop:8, fontWeight:600, position:'relative' }}>basierend auf {totalLP} LP · {allPast.length} Modulen</p>

          {/* ECTS Progress */}
          <div style={{ marginTop:18, position:'relative' }}>
            <div style={{ display:'flex', justifyContent:'space-between', marginBottom:6 }}>
              <span style={{ fontSize:11, color:'rgba(255,255,255,0.82)', fontWeight:700, letterSpacing:'0.04em' }}>ECTS-FORTSCHRITT</span>
              <span style={{ fontSize:11, color:'white', fontWeight:800 }}>{earnedLP} / {totalEctsRequired} LP</span>
            </div>
            <div style={{ height:8, background:'rgba(255,255,255,0.2)', borderRadius:4, overflow:'hidden' }}>
              <div style={{ height:'100%', width:`${ectsPct * 100}%`, background:'white', borderRadius:4, transition:'width 0.9s ease' }}></div>
            </div>
          </div>
        </div>

        {/* Notification settings card */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:'13px 15px', marginBottom:18, display:'flex', alignItems:'center', gap:12, boxShadow:`0 2px 10px ${C.shadow}` }}>
          <div style={{ width:38, height:38, borderRadius:R.tile - 4, background:C.greenLight, display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <Icon name="bell" size={16} color={C.green} />
          </div>
          <div style={{ flex:1 }}>
            <p style={{ fontSize:12, fontWeight:700, color:C.text }}>Benachrichtigung bei neuer Note</p>
            <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>Push, sobald Noten eingetragen werden</p>
          </div>
          <div className="tap" onClick={() => onToggleGradeNotif(!gradeNotifEnabled)} style={{ width:40, height:24, borderRadius:12, background: gradeNotifEnabled ? C.green : C.surfaceAlt, position:'relative', transition:'background 0.2s' }}>
            <div style={{ position:'absolute', top:2, left: gradeNotifEnabled ? 18 : 2, width:20, height:20, borderRadius:10, background:'white', transition:'left 0.2s', boxShadow:'0 1px 3px rgba(0,0,0,0.25)' }}></div>
          </div>
        </div>

        {/* Current semester */}
        <p style={{ fontSize:11, fontWeight:700, color:C.textMuted, letterSpacing:'0.04em', marginBottom:8, paddingLeft:4 }}>AKTUELLES SEMESTER</p>
        <div style={{ background:C.surface, borderRadius:R.card, overflow:'hidden', marginBottom:18, boxShadow:`0 2px 10px ${C.shadow}` }}>
          {currentCourses.map((c, i) => {
            const col = CC[c.id] || { bg:C.primaryLight, fg:C.primary, dot:C.primary };
            return (
              <div key={c.id} style={{ display:'flex', alignItems:'center', gap:11, padding:'12px 14px', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                <div style={{ width:8, height:38, borderRadius:4, background:col.dot, flexShrink:0 }}></div>
                <div style={{ flex:1, minWidth:0 }}>
                  <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{c.name}</p>
                  <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{c.credits} LP · Endklausur {c.nextExam || 'tbd'}</p>
                </div>
                <span style={{ fontSize:10, fontWeight:700, padding:'4px 9px', background:C.surfaceAlt, color:C.textMuted, borderRadius:8, whiteSpace:'nowrap' }}>Ausstehend</span>
              </div>
            );
          })}
        </div>

        {/* Past semesters */}
        {pastSemesters.map(sem => {
          const open = openSem === sem.id;
          const semLP = sem.courses.reduce((a, c) => a + c.credits, 0);
          const semAvg = sem.courses.reduce((a, c) => a + parseFloat(c.grade.replace(',', '.')) * c.credits, 0) / semLP;
          const avgStr = semAvg.toFixed(2).replace('.', ',');
          const avgCol = gradeColor(C, semAvg);
          return (
            <div key={sem.id} style={{ marginBottom:12 }}>
              <div className="tap" onClick={() => setOpenSem(open ? null : sem.id)} style={{ background:C.surface, borderRadius: open ? `${R.card}px ${R.card}px 0 0` : R.card, padding:'14px 16px', display:'flex', alignItems:'center', gap:12, boxShadow:`0 2px 10px ${C.shadow}` }}>
                <div style={{ flex:1 }}>
                  <p style={{ fontSize:13, fontWeight:800, color:C.text }}>{sem.label}{sem.semester ? ` · ${sem.semester}` : ''}</p>
                  <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{sem.courses.length} Module · {semLP} LP</p>
                </div>
                <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end' }}>
                  <p style={{ fontSize:9, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em' }}>SCHNITT</p>
                  <p style={{ fontSize:18, fontWeight:800, color:avgCol, lineHeight:1, marginTop:2 }}>{avgStr}</p>
                </div>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={C.textMuted} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ transform: open ? 'rotate(180deg)' : 'rotate(0)', transition:'transform 0.2s' }}>
                  <path d="M6 9l6 6 6-6"/>
                </svg>
              </div>
              {open && (
                <div style={{ background:C.surface, borderRadius:`0 0 ${R.card}px ${R.card}px`, overflow:'hidden', boxShadow:`0 2px 10px ${C.shadow}`, borderTop:`1px solid ${C.border}` }}>
                  {sem.courses.map((c, i) => {
                    const col = CC[c.colorRef] || { bg:C.surfaceAlt, fg:C.text, dot:C.textMuted };
                    const gCol = gradeColor(C, c.grade);
                    return (
                      <div key={c.id} style={{ display:'flex', alignItems:'center', gap:11, padding:'12px 14px', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                        <div style={{ width:8, height:38, borderRadius:4, background:col.dot, flexShrink:0 }}></div>
                        <div style={{ flex:1, minWidth:0 }}>
                          <p style={{ fontSize:13, fontWeight:700, color:C.text }}>{c.name}</p>
                          <p style={{ fontSize:11, color:C.textMuted, marginTop:2 }}>{c.credits} LP · {c.prof}</p>
                        </div>
                        <div style={{ minWidth:42, height:34, borderRadius:8, background: gCol + (C.bg.startsWith('oklch(1') ? '22' : '33'), display:'flex', alignItems:'center', justifyContent:'center', padding:'0 9px' }}>
                          <span style={{ fontSize:15, fontWeight:800, color:gCol }}>{c.grade}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}

        {/* Grade scale legend */}
        <div style={{ background:C.surface, borderRadius:R.card, padding:'14px', marginTop:8, boxShadow:`0 2px 10px ${C.shadow}` }}>
          <p style={{ fontSize:10, fontWeight:700, color:C.textMuted, letterSpacing:'0.05em', marginBottom:10 }}>NOTENSKALA</p>
          <div style={{ display:'flex', gap:6 }}>
            {[['1,0–1,5','sehr gut',1.0],['1,6–2,5','gut',2.0],['2,6–3,5','befr.',3.0],['3,6–4,0','ausr.',4.0],['4,1–5,0','nicht best.',5.0]].map(([range, lbl, val]) => (
              <div key={lbl} style={{ flex:1, padding:'8px 4px', background: gradeColor(C, val) + '22', borderRadius:8, textAlign:'center' }}>
                <p style={{ fontSize:9, fontWeight:800, color:gradeColor(C, val) }}>{lbl}</p>
                <p style={{ fontSize:8, color:C.textMuted, marginTop:2, fontWeight:600 }}>{range}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── EXPORT ─────────────────────────────────────────────────────── */
Object.assign(window, {
  TAB_CATALOG,
  Icon, StarFill, StatusBar, BottomNav, SectionLabel, SubHeader,
  HomeScreen, StundenplanScreen, MensaScreen, BibliothekScreen, KurseScreen,
  MailScreen, TodoScreen, KinoScreen,
  ProfileScreen, MensaCardScreen, CampusScreen, NavSettingsScreen,
  LerngruppenScreen, KlausurenScreen, PushScreen, SportScreen,
  NotenScreen, LibraryBookingScreen, BIB_DAYS,
});
