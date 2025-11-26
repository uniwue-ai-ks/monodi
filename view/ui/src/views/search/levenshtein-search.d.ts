declare module 'levenshtein-search' {
  export function fuzzySearch(
    needle: string, haystack: string, maxDist: number
  ): Generator<{ dist: number; start: number; end: number; }>;
}
