import { Fragment, ReactElement, useContext, useEffect, useState } from 'react';
import { useUrlData, useUrlDataWithDefault } from "../../hooks/useUrlData";

import * as Api from "../../api";
import { svgBase } from "../../api";
import { getSynopsis } from '../../DedicatedBackend';
import { Entity } from "../../model/entity";
import Translation from '../../translation/Translation';
import { classNames, encodeStringAsUTF8Base64 } from "../../util";
import LangContext from "../app/App";
import { getInlineAttribute } from '../view/View';
import './Synopsis.scss';
import { StaticRoutes } from '../static/StaticContent';

export const Synopsis = ({ staticRoutes }: { staticRoutes: StaticRoutes }): ReactElement => {
  const [uris] = useUrlData<Params>()("uris");
  const [searchUri] = useUrlData<Params>()("searchUri");

  const [entities, setEntities] = useState<Entity[] | null>(null);
  const [filter, setFilter] = useUrlDataWithDefault<Params>()("filter", "");
  const defaultFilter = useUrlDataWithDefault<Params>()("filter", "");
  const [acceptedFilter, setAcceptedFilter] = useState<string | undefined>(undefined);
  const [singleLineMode, setSingleLineMode] = useUrlDataWithDefault<Params>()("singleLine", false);
  const [textInLastDocOnly, setTextInLastDocOnly] = useUrlDataWithDefault<Params>()("textInLastDocOnly", false);

  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  const [svgState, setSvgState] = useState<string[][]>([]);
  const firstDefaultFilter = defaultFilter !== null ? defaultFilter[0] : null;

  useEffect(() => {
    let canceled = false;
    const backendIds = uris?.map(u => u.substring("http://monodicum/".length));

    if (backendIds) {
      const filterToUse = acceptedFilter === undefined && firstDefaultFilter ? firstDefaultFilter.trim() : acceptedFilter;

      getSynopsis(backendIds, filterToUse, singleLineMode, textInLastDocOnly)
        .then(svgState => {
          if (!canceled) {
            setSvgState(svgState);
          }
        });
    }

    return () => { canceled = true };
  }, [uris, acceptedFilter, singleLineMode, textInLastDocOnly, firstDefaultFilter]);

  const acceptFilter = () => {
    if (filter.trim().length > 0) {
      setAcceptedFilter(filter.trim());
    } else {
      setAcceptedFilter(undefined);
    }
  };

  const filterBar =
    <div className="filter">
      <div className="filter-help-text">
        {translate("filterHelp1")}<br />
        {translate("filterHelp2")}<br />
      </div>
      <div className="filter-input">
        <input className="filter-input-box" value={filter} onChange={e => setFilter(e.target.value)} />
        <button className="filterButton" onClick={acceptFilter}>{translate("filter")}</button>
      </div>
      <div className="checkboxes">
        <input type="checkbox"
          id="single-line-checkbox"
          onChange={e => setSingleLineMode(e.target.checked)}
          checked={singleLineMode} />
        <label htmlFor="single-line-checkbox">{translate("singleLine")}</label>

        <input type="checkbox"
          id="text-in-last-doc-only-checkbox"
          onChange={e => setTextInLastDocOnly(e.target.checked)}
          checked={textInLastDocOnly} />
        <label htmlFor="text-in-last-doc-only-checkbox">{translate("textInLastDocOnly")}</label>
      </div>
    </div>;

  useEffect(() => {
    let isCanceled = false;

    if (uris && searchUri) {
      (async () => {
        const s = await Api.getEntityDescription(searchUri, langContext.lang);
        const e = await Promise.all(uris.map(uri => Api.getEntity(s, uri, langContext.lang)));

        if (!isCanceled) {
          setEntities(e);
        }
      })()
    }

    return () => { isCanceled = true }
  }, [searchUri, uris, langContext]);

  return <div className="synopsis-main">
    {
      searchUri === null || uris === null ? <span>{translate("wrongInput")}</span> :
        entities === null ? <span>{translate("loading")}</span> : <Fragment>
          <div className="attributes">
            {
              entities.map((entity, i) =>
                <div className="entity" key={i}>
                  <div className="header">{i + 1}</div>
                  <div className="content">
                    <table><tbody>{
                      entity.attributes
                        .sort((a, b) => a.attribute.documentPosition.synopsis! - b.attribute.documentPosition.synopsis!)
                        .map(attr => ({ attr: attr, ...getInlineAttribute(attr, staticRoutes) }))
                        .filter(({ attr, error }) => error === undefined && attr.attribute.documentPosition.synopsis !== undefined)
                        .map(({ attr, label, value, error }) => <tr key={attr.attribute.uri}>
                          <td>{label}</td>
                          <td className="value">{value || error}</td>
                        </tr>)
                    }</tbody></table>
                  </div>
                </div>
              )
            }
          </div>

          {filterBar}
          <div className="synopsis-part">{
            svgState.map((synRow, index) =>
              <div className="row-content" key={`syn-row-${index}`}>
                {
                  synRow.map((part, index) =>
                    <Fragment key={index}>
                      <div className="identifier">{entities ? getSynopsisId(entities[index], staticRoutes) : "--"}</div>
                      <div className={classNames({ content: true, empty: !part })}>
                        {!part || part === "null"
                          ? ""
                          : <object data={"data:image/svg+xml;base64," +
                            encodeStringAsUTF8Base64(part
                              .replaceAll("tnr.ttf", svgBase + "tnr.ttf")
                              .replaceAll("cr.ttf", svgBase + "cr.ttf"))}>Synopsis Part</object>
                        }
                      </div>
                    </Fragment>
                  )
                }
              </div>
            )
          }</div>
        </Fragment>
    }
  </div>;
};

const getSynopsisId = (e: Entity, staticRoutes: StaticRoutes): ReactElement => {
  const attr = e.attributes.find(a => a.attribute.documentPosition.synopsisId);
  if (attr) {
    const { value, error } = getInlineAttribute(attr, staticRoutes)
    return value || <>{error}</>;
  } else {
    return <>--</>;
  }

}

export interface Params {
  itemsPerPage: number;
  page: number;
  uris: string[];
  searchUri: string;
  filter: string;
  singleLine: boolean;
  textInLastDocOnly: boolean;
}
