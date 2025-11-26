import { useContext } from "react";
import LangContext from '../app/App';
import Translation from '../../translation/Translation';
import './CopyableTextField.scss';

export const CopyableTextField = ({ text, label, className }: { text: string, label?: string, className?: string }) => {
  const copyText = () => navigator.clipboard.writeText(text);
  const langContext = useContext(LangContext);
  const translate = (key: string): string => Translation.getTranslation(key, langContext.lang, langContext.overrides);

  return <div className={className}>
    {label ? <div className="copy-text-label">{label}</div> : <></>}
    <div className="copy-text">
      <input type={"text"} readOnly={true} value={text}></input>
      <button className="copy-text"
        onClick={() => copyText()}
        title={translate("copy")}>
        <svg xmlns="http://www.w3.org/2000/svg" className="icon icon-tabler icon-tabler-clipboard" width="24" height="24" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor" fill="none" strokeLinecap="round" strokeLinejoin="round">
          <path stroke="none" d="M0 0h24v24H0z" fill="none"></path>
          <path d="M9 5h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2h-2"></path>
          <rect x="9" y="3" width="6" height="4" rx="2"></rect>
        </svg>
      </button>
    </div>
  </div>
}
