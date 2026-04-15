import { faBook, faGear, faTrashAlt } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { Fragment, ReactElement, useContext, useEffect, useState } from "react"
import * as Api from '../../api'
import { TailSpin } from "react-loader-spinner";
import { abortMerging, getMerginStatus, MergedDocStatus, queueDocsToMerge, CustomCover as CC } from "../../DedicatedBackend";
import Translation from "../../translation/Translation";
import LangContext from "../app/App";

import './booklet.scss'
import { Attribute } from "../../model/attribute";
import { QueueDoc, usePrintQueue } from "../../hooks/usePrintQueue";
import { PrintSettings, Settings } from "./Settings";
import { assertNever } from "../../util";
import { CustomCover } from "./CustomCover";

export const settingsKey = "corpus-monodicum-settings"

export const Booklet = ({ isEditor }: { isEditor: boolean }): ReactElement => {

  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  const { docs, addOrRemove, move, clear } = usePrintQueue();
  const [dragging, setDragging] = useState<{ id: string, oldIndex: number } | null>(null)
  const [mergin, setMerging] = useState<string>("");
  const [mergeId, setMergeId] = useState<string>("");
  const [headers, setHeaders] = useState<Attribute[]>([])
  const [over, setOver] = useState<string>("");
  const [settingsDialog, setSettingsDialog] = useState<boolean>(false);
  const [coverDialog, setCoverDialog] = useState<boolean>(false);
  const [printSettings, setPrintSettings] = useState<PrintSettings>(getDefaultPrintSettings());
  const [customCover, setCustomCover] = useState<CC | null>(null);

  useEffect(() => {
    const getAndSetEntitys = async () => {
      const res = await Api.getEntityDescription("http://monodicum/document", langContext.lang);
      if (res) {
        const headers: Attribute[] = res.attributes.filter(a => a.headerOrder !== undefined).sort((a, b) => a.headerOrder! - b.headerOrder!);
        setHeaders(headers);
      }
    }
    getAndSetEntitys();
  }, [langContext])

  const doDrop = (index: number) => {
    if (dragging !== null) {
      move(dragging.id, index);
    }
    setDragging(null);
  }

  const dragStart = (index: number, uri: string) => {
    setDragging({ id: uri, oldIndex: index });
  }

  const abortCreation = async (id: string) => {
    await abortMerging(id);
    setMerging("");
  }

  const createPdf = async () => {
    const convertAndDownload = (base64: string, filename: string) => {
      const binaryString = window.atob(base64);
      const binaryLen = binaryString.length;
      const bytes = new Uint8Array(binaryLen);
      for (let i = 0; i < binaryLen; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes]);
      const link = document.createElement('a');
      const href = window.URL.createObjectURL(blob);
      link.href = href;
      link.download = filename;
      document.body.appendChild(link);
      link.setAttribute("type", "hidden");
      link.click();
    }

    if (docs && docs.length > 0) {
      setMerging("Sende Anfrage");
      const res = await queueDocsToMerge(docs.map(d => d.uri), printSettings, customCover);
      if (!res) {
        window.alert("Zu viele Anfragen. Bitte warten Sie einen Moment und versuchen Sie es erneut.");
      } else {
        setMergeId(res);
        while (true) {
          await new Promise(r => setTimeout(r, 200));
          const status = await getMerginStatus(res);
          if (status.kind === "Done") {
            convertAndDownload(status.data, "Merged.pdf");
            break;
          } else if (status.kind === "NotFound") {
            window.alert("Dokument nicht gefunden.");
            break;
          } else {
            setMerging(stringifyMergeStatus(status));
          }
        }
      }
      setMerging("");
    }
  }

  const renderRow = (doc: QueueDoc, index: number) => {
    return <tr
      className={(dragging !== null && over === doc.uri) ? "drop-zone" : ""}
      key={index}
      draggable={true}
      onDragStart={() => dragStart(index, doc.uri)}
      onDragOver={(e) => { setOver(doc.uri); e.stopPropagation(); e.preventDefault(); }}
      onDrop={() => doDrop(index)}>

      {headers.map((h, i) => <td key={i}>{doc.data[h.uri]}</td>)}
      <td className="tool" onClick={() => addOrRemove([doc])}><FontAwesomeIcon icon={faTrashAlt} /></td>
      {renderLink(doc)}
    </tr>
  }

  const getURL = (uri: string): string => {
    const id = uri.substring(uri.lastIndexOf('/') + 1);
    return "" + window.location.origin + "/d/" + id;
  }

  const renderLink = (doc: QueueDoc) => {
    return <td className="openLink">
      <a href={getURL(doc.uri)}>
        {translate("goTo")}
      </a>
    </td>
  }

  const onApproveNewSettings = (settings: PrintSettings) => {
    setPrintSettings(settings);
    setSettingsDialog(false);
  }

  return <div className="booklet">
    <div className="top-tool">
      <button className={"synopsis-button " + (docs.length === 0 ? "disabled" : "")} onClick={() => createPdf()}>{translate("print_document")}</button>
      <button className="synopsis-button" onClick={() => clear()}>{translate("clearAll")}</button>
      <FontAwesomeIcon className="settings-icon" icon={faGear} onClick={() => setSettingsDialog(true)} />
      {!isEditor ? null :
        <FontAwesomeIcon className="settings-icon" icon={faBook} onClick={() => setCoverDialog(true)} />
      }
    </div>
    <div className="doc-count">
      <span>{translate("printBook")}</span>
      {docs.length} {translate("documents")}
    </div>
    <table className="search-output-table">
      <tbody>
        <tr>
          <th></th>
          {headers.map((h, i) => <th key={i}>{h.label}</th>)}
          <th onClick={() => clear()}><FontAwesomeIcon icon={faTrashAlt} /></th>
          <th>{translate("go_to")}</th>
        </tr>
        {docs.map((d, i) => renderRow(d, i))}
      </tbody>
    </table>
    {!mergin ? null : <div className="merge-loader">
      <div className="content">
        <div className="title">Pdf-Erstellung</div>
        Status: {mergin}
        <TailSpin height="200" width="200" />
        <button className="synopsis-button" onClick={() => abortCreation(mergeId)}>{translate("cancel")}</button>
      </div>
    </div>}
    {
      settingsDialog ? <Fragment>
        <div className="dialog-background" ></div>
        <Settings onCancel={() => setSettingsDialog(false)} onOk={onApproveNewSettings} settings={printSettings} />
      </Fragment> : null
    }
    {
      coverDialog ? <div className={"cover-dialog " + (coverDialog ? "open" : "closed")} >
        <CustomCover
          customCover={customCover}
          setCustomCover={setCustomCover}
          onClose={() => setCoverDialog(false)}
        />
      </div> : null
    }
  </div>

}

export const getDefaultPrintSettings = (): PrintSettings => {
  return {
    paperWidthInMm: 210,
    paperHeightInMm: 297,
    marginTopInMm: 14,
    marginRightInMm: 20,
    marginBottomInMm: 14,
    marginLeftInMm: 20,
    zoomFactor: 0.6,
    paratextWordPadding: 5.0,
    paratextLinePadding: 5.0,
    lineContinuationIndent: 50.0,
    linePartPadding: 16.0,
    linePadding: 5.0,
    liquescentScale: 0.7,
    noteLineJutLeft: 3.0,
    noteLineJutRight: 2.5,
    endOfDocumentPadding: 10.0,
    additionalContainerLevelDifferencePadding: 30.0,
    signatureWidth: 50.0,
    folioTextWidth: 80.0,
    commentMarkerXOffsetRelative: 0.5,
    commentMarkerYOffsetRelative: 0.5,
    drawCommentMarks: false,
    synopsisParatextYPadding: 3.0,
    syllableFontSize: 20.0,
    drawPageNumbers: true,
    drawTableOfContents: true,
    addSourceDescriptions: true,
    addCriticalApparatus: true,
  };
}

const stringifyMergeStatus = (status: MergedDocStatus): string => {
  switch (status.kind) {
    case "Queued":
      return "Warten auf freien Prozess: " + status.atIndex;
    case "Active":
      switch (status.progress.kind) {
        case "StartingUp": return "Starte Prozess";
        case "LoadingNotes": return "Lade Noten: " + status.progress.document;
        case "Layouting": return "(Stage " + status.progress.stage + ") Layouting Seite: " + status.progress.page;
        case "AddingLineNumbers": return "Füge Zeilennummern hinzu";
        case "AddingToc": return "Füge Inhaltsverzeichnis hinzu";
        case "BoxToSvg": return "Box zu SVG: " + status.progress.page;
        case "ConvertingSvgToPdf": return "Konvertiere SVG zu PDF";
        default: return assertNever(status.progress);
      }
    case "Done":
      return "Fertig";
    case "NotFound":
      return "Das Dokument wurde nicht gefunden.";
    default: return assertNever(status);
  }
}
