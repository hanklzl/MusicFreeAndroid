// Action matcher: maps ordered ParsedEvent[] within a session to a mixed list of
// structured ActionCards + raw rows. Rules are applied in declared order; the first
// rule to match at a position wins and "consumes" indices.
//
// Mirrors the prototyped vanilla JS in tools/logan-viewer-mockup/index.html.

import type { ParsedEvent } from '../model/types.js';
import type {
  ActionCardData,
  ActionMatch,
  ActionResult,
  ActionRule,
  TimelineItem,
} from './types.js';

// ---------- helpers ----------

function fmtDuration(ms: number | undefined): string {
  if (ms == null) return '';
  if (ms < 1000) return `${ms}ms`;
  const totalSec = Math.round(ms / 1000);
  if (totalSec < 60) return `${(ms / 1000).toFixed(1)}s`;
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

function fieldStr(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return '?';
  }
}

function f(event: ParsedEvent, key: string): unknown {
  return event.fields?.[key];
}

// ---------- rules ----------

const ruleAppStartup: ActionRule = {
  id: 'app_startup',
  label: 'App 启动',
  icon: '🚀',
  match(events, start) {
    const head = events[start]!;
    if (head.event !== 'app_startup_activity_create_start') return null;
    const consumed = [start];
    let end = start;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === head.sessionId; i++) {
      if (events[i]!.event.startsWith('app_startup_')) {
        consumed.push(i);
        end = i;
      } else break;
    }
    const dur = events[end]!.timestampMs - head.timestampMs;
    return mkMatch('app_startup', '🚀', 'App 启动', `cold · ${fmtDuration(dur)}`, 'success', consumed, events, { durationMs: dur });
  },
};

const ruleAppLifecycle: ActionRule = {
  id: 'app_lifecycle',
  label: '前后台切换',
  icon: '📲',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'app_background' && e.event !== 'app_foreground') return null;
    const isBackground = e.event === 'app_background';
    const title = isBackground ? '切到后台' : '回到前台';
    const bits: string[] = [];
    if (isBackground) {
      const ls = f(e, 'lastScreen');
      if (ls) bits.push(`从 ${fieldStr(ls)}`);
      if (f(e, 'isPlaying')) bits.push('播放中');
      const t = f(e, 'trigger');
      if (t) bits.push(fieldStr(t));
    } else {
      const rs = f(e, 'resumeScreen');
      if (rs) bits.push(`回到 ${fieldStr(rs)}`);
      const bg = f(e, 'backgroundedDurationMs');
      if (typeof bg === 'number') bits.push(`后台 ${fmtDuration(bg)}`);
      const t = f(e, 'trigger');
      if (t) bits.push(fieldStr(t));
    }
    return mkMatch('app_lifecycle', '📲', title, bits.join(' · '), 'success', [start], events, {});
  },
};

const COMPONENT_LIFECYCLE: Record<string, { icon: string; verb: string; kind: string; subjectKey: string | null }> = {
  activity_created:         { icon: '🧩', verb: '创建',         kind: 'Activity',     subjectKey: 'activity' },
  activity_destroyed:       { icon: '🧩', verb: '销毁',         kind: 'Activity',     subjectKey: 'activity' },
  store_created:            { icon: '🧱', verb: '创建',         kind: 'Store',        subjectKey: 'storeId' },
  store_destroyed:          { icon: '🧱', verb: '销毁',         kind: 'Store',        subjectKey: 'storeId' },
  plugin_engine_init:       { icon: '⚙️', verb: '初始化',       kind: '插件引擎',     subjectKey: null },
  plugin_engine_destroyed:  { icon: '⚙️', verb: '销毁',         kind: '插件引擎',     subjectKey: null },
  media_session_started:    { icon: '🎧', verb: '启动',         kind: 'MediaSession', subjectKey: null },
  media_session_destroyed:  { icon: '🎧', verb: '销毁',         kind: 'MediaSession', subjectKey: null },
  process_start_after_kill: { icon: '💀', verb: '进程死亡后重启', kind: '',             subjectKey: null },
};

const ruleComponentLifecycle: ActionRule = {
  id: 'component_lifecycle',
  label: '组件生命周期',
  icon: '🧩',
  match(events, start) {
    const e = events[start]!;
    const meta = COMPONENT_LIFECYCLE[e.event];
    if (!meta) return null;
    const subject = meta.subjectKey ? fieldStr(f(e, meta.subjectKey)) : '';
    const title = meta.kind
      ? `${meta.kind}${subject ? ` « ${subject} »` : ''} ${meta.verb}`
      : meta.verb;
    const bits: string[] = [];
    if (e.event === 'activity_destroyed') {
      const reason = f(e, 'reason'); if (reason) bits.push(fieldStr(reason));
      if (f(e, 'isFinishing')) bits.push('用户退出');
      if (f(e, 'isChangingConfigurations')) bits.push('配置变更');
      const lt = f(e, 'lifetimeMs'); if (typeof lt === 'number') bits.push(`存活 ${fmtDuration(lt)}`);
    } else if (e.event === 'activity_created') {
      if (f(e, 'isColdStart')) bits.push('冷启动');
      else if (f(e, 'isConfigChange')) bits.push('配置变更重建');
      else if (f(e, 'hasSavedState')) bits.push('从 SavedState 恢复');
      else bits.push('全新创建');
    } else if (e.event === 'store_destroyed') {
      const sc = f(e, 'scope'); if (sc) bits.push(`scope=${fieldStr(sc)}`);
      const r = f(e, 'reason'); if (r) bits.push(fieldStr(r));
      if (f(e, 'isPlaying')) bits.push('销毁时仍在播放');
    } else if (e.event === 'store_created') {
      const sc = f(e, 'scope'); if (sc) bits.push(`scope=${fieldStr(sc)}`);
      if (f(e, 'restoredFromSnapshot')) bits.push(`从 snapshot 恢复 (${fieldStr(f(e, 'snapshotKeys')) || '?'} 字段)`);
      else bits.push('全新创建');
    } else if (e.event === 'plugin_engine_init') {
      const pc = f(e, 'pluginCount'); if (pc != null) bits.push(`${fieldStr(pc)} 个插件`);
      const dm = f(e, 'durationMs'); if (typeof dm === 'number') bits.push(fmtDuration(dm));
      const ver = f(e, 'jsEngineVersion'); if (ver) bits.push(fieldStr(ver));
    } else if (e.event === 'plugin_engine_destroyed') {
      const r = f(e, 'reason'); if (r) bits.push(fieldStr(r));
    } else if (e.event === 'media_session_started') {
      const rq = f(e, 'restoredQueueSize'); if (rq != null) bits.push(`队列 ${fieldStr(rq)} 首`);
    } else if (e.event === 'media_session_destroyed') {
      const r = f(e, 'reason'); if (r) bits.push(fieldStr(r));
      const qs = f(e, 'queueSize'); if (qs != null) bits.push(`队列 ${fieldStr(qs)} 首`);
    } else if (e.event === 'process_start_after_kill') {
      const sr = f(e, 'suspectedReason'); if (sr) bits.push(fieldStr(sr));
      const lbd = f(e, 'lastBackgroundedDurationMs');
      if (typeof lbd === 'number') bits.push(`后台 ${fmtDuration(lbd)} 后被杀`);
    }
    let result: ActionResult = 'success';
    if (/_destroyed$/.test(e.event)) result = 'partial';
    if (e.event === 'process_start_after_kill') result = 'failure';
    return mkMatch('component_lifecycle', meta.icon, title, bits.join(' · '), result, [start], events, {});
  },
};

const ruleTabSwitch: ActionRule = {
  id: 'tab_switch',
  label: 'Tab 切换',
  icon: '🔁',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'tab_switch') return null;
    const to = fieldStr(f(e, 'to'));
    const from = fieldStr(f(e, 'from'));
    const src = fieldStr(f(e, 'source'));
    return mkMatch(
      'tab_switch',
      '🔁',
      `切到 « ${to} » Tab`,
      [from && `from ${from}`, src && `来源 ${src}`].filter(Boolean).join(' · '),
      'success',
      [start],
      events,
      {},
    );
  },
};

const rulePlayAction: ActionRule = {
  id: 'play',
  label: '播放',
  icon: '▶',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'player_play') return null;
    const sid = fieldStr(f(e, 'sid'));
    const itemName = fieldStr(f(e, 'itemName')) || '(未知)';
    const platform = fieldStr(f(e, 'itemPlatform')) || '?';
    const consumed = [start];
    let end = start;
    let result: ActionResult = 'ongoing';
    let durationMs: number | undefined;
    let cacheStatus: 'hit' | 'miss' | 'bypass' | undefined;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === e.sessionId; i++) {
      const ev = events[i]!;
      const sameSid = sid && fieldStr(f(ev, 'sid')) === sid;
      const sameTrace = e.traceId && ev.traceId === e.traceId;
      if (sameSid || sameTrace) {
        consumed.push(i); end = i;
        if (ev.event === 'cache_hit') cacheStatus = 'hit';
        else if (ev.event === 'cache_miss') cacheStatus = 'miss';
        else if (ev.event === 'cache_bypass') cacheStatus = 'bypass';
        if (
          ev.event === 'player_pause' ||
          ev.event === 'player_skip_next' ||
          ev.event === 'playback_failed'
        ) {
          durationMs = ev.timestampMs - e.timestampMs;
          result = ev.event === 'playback_failed' ? 'failure' : 'success';
          break;
        }
      } else if (ev.event === 'player_play') {
        durationMs = events[end]!.timestampMs - e.timestampMs;
        result = 'success';
        break;
      }
    }
    const cacheLabel =
      cacheStatus === 'hit' ? '命中缓存' :
      cacheStatus === 'miss' ? '未命中' :
      cacheStatus === 'bypass' ? '跳过缓存' : null;
    const subtitle = [
      platform,
      cacheLabel,
      result === 'ongoing' ? '播放中' : fmtDuration(durationMs ?? 0),
    ].filter(Boolean).join(' · ');
    return mkMatch('play', '▶', `播放《${itemName}》`, subtitle, result, consumed, events, {
      durationMs,
      cache: cacheStatus ?? 'unknown',
    });
  },
};

const ruleSearchAction: ActionRule = {
  id: 'search',
  label: '搜索',
  icon: '🔍',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'search_start') return null;
    const trace = e.traceId;
    const consumed = [start];
    let ok = 0, fail = 0, total = 0, lastDur = 0;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === e.sessionId; i++) {
      const ev = events[i]!;
      if (ev.traceId === trace && ev.event.startsWith('search_')) {
        consumed.push(i);
        if (ev.event === 'search_plugin_success') ok++;
        if (ev.event === 'search_plugin_failed') fail++;
        if (ev.event === 'search_session_page_success') {
          lastDur = ev.durationMs ?? lastDur;
          const tc = f(ev, 'totalCount');
          if (typeof tc === 'number') total = tc;
        }
      } else if (ev.timestampMs - e.timestampMs > 30_000) break;
    }
    const followups: { arrow: string; text: string; id: string }[] = [];
    const searchEnd = consumed.length
      ? events[consumed[consumed.length - 1]!]!.timestampMs
      : e.timestampMs;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === e.sessionId; i++) {
      const ev = events[i]!;
      if (ev.timestampMs < searchEnd) continue;
      if (ev.timestampMs - searchEnd > 30_000) break;
      const target = fieldStr(f(ev, 'targetId'));
      if (ev.event === 'ui_click' && /search\.result\./.test(target)) {
        const label = fieldStr(f(ev, 'sheetName')) || fieldStr(f(ev, 'itemName')) || target;
        const kind = target.includes('sheet') ? '打开歌单' : '点击结果';
        followups.push({ arrow: '→', text: `${kind} « ${label} »`, id: ev.id });
        consumed.push(i);
      }
    }
    const platformCountField = f(e, 'platformCount');
    const platforms = (ok + fail) || (typeof platformCountField === 'number' ? platformCountField : 0);
    const subBits: string[] = [`${ok}/${platforms} 插件`];
    if (total) subBits.push(`${total} 条`);
    if (lastDur) subBits.push(fmtDuration(lastDur));
    const result: ActionResult = fail === 0 ? 'success' : ok > 0 ? 'partial' : 'failure';
    const card: ActionCardData = {
      ruleId: 'search',
      icon: '🔍',
      title: `搜索 "${fieldStr(f(e, 'query'))}"`,
      subtitle: subBits.join(' · '),
      result,
      durationMs: lastDur,
      followups,
      fields: { ok, fail, total },
      childEventIds: consumed.map((i) => events[i]!.id),
    };
    return { ruleId: 'search', consumedIndices: consumed, card };
  },
};

const rulePluginInstall: ActionRule = {
  id: 'plugin_install',
  label: '插件安装',
  icon: '📦',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'plugin_operation_start' || fieldStr(f(e, 'operation')) !== 'install') return null;
    const trace = e.traceId;
    const consumed = [start];
    let endEvent: ParsedEvent | undefined;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === e.sessionId; i++) {
      const ev = events[i]!;
      if (ev.traceId === trace && /^plugin_operation_(success|failed)$/.test(ev.event)) {
        consumed.push(i);
        endEvent = ev;
        break;
      }
    }
    const success = endEvent?.event === 'plugin_operation_success';
    const dur = endEvent ? endEvent.timestampMs - e.timestampMs : undefined;
    const pluginName = fieldStr(f(e, 'pluginName'));
    const reason = endEvent ? fieldStr(f(endEvent, 'reason')) || '未知' : '';
    const version = endEvent ? fieldStr(f(endEvent, 'version')) || '?' : '';
    const subtitle = success
      ? `成功 · v${version} · ${fmtDuration(dur)}`
      : endEvent ? `失败：${reason}` : '安装中…';
    const result: ActionResult = success ? 'success' : endEvent ? 'failure' : 'ongoing';
    return mkMatch('plugin_install', '📦', `安装插件 « ${pluginName} »`, subtitle, result, consumed, events, { durationMs: dur });
  },
};

const ruleDialog: ActionRule = {
  id: 'dialog',
  label: '弹窗',
  icon: '💬',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'dialog_open') return null;
    const dialogId = fieldStr(f(e, 'dialogId'));
    const consumed = [start];
    let closer: ParsedEvent | undefined;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === e.sessionId; i++) {
      const ev = events[i]!;
      if (ev.event === 'dialog_dismiss' && fieldStr(f(ev, 'dialogId')) === dialogId) {
        consumed.push(i);
        closer = ev;
        break;
      }
      if (ev.event === 'ui_click' && fieldStr(f(ev, 'dialogId')) === dialogId) {
        consumed.push(i);
      }
    }
    const dur = closer
      ? (typeof f(closer, 'durationMs') === 'number'
          ? (f(closer, 'durationMs') as number)
          : closer.timestampMs - e.timestampMs)
      : undefined;
    const outcome = closer ? fieldStr(f(closer, 'outcome')) : 'ongoing';
    const innerClicks = consumed.map((i) => events[i]!).filter((ev) => ev.event === 'ui_click');
    const subtitle = closer
      ? `${outcome === 'confirm' ? '确认' : outcome === 'cancel' ? '取消' : outcome} · ${fmtDuration(dur)} · ${innerClicks.length} 次内部点击`
      : '打开中';
    const result: ActionResult = outcome === 'confirm' ? 'success' : outcome === 'cancel' ? 'failure' : 'ongoing';
    return mkMatch('dialog', '💬', `弹窗 « ${dialogId} »`, subtitle, result, consumed, events, { durationMs: dur });
  },
};

const ruleScreenVisit: ActionRule = {
  id: 'screen_visit',
  label: '页面浏览',
  icon: '📱',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'screen_enter') return null;
    const route = fieldStr(f(e, 'route'));
    const consumed = [start];
    let dur: number | undefined;
    for (let i = start + 1; i < events.length && events[i]!.sessionId === e.sessionId; i++) {
      const ev = events[i]!;
      if (ev.event === 'screen_exit' && fieldStr(f(ev, 'route')) === route) {
        consumed.push(i);
        const d = f(ev, 'durationMs');
        dur = typeof d === 'number' ? d : ev.timestampMs - e.timestampMs;
        break;
      }
      if (ev.event === 'screen_enter') break;
    }
    const source = fieldStr(f(e, 'source'));
    const subtitle = dur != null
      ? `停留 ${fmtDuration(dur)} · 来源 ${source || '—'}`
      : `来源 ${source || '—'}`;
    return mkMatch('screen_visit', '📱', `进入 « ${route} »`, subtitle, dur != null ? 'success' : 'ongoing', consumed, events, { durationMs: dur });
  },
};

const ruleUiClick: ActionRule = {
  id: 'ui_click',
  label: '点击',
  icon: '👆',
  match(events, start) {
    const e = events[start]!;
    if (e.event !== 'ui_click') return null;
    const targetId = fieldStr(f(e, 'targetId'));
    const targetLabel = fieldStr(f(e, 'targetLabel'));
    const screen = fieldStr(f(e, 'screen'));
    const label = targetLabel || targetId;
    return mkMatch('ui_click', '👆', `点击 « ${label} »`, `${screen || '—'} · ${targetId || '—'}`, 'success', [start], events, {});
  },
};

const ruleError: ActionRule = {
  id: 'error',
  label: '错误',
  icon: '❌',
  match(events, start) {
    const e = events[start]!;
    if (e.level !== 'error') return null;
    const cls = e.errorClass || '错误';
    const msg = e.errorMessage || e.event;
    const reason = fieldStr(f(e, 'reason'));
    return mkMatch(
      'error',
      '❌',
      `${cls}: ${msg}`,
      `${e.category}/${e.event}${reason ? ` · ${reason}` : ''}`,
      'failure',
      [start],
      events,
      { errorClass: e.errorClass, errorMessage: e.errorMessage },
    );
  },
};

export const RULES: ActionRule[] = [
  ruleAppStartup,
  ruleAppLifecycle,
  ruleComponentLifecycle,
  ruleTabSwitch,
  rulePlayAction,
  ruleSearchAction,
  rulePluginInstall,
  ruleDialog,
  ruleScreenVisit,
  ruleUiClick,
  ruleError,
];

function mkMatch(
  ruleId: string,
  icon: string,
  title: string,
  subtitle: string | undefined,
  result: ActionResult,
  consumed: number[],
  events: ParsedEvent[],
  extraFields: Record<string, unknown>,
): ActionMatch {
  return {
    ruleId,
    consumedIndices: consumed,
    card: {
      ruleId,
      icon,
      title,
      subtitle,
      result,
      durationMs: typeof extraFields.durationMs === 'number' ? (extraFields.durationMs as number) : undefined,
      fields: extraFields,
      childEventIds: consumed.map((i) => events[i]!.id),
    },
  };
}

/**
 * Run rules over a session's events. Output items are sorted by timestamp, mixing
 * structured ActionCards and raw rows. Each event ends up in at most one item.
 */
export function matchActions(events: ParsedEvent[]): TimelineItem[] {
  const items: TimelineItem[] = [];
  const consumed = new Set<number>();
  for (let i = 0; i < events.length; i++) {
    if (consumed.has(i)) continue;
    let matched: ActionMatch | null = null;
    for (const rule of RULES) {
      matched = rule.match(events, i);
      if (matched) break;
    }
    if (matched) {
      for (const idx of matched.consumedIndices) consumed.add(idx);
      items.push({
        kind: 'action',
        card: matched.card,
        sortMs: events[i]!.timestampMs,
        eventIds: matched.consumedIndices.map((idx) => events[idx]!.id),
      });
    } else {
      items.push({ kind: 'raw', event: events[i]!, sortMs: events[i]!.timestampMs });
    }
  }
  items.sort((a, b) => a.sortMs - b.sortMs);
  return items;
}
