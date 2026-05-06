import { useContext } from 'react';
import { NavLink } from 'react-router-dom';
import { useUrlData } from '../../hooks/useUrlData';
import Translation from '../../translation/Translation';
import LangContext from '../app/App';
import { Params, SearchNavParams } from '../view/Params';
import './SearchNavigation.scss';

const SearchNavigation = ({ onDocumentChange, className }: { onDocumentChange: (params: Params) => void, className?: string}) => {
  const [doc] = useUrlData<Params>()('document');
  const langContext = useContext(LangContext);
  const translate = (key: string): string => Translation.getTranslation(key, langContext.lang, langContext.overrides);
  const searchSessionString = sessionStorage.getItem("monodicumSearch");
  if (searchSessionString) {
    const searchSession: SearchNavParams = JSON.parse(searchSessionString);
    const searchLink = searchSession.link.startsWith("http") ? stripHost(searchSession.link) : searchSession.link
    if (searchSession.results.length > 1) {
      const index = searchSession.results.findIndex(s => s.document === doc);
      const nextDoc = () => {
        if (index + 1 < searchSession.results.length) {
          onDocumentChange({ document: searchSession.results[index + 1].document, search: searchSession.results[index + 1].search })
        }
      }
      const previousDoc = () => {
        if (index - 1 >= 0) {
          onDocumentChange({ document: searchSession.results[index - 1].document, search: searchSession.results[index - 1].search })
        }
      }
      const filters = (): string => {
        if (searchSession.querys) {
          return searchSession
            .querys
            .flatMap(q => {
              const attribute = searchSession.attributes.find(a => a.uri === q.uri);
              if (attribute && q.value.length > 0) {
                return [attribute?.label + "=" + q.value];
              } else {
                return [];
              }
            }).join("; ")
        } else {
          return "";
        }
      }

      return (
        <div className={`searchNavHeader ${className ?? ""}`}>
          <div className="searchNavLabel">
            <span>{translate("searchNav") + " : " + (index + 1) + " von " + searchSession.results.length}</span>
            <div className="selected-filters">
              <span>
                {translate("selectedFilters") + ": "}
              </span>
              <span>
                {filters()}
              </span>
            </div>
          </div>
          <div className="searchNav">
            <button disabled={index === 0} onClick={() => previousDoc()}>{translate("back")}</button>
            <span>-</span>
            <button disabled={index === searchSession.results.length - 1} onClick={() => nextDoc()}>{translate("next")}</button>
          </div>
          <div className="searchNavFilter">
            <NavLink to={searchLink}>{translate("backToSearch")}</NavLink>
          </div>
        </div>
      )
    }
  }
  return (
    <div className="searchNavHeader"></div>
  )
}

const stripHost = (url: string): string => {
  try {
    const parsedUrl = new URL(url);
    return parsedUrl.pathname + parsedUrl.search;
  } catch (_) {
    return url; // If URL parsing fails, return the original string
  }
}
export default SearchNavigation;
