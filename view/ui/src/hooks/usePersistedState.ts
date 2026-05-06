import { Dispatch, SetStateAction, useCallback, useState } from 'react';

const KEY_PREFIX = 'monodi.';

function readFromStorage<T>(key: string, defaultValue: T): T {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + key);
    if (raw === null) return defaultValue;
    return JSON.parse(raw) as T;
  } catch {
    return defaultValue;
  }
}

function writeToStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(KEY_PREFIX + key, JSON.stringify(value));
  } catch {
    // localStorage unavailable or full — silently degrade
  }
}

/**
 * Drop-in replacement for `useState` that persists the value in `localStorage`.
 * The key is automatically prefixed with `"monodi."`.
 */
export function usePersistedState<T>(key: string, defaultValue: T): [T, Dispatch<SetStateAction<T>>] {
  const [state, setStateRaw] = useState<T>(() => readFromStorage(key, defaultValue));

  const setState: Dispatch<SetStateAction<T>> = useCallback(
    (action) => {
      setStateRaw(prev => {
        const next = typeof action === 'function'
          ? (action as (prev: T) => T)(prev)
          : action;
        writeToStorage(key, next);
        return next;
      });
    },
    [key],
  );

  return [state, setState];
}
