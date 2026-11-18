import { Fragment, ReactElement, useContext, useEffect, useMemo, useState } from 'react';
import * as Api from '../../api';
import { QueryParameter } from '../../hooks/useQuery';
import { useRedirect, useUrlData } from "../../hooks/useUrlData";
import { EntityDescription } from "../../model/entity-description";
import LangContext from "../app/App";
import * as Synopsis from '../synopsis/Synopsis';
import './Search.scss';
import { SearchBar } from "./SearchBar";

import { useHistory } from 'react-router-dom';
import Translation from '../../translation/Translation';
import { assertNever } from '../../util';
import { StaticContent, StaticRoutes } from '../static/StaticContent';
import { Table, TableMode } from './Table';

export function Search(props: { uri: string, compare?: string, staticRoutes: StaticRoutes }) {
  const [tableMode, setTableMode] = useState<TableMode>({ kind: 'default' });
  const history = useHistory();

  const synopsisRedirect = useRedirect<Synopsis.Params>('synopsis');

  const [searchView, setSearchView] = useState<Api.SearchView | null>(null);
  const [landingDismissed, setLandingDismissed] = useState(false);
  const [queryParam] = useUrlData<Params>()('query');

  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  const renderWaiting = () => {
    return <div className="queryWaiting">
      {translate("queryWait")}
    </div>;
  }

  const sortedSearchAttributes = useMemo(() => {
    const entity = searchView?.entity;
    if (!entity) {
      return [];
    } else {
      const copy = entity.attributes.slice();
      copy.sort((a1, a2) => (a1.searchOrder || 0) - (a2.searchOrder || 0));
      return copy;
    }
  }, [searchView]);

  useEffect(() => {
    let canceled = false;
    setSearchView(null);
    setLandingDismissed(false);
    Api.getSearchView(props.uri, langContext.lang).then(v => {
      if (!canceled) setSearchView(v);
    });
    return () => { canceled = true; }
  }, [props.uri, langContext])

  // Seed a :Search's prefilled filters into the URL once, on fresh arrival (no query
  // param yet), so they show up in the search form as editable defaults. Once a query
  // is present — including an empty one the user produced by clearing filters — leave
  // it alone. `replace` avoids a back-button trap back to the unseeded URL.
  useEffect(() => {
    const prefilled = searchView?.search?.prefilledFilters;
    if (!prefilled || prefilled.length === 0 || queryParam !== null) return;
    const seeded: QueryParameter[] = prefilled.map(f => ({ uri: f.attribute, value: f.value }));
    const params = new URLSearchParams(document.location.search);
    params.set('query', JSON.stringify(seeded));
    history.replace({ ...document.location, search: `?${params.toString()}` });
  }, [searchView, queryParam, history])

  const gotoSynopsis: () => void =
    () => synopsisRedirect({
      searchUri: searchView!.entity.uri,
      uris: tableMode.kind === 'synopsis' ? tableMode.selected : [],
      itemsPerPage: 5,
      page: 0,
      filter: "",
      singleLine: false,
      textInLastDocOnly: false
    })

  if (searchView === null) {
    return <div>{renderWaiting()}</div>;
  }

  const entity = searchView.entity;
  const config = searchView.search;

  // Optional landing page: rendered in place of the search until dismissed.
  if (config?.landingHtml && !landingDismissed) {
    return <div className="search-landing">
      <StaticContent innerHtml={config.landingHtml} staticRoutes={props.staticRoutes} />
      <button className="search-landing-button" onClick={() => setLandingDismissed(true)}>
        {config.landingButtonText || translate("toSearch")}
      </button>
    </div>;
  }

  return <div>
    {config?.introText &&
      <StaticContent className="search-intro" innerHtml={config.introText} staticRoutes={props.staticRoutes} />}
    <SearchBar entity={entity} search={sortedSearchAttributes} fixedFilters={config?.fixedFilters} modeButtons={renderModeButton(
      entity,
      tableMode,
      setTableMode,
      gotoSynopsis,
      () => history.push("/print"),
      translate
    )} />
    <Table search={entity} fixedFilters={config?.fixedFilters} tableMode={tableMode} updateTableMode={setTableMode} staticRoutes={props.staticRoutes} />
  </div>;
}


const renderModeButton = (
  entity: EntityDescription,
  mode: TableMode,
  setTableMode: (mode: TableMode) => void,
  gotoSynopsis: () => void,
  gotoPrint: () => void,
  translate: (key: string) => string
): ReactElement => {
  const deactivated = entity.disablePrintColumn || !entity.comparableInSynopsis

  const changeToSynopsis = () => setTableMode({ kind: 'synopsis', selected: [] });
  const changeToPrint = () => setTableMode({ kind: 'print' });
  const changeToDefault = () => setTableMode({ kind: 'default' });

  if (deactivated) {
    return <Fragment />;
  } else {
    if (mode.kind === 'default') {
      return <div className='button-row'>
        <button className="synopsis-button" data-intro={translate("introSearch")} onClick={changeToSynopsis}>{translate("compare_version")}</button>
        <button className="compare-button" onClick={changeToPrint}>{translate("addToBook")}</button>
      </div>;
    } else if (mode.kind === 'synopsis') {
      const disabled = mode.selected.length === 0;
      return <div className='button-row'>
        <button className="synopsis-button main-mode-button" disabled={disabled} data-intro={translate("introSearch")} onClick={gotoSynopsis}>{translate("compare")}</button>
        <button className="compare-button" onClick={changeToDefault}>{translate("cancel")}</button>
        <div className="mode-description">{translate("synopsis_mode_description")}</div>
      </div>;
    } else if (mode.kind === 'print') {
      return <div className='button-row'>
        <button className="synopsis-button main-mode-button" data-intro={translate("introSearch")} onClick={gotoPrint}>{translate("print_document")}</button>
        <button className="compare-button" onClick={changeToDefault}>{translate("cancel")}</button>
        <div className="mode-description">{translate("print_mode_description")}</div>
      </div>;
    } else {
      return assertNever(mode);
    }
  }
}

export interface Params {
  query: QueryParameter[];
  creation: string[];
  sortBy: SortBy;
  pagination: Api.Pagination;
  hideFromSearch?: string[];
  hideFromTable?: string[];
  intersection?: string[];
}


interface SortBy {
  key: string;
  asc: boolean;
}
