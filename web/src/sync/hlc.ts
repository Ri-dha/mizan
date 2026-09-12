/**
 * Hybrid logical clock stamps: `<13-digit wall ms>:<4-hex counter>:<node id>`, ordered by
 * plain string comparison so the server and every device agree without parsing.
 */
export interface HlcState {
  wall: number
  counter: number
  node: string
}

const COUNTER_LIMIT = 0xffff

export function formatHlc(state: HlcState): string {
  return `${String(state.wall).padStart(13, "0")}:${state.counter.toString(16).padStart(4, "0")}:${state.node}`
}

export function parseHlc(stamp: string): HlcState {
  const [wall, counter, node] = stamp.split(":")
  return { wall: Number(wall), counter: parseInt(counter, 16), node }
}

export function isAfter(candidate: string, reference: string | undefined): boolean {
  return reference === undefined || candidate > reference
}

/** Advances the clock for a local write: never behind wall time, never behind what it has seen. */
export function tick(state: HlcState, now = Date.now()): HlcState {
  if (now > state.wall) {
    return { ...state, wall: now, counter: 0 }
  }
  if (state.counter >= COUNTER_LIMIT) {
    return { ...state, wall: state.wall + 1, counter: 0 }
  }
  return { ...state, counter: state.counter + 1 }
}

/** Folds in a stamp received from elsewhere so later local stamps sort after it. */
export function observe(state: HlcState, received: string, now = Date.now()): HlcState {
  const remote = parseHlc(received)
  const wall = Math.max(state.wall, remote.wall, now)
  if (wall === state.wall && wall === remote.wall) {
    return { ...state, counter: Math.max(state.counter, remote.counter) + 1 }
  }
  if (wall === state.wall) {
    return { ...state, counter: state.counter + 1 }
  }
  if (wall === remote.wall) {
    return { ...state, wall, counter: remote.counter + 1 }
  }
  return { ...state, wall, counter: 0 }
}

export function newNodeId(): string {
  return crypto.randomUUID().slice(0, 8)
}
