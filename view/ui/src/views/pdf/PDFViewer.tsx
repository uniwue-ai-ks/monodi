import { find, findLast, head, indexOf, last, range } from 'lodash';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { TextItem, TextMarkedContent } from 'pdfjs-dist/types/src/display/api';
import { useCallback, useContext, useRef, useState } from 'react';
import { Document, Page } from 'react-pdf';
import { CustomTextRenderer } from 'react-pdf/dist/cjs/shared/types';
import 'react-pdf/dist/esm/Page/AnnotationLayer.css';
import 'react-pdf/dist/esm/Page/TextLayer.css';
import { useResizeObserver } from 'usehooks-ts';
import Translation from '../../translation/Translation';
import LangContext from '../app/App';
import { SearchNavParams } from '../view/Params';
import './PDFViewer.scss';

(async () => {
  const { pdfjs } = await import("react-pdf");
  pdfjs.GlobalWorkerOptions.workerSrc = new URL(
    "pdfjs-dist/build/pdf.worker.min.mjs",
    import.meta.url
  ).toString();
})();

type Size = {
  width?: number
  height?: number
}
const zoom_levels = [10, 25, 30, 50, 75, 100, 125, 150, 200];

export function PDFViewer({ file, textAttributeUri }: { file: string, textAttributeUri?: string }) {
  const [numPages, setNumPages] = useState(0);
  const [pageNumber, setPageNumber] = useState(1);
  const [matchedPages, setMatchedPages] = useState<number[]>([]);
  const [zoomLevel, setZoomLevel] = useState<number>(100); // Default zoom level (100%)
  const langContext = useContext(LangContext);
  const translate = (key: string): string => Translation.getTranslation(key, langContext.lang, langContext.overrides);
  const ref = useRef<HTMLDivElement>(null);

  const [width, setWidth] = useState<number>(0)
  const onResize = (size: Size) => {
    // this prevents flickering from a scrollbar changing the size
    if (size.width && size.width !== width && Math.abs(size.width - width) > 50) {
      setWidth(size.width)
    }
  }
  useResizeObserver({ ref, onResize });

  // Calculate actual display width based on container width and zoom level
  const displayWidth = width * (zoomLevel / 100);

  const textQueries = textAttributeUri ? getTextQueries(textAttributeUri) : undefined;

  const onDocumentLoadSuccess = useCallback((pdf: PDFDocumentProxy) => {
    setNumPages(pdf.numPages);
    if (textQueries) {
      Promise.all(
        range(1, pdf.numPages + 1).map(async (page) => {
          const p = await pdf.getPage(page);
          const { items } = await p.getTextContent();
          return { page: page, hasMatch: pdfTextContains(items, textQueries) };
        })
      ).then(searchResults => searchResults.flatMap(({ page, hasMatch }) => hasMatch ? [page] : []))
        .then(matches => { setMatchedPages(matches); setPageNumber(head(matches) || 1) })
    } else {
      setPageNumber(1);
    }
  }, [setNumPages, setMatchedPages, setPageNumber, textQueries])


  const onDocumentLoadError = (_: Error) => {
    setNumPages(0);
    setPageNumber(0);
  }

  const changePage = useCallback((offset: number) => setPageNumber(prevPageNumber => prevPageNumber + offset), [setPageNumber])

  const previousPage = useCallback(() => changePage(-1), [changePage])

  const nextPage = useCallback(() => changePage(1), [changePage])

  const previousMatch = useCallback(() => {
    const next = findLast(matchedPages, (i) => i < pageNumber)
    if (next) setPageNumber(next)
  }, [pageNumber, setPageNumber, matchedPages])

  const nextMatch = useCallback(() => {
    const next = find(matchedPages, (i) => i > pageNumber)
    if (next) setPageNumber(next)
  }, [pageNumber, setPageNumber, matchedPages])

  // Zoom functionality

  const zoomIn = useCallback(() => {
    setZoomLevel(prevZoom => {
      const nextHigherZoom = zoom_levels.find(z => z > prevZoom);
      return nextHigherZoom || prevZoom;
    });
  }, [setZoomLevel]);

  const zoomOut = useCallback(() => {
    setZoomLevel(prevZoom => {
      const nextLowerZoom = findLast(zoom_levels, z => z < prevZoom);
      return nextLowerZoom || prevZoom;
    });
  }, [setZoomLevel]);

  const resetZoom = useCallback(() => {
    setZoomLevel(100);
  }, [setZoomLevel]);

  const handleZoomChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setZoomLevel(parseInt(e.target.value, 10));
  }, [setZoomLevel]);


  const customTextRenderer = textQueries ? queryRenderer(textQueries) : undefined;

  const currentMatchPage = indexOf(matchedPages, pageNumber)
  const paginator = <>
    <div className="pdf-pagination">
      {numPages > 1 && <>
        <button type="button" disabled={pageNumber <= 1} onClick={previousPage}>
          {translate('pdfPrevPage')}
        </button>
        <span>{translate('page')}: <input style={{ width: `${Math.ceil(Math.log10(numPages)) + 2}ex` }} type="number" value={pageNumber} onChange={e => setPageNumber(e.target.valueAsNumber)} /> / {numPages}</span>
        <button type="button" disabled={pageNumber >= numPages} onClick={nextPage} >
          {translate('pdfNextPage')}
        </button>
      </>
      }

      <div className="pdf-zoomcontrols">

        <button type="button" onClick={zoomOut} disabled={zoomLevel <= zoom_levels[0]}>
          {translate('shrink')}
        </button>
        <select value={zoomLevel} onChange={handleZoomChange}>
          {zoom_levels.map(level => (
            <option key={level} value={level}>{level}%</option>
          ))}
        </select>
        <button type="button" onClick={zoomIn} disabled={zoomLevel >= zoom_levels[zoom_levels.length - 1]}>
          {translate('enhance')}
        </button>
        <button type="button" onClick={resetZoom} disabled={zoomLevel === 100}>
          {translate('pdf_zoom_reset')}
        </button>
      </div>
    </div>
    {textQueries &&
      <div className="pdf-pagination">
        <br />
        <button type="button" disabled={pageNumber <= (head(matchedPages) || pageNumber)} onClick={previousMatch}>
          {translate('pdfPrevPageMatch')}
        </button>
        {currentMatchPage > -1
          ? <span>{translate('matchedPage')}: {currentMatchPage + 1} / {matchedPages.length}</span>
          : <span>{translate('noMatchHere')}</span>
        }
        <button type="button" disabled={pageNumber >= (last(matchedPages) || pageNumber)} onClick={nextMatch} >
          {translate('pdfNextPageMatch')}
        </button>
      </div>}
  </>

  return (
    <>
      {paginator}
      <div ref={ref} className="container pdfviewer">
        <Document
          file={file}
          onLoadSuccess={onDocumentLoadSuccess}
          onLoadError={onDocumentLoadError}
        >
          <Page
            width={displayWidth}
            customTextRenderer={customTextRenderer}
            pageNumber={pageNumber} />
        </Document>
      </div>
      {paginator}
    </>
  );
}

const getTextQueries = (textAttributeUri: string): RegExp | undefined => {
  const searchSessionString = sessionStorage.getItem("monodicumSearch");
  if (searchSessionString) {
    const searchSession: SearchNavParams = JSON.parse(searchSessionString);
    const textSearches = searchSession.querys?.filter(q => q.uri === textAttributeUri)
    if (textSearches?.length) {
      return new RegExp(textSearches
        .map(q => q.value)
        .join("|"), "ig")
    }
  }
}
const queryRenderer = (textQueries: RegExp): CustomTextRenderer =>
  ({ str }) => str.replace(textQueries, value => `<mark class="search-result">${value}</mark>`)

const pdfTextContains = (items: (TextItem | TextMarkedContent)[], textQueries: RegExp) =>
  items.find((value) => 'str' in value ? textQueries.test(value.str) : false)
