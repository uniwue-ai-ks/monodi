import React, { MutableRefObject, ReactElement, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { svgBase } from "../../api";
import { DocumentPart, MetadataEntry } from "../../model/attribute";
import { Dragon } from '../dragon/Dragon';
import './ImageCollectionViewer.scss';
import { viewType } from './View';

export const ImageCollectionViewer = (props: { completeDocument: DocumentPart[], currentView: viewType, onChangeView: (view: viewType) => void, allowFullscreenToggle: boolean, closable: boolean, showMetadata?: boolean }): ReactElement => {

  // TODO pass closable to dragon from view
  const pages = props.completeDocument;
  //useEffect(() => console.log("Number of pages:",pages.length), [pages])
  const scrollStateRef = useRef<ScrollState>({ topY: [], bottomY: [], disabled: null });

  const [currentPage, setCurrentPage] = useState<CurrentPage>({ page: 0, setBy: "document-scroll" });
  const [currentWidth, setCurrentWidth] = useState<number>(0);

  const ref = useRef<HTMLDivElement | null>(null);

  const update = () => {
    if (ref.current !== null) {
      const offset = ref.current.offsetWidth;
      if (offset && offset !== currentWidth) {
        setCurrentWidth(offset);
      }
    }
  }

  const filteredIIIfPages = (): DocumentPart[] => {
    if (pages.length > 1) {
      //foliostart is always first page
      const startImageUrl = pages[0].imageURL;
      const otherPages = pages.slice(1, undefined);
      if (otherPages.findIndex(p => p.imageURL === startImageUrl) >= 0) {
        //if foliostart is already included in array return subset without foliostart
        return otherPages;
      }
    }
    return pages
  }

  const getJsonPaths = filteredIIIfPages().map(p => p.imageURL);
  const getLabels = filteredIIIfPages().map(p => p.label);

  useEffect(() => {
    const intHandler = setInterval(update, 250);
    setTimeout(() => update, 0);

    return () => {
      window.clearInterval(intHandler);
    };
  });

  const renderPageView = () => {
    const useGrid = props.showMetadata && pages.some(p => p.metadata && p.metadata.length > 0);

    if (useGrid) {
      return <div className="document-pages">
        <div className={"complete-document-grid"} ref={ref}>
          {groupByMetadata(pages).map(pages =>
            <div className="grid-sub-row" key={pages[0].page}>
              <div>
                {pages.map(i =>
                <DocumentSVGPage page={i} currentPage={currentPage} parentWidth={currentWidth} scrollState={scrollStateRef} />
                )}
              </div>
              <PageMetadata metadata={pages[0].metadata} />
            </div>
          )}
        </div>
      </div>
    } else {
      return <div className="document-pages">
        <div className={"complete-document"} ref={ref}>
          {pages.map(i =>
            <DocumentSVGPage key={i.page} page={i} currentPage={currentPage} parentWidth={currentWidth} scrollState={scrollStateRef} />
          )}
        </div>
      </div>
    }
  }

  const renderDragon = () => {
    return <div className="iifViewer">
      <Dragon
        page={currentPage.page}
        jsons={getJsonPaths}
        currentView={props.currentView}
        labels={getLabels}
        onChangeView={props.onChangeView}
        onPageChange={num => setCurrentPage({ page: num, setBy: "image-viewer-click" })}
        allowFullscreenToggle={props.allowFullscreenToggle}
        closable={props.closable}
      />
    </div>
  }

  const renderPageWithImage = () => {
    return <div className="document-pages-image">
      <div className="complete-document" ref={ref}>
        {pages.map(i => {
          return (<DocumentSVGPage key={i.page} page={i} currentPage={currentPage} parentWidth={currentWidth} scrollState={scrollStateRef} />)
        })}
      </div>
      {renderDragon()}
    </div>
  }

  const renderImage = () => {
    return <div className="document-image">
      {renderDragon()}
    </div>
  }


  const switchView = () => {
    switch (props.currentView) {
      case "Pages": return renderPageView();
      case "PagesWithImage": return renderPageWithImage();
      case "Image": return renderImage();
      default: return (<div>ERROR undefined State</div>)
    }
  }

  if (props.completeDocument.length > 0) {
    return switchView();
  } else {
    return <div className="complete-document" ref={ref}>
      -
    </div>;
  }
}

export interface CurrentPage {
  page: number;
  setBy: "document-scroll" | "image-viewer-click";
}

interface ScrollState {
  topY: number[];
  bottomY: number[];
  disabled: any;
}

interface DocumentPageProps {
  currentPage: CurrentPage;
  page: DocumentPart;
  parentWidth: number;
  scrollState: MutableRefObject<ScrollState>;
}

const DocumentSVGPage = (props: DocumentPageProps): ReactElement => {

  const availableWidths = useMemo(() => props.page ? props.page.resolutions.map(r => r.width) : [0], [props.page]);
  const [svgWidth, setSvgWidth] = useState<number>(Math.min(...availableWidths));
  const ref = useRef<HTMLObjectElement | null>(null);

  const setMinWidth = useCallback((width: number): number => {
    for (let i = 0; i < availableWidths.length; i++) {
      if (availableWidths[i] >= width) {
        return (i - 1 < 0) ? availableWidths[0] : availableWidths[i - 1];
      }
    }
    return availableWidths[availableWidths.length - 1];
  }, [availableWidths])

  const getFileName = (): string => {
    const res = props.page?.resolutions.find(r => r.width === svgWidth);
    return res ? res.fileName : "";
  }

  useEffect(() => {
    setSvgWidth(setMinWidth(props.parentWidth));
  }, [props.parentWidth, svgWidth, setMinWidth])

  if (props.page) {
    const animationClass = props.currentPage.page === props.page.page ? "selected" : ""
    const file = getFileName()
    if (file === "") return <></>
    else return (<object className={animationClass} type="image/svg+xml" data={svgBase + getFileName()} title="Eintrag" ref={ref} />)
  } else {
    return (<span>ERROR Loading Page</span>)
  }
}

interface PageMetadataProps {
  metadata?: MetadataEntry[];
}

const PageMetadata = ({ metadata }: PageMetadataProps): ReactElement => {
  if (!metadata || metadata.length === 0) {
    return <div className="page-metadata-empty"></div>;
  }
  
  return (
    <div className="page-metadata">
      {metadata
        .sort((a, b) => a.index - b.index)
        .map(entry => (
          <div key={entry.index} className="metadata-entry">
            <span className="metadata-label">{entry.label}:</span>
            <span className="metadata-value">{entry.value}</span>
          </div>
        ))
      }
    </div>
  );
}

/**
 * Groups document pages for display with metadata sections. Each page that has metadata
 * starts a new group, while consecutive pages without metadata are grouped together
 * with it and will share the metadata display from the preceding metadata page.
 * 
 * Example: [P1(meta), P2, P3, P4(meta), P5] -> [[P1(meta), P2, P3], [P4(meta), P5]]
 */
const groupByMetadata = (pages: DocumentPart[]): DocumentPart[][] => {
  const groups: DocumentPart[][] = [];

  for (const p of pages) {
    if (p.metadata) {
      groups.push([p]);
    } else {
      if (groups.length === 0) {
        groups.push([]);
      }
      groups[groups.length - 1].push(p);
    }
  }

  return groups;
}
