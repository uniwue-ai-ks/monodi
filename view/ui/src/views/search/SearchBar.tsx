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

  const addParameter = (uri: string): void => {
    const attribute = props.search.find(a => a.uri === uri);
    if (attribute) {
      dispatch({ kind: "AddParameter", param: { uri: attribute.uri, value: "" } });
    }
  }
  useEffect(() => {
    props.search.filter(a => a.initialSearch && !urlState?.some(qp => qp.uri === a.uri)).forEach(attribute => {
      dispatch({ kind: "AddParameter", param: { uri: attribute.uri, value: "" } });
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
      <select data-intro={translate("introSearch2")} className="simple-select" value="" onChange={e => { addParameter(e.target.value) }}>
        <option value="" disabled hidden>{translate("search_criteria")}</option>
        {
          props.search.filter(a => a.searchOrder !== undefined && !isHidden(a.uri)).map(a =>
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

    const inputs = props.values.map(({ value, index }) => <QueryParamSingle
      key={index}
      attribute={attribute}
      entity={props.entity}
      search={props.search}
      uri={props.uri}
      value={value}
      index={index}
      params={props.params}
      dispatch={props.dispatch} />);

    const intersection = getIntersection(attribute.uri);
    const translate = (key: string): string => {
      return Translation.getTranslation(key, langContext.lang, langContext.overrides);
    }
    const logicLabel = intersection ? translate("allOf") : translate("anyOf")

    const toggleButton = inputs?.length > 1 ? <>
      <br />
      <button className="intersectionToggle" onClick={() => setIntersection(attribute.uri, !intersection)}>
        {logicLabel}
      </button>
    </> : null;

    const logicSymbol = inputs?.length > 1 ? <span className="logicSymbol" onClick={() => setIntersection(attribute.uri, !intersection)} title={logicLabel}>
      {intersection ? "\u2227" : "\u2228"}
    </span> : null;

    return !inputs ? null : <div className={"searchQueryInput " + uriToClasName(attribute.uri)}>
      <div className="searchLabel">
        {attribute.label}
        {toggleButton}
      </div>
      {logicSymbol}
      <div className="searchValues">
        {inputs}
      </div>
    </div>
  } else {
    return null;
  }
}


const QueryParamSingle = (props: { attribute: Attribute, entity: EntityDescription, search: Attribute[], uri: string, value: string, index: number, params: QueryParameter[], dispatch: (ce: ChangeEvent) => void }): ReactElement | null => {

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

  const input = (() => {
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
      default: return assertNever(attribute);
    }
  })();

  return input === null ? null : <>
    {input}
    <div onClick={() => props.dispatch({ kind: "RemoveParameter", index: index })} className="searchClose">x</div>
    {userSearchable ? <div onClick={() => props.dispatch({ kind: "AddParameter", param: { uri: attribute.uri, value: "" } })} className="searchClose">+</div> : null}
  </>;
}

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
