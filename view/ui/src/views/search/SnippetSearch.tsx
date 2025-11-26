import { ReactElement } from "react";
import { SearchPreviewConfig } from "../../model/attribute"
import { fuzzySearch } from "levenshtein-search";
import { assertNever } from "../../util";

export type Props = {
  data: string;
  query: string;
  config: SearchPreviewConfig;
}

export const SnippetSearch = ({ data, query, config }: Props): ReactElement => {
  const match = getStartEnd(data, query, Math.ceil(query.length * config.maxDistanceRatio));
  if (match === null) {
    return <div></div>;
  } else {
    const prefix = takeOccurences(data, match.start - 1, "backward", char => char === " ", config.contextCount);
    const suffix = takeOccurences(data, match.end, "forward", char => char === " ", config.contextCount);
    if (config.contextKind === "lines") {
      const middleLinePrefix = prefix.length > 0 ? prefix[prefix.length - 1] : "";
      const middleLineSuffix = suffix.length > 0 ? suffix[0] : "";
      const previousLines = prefix.length > 1 ? prefix.slice(0, prefix.length - 1) : [];
      const nextLines = suffix.length > 1 ? suffix.slice(1) : [];
      return (
        <div>
          {previousLines.map((token, i) => <div key={i}>{token}</div>)}
          <div>{middleLinePrefix} <mark>{data.slice(match.start, match.end)}</mark> {middleLineSuffix}</div>
          {nextLines.map((token, i) => <div key={i}>{token}</div>)}
        </div>
      );
    } else if (config.contextKind === "words") {
      return (
        <div>
          <span>{prefix.join(" ")}</span>
          <mark> {data.slice(match.start, match.end)} </mark>
          <span>{suffix.join(" ")}</span>
        </div>
      );
    } else {
      return assertNever(config.contextKind);
    }
  }
}

const getStartEnd = (
  data: string,
  query: string,
  maxDistance: number
): { start: number, end: number } | null => {
  const bestMatch = [-1, -1, -1]; // [dist, start, end]
  [...fuzzySearch(query.toLowerCase(), data.toLowerCase(), maxDistance)].forEach(match => {
    if (bestMatch[0] === -1 || bestMatch[0] > match.dist) {
      bestMatch[0] = match.dist;
      bestMatch[1] = match.start;
      bestMatch[2] = match.end;
    }
  });
  if (bestMatch[0] === -1) {
    return null;
  } else {
    return { start: bestMatch[1], end: bestMatch[2] };
  }
}

const takeOccurences = (
  data: string,
  start: number,
  direction: "forward" | "backward",
  isBoundary: (char: string) => boolean,
  maxOccurences: number
): string[] => {
  const iDelta = direction === "forward" ? 1 : -1;
  const outOfBounds = (i: number) => i < 0 || i >= data.length;

  const occurences: string[] = [];
  let i = start;
  while (true) {
    if (occurences.length >= maxOccurences) {
      break;
    } else if (outOfBounds(i)) {
      break;
    }

    while (isBoundary(data[i]) && !outOfBounds(i)) {
      i += iDelta;
    }

    const boundA = i;
    while (!isBoundary(data[i]) && !outOfBounds(i)) {
      i += iDelta;
    }
    const boundB = i;

    if (boundA !== boundB) {
      if (direction === "forward") {
        occurences.push(data.slice(boundA, boundB + 1));
      } else {
        occurences.unshift(data.slice(boundB, boundA + 1));
      }
    }
  }

  return occurences;
}
