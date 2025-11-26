import "fast-text-encoding";
import { encode as base64Encode } from "base64-arraybuffer";

export const assertNever = (n: never): never => {
  console.log(n);
  throw new Error("assertNever: " + JSON.stringify(n));
}

export const escapeRegexGerman = (s: string): string => {
  return s.replace(/[^a-zA-Z0-9äöüÄÖÜß]/g, '.');
}

export const escapeRegex = (s: string): string => {
  return s.replace(/[\P{Letter}&&\P{Number}]/gv, '.');
}

/* aligns two collections by a key function. Uses undefined when a key can only be found in one of the
 * given collections. For example:
 * const ages = [{name: "Felix", age: 20}, { name: "Christoph", age: 15 }]
 * const postals = [{name: "Felix", postal: "93842"}, {name: "Nikolai", postal: "33337"}]
 * const result = align(ages, postals, a => a.name, p => p.name, (a: string, b: string) => a === b ? 0 : a < b ? -1 : 1 )
 * result = [
 *   [ {name: "Christoph", age: 15 }, undefined                         ],
 *   [ {name: "Felix",     age: 20 }, {name: "Felix",   postal: "93842"}],
 *   [ undefined                    , {name: "Nikolai", postal: "33337"}],
 * ]
*/
export const align = <A, B, C>(
  as: A[],
  bs: B[],
  fa: (a: A) => C,
  fb: (b: B) => C,
  c: (c1: C, c2: C) => number): ([A, B] | [undefined, B] | [A, undefined])[] => {

  const sa = as.slice().sort((a1, a2) => c(fa(a1), fa(a2)));
  const sb = bs.slice().sort((b1, b2) => c(fb(b1), fb(b2)));

  let ai = 0;
  let bi = 0;
  const out: ([A, B] | [undefined, B] | [A, undefined])[] = [];

  const takeLeft = () => { out.push([sa[ai], undefined]); ai++; }
  const takeRight = () => { out.push([undefined, sb[bi]]); bi++; }
  const takeBoth = () => { out.push([sa[ai], sb[bi]]); ai++; bi++; }

  while (ai < sa.length || bi < sb.length) {
    if (ai === sa.length) {
      takeRight();
    } else if (bi === sb.length) {
      takeLeft();
    } else {
      const cmp = c(fa(sa[ai]), fb(sb[bi]));

      if (cmp === 0) {
        takeBoth();
      } else if (cmp < 0) {
        takeLeft();
      } else {
        takeRight();
      }
    }
  }

  return out;
}

export type SimpleObject = { [key: string]: any };

type SwitchOnKindMap<I extends SimpleObject, O> = {
  [key in I["kind"]]: (i: I extends { kind: key } ? I : never) => O
}

export const switchOnKind = <I extends SimpleObject, O>(i: I, m: SwitchOnKindMap<I, O>): O => (m as any)[i.kind](i);

export const classNames = (classes: { [key: string]: boolean }): string =>
  Object.keys(classes).map(className => classes[className] ? className : "").join(" ")

export const chainComparator = <X>(f1: (a: X, b: X) => number, f2: (a: X, b: X) => number) => (a: X, b: X): number => {
  const ret = f1(a, b);
  if (ret === 0) {
    return f2(a, b)
  } else {
    return ret;
  }
}

export const alreadySorted = function <A>(arr: A[], sorter: (aa: A, ab: A) => number): boolean {
  for (let i = 1; i < arr.length; i++) {
    if (sorter(arr[i - 1], arr[i]) > 0) {
      return false;
    }
  }
  return true;
}

export const uriToClasName = (uriString: string): string => {
  return decodeURI(uriString).replace(/.*\//, "")
}

export const mmToPixel = (mm: number): number => {
  return Math.floor(mm * 3.7795275591);
}

export const pixelTomm = (pixel: number): number => {
  return Math.ceil(pixel * 0.2645833333);
}

const utf8Encoder = new TextEncoder();

export const encodeStringAsUTF8Base64 = (input: string): string => {
  return base64Encode(utf8Encoder.encode(input));
}
