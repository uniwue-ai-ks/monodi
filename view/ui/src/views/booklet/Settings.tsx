import { faClose } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { ReactElement, useContext, useState } from "react";
import ReactSelect from "react-select";
import Translation from "../../translation/Translation";
import { assertNever } from "../../util";
import LangContext from "../app/App";
import { getDefaultPrintSettings } from "./Booklet";

import './booklet.scss';

export interface SettingsProps {
  onOk: (settings: PrintSettings) => void;
  onCancel: () => void;
  settings: PrintSettings;
}

export interface PrintSettings {
  paperWidthInMm: number;
  paperHeightInMm: number;
  marginTopInMm: number;
  marginRightInMm: number;
  marginBottomInMm: number;
  marginLeftInMm: number;
  zoomFactor: number;
  paratextWordPadding: number;
  paratextLinePadding: number;
  lineContinuationIndent: number;
  linePartPadding: number;
  linePadding: number;
  liquescentScale: number;
  noteLineJutLeft: number;
  noteLineJutRight: number;
  endOfDocumentPadding: number;
  additionalContainerLevelDifferencePadding: number;
  drawCommentMarks: boolean;
  commentMarkerXOffsetRelative: number;
  commentMarkerYOffsetRelative: number;
  synopsisParatextYPadding: number;
  signatureWidth: number;
  folioTextWidth: number;
  syllableFontSize: number;
  drawPageNumbers: boolean;
  drawTableOfContents: boolean;
  addSourceDescriptions: boolean;
  addCriticalApparatus: boolean;
}

interface PageOption {
  width: number;
  height: number;
  value: string;
  label: string;
}

export const BASE_CANVAS_WIDTH = 800.0;

export const Settings = (props: SettingsProps): ReactElement => {
  const [pageLabel, setPageLabel] = useState("A4");

  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  const isCustomOption = !["A3", "A4", "A5"].includes(pageLabel);

  const [settings, setSettings] = useState<PrintSettings>(props.settings);
  const [customPage, setCustomPage] = useState<boolean>(isCustomOption);

  const applySettings = () => {
    props.onOk(settings);
  }

  const onPageSelect = (page: PageOption | null) => {
    if (page) {
      setCustomPage(page.value === "custom");
      setSettings({
        ...settings,
        paperWidthInMm: page.width,
        paperHeightInMm: page.height,
      });
      setPageLabel(page.label);
    }
  }

  const getSelectedPage = (): PageOption => {
    switch (pageLabel) {
      case "A3": return pageOptions[0];
      case "A4": return pageOptions[1];
      case "A5": return pageOptions[2];
      default: return pageOptions[3];
    }
  }

  const renderPropInput = (prop: RenderProp): ReactElement => {
    switch (prop.kind) {
      case "bool":
        return <input type="checkbox" checked={prop.prop.getter(settings)} onChange={e => setSettings(prop.prop.setter(settings, e.target.checked))} />
      case "number":
        return <input type="number" value={prop.prop.getter(settings)} onChange={e => setSettings(prop.prop.setter(settings, +e.target.value))} />
      default:
        return assertNever(prop);
    }
  }

  const renderProp = (prop: RenderProp): ReactElement => {
    return <>
      <label>{translate(prop.prop.translationKey)}</label>
      {renderPropInput(prop)}
    </>;
  }

  const renderArea = (area: Area, isFirst: boolean): ReactElement => {
    const className = area.singleColumn ? "area single-column" : "area";
    const elem = <div className={className}>
      <span className="area-title">{translate(area.translationKey)}</span>
      {area.props.map(prop => renderProp(prop))}
    </div>;

    if (isFirst) {
      return elem;
    } else {
      return <><hr />{elem}</>;
    }
  }

  return <div className="settings-dialog">
    <FontAwesomeIcon className="close-icon" icon={faClose} onClick={() => props.onCancel()} />
    <div className="main">
      <div className="page-setup">
        <span className="settings-titel">{translate("page-setup")}</span>
        <div className="page-selection">
          <label>{translate("page")}</label>
          <ReactSelect className="select-page" value={getSelectedPage()} options={pageOptions} onChange={(e) => onPageSelect(e)} />
          <span className="restore-default" onClick={() => setSettings(getDefaultPrintSettings())}>{translate("restore")}</span>
        </div>
        <div className="page-inputs">
          <label>{translate("page-width")} (mm)</label>
          <input disabled={!customPage} value={settings.paperWidthInMm} type={"number"} min="1" step="1" onChange={(e) => setSettings({
            ...settings,
            paperWidthInMm: +e.target.value
          })} />
          <label>{translate("page-height")} (mm)</label>
          <input disabled={!customPage} value={settings.paperHeightInMm} type={"number"} min="1" step="1" onChange={(e) => setSettings({
            ...settings,
            paperHeightInMm: +e.target.value
          })} />
          <label>{translate("zoom_factor")}</label>
          <div className="zoom-slider">
            <input type="range" value={settings.zoomFactor} step="0.1" min="0.5" max="1.7" onChange={e => setSettings({ ...settings, zoomFactor: +e.target.value })} />
            <span>{Math.round(settings.zoomFactor * 100.0)}%</span>
          </div>
        </div>
      </div>
      {areas.map(a => renderArea(a, false))}
    </div>
    <div className="buttons">
      <button onClick={() => props.onCancel()}>{translate("cancel")}</button>
      <button onClick={() => applySettings()}>{translate("apply")}</button>
    </div>
  </div>
}

class Prop<A>{
  constructor(public translationKey: string, public getter: (ps: PrintSettings) => A, public setter: (ps: PrintSettings, value: A) => PrintSettings) { }

  zoom<K extends keyof A>(k: K): Prop<A[K]> {
    const setter = (ps: PrintSettings, value: A[K]) => {
      const a = { ...this.getter(ps) };
      a[k] = value;
      return this.setter(ps, a);
    }

    const getter = (ps: PrintSettings) => this.getter(ps)[k];

    return new Prop(
      this.translationKey,
      getter,
      setter
    );
  }

  asBooleanProp(): MakeBoolProp<A> {
    return {
      kind: "bool",
      prop: this
    } as never;
  }

  asNumberProp(): MakeNumProp<A> {
    return {
      kind: "number",
      prop: this
    } as never;
  }
}

const idProp = (translationKey: string): Prop<PrintSettings> => new Prop(translationKey, (ps) => ps, (_, value) => value);

type MakeBoolProp<T> = T extends boolean ? RenderProp : void;
type MakeNumProp<T> = T extends number ? RenderProp : void;

type RenderProp = {
  kind: "bool";
  prop: Prop<boolean>;
} | {
  kind: "number";
  prop: Prop<number>;
}

type Area = {
  translationKey: string;
  props: RenderProp[];
  singleColumn: boolean;
}

const areas: Area[] = [
  {
    translationKey: "paperMargin",
    singleColumn: false,
    props: [
      idProp("marginTopInMm").zoom("marginTopInMm").asNumberProp(),
      idProp("marginRightInMm").zoom("marginRightInMm").asNumberProp(),
      idProp("marginBottomInMm").zoom("marginBottomInMm").asNumberProp(),
      idProp("marginLeftInMm").zoom("marginLeftInMm").asNumberProp(),
    ]
  },
  {
    translationKey: "spacing",
    singleColumn: false,
    props: [
      idProp("paratextWordPadding").zoom("paratextWordPadding").asNumberProp(),
      idProp("paratextLinePadding").zoom("paratextLinePadding").asNumberProp(),
      idProp("lineContinuationIndent").zoom("lineContinuationIndent").asNumberProp(),
      idProp("linePartPadding").zoom("linePartPadding").asNumberProp(),
      idProp("linePadding").zoom("linePadding").asNumberProp(),
      idProp("liquescentScale").zoom("liquescentScale").asNumberProp(),
      idProp("noteLineJutLeft").zoom("noteLineJutLeft").asNumberProp(),
      idProp("noteLineJutRight").zoom("noteLineJutRight").asNumberProp(),
      idProp("endDocumentPadding").zoom("endOfDocumentPadding").asNumberProp(),
      idProp("containerLevelPadding").zoom("additionalContainerLevelDifferencePadding").asNumberProp(),
      idProp("signatureWidth").zoom("signatureWidth").asNumberProp(),
      idProp("folioTextWidth").zoom("folioTextWidth").asNumberProp(),
    ]
  },
  {
    singleColumn: true,
    translationKey: "comments",
    props: [
      idProp("drawComments").zoom("drawCommentMarks").asBooleanProp(),
      idProp("commentOffsetX").zoom("commentMarkerXOffsetRelative").asNumberProp(),
      idProp("commentOffsetY").zoom("commentMarkerYOffsetRelative").asNumberProp(),
    ]
  },
  {
    singleColumn: true,
    translationKey: "font-sizes",
    props: [
      idProp("syllable-font-size").zoom("syllableFontSize").asNumberProp(),
    ]
  },
  {
    singleColumn: true,
    translationKey: "supplementarySettings",
    props: [
      idProp("drawPageNumbers").zoom("drawPageNumbers").asBooleanProp(),
      idProp("drawTableOfContents").zoom("drawTableOfContents").asBooleanProp(),
      idProp("addSourceDescriptions").zoom("addSourceDescriptions").asBooleanProp(),
      idProp("addCriticalApparatus").zoom("addCriticalApparatus").asBooleanProp(),
    ]
  }
]

const pageOptions: PageOption[] = [
  { width: 297, height: 420, value: 'A3', label: 'A3' },
  { width: 210, height: 297, value: 'A4', label: 'A4' },
  { width: 148, height: 210, value: 'A5', label: 'A5' },
  { width: 148, height: 210, value: 'custom', label: 'custom' },
];
