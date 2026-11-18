import { QueryParameter } from "./hooks/useQuery";
import {
  Attribute,
  AttributeWithData, CategoryEntryGuard, CollectionImageHtml, DocumentPart, DocumentPosition, HtmlImageCollection,
  Interval,
  Kind,
  MetadataEntry,
  ReferenceAttribute,
  ShortenPosition,
  SHORTEN_POSITION_NONE
} from "./model/attribute";
import { Entity } from "./model/entity";
import { EntityDescription } from "./model/entity-description";
import { SubEntity } from "./model/subentity";
import { LangOverrides } from "./translation/Translation";
import { assertNever, switchOnKind, escapeRegex } from "./util";
import { formatIntervalSearchValue, parseIntervalSearchValue } from "./model/interval";

export async function getContentsByLang(language: string): Promise<Contents> {
  const result = await query(`
    SELECT ?entity ?content ?navPositionLeft ?navPositionRight ?navPositionFooter ?title ?route WHERE {
      {
        SELECT DISTINCT ?entity
        WHERE {
          { ?entity :hasContent [] }
          UNION
          { ?entity :hasTitle [] }
          UNION
          { ?entity :hasRoute [] }
          UNION
          { ?entity :position [] }
        }
      }
      OPTIONAL {
        ?entity :hasContent ?c.
        FILTER (lang(?c) = '${language}')
        bind(STR(?c) as ?content).
      }
      OPTIONAL {
        ?entity  :hasTitle  ?t bind(STR(?t) as ?title).
        FILTER (lang(?t) = '${language}')
      }
      OPTIONAL { ?entity  :hasRoute  ?route . }
      OPTIONAL { ?entity  :position [ :right ?navPositionRight ] . }
      OPTIONAL { ?entity  :position [ :left ?navPositionLeft ] . }
      OPTIONAL { ?entity  :position [ :footer ?navPositionFooter ] . }
    }
  `)

  const res: Contents = { contents: [] };
  result.results.bindings.forEach((c: any) => {
    if (!c.entity?.value) return;
    let navLink: ContentNavLink | undefined
    if (c.title?.value && c.route?.value) {
      navLink = {
        position: {
          left: c.navPositionLeft ? c.navPositionLeft.value : undefined,
          right: c.navPositionRight ? c.navPositionRight.value : undefined,
          footer: c.navPositionFooter ? c.navPositionFooter.value : undefined,
        },
        title: c.title.value,
        route: c.route.value,
      }
    }
    res.contents.push({
      uri: c.entity.value,
      content: c.content?.value,
      navLink: navLink
    })
  })
  return res;
}

export async function getShortUrlEntitys(language: string): Promise<ShortUrlEntitys[]> {
  const result = await query(`
    SELECT ?entity ?name ?tag ?url WHERE {
      ?entity a rdfs:Class.
      ?entity rdfs:label ?label.
      ?entity :shortUrlTag ?tag.
      BIND(STR(?label) as ?name) .
      FILTER (lang(?label) = '${language}')
    }
  `);
  return result.results.bindings.map((b: any) => ({
    uri: b.entity.value,
    name: b.name.value,
    tag: b.tag.value,
  }))
}

export async function getLanguages(): Promise<string[]> {
  const result = await query(`
    SELECT DISTINCT (LANG(?label) AS ?language)
    WHERE {
        :tabTitle :hasContent ?label .
          FILTER(LANG(?label) != "")
    }
  `);
  return result.results.bindings.map((b: any) => b.language.value)
}

export async function getLanguageOverrides(): Promise<LangOverrides> {
  const result = await query(`
    SELECT ?key ?value ?lang WHERE {
      :uiText :hasOverride [ :key ?key ; :value ?value ].
      BIND (LANG(?value) as ?lang).
      }
  `);

  const overrides: LangOverrides = {};
  for (const b of result.results.bindings) {
    overrides[b.lang.value] = (overrides[b.lang.value] || {})[b.key.value] = b.value.value;
  }
  return overrides;
}

export async function getEntityNames(language: string): Promise<EntityName[]> {
  const result = await query(`
  SELECT ?entity ?name ?alternativeLink ?orderBy WHERE {
    ?entity a rdfs:Class.
    ?entity rdfs:label ?literal.
    OPTIONAL { ?entity :alternativeLink ?alternativeLink .  }
    OPTIONAL { ?entity :orderBy ?orderBy .  }
    BIND(STR(?literal) as ?name) .
    FILTER (lang(?literal) = '${language}')
  }
  `);

  return result.results.bindings.map((b: any) => ({
    uri: b.entity.value,
    name: b.name.value,
    alternativeLink: b.alternativeLink ? b.alternativeLink.value : undefined,
    orderBy: b.orderBy ? b.orderBy.value : undefined,
  }));
}

// --- Navigation menu (:MenuEntry) and configured searches (:Search) ---------

export type MenuTarget =
  | { kind: "search"; uri: string }
  | { kind: "href"; url: string };

export interface MenuEntry {
  uri: string;
  label: string;
  target?: MenuTarget;
  topLevelOrder?: number;
  children: MenuEntry[];
}

// Fetch every :MenuEntry plus its child/order edges in ONE query, then assemble
// the hierarchy in code: roots are the entries carrying :topLevelOrder (ordered by
// it), children sorted by their placement :order. A visited-set guards against
// cycles from malformed TTL;
export async function getMenuTree(language: string): Promise<MenuEntry[]> {
  const result = await query(`
    SELECT ?entry ?label ?topOrder ?search ?href ?childEntry ?childOrder WHERE {
      ?entry a :MenuEntry .
      OPTIONAL { ?entry rdfs:label ?l . FILTER(lang(?l) = '${language}') BIND(STR(?l) AS ?label) }
      OPTIONAL { ?entry :topLevelOrder ?topOrder }
      OPTIONAL { ?entry :search ?search }
      OPTIONAL { ?entry :href ?href }
      OPTIONAL { ?entry :child [ :entry ?childEntry ; :order ?childOrder ] }
    }
  `);

  interface Raw { uri: string; label: string; target?: MenuTarget; topLevelOrder?: number; edges: { child: string; order: number }[] }
  const raw = new Map<string, Raw>();
  const getRaw = (uri: string): Raw => {
    let r = raw.get(uri);
    if (!r) { r = { uri, label: uri, edges: [] }; raw.set(uri, r); }
    return r;
  };

  for (const b of result.results.bindings as any[]) {
    const r = getRaw(b.entry.value);
    if (b.label) r.label = b.label.value;
    if (b.topOrder) r.topLevelOrder = parseInt(b.topOrder.value, 10);
    if (b.search) r.target = { kind: "search", uri: b.search.value };
    else if (b.href) r.target = { kind: "href", url: b.href.value };
    if (b.childEntry && b.childOrder) {
      const child = b.childEntry.value;
      const order = parseInt(b.childOrder.value, 10);
      if (!r.edges.some(e => e.child === child && e.order === order)) r.edges.push({ child, order });
    }
  }

  const build = (uri: string, visited: Set<string>): MenuEntry | null => {
    if (visited.has(uri)) return null; // cycle guard
    const r = raw.get(uri);
    if (!r) return null;
    const nextVisited = new Set(visited).add(uri);
    const children = r.edges
      .slice()
      .sort((a, b) => a.order - b.order)
      .map(e => build(e.child, nextVisited))
      .filter((c): c is MenuEntry => c !== null);
    return { uri: r.uri, label: r.label, target: r.target, topLevelOrder: r.topLevelOrder, children };
  };

  return [...raw.values()]
    .filter(r => r.topLevelOrder !== undefined)
    .sort((a, b) => a.topLevelOrder! - b.topLevelOrder!)
    .map(r => build(r.uri, new Set<string>()))
    .filter((e): e is MenuEntry => e !== null);
}

// :Search nodes are routable like classes (resolved by type in Search.tsx). Only
// uri is needed for route generation; name falls back to the uri.
export async function getSearchNodes(language: string): Promise<EntityName[]> {
  const result = await query(`
    SELECT ?entity ?name WHERE {
      ?entity a :Search .
      OPTIONAL { ?entity rdfs:label ?literal . FILTER(lang(?literal) = '${language}') BIND(STR(?literal) AS ?name) }
    }
  `);
  return result.results.bindings.map((b: any) => ({
    uri: b.entity.value,
    name: b.name ? b.name.value : b.entity.value,
  }));
}

export interface FixedFilter {
  attribute: string;
  value: string;
}

// A configuration for a search page
export interface SearchConfig {
  introText?: string;
  landingHtml?: string;
  landingButtonText?: string;
  // Applied as hidden, non-overridable filters (the field is removed from the form).
  fixedFilters: FixedFilter[];
  // Seeded into the search form as editable defaults on first arrival; the user can
  // change or remove them. An empty value just pre-opens the filter row.
  prefilledFilters: FixedFilter[];
}

// An entity to search over, plus optional search config. `entity` is always the base
// class; `search` is present only when the routed uri was a :Search node.
export interface SearchView {
  entity: EntityDescription;
  search?: SearchConfig;
}

export async function getSearchView(uri: string, language: string): Promise<SearchView> {
  const type = await getEntityType(uri);
  if (type !== "http://olyro.de/mondiview/Search") {
    return { entity: await getEntityDescription(uri, language) };
  }

  const result = await query(`
    SELECT ?baseClass ?introText ?landingHtml ?landingButtonText ?fAttr ?fValue ?pAttr ?pValue WHERE {
      <${uri}> :baseClass ?baseClass .
      OPTIONAL { <${uri}> :introText ?it . FILTER(lang(?it) = '${language}') BIND(STR(?it) AS ?introText) }
      OPTIONAL { <${uri}> :landingHtml ?lh . FILTER(lang(?lh) = '${language}') BIND(STR(?lh) AS ?landingHtml) }
      OPTIONAL { <${uri}> :landingButtonText ?lb . FILTER(lang(?lb) = '${language}') BIND(STR(?lb) AS ?landingButtonText) }
      OPTIONAL { <${uri}> :fixedFilter [ :attribute ?fAttr ; :value ?fValue ] }
      OPTIONAL { <${uri}> :prefilledFilter [ :attribute ?pAttr ; :value ?pValue ] }
    }
  `);

  const bindings = result.results.bindings as any[];
  const first = bindings[0];
  if (!first || !first.baseClass) {
    throw new Error(`:Search ${uri} has no :baseClass`);
  }
  const entity = await getEntityDescription(first.baseClass.value, language);

  const collectFilters = (attrKey: string, valueKey: string): FixedFilter[] => bindings
    .filter(b => b[attrKey] && b[valueKey])
    .map(b => ({ attribute: b[attrKey].value, value: b[valueKey].value as string }))
    .filter((f, i, arr) => arr.findIndex(g => g.attribute === f.attribute && g.value === f.value) === i);

  return {
    entity,
    search: {
      introText: valueOrUndefined(first.introText),
      landingHtml: valueOrUndefined(first.landingHtml),
      landingButtonText: valueOrUndefined(first.landingButtonText),
      fixedFilters: collectFilters("fAttr", "fValue"),
      prefilledFilters: collectFilters("pAttr", "pValue"),
    },
  };
}

// Entity descriptions only change with a new export, so they are cached for the
// lifetime of the page. The promise is cached, so concurrent callers (e.g. the
// prefetch in App and a user opening a search) share one request.
const entityDescriptionCache = new Map<string, Promise<EntityDescription>>();

export function getEntityDescription(uri: string, language: string): Promise<EntityDescription> {
  const key = `${language}|${uri}`;
  let description = entityDescriptionCache.get(key);
  if (!description) {
    description = getByQueryEntityDescription(uri, language);
    description.catch(() => entityDescriptionCache.delete(key));
    entityDescriptionCache.set(key, description);
  }
  return description;
}

// Entity descriptions used to be written to localStorage under their uri (plus
// `${uri}_version`). Those entries are several MB and would crowd out the
// storage we still use, so drop them.
export function removeLegacyEntityDescriptionCache(): void {
  try {
    const ls = window.localStorage;
    const legacyKeys: string[] = [];
    for (let i = 0; i < ls.length; i++) {
      const key = ls.key(i);
      if (key && (key.startsWith("http://") || key.startsWith("https://"))) legacyKeys.push(key);
    }
    legacyKeys.forEach(key => ls.removeItem(key));
  } catch {
    // localStorage unavailable, nothing to clean up
  }
}

const valueOrUndefined = (val: BindingValue) => val ? val.value : undefined

export async function getEntityType(uri: string): Promise<string | undefined> {
  const result = await query(`
    SELECT ?type WHERE {
      <${uri}> a ?type .
    }
  `);
  return result.results.bindings?.[0]?.type?.value;
}

async function getEntityAttributes(uri: string, language: string): Promise<Attribute[]> {
  const selectionVars = `
      ?property ?label ?range
      ?headerOrder ?searchOrder
      ?documentPositionMain ?documentPositionPriority
      ?documentPositionSticky ?documentPositionHeader
      ?documentPositionRight ?documentPositionPopup
      ?documentPositionSynopsisId ?documentPositionSynopsis
      ?documentPositionDownload ?documentPositionNavigation
      ?documentPositionOwnPage
      ?referenceField ?alternativeSortAttribute ?searchSpan
      ?searchPreviewLabel ?searchPreviewContextKind ?searchPreviewContextCount ?searchPreviewMaxDistanceRatio
      ?referenceSearch ?pdfTextAttribute ?downloadIcon ?initialSearch
      ?normalizationAttribute ?normalizationNormalizer
      ?implicitQueryValue ?uiQueryPrefillValue ?hideInputFromSearch ?allowMultipleSearch
  `
  const attributeQueryString = `
  SELECT
      ${selectionVars}
      (GROUP_CONCAT(?shortenPosition; separator=";") AS ?shortenPositions)
  WHERE {
    VALUES ?language {'${language}'}
    ?property  rdfs:domain <${uri}> .
    ?property  rdfs:range ?range .
    ?property  rdfs:label ?literal .
    OPTIONAL { ?property  :headerOrder  ?headerOrder . }
    OPTIONAL { ?property  :searchOrder  ?searchOrder . }
    OPTIONAL { ?property  :searchSpan   ?searchSpan . }
    OPTIONAL { ?property  :documentPosition [ :main ?documentPositionMain ] . }
    OPTIONAL { ?property  :documentPosition [ :priority ?documentPositionPriority ] . }
    OPTIONAL { ?property  :documentPosition [ :sticky ?documentPositionSticky ] . }
    OPTIONAL { ?property  :documentPosition [ :header ?documentPositionHeader ] . }
    OPTIONAL { ?property  :documentPosition [ :right ?documentPositionRight ] . }
    OPTIONAL { ?property  :documentPosition [ :popup ?documentPositionPopup ] . }
    OPTIONAL { ?property  :documentPosition [ :synopsisId ?documentPositionSynopsisId ] . }
    OPTIONAL { ?property  :documentPosition [ :synopsis ?documentPositionSynopsis ] . }
    OPTIONAL { ?property  :documentPosition [ :download ?documentPositionDownload ] . }
    OPTIONAL { ?property  :documentPosition [ :docNavigation ?documentPositionNavigation ] . }
    OPTIONAL { ?property  :documentPosition [ :ownPage ?documentPositionOwnPage ] . }
    OPTIONAL { ?property  :shorten ?shortenPosition . }
    OPTIONAL {
      ?property  :normalization ?normalization .
      ?normalization :normalizer ?normalizationNormalizer .
      OPTIONAL { ?normalization :attribute ?normalizationAttribute . }
    }
    OPTIONAL { ?property  :referenceField ?referenceField . }
    OPTIONAL { ?property  :alternativeSortAttribute ?alternativeSortAttribute . }
    OPTIONAL { ?property  :referenceSearch ?referenceSearch . }
    OPTIONAL { ?property  :pdfTextAttribute ?pdfTextAttribute . }
    OPTIONAL { ?property  :icon ?downloadIcon . }
    OPTIONAL { ?property  :initialSearch ?initialSearch . }
    OPTIONAL { ?property  :implicitQueryValue ?implicitQueryValue . }
    OPTIONAL { ?property  :uiQueryPrefillValue ?uiQueryPrefillValue . }
    OPTIONAL { ?property  :hideInputFromSearch ?hideInputFromSearch . }
    OPTIONAL { ?property  :allowMultipleSearch ?allowMultipleSearch . }
    OPTIONAL {
      ?property  :searchPreview  [
        :label ?searchPreviewLangs ;
        :config [
            :contextKind  ?searchPreviewContextKind;
            :contextCount ?searchPreviewContextCount;
            :maxDistanceRatio ?searchPreviewMaxDistanceRatio
        ]
      ] .
      BIND(STR(?searchPreviewLangs) as ?searchPreviewLabel) FILTER (lang(?searchPreviewLangs) = ?language)
    }
    BIND(STR(?literal) as ?label) FILTER (lang(?literal) = ?language)
  }
  GROUP BY ${selectionVars}
  `

  const getDocumentPosition = (b: BindingRow): DocumentPosition => ({
    header: valueOrUndefined(b.documentPositionHeader),
    main: valueOrUndefined(b.documentPositionMain),
    priority: valueOrUndefined(b.documentPositionPriority),
    sticky: valueOrUndefined(b.documentPositionSticky),
    right: valueOrUndefined(b.documentPositionRight),
    popup: valueOrUndefined(b.documentPositionPopup),
    synopsisId: valueOrUndefined(b.documentPositionSynopsisId),
    synopsis: valueOrUndefined(b.documentPositionSynopsis),
    download: valueOrUndefined(b.documentPositionDownload),
    navigation: valueOrUndefined(b.documentPositionNavigation),
    ownPage: valueOrUndefined(b.documentPositionOwnPage),
  });

  const getShortenPosition = (b: BindingRow): ShortenPosition => {
    if (!b.shortenPositions) return SHORTEN_POSITION_NONE;
    const parts: string[] = b.shortenPositions.value.split(";");
    return {
      header: parts.includes("http://olyro.de/mondiview/header"),
      right: parts.includes("http://olyro.de/mondiview/right"),
      main: parts.includes("http://olyro.de/mondiview/main"),
      priority: parts.includes("http://olyro.de/mondiview/priority"),
      sticky: parts.includes("http://olyro.de/mondiview/sticky"),
      popup: parts.includes("http://olyro.de/mondiview/popup"),
      synopsisId: parts.includes("http://olyro.de/mondiview/synopsisId"),
      synopsis: parts.includes("http://olyro.de/mondiview/synopsis"),
      download: parts.includes("http://olyro.de/mondiview/download"),
      navigation: parts.includes("http://olyro.de/mondiview/navigation"),
      results: parts.includes("http://olyro.de/mondiview/results"),
    }
  }

  const attributeResults = await query(attributeQueryString);
  const attributes: Attribute[] = attributeResults.results.bindings.map((b: BindingRow) => {
    const a: Attribute = {
      uri: b.property.value,
      kind: b.range.value,
      label: b.label.value,
      headerOrder: b.headerOrder ? +b.headerOrder.value : undefined,
      searchOrder: b.searchOrder ? +b.searchOrder.value : undefined,
      searchSpan: b.searchSpan?.value.toLowerCase() === "true" ? true : false,
      referenceField: valueOrUndefined(b.referenceField),
      documentPosition: getDocumentPosition(b),
      alternativeSortAttribute: valueOrUndefined(b.alternativeSortAttribute),
      referenceSearch: valueOrUndefined(b.referenceSearch),
      pdfTextAttribute: valueOrUndefined(b.pdfTextAttribute),
      shortenPosition: getShortenPosition(b),
      downloadIcon: valueOrUndefined(b.downloadIcon),
      initialSearch: !!b.initialSearch,
      implicitQueryValue: valueOrUndefined(b.implicitQueryValue),
      uiQueryPrefillValue: valueOrUndefined(b.uiQueryPrefillValue),
      hideInputFromSearch: b.hideInputFromSearch?.value.toLowerCase() === "true" ? true : false,
      allowMultipleSearch: b.allowMultipleSearch
        ? b.allowMultipleSearch.value.toLowerCase() === "true"
        : b.range.value !== "http://olyro.de/mondiview/boolean",
    } as Attribute

    if (
      b.searchPreviewLabel &&
      b.searchPreviewContextKind &&
      b.searchPreviewContextCount &&
      b.searchPreviewMaxDistanceRatio
    ) {
      a.searchPreview = {
        label: b.searchPreviewLabel.value as string,
        config: {
          contextCount: +b.searchPreviewContextCount.value,
          contextKind: b.searchPreviewContextKind.value as "words" | "lines",
          maxDistanceRatio: +b.searchPreviewMaxDistanceRatio.value,
        }
      }
    }

    if ((a.kind === 'http://olyro.de/mondiview/string' || a.kind === 'http://olyro.de/mondiview/interval') && b.normalizationNormalizer) {
      a.normalization = {
        // without an explicit target attribute, the normalized value queries the attribute itself
        attribute: b.normalizationAttribute?.value ?? a.uri,
        normalizer: b.normalizationNormalizer.value
      }
    }

    return a;
  });

  const withCatValues = await getCategoryValuesForAttributes(attributes, language);
  return await getCategoryMappingsForAttributes(withCatValues, language);
}

async function getEntitySubEntities(uri: string, language: string): Promise<SubEntity[]> {
  const subEntityQueryString = `
  SELECT ?subEntity ?referenceLabel ?referenceAttribute WHERE {
    ?subEntity :referenceClass <${uri}>.
    ?subEntity :referenceLabel ?literal.
    ?subEntity :referenceAttribute ?referenceAttribute.
    BIND(STR(?literal) as ?referenceLabel) .
    FILTER (lang(?literal) = '${language}')
  }
  `
  const getFullSubEntity = (b: any): Promise<SubEntity> => {
    return getEntityDescription(b.subEntity.value, language)
      .then(subEntityDesc => ({
        uri: b.subEntity.value,
        label: b.referenceLabel.value,
        referenceAttribute: b.referenceAttribute.value,
        description: subEntityDesc,
      }))
  }

  const subEntityResults = await query(subEntityQueryString);
  return Promise.all(subEntityResults.results.bindings.map(getFullSubEntity));
}

function optLangOrUntagged(internalField: string, bindField: string, language: string): string {
  return `BIND(STR(${internalField}) as ${bindField}) FILTER(!isLiteral(${internalField}) || LANG(${internalField}) = "" || LANGMATCHES(LANG(${internalField}), ${language}))`
}

async function getByQueryEntityDescription(uri: string, language: string): Promise<EntityDescription> {
  const attributes = await getEntityAttributes(uri, language);
  const subEntities = await getEntitySubEntities(uri, language);
  const conditionalOpenText = await getSwitch(uri, "http://olyro.de/mondiview/conditionalOpenText", language);

  const nameQueryString = `
  select ?label ?openPopupText ?popupTitle ?openIIIFText ?comparableInSynopsis ?referenceAttribute ?referenceClass ?alternativeLink ?disablePrintColumn ?shortUrlTag WHERE {
      VALUES ?language {'${language}'}
          <${uri}>  rdfs:label ?l . BIND(STR(?l) as ?label) .
          OPTIONAL { <${uri}> :openPopupText ?opt . ${optLangOrUntagged("?opt", "?openPopupText", "?language")} }
          OPTIONAL { <${uri}> :popupTitle ?pt . ${optLangOrUntagged("?pt", "?popupTitle", "?language")} }
          OPTIONAL { <${uri}> :openIIIFText ?oit . ${optLangOrUntagged("?oit", "?openIIIFText", "?language")} }
          OPTIONAL { <${uri}> :comparableInSynopsis ?comparableInSynopsis .  }
          OPTIONAL { <${uri}> :referenceAttribute ?referenceAttribute .  }
          OPTIONAL { <${uri}> :referenceClass ?referenceClass .  }
          OPTIONAL { <${uri}> :alternativeLink ?alternativeLink .  }
          OPTIONAL { <${uri}> :disablePrintColumn ?disablePrintColumn .  }
          OPTIONAL { <${uri}> :shortUrlTag ?shortUrlTag . }
      FILTER (lang(?l) = ?language)
    }
  `
  const nameResults = await query(nameQueryString);
  const nameResult = nameResults.results.bindings[0]

  return {
    uri: uri,
    name: nameResult.label.value,
    openPopupText: valueOrUndefined(nameResult.openPopupText),
    popupTitle: valueOrUndefined(nameResult.popupTitle),
    openIIIFText: valueOrUndefined(nameResult.openIIIFText),
    referenceAttribute: valueOrUndefined(nameResult.referenceAttribute),
    referenceClass: valueOrUndefined(nameResult.referenceClass),
    comparableInSynopsis: !!nameResult.comparableInSynopsis,
    attributes,
    conditionalOpenText,
    alternativeLink: valueOrUndefined(nameResult.alternativeLink),
    disablePrintColumn: !!nameResult.disablePrintColumn,
    shortUrlTag: valueOrUndefined(nameResult.shortUrlTag),
    subEntities,
  };
}

const remapNormalizedAttributes = (entityDescription: EntityDescription, params: QueryParameter[]): QueryParameter[] => {
  return params.map(p => {
    const attribute = entityDescription.attributes.find(a => a.uri === p.uri)!;
    if (attribute.kind === "http://olyro.de/mondiview/string" && attribute.normalization) {
      // evaluated javascript comes from the readonly database.

      const normalizer = eval(attribute.normalization.normalizer);
      return {
        uri: attribute.normalization.attribute,
        value: normalizer(p.value)
      }
    } else if (attribute.kind === "http://olyro.de/mondiview/interval" && attribute.normalization) {
      // evaluated javascript comes from the readonly database.
      const normalizer = eval(attribute.normalization.normalizer);
      const normalize = (bound: string): string => bound === "" ? "" : normalizer(bound);
      const [lower, upper] = parseIntervalSearchValue(p.value);
      return {
        uri: attribute.normalization.attribute,
        value: formatIntervalSearchValue(normalize(lower), normalize(upper))
      }
    } else {
      return p;
    }
  });
}

export async function doQuery(
  entityDescription: EntityDescription,
  language: string,
  params: QueryParameter[],
  pagination: Pagination | null = null,
  intersections: string[] | null = null
): Promise<QueryResult> {
  const newEntityDescription: EntityDescription = JSON.parse(JSON.stringify(entityDescription))
  const newFilterdParams = remapNormalizedAttributes(entityDescription, params).filter(p => p.value.length > 0)
  const newFilterdAttributes = entityDescription.attributes.filter(a => {
    return newFilterdParams.findIndex(p => p.uri === a.uri) > -1
      || a.headerOrder !== undefined
      || params.some(p => p.uri === a.uri && p.value.length > 0 && a.kind === "http://olyro.de/mondiview/string" && a.normalization)
  })
  newEntityDescription.attributes = newFilterdAttributes;

  const result = await query(buildQuery(newEntityDescription, language, newFilterdParams, pagination, intersections));

  let count = 0;

  const rows = result.results.bindings.map(b => {
    const newObj: RowResult = { uri: "", data: {} };
    count = b.count.value;
    for (const k of Object.keys(b)) {
      if (k === "obj") {
        newObj.uri = b[k].value;
      } else if (k !== "count") {
        newObj.data[newFilterdAttributes[+k.substring(1)].uri] = b[k].value;
      }
    }

    return newObj;
  });

  return { count, rows };
}

export interface QueryResult {
  count: number;
  rows: RowResult[];
}

export interface Pagination {
  attribute: string;
  offset: number;
  limit: number;
  asc: boolean;
}

export interface RowResult {
  uri: string;
  data: { [key: string]: any };
}

// if we stumble upon an encoding error again:
// If you change the code regarding encoding/decoding of urls, check that
// a) you can still open a source with an umlaut within its name
// b) you can still open a document with an umlaut within its name
// c) you can still filter for something with umlatus (like quellensigle)
const encodeURI__readme_before_removing = (s: string): string => s

function buildQuery(description: EntityDescription, language: string, params: QueryParameter[], pagination: Pagination | null = null, intersections: string[] | null = null): string {

  const sparqlSelectVariables = description.attributes.map((_, i) => `?P${i}`);
  const uriToSparqlVariable: { [key: string]: string } = description.attributes.reduce((acc, a, i) => ({ ...acc, [a.uri]: sparqlSelectVariables[i] }), {});
  const sorterGraphPattern = !pagination ? "" : getSorterGraphPattern(description, pagination.attribute);
  const uriToGraphPattern: { [key: string]: string } = description.attributes.reduce((acc, a, i) => ({ ...acc, [a.uri]: makeAttributeGraphPattern(a, i, language) }), {});
  const refGraphPattern = getRefGraphPattern(description);
  const urisThatHaveAFilter = description.attributes.map(a => a.uri).filter(uri => params.some(p => p.uri === uri));

  const makeFilter = (ps: QueryParameter[], sparqlVariable: string, operator: "||" | "&&"): string => {
    return `FILTER ( ${ps.map(qp => makeFilterInner(qp, sparqlVariable)).join(` ${operator} `)}).`;
  }

  const makeFilterInner = (p: QueryParameter, sparqlVariable: string): string => {
    const attribute = description.attributes.find(a => a.uri === p.uri)!;
    if (attribute.kind === "http://olyro.de/mondiview/boolean") {
      return `(str(${sparqlVariable}) = "${p.value}")`;
    }
    if (attribute.kind === "http://olyro.de/mondiview/interval") {
      // matches every interval that overlaps the searched range, which for a single value means containing it
      const [lower, upper] = parseIntervalSearchValue(p.value);
      const bounds = intervalBoundVariables(sparqlVariable);
      const conditions = [
        lower !== "" ? `${bounds.upper} >= ${sparqlStringLiteral(lower)}` : null,
        upper !== "" ? `${bounds.lower} <= ${sparqlStringLiteral(upper)}` : null,
      ].filter((c): c is string => c !== null);
      return conditions.length > 0 ? `(${conditions.join(" && ")})` : "true";
    }
    const value: string = switchOnKind(attribute, {
      "http://olyro.de/mondiview/reference": () => p.value, // this is not completely correct,
      //since a filter on a referenced substringSearchText field would be missing the transformation.
      //So substringSearchText refernce filters are currently unsupported
      "http://olyro.de/mondiview/category": () => p.value,
      "http://olyro.de/mondiview/htmlContent": () => p.value,
      "http://olyro.de/mondiview/htmlImageCollection": () => { throw new Error("You can not search within a html image collection") },
      "http://olyro.de/mondiview/externalLink": () => { throw new Error("You can not search within an external link") },
      "http://olyro.de/mondiview/imageCollection": () => { throw new Error("You can not search within a image collection") },
      "http://olyro.de/mondiview/pdf": () => p.value,
      "http://olyro.de/mondiview/number": () => p.value,
      "http://olyro.de/mondiview/string": () => p.value,
      "http://olyro.de/mondiview/entity": () => p.value,
      "http://olyro.de/mondiview/copyText": () => p.value,
      "http://olyro.de/mondiview/substringSearchText": () => p.value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/\s+/g, "")
        .replace(/-/g, "")
    });
    if (attribute.searchSpan && attribute.kind === "http://olyro.de/mondiview/number") {
      const [min, max] = value.split("-").map(v => {
        const num = parseInt(v);
        return isNaN(num) ? undefined : num
      })
      if (min || max) {
        const lower = min ? `${sparqlVariable} >= ${min}` : '';
        const upper = max ? `${sparqlVariable} <= ${max}` : '';
        return `(${lower}${min && max ? ' && ' : ''}${upper})`;
      } else return "true";
    } else
      return `(regex(str(${sparqlVariable}), "${escapeRegex(value)}", "i"))`;
  }

  const queryParamsGrouped = params.reduce((acc, p) => { acc[p.uri] = [...(acc[p.uri] || []), p]; return acc; }, {} as { [key: string]: QueryParameter[] });
  const filters = description.attributes.filter(a => queryParamsGrouped[a.uri])
    .map(a => {
      const operator = intersections?.includes(a.uri) ? "&&" : "||";
      return makeFilter(queryParamsGrouped[a.uri], uriToSparqlVariable[a.uri], operator)
    }).join("\n");
  const order = !pagination ? "" : `ORDER BY ${pagination.asc ? "ASC" : "DESC"}(?sorter)`;
  const orderWithLimitAndOffset = !pagination ? "" : `${order} LIMIT ${pagination.limit} OFFSET ${pagination.offset}`;
  const filteredVariables = urisThatHaveAFilter.map(uri => uriToSparqlVariable[uri]);
  const unfilteredGraphPatterns = description.attributes
    .filter(a => !urisThatHaveAFilter.includes(a.uri))
    .map(a => uriToGraphPattern[a.uri]);

  return `
    SELECT * {
      {
        select ?obj ${sparqlSelectVariables.join(" ")} WHERE {
          {
            select ?obj ?ref ?sorter ${filteredVariables.join(" ")} WHERE {
              ?obj a <${description.uri}> .

              ${refGraphPattern}

              ${sorterGraphPattern}

              ${urisThatHaveAFilter.map(uri => uriToGraphPattern[uri]).join("\n")}

              ${filters}
            }

            ${orderWithLimitAndOffset}
          }

          ${unfilteredGraphPatterns.join("\n")}
        }

        ${order}

      }
      {
        select (count(*) as ?count) WHERE {
          ?obj a <${description.uri}> .

          ${refGraphPattern}

          ${urisThatHaveAFilter.map(uri => uriToGraphPattern[uri]).join("\n")}

          ${filters}
        }

      }
    }
  `;
}

const getRefGraphPattern = (e: EntityDescription): string => {
  if (e.referenceAttribute) return `?obj <${e.referenceAttribute}> ?ref`;
  else return "";
}

const getSorterGraphPattern = (description: EntityDescription, sortAttribute: string): string => {
  const attr = description.attributes.find(a => a.uri === sortAttribute);
  const defaultString = `OPTIONAL { ?obj <${sortAttribute}> ?sorter . }`;
  if (attr?.kind === 'http://olyro.de/mondiview/interval') return `OPTIONAL { ?obj <${sortAttribute}> [ <http://olyro.de/mondiview/lowerBound> ?sorter ] . }`;
  if (!attr || attr.kind !== 'http://olyro.de/mondiview/reference') return defaultString;
  else return `OPTIONAL { ?ref <${attr.referenceField}> ?sorter . }`;
}

const makeAttributeGraphPattern = (a: Attribute, i: number, language: string): string => {
  if (a.kind === 'http://olyro.de/mondiview/reference') {
    return `OPTIONAL { ?ref <${encodeURI__readme_before_removing(a.referenceField)}> ?intP${i} . ${optLangOrUntagged(`?intP${i}`, `?P${i}`, `'${language}'`)}}`;
  } else if (a.kind === 'http://olyro.de/mondiview/interval') {
    return intervalGraphPattern("?obj", a.uri, `?P${i}`);
  } else if (translatable(a.kind)) {
    return `OPTIONAL { ?obj <${encodeURI__readme_before_removing(a.uri)}> ?intP${i} . ${optLangOrUntagged(`?intP${i}`, `?P${i}`, `'${language}'`)}}`;
  } else {
    return `OPTIONAL { ?obj <${encodeURI__readme_before_removing(a.uri)}> ?P${i} . }`;
  }
}

// externalLink values are blank nodes carrying view:url and view:text. The
// per-entity query (buildEntityQuery) concatenates the two strings into a
// single ?P variable using this separator; parseAndFetch splits them back
// apart. The search query never sees externalLink — those attributes have
// no headerOrder and aren't filterable, so they never reach
// makeAttributeGraphPattern.
export const EXTERNAL_LINK_SEPARATOR = "\u0001";

// interval values are blank nodes carrying view:lowerBound and view:upperBound.
// Like externalLink, both are concatenated into the attribute's variable for
// displaying, see parseIntervalBinding. Filters compare against the separate
// bound variables instead.
const INTERVAL_SEPARATOR = "\u0001";

const intervalBoundVariables = (variable: string) => ({ lower: `${variable}Lower`, upper: `${variable}Upper` });

const intervalGraphPattern = (subject: string, attrUri: string, variable: string): string => {
  const bounds = intervalBoundVariables(variable);
  return `OPTIONAL {
    ${subject} <${encodeURI__readme_before_removing(attrUri)}> ${variable}Node .
    ${variable}Node <http://olyro.de/mondiview/lowerBound> ${bounds.lower} .
    ${variable}Node <http://olyro.de/mondiview/upperBound> ${bounds.upper} .
    BIND(CONCAT(${bounds.lower}, "${INTERVAL_SEPARATOR}", ${bounds.upper}) AS ${variable})
  }`;
}

export const parseIntervalBinding = (value: string): Interval => {
  const [lowerBound, upperBound] = value.split(INTERVAL_SEPARATOR);
  return { lowerBound: lowerBound ?? "", upperBound: upperBound ?? "" };
}

const sparqlStringLiteral = (s: string): string =>
  `"${s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\r/g, "\\r")}"`;

const externalLinkGraphPattern = (subject: string, attrUri: string, i: number): string =>
  `OPTIONAL {
    ${subject} <${encodeURI__readme_before_removing(attrUri)}> ?bn${i} .
    ?bn${i} <http://olyro.de/mondiview/url> ?url${i} .
    ?bn${i} <http://olyro.de/mondiview/text> ?text${i} .
    BIND(CONCAT(?text${i}, "${EXTERNAL_LINK_SEPARATOR}", ?url${i}) AS ?P${i})
  }`;

const translatable = (kind: Kind): boolean =>
  kind !== 'http://olyro.de/mondiview/number' &&
  kind !== "http://olyro.de/mondiview/entity" &&
  kind !== "http://olyro.de/mondiview/imageCollection" &&
  kind !== "http://olyro.de/mondiview/htmlImageCollection" &&
  kind !== "http://olyro.de/mondiview/externalLink" &&
  kind !== "http://olyro.de/mondiview/boolean"

export async function getEntity(description: EntityDescription, uri: string, language: string): Promise<Entity> {
  const withoutReference = description.attributes.filter(a => a.kind !== "http://olyro.de/mondiview/reference");
  const result = await query(buildEntityQuery(withoutReference, uri));

  const o = result.results.bindings[0];
  const ret: AttributeWithData[] = [];

  for (const k of Object.keys(o)) {
    const attr = withoutReference[+k.substring(1)];
    const value = await parseAndFetch(o[k], attr, language);
    if (value !== null) {
      ret.push(value);
    }
  }

  const reference = await getReferenceEntity(description, ret, language);

  for (const attribute of description.attributes) {
    if (attribute.kind === "http://olyro.de/mondiview/reference") {
      const attr: ReferenceAttribute = attribute;
      ret.push({ kind: "http://olyro.de/mondiview/reference", attribute: attribute, data: reference.find(ad => ad.attribute.uri === attr.referenceField)! });
    }
  }

  return new Entity(uri, description, ret);
}

export async function getReferenceEntity(description: EntityDescription, localAttributes: AttributeWithData[], language: string): Promise<AttributeWithData[]> {
  if (!description.referenceAttribute || !description.referenceClass) {
    return [];
  }

  const referenceClass = await getEntityDescription(description.referenceClass, language);
  const reference = await getEntity(referenceClass, localAttributes.flatMap(ad => {
    if ((ad.kind === "http://olyro.de/mondiview/string" || ad.kind === "http://olyro.de/mondiview/entity") && ad.attribute.uri === description.referenceAttribute) {
      return [ad.data];
    } else {
      return [];
    }
  })[0], language);

  return reference.attributes;
}

async function parseAndFetch(obj: BindingValue, attribute: Attribute, language: string): Promise<AttributeWithData | null> {
  switch (attribute.kind) {
    case "http://olyro.de/mondiview/number": return {
      kind: attribute.kind,
      attribute: attribute,
      data: +obj.value
    };
    case "http://olyro.de/mondiview/string": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value
    };
    case "http://olyro.de/mondiview/pdf": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value
    };
    case "http://olyro.de/mondiview/imageCollection": {
      const completeDocument = await getImage(obj.value, language);
      return {
        kind: attribute.kind,
        attribute: attribute,
        data: {
          uri: obj.value,
          completeDocument
        }
      };
    }
    case "http://olyro.de/mondiview/category":
      return {
        kind: attribute.kind,
        attribute: attribute,
        data: obj.value,
      };
    case "http://olyro.de/mondiview/htmlContent": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value
    };
    case "http://olyro.de/mondiview/htmlImageCollection": {
      const collection = await getHtmlImageCollection(obj.value);
      return {
        kind: attribute.kind,
        attribute: attribute,
        data: collection
      };
    }
    case "http://olyro.de/mondiview/substringSearchText": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value
    };
    case "http://olyro.de/mondiview/entity": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value
    };
    case "http://olyro.de/mondiview/copyText": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value
    };
    case "http://olyro.de/mondiview/boolean": return {
      kind: attribute.kind,
      attribute: attribute,
      data: obj.value === "true"
    };
    case "http://olyro.de/mondiview/externalLink": {
      const [text, url] = obj.value.split(EXTERNAL_LINK_SEPARATOR);
      return {
        kind: attribute.kind,
        attribute: attribute,
        data: { url: url ?? "", text: text ?? "" }
      };
    }
    case "http://olyro.de/mondiview/interval": return {
      kind: attribute.kind,
      attribute: attribute,
      data: parseIntervalBinding(obj.value)
    };
    case "http://olyro.de/mondiview/reference": return null;
    default: return assertNever(attribute);
  }
}

async function getHtmlImageCollection(collectionUri: string): Promise<HtmlImageCollection> {
  const result = await query(`SELECT ?html ?fileName ?blockStartClass ?order WHERE {
  {
    <${collectionUri}> :hasMember [
        :orderNumber ?order;
        :fileName    ?fileName;
      ].
  } UNION {
    <${collectionUri}> :hasMember [
        :orderNumber ?order;
        :html        ?html;
      ].
  } UNION {
    <${collectionUri}> :hasMember [
        :orderNumber ?order;
        :blockStartClass ?blockStartClass;
      ].
  }
  }`);

  const collection: HtmlImageCollection = {
    uri: collectionUri,
    parts: []
  }

  result.results.bindings.forEach(b => {
    const order = +b.order.value;
    if (b.html) {
      collection.parts.push({
        html: b.html.value,
        orderNumber: order
      });
    } else if (b.fileName) {
      collection.parts.push({
        fileName: b.fileName.value,
        orderNumber: order
      });
    } else if (b.blockStartClass) {
      collection.parts.push({
        blockStartClass: b.blockStartClass.value,
        orderNumber: order
      });
    }
  });

  const sortCollectionImageHtml = (a: CollectionImageHtml, b: CollectionImageHtml): number => {
    if (a.orderNumber < b.orderNumber) return -1;
    else if (a.orderNumber > b.orderNumber) return +1;
    else if ("fileName" in a && "html" in b) return +1;
    else if ("html" in a && "fileName" in b) return -1;
    else return 0;
  }

  collection.parts.sort(sortCollectionImageHtml)
  return collection;
}

async function getPageMetadata(collectionUri: string, language: string): Promise<Map<number, MetadataEntry[]>> {
  const queryToCall = `SELECT ?page ?metadataIndex ?metadataLabel ?metadataValue WHERE {
    <${collectionUri}> :hasPage ?pages .
    ?pages :pageNr ?page .
    ?pages :hasMetadata ?metadata .
    ?metadata :metadataEntry ?entry .
    ?entry :index ?metadataIndex .
    ?entry :label ?labelLiteral .
    ?entry :value ?metadataValue .
    BIND(STR(?labelLiteral) as ?metadataLabel)
    FILTER (lang(?labelLiteral) = '${language}')
  }`;

  const result = await query(queryToCall);
  const metadataMap = new Map<number, MetadataEntry[]>();

  result.results.bindings.forEach(r => {
    const pageNumber: number = +r.page.value;
    const metadataEntry: MetadataEntry = {
      index: +r.metadataIndex.value,
      label: r.metadataLabel.value,
      value: r.metadataValue.value
    };

    if (!metadataMap.has(pageNumber)) {
      metadataMap.set(pageNumber, []);
    }

    metadataMap.get(pageNumber)!.push(metadataEntry);
  });

  metadataMap.forEach(entries => {
    entries.sort((a, b) => a.index - b.index);
  });

  return metadataMap;
}

async function getImage(collectionUri: string, language: string): Promise<DocumentPart[]> {
  const queryToCall = ` SELECT ?page ?fileName ?width ?imageURL ?label ?pdf WHERE {
        <${collectionUri}> :hasPage ?pages .
        ?pages :pageNr ?page .
        ?pages :hasResolutions ?resolutions .
        OPTIONAL {?resolutions :resolution [:url ?fileName; :res ?width] .}
        OPTIONAL {?pages :image ?imageURL}
        OPTIONAL {?pages rdfs:label ?label}
        OPTIONAL {<${collectionUri}> data:hasPrintView ?pdf .}
      }`;
  const result = await query(queryToCall);

  const metadataMap = await getPageMetadata(collectionUri, language);

  const res: DocumentPart[] = [];
  result.results.bindings.forEach(r => {
    const pageNumber: number = +r.page.value;
    const index = res.findIndex(r => r.page === pageNumber);
    if (index === -1) {
      const newPart: DocumentPart = {
        page: pageNumber,
        imageURL: r.imageURL ? r.imageURL.value : "",
        label: r.label ? r.label.value : "",
        pdfUrl: r.pdf ? r.pdf.value : "",
        resolutions: r.width && r.fileName ? [{ width: r.width.value, fileName: r.fileName.value }] : []
      };

      const pageMetadata = metadataMap.get(pageNumber);
      if (pageMetadata) {
        newPart.metadata = pageMetadata;
      }

      res.push(newPart);
    } else {
      res[index].resolutions.push({ width: r.width.value, fileName: r.fileName.value })
    }
  })
  return res.sort((a, b) => a.page - b.page);
}

async function getCategoryValuesForAttributes(attributes: Attribute[], language: string): Promise<Attribute[]> {

  const newAttributes: Attribute[] = JSON.parse(JSON.stringify(attributes));

  const result = await query(`SELECT ?id ?value ?label ?guardAttribute ?guardValue WHERE {
    ?id a rdf:Property .
    ?id :hasPossibleValue ?node.
    ?node :value ?value.
    OPTIONAL { ?node rdfs:label ?l . BIND(STR(?l) as ?label) . FILTER (lang(?l) = '${language}') }
    OPTIONAL {
      ?node :guard [
        :attribute ?guardAttribute;
        :value ?guardValue
      ]
    }
  }`);

  const data: {
    [attributeUri: string]: {
      [value: string]: {
        label: string
        unguarded: boolean
        guards: CategoryEntryGuard[]
      }
    }
  } = {};

  for (const row of result.results.bindings) {
    const attributeUri = row.id.value as string;
    const attributeValue = row.value.value as string;
    const label = row.label ? row.label.value as string : attributeValue;

    data[attributeUri] = data[attributeUri] || {};
    const attribute = data[attributeUri];
    attribute[attributeValue] = attribute[attributeValue] || {
      label,
      unguarded: false,
      guards: []
    };
    attribute[attributeValue].label = label;
    attribute[attributeValue].unguarded = attribute[attributeValue].unguarded || !row.guardAttribute;
    if (row.guardAttribute) {
      attribute[attributeValue].guards.push({
        attribute: row.guardAttribute.value,
        value: row.guardValue.value
      });
    }
  }

  return newAttributes.map(a => {
    if (a.kind === 'http://olyro.de/mondiview/category') {
      const values = data[a.uri];
      if (values) {
        a.values = Object.entries(values).map(([value, { label, unguarded, guards }]) => ({
          value,
          label,
          guards: unguarded ? [] : guards
        }));
      } else {
        a.values = [];
      }
    }
    return a;
  });
}

async function getCategoryMappingsForAttributes(attributes: Attribute[], language: string): Promise<Attribute[]> {

  const newAttributes: Attribute[] = JSON.parse(JSON.stringify(attributes));

  const result = await query(`SELECT ?id ?value ?label WHERE {
    ?id a rdf:Property .
    ?id :mapValues ?node.
    ?node :value ?value.
    OPTIONAL { ?node rdfs:label ?l . BIND(STR(?l) as ?label) . FILTER (lang(?l) = '${language}') }
  }`);

  const data: {
    [attributeUri: string]: {
      [value: string]: string
    }
  } = {};

  for (const row of result.results.bindings) {
    const attributeUri = row.id.value as string;
    const attributeValue = row.value.value as string;
    const label = row.label ? row.label.value as string : attributeValue;

    data[attributeUri] = data[attributeUri] || {};
    const attribute = data[attributeUri];
    attribute[attributeValue] = label;
  }

  return newAttributes.map(a => {
    if (a.kind === 'http://olyro.de/mondiview/category') {
      a.mapping = data[a.uri];
    }
    return a;
  });
}


function buildEntityQuery(attrsWithoutReferences: Attribute[], uri: string): string {
  const paraList = attrsWithoutReferences.map((_, i) => `?P${i}`).join(" ");
  const subject = `<${encodeURI__readme_before_removing(uri)}>`;
  const attributes = attrsWithoutReferences.map((a, i) =>
    a.kind === 'http://olyro.de/mondiview/externalLink'
      ? externalLinkGraphPattern(subject, a.uri, i)
      : a.kind === 'http://olyro.de/mondiview/interval'
        ? intervalGraphPattern(subject, a.uri, `?P${i}`)
        : `OPTIONAL { ${subject} <${encodeURI__readme_before_removing(a.uri)}> ?P${i} . }`
  ).join("\n");

  return `
    SELECT ${paraList} WHERE {
      ${attributes}
    }
  `
}

async function getSwitch(idUri: string, propertyUri: string, language: string): Promise<Switch | undefined> {
  const result = await query(`SELECT ?defaultResult ?property ?result ?order ?value
    WHERE {
      VALUES ?language {'${language}'}
      <${idUri}> <${propertyUri}> [
          :defaultResult ?defaultResult;
          :property ?property;
          :check [
            :value ?value;
            :order ?order;
            :result ?result
          ]
        ].
      FILTER (lang(?result) = ?language || lang(?result) = "")
      FILTER (lang(?defaultResult) = ?language || lang(?defaultResult) = "")
    }
  `);

  if (result.results.bindings.length === 0) return undefined;

  const checks: SwitchCheck[] = result.results.bindings.map(b => ({
    order: +b.order.value,
    value: b.value.value,
    result: b.result.value
  })).sort((c1, c2) => c1.order - c2.order);

  return {
    checks,
    defaultResult: result.results.bindings[0].defaultResult.value,
    property: result.results.bindings[0].property.value,
  };
}

export interface Contents {
  contents: Content[];
}

export interface Content {
  uri: string;
  content?: string;
  navLink?: ContentNavLink;
}

export interface ContentNavLink {
  position: ContentNavPosition;
  title: string;
  route: string;
}

export interface ContentNavPosition {
  left?: number;
  right?: number;
  footer?: number;
}

export interface ShortUrlEntitys {
  uri: string;
  name: string;
  tag: string;
}

export interface EntityName {
  uri: string;
  name: string;
  alternativeLink?: string;
  orderBy?: string;
}

export interface Switch {
  property: string;
  checks: SwitchCheck[];
  defaultResult: string;
}

export interface SwitchCheck {
  order: number;
  value: string;
  result: string;
}

export async function getContent(uri: string): Promise<string | null> {
  const response = await query(`SELECT ?content WHERE { <${uri}> :hasContent ?content. }`);
  if (response.results.bindings.length > 0) {
    return response.results.bindings[0]["content"].value;
  } else {
    return null;
  }
}

// low level method. Should rarely be used.
export async function query(query: string): Promise<Response> {
  const endpoint = base;

  const response = await fetch(endpoint, {
    body: prefixes + query,
    method: 'POST',
    headers: { 'Content-Type': 'application/sparql-query' }
  });
  return await response.json();
}

const prefixes = `
  PREFIX : <http://olyro.de/mondiview/>
  PREFIX data: <http://monodicum/>
  PREFIX text: <http://jena.apache.org/text#>
  PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
  PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
`;

interface Response {
  head: {
    vars: string[];
  };
  results: {
    bindings: BindingRow[];
  };
}

type BindingRow = {
  [variable: string]: BindingValue
}

interface BindingValue {
  type: string;
  value: any;
}

const __basePath = (window as any).__BASE_PATH__ || "";
export const base = import.meta.env.VITE_FUSEKI_URL
  ? __basePath + import.meta.env.VITE_FUSEKI_URL
  : [window.location.protocol, "//", window.location.hostname, ":3030/tdb2-database/query?query="].join("");
export const svgBase = import.meta.env.VITE_SVG_URL ? __basePath + import.meta.env.VITE_SVG_URL : "http://localhost/monodicum/svgs/"
export const pdfBase = import.meta.env.VITE_PDF_URL ? __basePath + import.meta.env.VITE_PDF_URL : svgBase
export const synopseBase = import.meta.env.VITE_SYNOPSE_URL || "http://localhost:9071"
