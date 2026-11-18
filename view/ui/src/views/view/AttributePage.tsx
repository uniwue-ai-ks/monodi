import { useContext, useEffect, useState } from 'react';
import { TailSpin } from 'react-loader-spinner';
import * as Api from "../../api";
import { useUrlData } from '../../hooks/useUrlData';
import { Entity } from "../../model/entity";
import LangContext from '../app/App';
import { StaticContent, StaticRoutes } from '../static/StaticContent';
import './AttributePage.scss';

export interface AttributePageParams {
  "document": string | null;
  "attribute": string | null;
}

// Dedicated page for a single attribute marked with :documentPosition
// [ :ownPage n ]. Reached via the extra per-row links in the result table.
export function AttributePage({ staticRoutes }: { staticRoutes: StaticRoutes }) {
  const [doc] = useUrlData<AttributePageParams>()('document');
  const [attributeUri] = useUrlData<AttributePageParams>()('attribute');
  const [entity, setEntity] = useState<Entity | null>(null);

  const langContext = useContext(LangContext);

  useEffect(() => {
    let isCanceled = false;

    if (doc) {
      (async () => {
        const docSearchName = await Api.getEntityType(doc)
        if (docSearchName) {
          const s = await Api.getEntityDescription(docSearchName, langContext.lang);
          const e = await Api.getEntity(s, doc, langContext.lang);
          if (!isCanceled) {
            setEntity(e);
          }
        }
      })()
    }
    return () => { isCanceled = true }
  }, [doc, langContext])

  if (entity === null) {
    return <div className="loader"><TailSpin /></div>;
  }

  const attribute = entity.attributes.find(a => a.attribute.uri === attributeUri);
  if (!attribute || attribute.kind !== "http://olyro.de/mondiview/htmlContent") {
    return null;
  }

  return <div className="attribute-page-main">
    <StaticContent innerHtml={attribute.data} staticRoutes={staticRoutes} />
  </div>;
}
