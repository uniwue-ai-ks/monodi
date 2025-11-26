import { ReactElement, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { TailSpin } from 'react-loader-spinner';

import * as Api from '../../api';
import { QueryParameter } from '../../hooks/useQuery';
import { ParamInput, useRedirect, useUrlData } from '../../hooks/useUrlData';
import { Attribute } from '../../model/attribute';
import { EntityDescription } from '../../model/entity-description';
import * as View from '../view/Params';
import * as Search from './Search';

import { faAngleDoubleLeft, faAngleDoubleRight, faAngleLeft, faAngleRight, faSortAlphaDown, faSortAlphaUp } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { Link } from 'react-router-dom';
import { PrintQueue, QueueDoc, usePrintQueue } from '../../hooks/usePrintQueue';
import { SubEntity } from '../../model/subentity';
import Translation from '../../translation/Translation';
import { assertNever } from '../../util';
import LangContext from '../app/App';
import { SnippetSearch } from './SnippetSearch';
import { RowResult } from '../../api';
import { StaticContent, StaticRoutes } from '../static/StaticContent';

export const Table = (props: Props): ReactElement => {
  const [pagination, setPagination, updatePagination] = useUrlData<Search.Params>()('pagination');
  const [hiddenFromTable, _] = useUrlData<Search.Params>()('hideFromTable');
  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  const onSortClick = useCallback((a: Attribute): void => {
    if (!pagination || (pagination.attribute !== a.uri && pagination.attribute !== a.alternativeSortAttribute)) {
      const uri = a.alternativeSortAttribute || a.uri;
      setPagination({ attribute: uri, limit: (pagination || { limit: 10 }).limit, offset: 0, asc: true });
    } else {
      setPagination({ ...pagination, asc: !pagination.asc });
    }
  }, [pagination, setPagination]);


  const pag = pagination || getDefaultPagination(props.search);

  const setPage = useCallback((page: number | null): void => {
    updatePagination(pagination => {
      const pag = pagination || getDefaultPagination(props.search);
      if (pag !== null) {
        if (page === null) {
          return { ...pag, offset: 0, limit: normalPageLimit };
        } else {
          return { ...pag, offset: page * pag.limit };
        }
      } else {
        return null;
      }
    });
  }, [updatePagination, props.search]);

  const showAll = useCallback(() => {
    updatePagination(pagination => {
      const pag = pagination || getDefaultPagination(props.search);
      if (pag !== null) {
        return { ...pag, offset: 0, limit: maxSinglePageLimit };
      } else {
        return null;
      }
    });
  }, [updatePagination, props.search]);

  if (pag === null) {
    return loader(props.search, pag, translate);
  } else {
    return <RenderTable
      ed={props.search}
      sortBy={pag}
      onSortClick={onSortClick}
      setPage={setPage}
      showAll={showAll}
      translate={translate}
      tableMode={props.tableMode}
      updateTableMode={props.updateTableMode}
      hidden={hiddenFromTable || []}
      staticRoutes={props.staticRoutes}
      language={langContext.lang}
    />
  }
}

type RenderTableProps = {
  ed: EntityDescription,
  onSortClick: (a: Attribute) => void,
  sortBy: Api.Pagination,
  setPage: (page: number | null) => void,
  showAll: () => void,
  translate: (key: string) => string,
  tableMode: TableMode,
  updateTableMode: (updater: (mode: TableMode) => TableMode) => void,
  hidden: string[],
  staticRoutes: StaticRoutes,
  language: string,
}
export const RenderTable = ({ ed, onSortClick, sortBy, setPage, showAll, translate, tableMode, updateTableMode, hidden, staticRoutes, language }: RenderTableProps): ReactElement => {
  const printQueue = usePrintQueue();
  const openDocument = useRedirect<View.Params>('view');
  const toggleSynopsisDoc = useCallback((uri: string) => {
    updateTableMode((tableMode: TableMode): TableMode => {
      if (tableMode.kind === "synopsis") {
        if (tableMode.selected.includes(uri)) {
          return ({ kind: "synopsis", selected: tableMode.selected.filter(s => s !== uri) });
        } else {
          return ({ kind: "synopsis", selected: [...tableMode.selected, uri] });
        }
      } else {
        return tableMode;
      }
    })
  }, [updateTableMode]);

  const [query] = useUrlData<Search.Params>()('query');
  const [intersections] = useUrlData<Search.Params>()('intersection');

  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
    } else {
      setPage(0);
    }
  }, [query, setPage]);
  const [result, running] = useResult(query, intersections || null, language, ed, sortBy);

  if (result === null) {
    return loader(ed, sortBy, translate);
  } else {
    const results: View.Params[] = result.rows.map(r => { return { document: r.uri, search: ed.uri } });
    const searchNav: View.SearchNavParams = { results: results, link: window.location.href, querys: query, attributes: ed.attributes }
    sessionStorage.setItem("monodicumSearch", JSON.stringify(searchNav));
  }

  return renderTable(
    ed,
    onSortClick,
    result,
    running,
    sortBy,
    tableMode,
    toggleSynopsisDoc,
    openDocument,
    setPage,
    showAll,
    translate,
    ed.disablePrintColumn ? undefined : printQueue,
    query,
    hidden,
    staticRoutes
  );
}

type AttributeSearch = {
  attr: Attribute;
  search: string;
}
export const renderTable = (
  ed: EntityDescription,
  onSortClick: (a: Attribute) => void,
  result: Api.QueryResult,
  loading: boolean,
  sortBy: Api.Pagination,
  tableMode: TableMode,
  toggleSynopsisDoc: (uri: string) => void,
  openDocument: (data: ParamInput<View.Params>) => void,
  setPage: (page: number | null) => void,
  showAll: () => void,
  translate: (key: string) => string,
  printQueue: PrintQueue | undefined,
  query: QueryParameter[] | null,
  hidden: string[],
  staticRoutes: StaticRoutes,
): ReactElement => {
  const headers: Attribute[] = ed
    .attributes
    .filter(a => a.headerOrder !== undefined && hidden.indexOf(a.uri) === -1)
    .sort((a, b) => a.headerOrder! - b.headerOrder!);
  const index = sortBy.offset / sortBy.limit;
  const maxIndex = Math.floor(result.count / sortBy.limit);

  const searches: AttributeSearch[] =
    query?.map(q => ({ attr: ed.attributes.find(a => a.uri === q.uri)!, search: q.value }))
      .filter(a => a.attr !== undefined)
    || []

  const pages = sortBy.limit === maxSinglePageLimit ?
    <div data-intro={translate("introTable")} className="pages">
      <div onClick={() => setPage(null)}>{translate("backToPage")}</div>
    </div> :
    <div data-intro={translate("introTable")} className="pages">
      <div onClick={() => setPage(0)} className="toStart"> <FontAwesomeIcon icon={faAngleDoubleLeft} /> </div>
      <div onClick={() => setPage(Math.max(0, index - 1))} className="previous"><FontAwesomeIcon icon={faAngleLeft} /> </div>
      <div className="current">
        {translate("page")} {index + 1} {translate("of")} {maxIndex + 1}
      </div>
      <div onClick={() => setPage(Math.min(maxIndex, index + 1))} className="next"><FontAwesomeIcon icon={faAngleRight} /> </div>
      <div onClick={() => setPage(maxIndex)} className="toEnd"><FontAwesomeIcon icon={faAngleDoubleRight} /> </div>
      <div onClick={showAll} className="toAll">{translate("all")}</div>
    </div>

  return <div className="pagination-and-table">

    <div className="searchTable">
      <div className="doc-count">{result.count} {translate("foundDocs")}</div>
      {pages}
      {printQueue && tableMode.kind == 'print' ? <PrintButtons result={result} translate={translate} printQueue={printQueue} /> : null}
      <div></div>
    </div>

    <table className="search-output-table">
      {renderHeader(headers, onSortClick, sortBy, translate, searches, tableMode)}
      {renderBody(headers, result, tableMode, toggleSynopsisDoc, openDocument, ed, translate, printQueue, searches, staticRoutes)}
    </table>
    {!loading ? null : <div className="loader"><TailSpin /></div>}
  </div>;
}

type PrintAllToggleProps = {
  result: Api.QueryResult,
  translate: (key: string) => string,
  printQueue: PrintQueue,
}
export const PrintButtons = ({ result, translate, printQueue: { clear, docs, add, remove } }: PrintAllToggleProps): ReactElement => {
  const addAllResultsToPrintQueue = () => {
    const toAdd: QueueDoc[] = result.rows.map(r => { return { data: r.data, uri: r.uri } });
    add(toAdd);
  }

  const removeAllResultsFromPrintQueue = () => {
    const toDel: QueueDoc[] = result.rows.map(r => { return { data: r.data, uri: r.uri } });
    remove(toDel);
  }

  const allIncludedInPrint = (): boolean => {
    const res: boolean[] = result.rows.map(r => docs.find(d => d.uri === r.uri) ? true : false);
    return res.every(a => a === true)
  }

  const removeOldClass = "print-buttons" + (docs.length == 0 ? " hidden" : "")

  return <div className="print-buttons">
    <div className={removeOldClass} onClick={() => clear()}>{translate("clearOldPrint")}</div>
    {
      allIncludedInPrint() ?
        <div className="print-all" onClick={() => removeAllResultsFromPrintQueue()}>{translate("removeAllFromPrint")}</div>
        :
        <div className="print-all" onClick={() => addAllResultsToPrintQueue()}>{translate("addAllToPrint")}</div>
    }
  </div>
}

export const renderHeader = (
  headers: Attribute[],
  onSortClick: (a: Attribute) => void,
  sortBy: Api.Pagination,
  translate: (key: string) => string,
  searches: AttributeSearch[],
  mode: TableMode
): ReactElement => {
  const previews = searches.filter(({ attr }) => attr.searchPreview !== undefined)
  return <thead>
    <tr>
      {
        headers.map(h => <th onClick={() => onSortClick(h)} key={h.uri}>
          {h.label}
          {
            !sortBy || !sortBy.attribute || (sortBy.attribute !== h.uri && sortBy.attribute !== h.alternativeSortAttribute) ?
              <FontAwesomeIcon className={"hide"} pull="right" icon={faSortAlphaDown} /> :
              sortBy.asc ?
                <FontAwesomeIcon pull="right" icon={faSortAlphaDown} /> :
                <FontAwesomeIcon pull="right" icon={faSortAlphaUp} />
          }
        </th>)
      }
      {
        previews.map(({ attr }, index) => <th key={attr.uri + "#preview" + index}> {attr.searchPreview?.label} </th>)
      }
      <th>{translate(getGoToButtonNameKey(mode))}</th>
    </tr>
  </thead>
}

const getGoToButtonNameKey = (mode: TableMode): string => {
  if (mode.kind === "default") {
    return "goTo";
  } else if (mode.kind === "print") {
    return "goToPrintMode";
  } else if (mode.kind === "synopsis") {
    return "goToSynopsisMode";
  } else {
    return assertNever(mode);
  }
}

export const renderBody = (
  headers: Attribute[],
  result: Api.QueryResult,
  tableMode: TableMode,
  toggleSynopsisDoc: (uri: string) => void,
  openDocument: (data: ParamInput<View.Params>) => void,
  ed: EntityDescription,
  translation: (key: string) => string,
  printQueue: PrintQueue | undefined,
  searches: AttributeSearch[],
  staticRoutes: StaticRoutes,
): ReactElement => {

  const getURL = (data: ParamInput<View.Params>): string => {
    const newParams = new URLSearchParams(document.location.search);
    if (data.document !== null) {
      newParams.set("document", JSON.stringify(data.document))
    }
    if (data.search !== null) {
      newParams.set("search", JSON.stringify(data.search))
    }
    const test: Location = { ...document.location, search: "?" + newParams.toString(), pathname: "/view" }
    return (test.origin + test.pathname + test.search);
  }

  const subEntityLink = (entity: string, se: SubEntity, searches: AttributeSearch[]): string => {
    const transferredSearches = searches
      .map(({ attr, search }) => ({ uri: se.description.attributes.find(a => a.referenceSearch === attr.uri)?.uri, value: search }))
      .filter(s => s.uri !== undefined)
    transferredSearches.push({ uri: se.referenceAttribute, value: entity });
    return "/search/" + encodeURIComponent(se.uri) + "/?query=" + JSON.stringify(transferredSearches)
  }

  const renderLinkOrCheckbox = (r: Api.RowResult) => {
    if (tableMode.kind === "synopsis") {
      const active = tableMode.selected.indexOf(r.uri) !== -1;
      return <td>
        <input type="checkbox" onChange={() => toggleSynopsisDoc(r.uri)} checked={active} />
      </td>
    } else if (tableMode.kind === "print") {
      return <PrintToggle result={r} printQueue={printQueue!} />
    } else if (tableMode.kind === "default") {
      return <td className="openLink">
        <ul>
          <li key={r.uri + "/openDocument"}>
            <a onClick={(e) => {
              e.preventDefault();
              openDocument({ document: r.uri, search: ed.uri })
            }}
              href={getURL({ document: r.uri, search: ed.uri })}>
              {getOpenText(ed, r, translation)}
            </a>
          </li>
          {ed.subEntities ? ed.subEntities.map(se =>
            <li key={r.uri + se.uri}><Link to={subEntityLink(r.uri, se, searches)}>{se.label}</Link></li>
          ) : null}
        </ul>
      </td>
    } else {
      return assertNever(tableMode);
    }
  }

  const formatCell = (row: Api.RowResult, header: Attribute) => {
    if (header.kind === "http://olyro.de/mondiview/category" && header.mapping && row.data[header.uri] !== undefined) {
      return <td key={header.uri}>{header.mapping[row.data[header.uri]] || showOrShorten(row, header)}</td>
    } else if (header.kind === "http://olyro.de/mondiview/htmlContent") {
      return <StaticContent tag="td" key={header.uri} innerHtml={row.data[header.uri]} staticRoutes={staticRoutes} />
    } else if (header.documentPosition.download) {
      return <td key={header.uri}><a href={row.data[header.uri]}>{header.label}</a></td>;
    } else {
      return <td key={header.uri}>{showOrShorten(row, header)}</td>;
    }
  }

  const previews = searches.filter(({ attr }) => attr.searchPreview !== undefined)

  const showOrShorten = (row: RowResult, attr: Attribute) => {
    if (attr.shortenPosition?.results) {
      return <ShortenString text={row.data[attr.uri]} />;
    } else
      return <>{row.data[attr.uri]}</>;
  }

  return <tbody>{
    result.rows.map(r =>
      <tr key={r.uri}>
        {
          headers.map(h => formatCell(r, h))
        }
        {
          previews.map(s => <td key={s.attr.uri + s.search}> {r.data[s.attr.uri] !== undefined && <SnippetSearch query={s.search} data={r.data[s.attr.uri]} config={s.attr.searchPreview!.config} />} </td>)
        }
        {renderLinkOrCheckbox(r)}
      </tr>
    )
  }</tbody>
}

type PrintToggleProps = {
  result: Api.RowResult,
  printQueue: PrintQueue,
}

type ShortenStringProps = { text: string }
export const ShortenString = ({ text }: ShortenStringProps): ReactElement => {
  return <div className="shorten-string"><div className="shortened">{text}</div><div className="full">{text}</div></div>
}

export const PrintToggle = ({ result, printQueue: { docs, addOrRemove } }: PrintToggleProps): ReactElement => {
  const onToggle = (r: Api.RowResult) => {
    addOrRemove([{ uri: r.uri, data: r.data }])
  }

  const countPrint = (uri: string): number => {
    return docs.filter(d => d.uri === uri).length;
  }

  const active = countPrint(result.uri) > 0;

  return <td> <input type="checkbox" onChange={() => onToggle(result)} checked={active} /> </td>
}

export interface Props {
  search: EntityDescription;
  tableMode: TableMode;
  updateTableMode: (updater: (mode: TableMode) => TableMode) => void;
  staticRoutes: StaticRoutes;
}

const useResult = (
  query: QueryParameter[] | null,
  intersections: string[] | null,
  language: string,
  search: EntityDescription,
  pagination: Api.Pagination,
): [(Api.QueryResult | null), boolean] => {
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<Api.QueryResult | null>(null);

  useEffect(() => {
    let canceled = false;
    setRunning(true);
    if (search !== null) {
      (async () => {
        const q = subsituteQueryLabelsByValues(search, query || []);
        const results = await Api.doQuery(search, language, q, pagination, intersections);
        if (!canceled) {
          setResult(results);
          setRunning(false);
        }
      })();
    }
    return () => { canceled = true }
  }, [query, search, pagination, intersections, language])

  return [result, running];
}

const subsituteQueryLabelsByValues = (search: EntityDescription, query: QueryParameter[]): QueryParameter[] => {
  return query.map(param => {
    const attr = search.attributes.find(a => a.uri === param.uri);
    if (attr && attr.kind === "http://olyro.de/mondiview/category") {
      const val = attr.values.find(v => v.label === param.value);
      if (val) {
        return { ...param, value: val.value };
      }
    }
    return param;
  });
}

export const getDefaultPagination = (ed: EntityDescription): Api.Pagination | null => {
  const headers: Attribute[] = ed.attributes.filter(a => a.headerOrder !== undefined).sort((a, b) => a.headerOrder! - b.headerOrder!);
  if (headers.length === 0) {
    return null;
  } else {
    return {
      asc: true,
      limit: normalPageLimit,
      offset: 0,
      attribute: headers[0].alternativeSortAttribute || headers[0].uri
    };
  }
}

const getOpenText = (
  s: EntityDescription,
  rrs: Api.RowResult,
  translate: (key: string) => string
): string => {
  if (s.conditionalOpenText) {
    for (const c of s.conditionalOpenText.checks) {
      if (rrs.data[s.conditionalOpenText.property] === c.value) {
        return c.result;
      }
    }
    return s.conditionalOpenText.defaultResult;
  } else {
    return translate("description");
  }
}

const loader = (
  ed: EntityDescription,
  pagination: Api.Pagination | null,
  translate: (key: string) => string
): ReactElement => {
  const headers: Attribute[] = ed.attributes.filter(a => a.headerOrder !== undefined).sort((a, b) => a.headerOrder! - b.headerOrder!);

  return <div className="pagination-and-table">
    <table className="search-output-table">
      {renderHeader(headers, () => { }, pagination || { attribute: "", offset: 0, limit: 0, asc: true }, translate, [], { kind: "default" })}
    </table>
    <div className="loader"><TailSpin /></div>
  </div>;
}

export type TableMode =
  { kind: "default" } |
  { kind: "print" } |
  { kind: "synopsis", selected: string[] }


const maxSinglePageLimit = 99999
const normalPageLimit = 10
