import { Fragment, ReactElement, useContext, useEffect, useMemo, useState } from 'react';
import { TailSpin } from 'react-loader-spinner';
import * as Api from "../../api";
import { pdfBase, svgBase } from "../../api";
import { useRedirect, useUrlData } from '../../hooks/useUrlData';
import { Attribute, AttributeWithData, CollectionImageHtml, DataOf, DocumentPart, DocumentPosition, HtmlImageCollection, ImageCollection, Kind } from "../../model/attribute";
import { Entity } from "../../model/entity";
import { assertNever, switchOnKind } from "../../util";
import LangContext from '../app/App';
import { PDFViewer } from '../pdf/PDFViewer';
import { StaticContent, StaticRoutes } from '../static/StaticContent';
import { ImageCollectionViewer } from './ImageCollectionViewer';
import { Params } from './Params';
import { getOpenPopupState, Popup, State as PopupState } from './Popup';
import SearchNavigation from './SearchNavigation';
import SidePanel from './SidePanel';
import './View.scss';
import { Allotment } from "allotment";
import "allotment/dist/style.css";
import { CopyableTextField } from './CopyableTextField';

export const allViews = ["Pages", "PagesWithImage", "Image"] as const;
export type viewType = typeof allViews[number];

const anyViewIsAvaialble = (imageCollection: ImageCollection | null): boolean => {
  return allViews.some(viewType => viewIsAvailable(imageCollection, viewType));
}

const viewIsAvailable = (imageCollection: ImageCollection | null, view: viewType): boolean => {
  if (imageCollection) {
    const doc: DocumentPart[] = imageCollection.completeDocument;
    const hasImages = doc.some(d => d.imageURL !== "")
    const hasResolutions = doc.some(d => d.resolutions.length > 0)
    switch (view) {
      case "Pages": return hasResolutions;
      case "PagesWithImage": return hasImages && hasResolutions;
      case "Image": return hasImages;
    }
  } else return false;
}

export function View({ staticRoutes }: { staticRoutes: StaticRoutes }) {
  const [doc] = useUrlData<Params>()('document');
  const [entity, setEntity] = useState<Entity | null>(null);
  const [popupState, setPopupState] = useState<PopupState | null>(null);
  const [sideBar, setSideBar] = useState<boolean>(true);
  const [view, setView] = useState<viewType>("Pages")
  const openDocument = useRedirect<Params>('view');
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    setTimeout(() => {
      const objects = document.querySelectorAll("object");
      objects.forEach(o => {
        o.onload = () => console.log("loaded");
      });
    }, 2000);
  }, []);

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
        setLoading(false);
      })()
    }
    return () => { isCanceled = true }
  }, [doc, langContext])

  const headerAttributes = entity === null ? [] : attributesByPosition(entity.attributes, p => p.header)
  const sidePanelAttributes = entity === null ? [] : attributesByPosition(entity.attributes, p => p.right)
  const priorityColumns = entity === null ? [] : attributesByPosition(entity.attributes, p => p.priority)
  const showStickyColumn = entity != null && priorityColumns.length === 0 && entity.attributes.some(r => r.attribute.documentPosition.sticky !== undefined)

  const imageCollections = entity ? getImageCollections(entity) : []
  const autoOpenIIIF = imageCollections.length > 0
    && imageCollections.every(ic => ic.data.completeDocument.every(dp => dp.resolutions.length === 0))

  const firstImageCollection = imageCollections.length > 0 ? imageCollections[0].data : null

  const openImageViewer = (imageCollection: ImageCollection | null): void => {
    if (viewIsAvailable(imageCollection, "PagesWithImage")) { setView("PagesWithImage") }
    else if (viewIsAvailable(imageCollection, "Image")) { setView("Image") }
  }

  const onDocumentChange = (params: Params) => { setLoading(true); openDocument(params) }

  // firstImageCollections gets a new reference on every redraw, even though
  // entity did not change, which is why it isn't included in the dependency
  // array
  /* eslint-disable-next-line */
  useEffect(() => { if (entity && autoOpenIIIF) openImageViewer(firstImageCollection) }, [entity])

  useEffect(() => {
    window.postMessage({ "doc": doc, "entity": entity });
  }, [doc, entity])

  const hideSidebar = entity ? imageCollectionViewerDisplaysSidebar(entity) : false;
  const showSidePanel = sideBar && !hideSidebar;

  return entity === null ? null : <div className="view-main">
    <Popup
      state={popupState}
      setState={setPopupState}
      title={entity.description.popupTitle || "Kritischer Apparat"}
    >
      {getAttributesForPopup(entity.attributes, view, setView, staticRoutes)}
    </Popup>
    <DocumentHeader entity={entity} attributes={headerAttributes} firstImageCollection={firstImageCollection} openViewer={() => openImageViewer(firstImageCollection)} openPopup={() => setPopupState(getOpenPopupState())} onDocumentChange={onDocumentChange} staticRoutes={staticRoutes} />
    <SearchNavigation onDocumentChange={onDocumentChange} />
    {!loading ?
      <>
        <Allotment separator={true} className={"documentViewer" + (showSidePanel ? " sidebar-active" : " sidebar-Inactive")} defaultSizes={showSidePanel ? [85, 15] : [100]}>
          <Allotment.Pane className={"documentMain" + (showStickyColumn ? " stickyColumn" : "")}>
            <DocumentNavigation entity={entity} attributeData={entity.attributes} onDocumentChange={onDocumentChange} staticRoutes={staticRoutes} />
            {priorityColumns.length > 0
              ? <AttributeRows position={p => p.priority} attributes={priorityColumns} view={view} setView={setView} staticRoutes={staticRoutes} hideSidebar={hideSidebar} />
              : <>
                <AttributeRows position={p => p.main} attributes={entity.attributes} view={view} setView={setView} staticRoutes={staticRoutes} hideSidebar={hideSidebar} />
                {showStickyColumn
                  ? <AttributeRows position={p => p.sticky} attributes={entity.attributes} view={view} setView={setView} className="stickyColumn" staticRoutes={staticRoutes} hideSidebar={hideSidebar} />
                  : null}
              </>}
          </Allotment.Pane>

          {!hideSidebar && (
            <SidePanel entity={entity} sidePanelAttributes={sidePanelAttributes} open={sideBar} setOpen={setSideBar} onDocumentChange={onDocumentChange} staticRoutes={staticRoutes} />
          )}
        </Allotment>
        <SearchNavigation onDocumentChange={onDocumentChange} />
      </>
      : <div className="loader"><TailSpin /></div>
    }
  </div >;
}

interface DocumentHeaderProps {
  entity: Entity,
  attributes: AttributeWithData[],
  firstImageCollection: ImageCollection | null,
  openViewer: () => void,
  openPopup: () => void,
  onDocumentChange: (params: Params) => void,
  staticRoutes: StaticRoutes,
}
const DocumentHeader = ({ entity, attributes, firstImageCollection, openViewer, openPopup, onDocumentChange, staticRoutes }: DocumentHeaderProps): ReactElement => {
  const renderInfoCard = (a: AttributeWithData) => {
    const { value, label, error } = getInlineAttribute(a, staticRoutes, (doc) => onDocumentChange({ document: doc, search: entity.description.uri }));

    return (
      <div key={a.attribute.uri} className="documentInfoCard">
        <label className="name">{label}</label>
        <label className="value">{value || error}</label>
      </div>
    )
  }
  return attributes.length === 0 ? <></> :
    <div className="documentHeader">
      {attributes.map(renderInfoCard)}
      <div className="viewer-button-wrapper">
        {
          !entity.description.openIIIFText ? null :
            <button className="open-viewer" onClick={openViewer} disabled={!(viewIsAvailable(firstImageCollection, "Image") || viewIsAvailable(firstImageCollection, "PagesWithImage"))} >
              {entity.description.openIIIFText || "IIIF-Viewer"}
            </button>
        }
      </div>
      <div className="popup-button-wrapper">
        {
          !entity.description.openPopupText ? null :
            <button className="open-popup" onClick={openPopup}>
              {entity.description.openPopupText || "Popup"}
            </button>
        }
      </div>

    </div>
}

interface DocumentNavigationProps {
  entity: Entity,
  attributeData: AttributeWithData[],
  onDocumentChange: (params: Params) => void,
  staticRoutes: StaticRoutes,
}
const DocumentNavigation = ({ entity, attributeData, onDocumentChange, staticRoutes }: DocumentNavigationProps): ReactElement => {
  const formatNavigationAttr = ({ attr, data }: { attr: Attribute, data: AttributeWithData | undefined }) => {
    if (data && data.data !== undefined) {
      const { value, label, error } = getInlineAttribute(data, staticRoutes, (doc) => onDocumentChange({ document: doc, search: entity.description.uri }))
      return <span key={data.attribute.uri}>{label}{label ? ": " : ""}{value || error}</span>
    } else {
      if (attr.kind === 'http://olyro.de/mondiview/entity') {
        return <button key={attr.uri} className="entityLink noValue" disabled={true}>{attr.label}</button>
      } else {
        return <span key={attr.uri} className="noValue">{attr.label}: -</span>
      }
    }
  }

  const navAttributes = entity.description.attributes
    .filter(r => r.documentPosition.navigation !== undefined)
    .sort((a, b) => a.documentPosition.navigation! - b.documentPosition.navigation!)
    .map(a => {
      return {
        attr: a,
        data: attributeData.find(adata => adata.attribute.uri === a.uri)!
      }
    })

  return <div className="docNavHeader">
    {navAttributes.map(formatNavigationAttr)}
  </div>
}

interface AttributeTableProps {
  position: (pos: DocumentPosition) => number | undefined;
  attributes: AttributeWithData[];
  view: viewType;
  setView: (view: viewType) => void;
  className?: string;
  staticRoutes: StaticRoutes;
  hideSidebar?: boolean;
}

const attributesByPosition = (attributes: AttributeWithData[], position: (pos: DocumentPosition) => number | undefined) => {
  return attributes.filter(r => position(r.attribute.documentPosition) !== undefined)
    .sort((a, b) => position(a.attribute.documentPosition)! - position(b.attribute.documentPosition)!)
}

const AttributeRows = ({ position, attributes, view, setView, className, staticRoutes, hideSidebar }: AttributeTableProps) => {
  const simpleAttributeRow = (a: AttributeWithData, value: string): ReactElement =>
    <p key={a.attribute.uri}><span className="name">{a.attribute.label}:</span> <span className="value">{value}</span></p>

  const getAttributeRow = (a: AttributeWithData): ReactElement => {
    return switchOnKind(a, {
      "http://olyro.de/mondiview/number": (n) => simpleAttributeRow(a, "" + n.data),
      "http://olyro.de/mondiview/string": (s) => simpleAttributeRow(a, s.data),
      "http://olyro.de/mondiview/pdf": (s) => <div key={a.attribute.uri} className="content"><PDFViewer file={getDownloadUrl(s)} textAttributeUri={a.attribute.pdfTextAttribute} /></div>,
      "http://olyro.de/mondiview/category": (s) => simpleAttributeRow(a, s.data),
      "http://olyro.de/mondiview/imageCollection": (i) => {
        if (anyViewIsAvaialble(i.data)) {
          console.log("Rendering image collection for attribute", i.data.completeDocument.length);
          return <div key={a.attribute.uri} className="content"><ImageCollectionViewer completeDocument={i.data.completeDocument} currentView={view} onChangeView={setView} allowFullscreenToggle={true} closable={viewIsAvailable(i.data, 'Pages')} showMetadata={hideSidebar} /></div>
        } else {
          return <span key={a.attribute.uri} />
        }
      },
      "http://olyro.de/mondiview/htmlContent": (i) =>
        <StaticContent key={a.attribute.uri} className="content" innerHtml={i.data} staticRoutes={staticRoutes} />,
      "http://olyro.de/mondiview/htmlImageCollection": (h) => <div key={a.attribute.uri} className="name"><HtmlImageCollectionComponent collection={h.data} staticRoutes={staticRoutes} /></div>,
      "http://olyro.de/mondiview/substringSearchText": (s) => simpleAttributeRow(a, s.data),
      "http://olyro.de/mondiview/entity": (s) => simpleAttributeRow(a, s.data),
      "http://olyro.de/mondiview/copyText": (s) => <CopyableTextField label={s.attribute.label} text={s.data} />,
      "http://olyro.de/mondiview/boolean": (b) => simpleAttributeRow(a, b.data ? "✔" : "✘"),
      "http://olyro.de/mondiview/reference": (a) => getAttributeRow(a.data),
    })
  }

  return (
    <div className={(className || "") + " attributeMain"}>
      {attributesByPosition(attributes, position).map(getAttributeRow)}
    </div>
  )
}


const getAttributesForPopup = (attributes: AttributeWithData[], view: viewType, setView: (view: viewType) => void, staticRoutes: StaticRoutes): ReactElement[] => {
  const getAttributeRow = (a: AttributeWithData): ReactElement => {
    return switchOnKind(a, {
      "http://olyro.de/mondiview/number": (n) => <div key={a.attribute.uri}>{"" + n.data}</div>,
      "http://olyro.de/mondiview/string": (s) => <div key={a.attribute.uri}>{s.data}</div>,
      "http://olyro.de/mondiview/pdf": (s) => <PDFViewer key={a.attribute.uri} file={getDownloadUrl(s)} textAttributeUri={a.attribute.pdfTextAttribute} />,
      "http://olyro.de/mondiview/category": (s) => <div key={a.attribute.uri}>{s.data}</div>,
      "http://olyro.de/mondiview/imageCollection": (i) => {
        if (anyViewIsAvaialble(i.data)) {
          return <div key={a.attribute.uri} className="content"><ImageCollectionViewer completeDocument={i.data.completeDocument} currentView={view} onChangeView={setView} allowFullscreenToggle={true} closable={viewIsAvailable(i.data, 'Pages')} showMetadata={false} /></div>
        } else {
          return <span key={a.attribute.uri} />
        }
      },
      "http://olyro.de/mondiview/htmlContent": (i) =>
        <StaticContent key={a.attribute.uri} innerHtml={i.data} staticRoutes={staticRoutes} />,
      "http://olyro.de/mondiview/htmlImageCollection": (h) => <HtmlImageCollectionComponent key={a.attribute.uri} collection={h.data} staticRoutes={staticRoutes} />,
      "http://olyro.de/mondiview/substringSearchText": () => <div>substring search texts are not supported here</div>,
      "http://olyro.de/mondiview/entity": (s) => <div key={a.attribute.uri}>{s.data}</div>,
      "http://olyro.de/mondiview/copyText": (s) => <CopyableTextField label={s.attribute.label} text={s.data} />,
      "http://olyro.de/mondiview/boolean": (b) => <div key={a.attribute.uri}>{b.data ? "✔" : "✘"}</div>,
      "http://olyro.de/mondiview/reference": (a) => getAttributeRow(a.data)
    })
  }

  return attributesByPosition(attributes, p => p.popup).map(getAttributeRow);
}

const getImageCollections = (entity: Entity) => entity.attributes.flatMap(r => {
  if (r.kind === "http://olyro.de/mondiview/imageCollection") {
    return [r];
  } else return [];
})

const imageCollectionViewerDisplaysSidebar = (entity: Entity): boolean => {
  const imageCollections = getImageCollections(entity);
  if (imageCollections.length === 0) return false;
  
  const hasMultiplePagesWithMetadata = imageCollections.some(ic => {
    const pagesWithMetadata = ic.data.completeDocument.filter(
      page => page.metadata && page.metadata.length > 0
    );
    return pagesWithMetadata.length > 1;
  });
  
  return hasMultiplePagesWithMetadata;
}


const renderSupersript = (value: string) => {
  return value.split(/(\^\S*|_\S*)/).map((part, i) => {
    if (part.startsWith("^")) {
      return <sup key={i}>{part.replace("^", "")}</sup>
    } else if (part.startsWith("_")) {
      return <sub key={i}>{part.replace("_", "")}</sub>
    } else {
      return <Fragment key={i}>{part}</Fragment>;
    }
  })
}

type InlineAttribute =
  { value: ReactElement, label?: ReactElement, error?: never } |
  { error: string, value?: never, label?: never }
export const getInlineAttribute = (attr: AttributeWithData, staticRoutes: StaticRoutes, openDocumentHandler?: (docUri: string) => void): InlineAttribute => {

  const text = (value: string): InlineAttribute => {
    const rendered = renderSupersript(value.trim());
    return { value: <>{rendered}</>, label: <>{attr.attribute.label}</> }
  }

  return switchOnKind(attr, {
    "http://olyro.de/mondiview/number": (n) => text("" + n.data),
    "http://olyro.de/mondiview/imageCollection": () => ({ error: "Image collections can not be displayed in the header" }),
    "http://olyro.de/mondiview/string": (s) => text(s.data),
    "http://olyro.de/mondiview/pdf": (pdf) => ({ value: <a href={getDownloadUrl(pdf)}>{pdf.attribute.label}</a> }),
    "http://olyro.de/mondiview/category": (s) => text(s.data),
    "http://olyro.de/mondiview/htmlContent": (html) => ({ value: <StaticContent innerHtml={html.data} staticRoutes={staticRoutes} /> }),
    "http://olyro.de/mondiview/htmlImageCollection": () => ({ error: "Html Image Collection content can not be displayed in the header" }),
    "http://olyro.de/mondiview/substringSearchText": () => ({ error: "SubstringSearchText content can not be displayed in the header" }),
    "http://olyro.de/mondiview/entity": (e) => {
      if (openDocumentHandler) {
        return { value: <button className="entityLink" onClick={() => openDocumentHandler(e.data)}>{e.attribute.label}</button> }
      } else {
        return text(e.data);
      }
    },
    "http://olyro.de/mondiview/copyText": (s) => ({ value: <CopyableTextField label={s.attribute.label} text={s.data} /> }),
    "http://olyro.de/mondiview/boolean": (b) => text(b.data ? "✔" : "✘"),
    "http://olyro.de/mondiview/reference": (a) => a.data ? getInlineAttribute(a.data, staticRoutes, openDocumentHandler) : { value: <></> },
  })
}

export const getIDfromParams = (doc: string | null): string => doc?.substring(doc.lastIndexOf('/') + 1) || "";

const buildHierarchy = (parts: CollectionImageHtml[], staticRoutes: StaticRoutes): ReactElement[] => {
  const ret: ReactElement[] = [];
  const openElements: ReactElement[] = [];
  let openClass: string | undefined;

  const close = (i: number) => {
    const openCopy = [...openElements];
    if (openClass !== undefined) {
      const newBlock = <div className={openClass} key={i}>{openCopy}</div>;
      ret.push(newBlock);
      openElements.length = 0;
      openClass = undefined;
    }
  }

  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];

    if ("html" in part) {
      const html = <StaticContent className="html-image-collection-part" key={i} innerHtml={part.html} staticRoutes={staticRoutes} />;
      if (openClass !== undefined) {
        openElements.push(html);
      } else {
        ret.push(html);
      }
    } else if ("fileName" in part) {
      const html = <div className="html-image-collection-part" key={i}> <object key={i} type="image/svg+xml" data={svgBase + part.fileName} title="Eintrag" /> </div>;
      if (openClass !== undefined) {
        openElements.push(html);
      } else {
        ret.push(html);
      }
    } else if ("blockStartClass" in part) {
      close(i);
      openClass = part.blockStartClass;
    } else {
      return assertNever(part);
    }
  }

  close(parts.length);

  return ret;
}

export function HtmlImageCollectionComponent(props: { collection: HtmlImageCollection, staticRoutes: StaticRoutes }): ReactElement {
  const hierarchy = useMemo(() => buildHierarchy(props.collection.parts, props.staticRoutes), [props.collection.parts, props.staticRoutes]);
  return <>
    {hierarchy}
  </>;
}


const findAttribute = <X extends Kind>(entity: Entity, kind: X): DataOf<X> | undefined =>
  entity.attributes.find(r => r.kind === kind)?.data as DataOf<X>

export const getPrintViewUrlFromAttributes = (entity: Entity | null): string | undefined => {
  if (entity) {
    const pdfAttr =
      findAttribute(entity, "http://olyro.de/mondiview/imageCollection")?.completeDocument[0]?.pdfUrl;
    if (pdfAttr) {
      return "" + pdfBase + pdfAttr;
    }
  }
}
export const getDownloadUrl = (a: AttributeWithData): string => {
  if (a.kind === "http://olyro.de/mondiview/string" || a.kind === "http://olyro.de/mondiview/pdf") {
    return "" + pdfBase + a.data;
  } else {
    return "";
  }
}
