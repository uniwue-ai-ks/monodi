import { faCaretLeft, IconName } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { Dispatch, SetStateAction, useContext, useEffect } from 'react';
import { useUrlData } from '../../hooks/useUrlData';
import { AttributeWithData } from "../../model/attribute";
import { Entity } from "../../model/entity";
import Translation from '../../translation/Translation';
import LangContext from '../app/App';
import { StaticRoutes } from '../static/StaticContent';
import { CopyableTextField } from './CopyableTextField';
import { Params } from './Params';
import './SidePanel.scss';
import { getDownloadUrl, getIDfromParams, getInlineAttribute, getPrintViewUrlFromAttributes } from './View';

type SidePanelProps = {
  sidePanelAttributes: AttributeWithData[];
  entity: Entity | null;
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>;
  onDocumentChange: (params: Params) => void;
  staticRoutes: StaticRoutes;
}
const SidePanel = ({ sidePanelAttributes, entity, open, setOpen, onDocumentChange, staticRoutes }: SidePanelProps) => {
  const [doc] = useUrlData<Params>()('document');
  const langContext = useContext(LangContext);
  const translate = (key: string): string => Translation.getTranslation(key, langContext.lang, langContext.overrides);

  const openDocumentInfo = () => setOpen(true);

  const renderPrintButton = (url: string | undefined, label: string | undefined = undefined, key: string | undefined = undefined) => {
    if (url !== undefined) {
      return (
        <tr className={"show"} key={key || (getIDfromParams(doc) + "_link")}>
          <td className="short-link-item" colSpan={2}>
            <div><span>{label || translate("printView")}:</span></div>
            <input type="button" onClick={() => goToFile(url)} value={translate("printButton")} />
          </td>
        </tr>
      )
    }
  }

  useEffect(() => { if (sidePanelAttributes.length === 0) setOpen(false) }, [setOpen, sidePanelAttributes.length]);

  if (!entity) return <></>;

  const getSidepanelRow = (a: AttributeWithData) => {
    if (!a.data) {
      return <></>
    } else {
      const { value, label, error } = getInlineAttribute(a, staticRoutes, (doc) => onDocumentChange({ document: doc, search: entity.description.uri }))
      return <div className="entry" key={a.attribute.uri}>{label ? <><span className="name">{label}</span><br /></> : <></>}{value || error}</div>
    }
  }

  if (open) {
    return (
      <div className="documentRight">
        <div className="documentRight-info">
          {
            entity.attributes.filter(r => r.attribute.documentPosition.right !== undefined)
              .sort((a, b) => a.attribute.documentPosition.right! - b.attribute.documentPosition.right!)
              .map(getSidepanelRow)
          }
          {entity && doc && <ShortLinkField entity={entity} doc={doc} />}
          {entity && <DownloadSection attributes={entity.attributes} />}
          {renderPrintButton(getPrintViewUrlFromAttributes(entity))}
        </div>
      </div>
    )
  } else {
    return (
      <div className="documentRight">
        <div className="sticky">
          <div onClick={() => openDocumentInfo()} className="documentRight-maximize" title={translate("foldout")}><FontAwesomeIcon icon={faCaretLeft} /> </div>
        </div>
      </div>
    )
  }
}

const ShortLinkField = ({ entity, doc }: { entity: Entity, doc: string }) => {
  const langContext = useContext(LangContext);
  const label = Translation.getTranslation("doc_link", langContext.lang, langContext.overrides);
  const url =
    entity.description.shortUrlTag?.includes("/")
      ? entity.description.shortUrlTag + "/" + getIDfromParams(doc)
      : "" + window.location.origin + "/" + (entity.description.shortUrlTag || "d") + "/" + getIDfromParams(doc);
  return <CopyableTextField className="short-link-container" text={url} label={label} />;
}

const goToFile = (link: string | undefined) => {
  if (link) window.location.href = link;
}

const DownloadSection = ({ attributes }: { attributes: AttributeWithData[] }) => {
  const downloads = attributes.filter(r => r.attribute.documentPosition.download !== undefined)
  const langContext = useContext(LangContext);
  if (downloads.length == 0) {
    return <></>
  } else {
    return <div className="downloadSection">
      <div>{Translation.getTranslation("downloads", langContext.lang, langContext.overrides)}</div>
      <ul className="downloadList">
        {downloads
          .sort((a, b) => a.attribute.documentPosition.download! - b.attribute.documentPosition.download!)
          .map(a => { console.log("Icon", a.attribute.downloadIcon); return a })
          .map(a =>
            <li key={a.attribute.uri}>
              <button className="downloadButton" onClick={() => goToFile(getDownloadUrl(a))}>
                <>
                  {a.attribute.downloadIcon != undefined && <><FontAwesomeIcon icon={a.attribute.downloadIcon as IconName} />&nbsp;</>}
                  {a.attribute.label}
                </>
              </button>
            </li>
          )
        }
      </ul>
    </div>
  }
}
export default SidePanel;
