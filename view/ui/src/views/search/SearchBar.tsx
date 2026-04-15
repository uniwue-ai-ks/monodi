import { groupBy } from 'lodash';
import { ReactElement, useCallback, useContext, useEffect, useMemo, useReducer } from 'react';
import Autosuggest from "react-autosuggest";
import { QueryParameter } from "../../hooks/useQuery";
import { useUrlData } from "../../hooks/useUrlData";
import { Attribute, CategoryEntry, CategoryEntryGuard } from "../../model/attribute";
import { EntityDescription } from '../../model/entity-description';
import Translation from '../../translation/Translation';
import { assertNever, uriToClasName } from "../../util";
import LangContext from '../app/App';
import * as Search from "./Search";
import './SearchBar.scss';

type GroupedQuery = {
  uri: string;
  values: { value: string, index: number }[];
}

const addParameter = (attributes: Attribute[], dispatch: (ce: ChangeEvent) => void) => (uri: string): void => {
  const attribute = attributes.find(a => a.uri === uri);
  if (attribute) {
    const value = attribute.uiQueryPrefillValue || "";
    dispatch({ kind: "AddParameter", param: { uri: attribute.uri, value } });
  }
}

export function SearchBar(props: { entity: EntityDescription, search: Attribute[], modeButtons: ReactElement }) {
  const [urlState, setUrlState] = useUrlData<Search.Params>()('query');
  const [hidden,] = useUrlData<Search.Params>()('hideFromSearch');
  const [query, dispatch] = useReducer(reduce, urlState ? urlState : []);
  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  useEffect(() => {
    dispatch({ kind: "Set", parameters: urlState ? urlState : [] })
  }, [urlState])

  const addParam = addParameter(props.search, dispatch);
  
  useEffect(() => {
    props.search.filter(a => a.initialSearch && !urlState?.some(qp => qp.uri === a.uri)).forEach(attribute => {
      addParam(attribute.uri);
    })
  }, [dispatch, urlState, props.search])

  const search = () => { setUrlState(query); }

  const isHidden = (uri: string): boolean => {
    return !!hidden && hidden.indexOf(uri) !== -1;
  }


  const groupedQuery: GroupedQuery[] =
    Object.entries(
      groupBy(query.map((qp, i) => { return { param: qp, index: i } }), qp => qp.param.uri)
    ).map(([uri, qps]) => { return { uri: uri, values: qps.map(qp => { return { value: qp.param.value, index: qp.index } }) } });

  return <div className="searchBar">
    <div className="parameters">
      <select data-intro={translate("introSearch2")} className="simple-select" value="" onChange={e => { addParam(e.target.value) }}>
        <option value="" disabled hidden>{translate("search_criteria")}</option>
        {
          props.search.filter(a =>
            a.searchOrder !== undefined &&
            !isHidden(a.uri) &&
            (a.allowMultipleSearch !== false || !query.some(qp => qp.uri === a.uri))
          ).map(a =>
            <option key={a.uri} value={a.uri}>
              {a.label}
            </option>
          )
        }
      </select>
      {
        groupedQuery.map((gqp, i) =>
          isHidden(gqp.uri) ? null :
            <QueryParameterComponentMulti entity={props.entity} key={i} dispatch={dispatch} uri={gqp.uri} values={gqp.values} params={query} search={props.search} />)
      }
    </div>
    <div className="button-column">
      <button data-intro={translate("introSearch3")} onClick={search} className="searchButton">{Translation.getTranslation("search", langContext.lang, langContext.overrides)}</button>
      {props.modeButtons}
    </div>
  </div>;
}

export function QueryParameterComponentMulti(props: { entity: EntityDescription, search: Attribute[], uri: string, values: { value: string, index: number }[], params: QueryParameter[], dispatch: (ce: ChangeEvent) => void }) {
  const [intersectState, setIntersectState] = useUrlData<Search.Params>()('intersection');
  const langContext = useContext(LangContext);

  const attribute = props.search.find(a => a.uri === props.uri);

  const setIntersection = (uri: string, state: boolean) => {
    const intersects = new Set(intersectState)
    if (state) {
      intersects.add(uri)
    } else {
      intersects.delete(uri)
    }
    setIntersectState(Array.from(intersects));
  }
  const getIntersection = (uri: string): boolean => {
    return !!intersectState && intersectState.indexOf(uri) !== -1;
  }

  if (attribute) {
    const allowMultiple = attribute.allowMultipleSearch !== false;

    const inputs = props.values.map(({ value, index }) => <QueryParamSingle
      key={index}
      attribute={attribute}
      entity={props.entity}
      search={props.search}
      uri={props.uri}
      value={value}
      index={index}
      params={props.params}
      allowMultiple={allowMultiple}
      dispatch={props.dispatch} />);

    const intersection = getIntersection(attribute.uri);
    const translate = (key: string): string => {
      return Translation.getTranslation(key, langContext.lang, langContext.overrides);
    }

    const multiValueControls = allowMultiple && inputs.length > 1
      ? renderIntersectionControls(intersection, () => setIntersection(attribute.uri, !intersection), translate)
      : null;

    return !inputs || inputs.every(i => i === null) ? null : <div className={"searchQueryInput " + uriToClasName(attribute.uri)}>
      <div className="searchLabel">
        {attribute.label}
        {multiValueControls?.toggleButton}
      </div>
      {multiValueControls?.logicSymbol}
      <div className="searchValues">
        {inputs}
      </div>
    </div>
  } else {
    return null;
  }
}

function renderIntersectionControls(
  intersection: boolean,
  onToggle: () => void,
  translate: (key: string) => string
): { toggleButton: ReactElement; logicSymbol: ReactElement } {
  const logicLabel = intersection ? translate("allOf") : translate("anyOf");
  return {
    toggleButton: <><br /><button className="intersectionToggle" onClick={onToggle}>{logicLabel}</button></>,
    logicSymbol: <span className="logicSymbol" onClick={onToggle} title={logicLabel}>{intersection ? "\u2227" : "\u2228"}</span>,
  };
}


const QueryParamSingle = (props: { attribute: Attribute, entity: EntityDescription, search: Attribute[], uri: string, value: string, index: number, params: QueryParameter[], allowMultiple: boolean, dispatch: (ce: ChangeEvent) => void }): ReactElement | null => {

  const attribute = props.attribute;
  const value = props.value;
  const index = props.index;

  const attributesUsedByGuards = useMemo(() => {
    const guardAttributes: { [uri: string]: boolean } = {};
    for (const attr of props.search) {
      if (attr.kind === 'http://olyro.de/mondiview/category') {
        for (const value of attr.values) {
          for (const guard of value.guards) {
            guardAttributes[guard.attribute] = true;
          }
        }
      }
    }
    return props.search.filter(a => guardAttributes[a.uri]);
  }, [props.search]);

  type GuardValue = {
    uri: string;
    value: string;
  }
  const guardValues: string = useMemo(() => {
    return JSON.stringify(
      attributesUsedByGuards.map(a => {
        const value = props.params.find(p => p.uri === a.uri);
        const gv: GuardValue = {
          uri: a.uri,
          value: value ? value.value : ""
        }
        return gv;
      })
    );
  }, [props.params, attributesUsedByGuards]);

  const matchGuard = useCallback((guards: CategoryEntryGuard[]): boolean => {
    if (guards.length === 0) return true;
    const guardValuesParsed = JSON.parse(guardValues) as GuardValue[];
    return guards.some(g => {
      const param = guardValuesParsed.find(gv => gv.uri === g.attribute);
      if (param === undefined || param.value === "") {
        return true;
      } else {
        return param.value === g.value;
      }
    });
  }, [guardValues]);

  const categoryValues: CategoryEntry[] = useMemo(() => {
    const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });
    const categoryValues = !attribute || attribute.kind !== "http://olyro.de/mondiview/category" ? [] : attribute.values.filter(v => matchGuard(v.guards) && v.label.toLocaleLowerCase().indexOf(value.toLowerCase()) !== -1);
    categoryValues.sort((a, b) => collator.compare(a.label, b.label));
    return categoryValues;
  }, [attribute, value, matchGuard]);

  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  const shortenRef = (ref: string): string => {
    const parts = ref.split("/");
    return parts[parts.length - 1];
  }

  const userSearchable = attribute.searchOrder !== undefined

  const input = attribute.hideInputFromSearch ? null : (() => {
    switch (attribute.kind) {
      case 'http://olyro.de/mondiview/number':
        if (attribute.searchSpan) {
          const [from, to] = value.split('-')
          return <span>
            {translate("spanFrom")}
            <input value={from || ""} onChange={e => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: e.target.value + "-" + to })} type="number" />
            {translate("spanTo")}
            <input value={to || ""} onChange={e => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: from + "-" + e.target.value })} type="number" />
          </span>;
        } else {
          return <input value={value} onChange={e => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: e.target.value })} type="number" />;
        }
      // handle reference a string for now. This is not perfect, but should be enough for now
      case 'http://olyro.de/mondiview/string':
      case 'http://olyro.de/mondiview/copyText':
      case 'http://olyro.de/mondiview/reference':
        return props.entity.referenceAttribute === attribute?.uri && !userSearchable ?
          <span>{shortenRef(value)}</span> :
          <input value={value} onChange={e => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: e.target.value })} readOnly={!userSearchable} />;
      case 'http://olyro.de/mondiview/category': return <Autosuggest
        shouldRenderSuggestions={() => true}
        suggestions={categoryValues}
        onSuggestionsFetchRequested={() => { }}
        onSuggestionsClearRequested={() => { }}
        getSuggestionValue={id => id.label || id.value}
        renderSuggestion={s => <div>{s.label}</div>}
        inputProps={({
          placeholder: "",
          value: value,
          onChange: (_, newValue) => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: newValue.newValue })
        })}
      />
      case 'http://olyro.de/mondiview/substringSearchText':
        return <input value={value} onChange={e => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: e.target.value })} />;
      case 'http://olyro.de/mondiview/entity':
        return <input value={value} onChange={e => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: e.target.value })} readOnly={!userSearchable} />;
      case 'http://olyro.de/mondiview/pdf':
      case 'http://olyro.de/mondiview/imageCollection':
      case 'http://olyro.de/mondiview/htmlContent':
      case 'http://olyro.de/mondiview/htmlImageCollection':
        return null;
      case 'http://olyro.de/mondiview/boolean': {
        const nextValue = value === "" ? "true" : value === "true" ? "false" : "";
        const display = value === "true" ? "✔" : value === "false" ? "✘" : "";
        return <button
          className="booleanFilter"
          title={value === "true" ? translate("boolean_true") : value === "false" ? translate("boolean_false") : translate("boolean_any")}
          onClick={() => props.dispatch({ kind: "ChangeParameterValue", index: index, newValue: nextValue })}
        >{display}</button>;
      }
      default: return assertNever(attribute);
    }
  })();

  return <>
    {input}
    <div onClick={() => props.dispatch({ kind: "RemoveParameter", index: index })} className="searchClose">{removeIcon}</div>
    {props.allowMultiple && userSearchable && !attribute.hideInputFromSearch ? <div onClick={() => {
      const addParam = addParameter(props.search, props.dispatch);
      addParam(attribute.uri);
    }} className="searchClose">{addIcon}</div> : null}
  </>;
}

const removeIcon = <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
  <line x1="3" y1="3" x2="13" y2="13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
  <line x1="13" y1="3" x2="3" y2="13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
</svg>

const addIcon = <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
  <line x1="8" y1="3" x2="8" y2="13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
  <line x1="3" y1="8" x2="13" y2="8" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
</svg>

type ChangeEvent = {
  kind: "AddParameter";
  param: QueryParameter;
} | {
  kind: "ChangeParameterValue";
  index: number;
  newValue: string;
} | {
  kind: "RemoveParameter";
  index: number;
} | {
  kind: "Set";
  parameters: QueryParameter[]
}

const reduce = (qs: QueryParameter[], ce: ChangeEvent): QueryParameter[] => {
  switch (ce.kind) {
    case "AddParameter": return qs.concat([ce.param]);
    case "RemoveParameter": return qs.filter((_, i) => i !== ce.index);
    case "ChangeParameterValue": {
      const newArr = qs.concat([]);
      newArr.splice(ce.index, 1, { ...qs[ce.index], value: ce.newValue });
      return newArr;
    }
    case "Set": return ce.parameters;
    default: return assertNever(ce);
  }
}
