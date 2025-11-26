import { Fragment, ReactElement, useContext, useEffect, useMemo, useState } from 'react';
import * as Api from '../../api';
import { QueryParameter } from '../../hooks/useQuery';
import { useRedirect } from "../../hooks/useUrlData";
import { EntityDescription } from "../../model/entity-description";
import LangContext from "../app/App";
import * as Synopsis from '../synopsis/Synopsis';
import './Search.scss';
import { SearchBar } from "./SearchBar";

import { useHistory } from 'react-router-dom';
import Translation from '../../translation/Translation';
import { assertNever } from '../../util';
import { StaticRoutes } from '../static/StaticContent';
import { Table, TableMode } from './Table';

export function Search(props: { uri: string, compare?: string, staticRoutes: StaticRoutes }) {
  const [tableMode, setTableMode] = useState<TableMode>({ kind: 'default' });
  const history = useHistory();

  const synopsisRedirect = useRedirect<Synopsis.Params>('synopsis');

  const [search, setSearch] = useState<EntityDescription | null>(null);

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
    if (search === null) {
      return [];
    } else {
      const copy = search.attributes.slice();
      copy.sort((a1, a2) => (a1.searchOrder || 0) - (a2.searchOrder || 0));
      return copy;
    }
  }, [search]);

  useEffect(() => {
    let canceled = false;
    Api.getEntityDescription(props.uri, langContext.lang).then((s) => {
      if (!canceled) setSearch(s);
    });
    return () => { canceled = true; }
  }, [props.uri, langContext])

  const gotoSynopsis: () => void =
    () => synopsisRedirect({
      searchUri: search!.uri,
      uris: tableMode.kind === 'synopsis' ? tableMode.selected : [],
      itemsPerPage: 5,
      page: 0,
      filter: "",
      singleLine: false,
      textInLastDocOnly: false
    })

  return <div>
    {
      search === null ? renderWaiting() : <div>
        <SearchBar entity={search} search={sortedSearchAttributes} modeButtons={renderModeButton(
          search,
          tableMode,
          setTableMode,
          gotoSynopsis,
          () => history.push("/print"),
          translate
        )} />
        <Table search={search} tableMode={tableMode} updateTableMode={setTableMode} staticRoutes={props.staticRoutes} />
      </div>
    }
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
