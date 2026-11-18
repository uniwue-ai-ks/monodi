import { Interval } from "./attribute";

// A search value for an interval attribute is either a single value or a range "lower-upper",
// where either side may be empty to leave the range open. Since "-" separates the bounds,
// a "-" inside a bound is escaped as %2D, and a "%" as %25.

export const escapeIntervalBound = (s: string): string =>
  s.replaceAll("%", "%25").replaceAll("-", "%2D");

export const unescapeIntervalBound = (s: string): string =>
  s.replaceAll("%2D", "-").replaceAll("%25", "%");

// A single value v is returned as the range [v, v].
export const parseIntervalSearchValue = (value: string): [string, string] => {
  const separator = value.indexOf("-");
  if (separator === -1) {
    const single = unescapeIntervalBound(value);
    return [single, single];
  }
  return [unescapeIntervalBound(value.substring(0, separator)), unescapeIntervalBound(value.substring(separator + 1))];
}

export const formatIntervalSearchValue = (lower: string, upper: string): string =>
  lower === upper ? escapeIntervalBound(lower) : escapeIntervalBound(lower) + "-" + escapeIntervalBound(upper);

export const intervalToString = (interval: Interval): string =>
  `${interval.lowerBound} – ${interval.upperBound}`;
