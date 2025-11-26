import { faArrowLeft, faArrowRight, faExpand, faMinus, faPlus, faWindowClose } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import OpenSeadragon from 'openseadragon';
import { useContext, useEffect, useRef, useState } from 'react';
import Translation from '../../translation/Translation';
import LangContext from '../app/App';
import { viewType } from '../view/View';
import './Dragon.scss';

export interface Props {
  onChangeView: (view: viewType) => void;
  onPageChange: (page: number) => void;
  currentView: viewType;
  jsons: string[];
  labels: string[];
  page: number;
  allowFullscreenToggle: boolean;
  closable: boolean;
}

export function Dragon(props: Props) {

  const ref = useRef<OpenSeadragon.Viewer | null>(null);
  const [page, setPage] = useState<number>(props.page);

  const langContext = useContext(LangContext);
  const translate = (key: string): string => {
    return Translation.getTranslation(key, langContext.lang, langContext.overrides);
  }

  useEffect(() => {
    if (document.getElementsByClassName("openseadragon-container").length === 0) {
      ref.current = new OpenSeadragon.Viewer(
        {
          id: "openseadragon1",
          sequenceMode: false,
          showHomeControl: false,
          zoomInButton: "zoom-In",
          zoomOutButton: "zoom-Out",
          tileSources: props.jsons,
        }
      )
    } else {
      ref.current?.open(props.jsons);
    }
  }, [props.jsons])

  useEffect(() => {
    ref.current?.goToPage(page)
  }, [page])

  useEffect(() => {
    setPage(props.page)
  }, [props.page])

  const nextPage = () => {
    const newPage = page + 1;
    if (newPage < props.jsons.length) {
      setPage(newPage);
      props.onPageChange(newPage);
    }
  }

  const previousPage = () => {
    const newPage = page - 1;
    if (newPage >= 0) {
      setPage(newPage);
      props.onPageChange(newPage);
    }
  }

  const closeView = () => {
    props.onChangeView("Pages");
  }

  const toggleFullscreen = () => {
    switch (props.currentView) {
      case "PagesWithImage":
        props.onChangeView("Image");
        break;
      case "Image":
        props.onChangeView("PagesWithImage");
        break;
      default:
        break;
    }
  }

  return (
    <div className="dragon">
      <div className="dragonToolbar">
        <div className="buttons">
          <button onClick={previousPage} className="dragonNavButton" title={translate("back")} disabled={page === 0 ? true : false} ><FontAwesomeIcon icon={faArrowLeft} /> </button>
          <button onClick={nextPage} className="dragonNavButton" title={translate("next")} disabled={page === (props.jsons.length - 1) ? true : false}><FontAwesomeIcon icon={faArrowRight} /> </button>
          <input className="imageLabel" value={props.labels[page]} disabled={true} />
          <button id="zoom-In" className="dragonNavButton" title={translate("enhance")}><FontAwesomeIcon icon={faPlus} /> </button>
          <button id="zoom-Out" className="dragonNavButton" title={translate("shrink")}><FontAwesomeIcon icon={faMinus} /> </button>
          <button className="dragonNavButton" onClick={toggleFullscreen} title={translate("max")} disabled={!props.allowFullscreenToggle}><FontAwesomeIcon icon={faExpand} /> </button>
          {props.closable ? <button className="dragonNavButton" onClick={closeView} title={translate("close")}><FontAwesomeIcon icon={faWindowClose} /> </button> : null}
        </div>
        <div className="dragon-note">
          <span>{translate("externalSource")}</span>
        </div>
        <div className="error-message">
          {props.jsons[page] === "" ? (<span className="empty">{translate("no_Page_data")}</span>) : null}
        </div>
      </div>

      <div id="openseadragon1"></div>
    </div>
  )

}
