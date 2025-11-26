import { EntityDescription } from "../model/entity-description";
import { useContext, useEffect, useState } from "react";
import { getEntityDescription, getEntityNames } from "../api";
import LangContext from "../views/app/App";

export const useEntityDescription = (): EntityDescription[] => {
  const [state, setState] = useState<EntityDescription[]>([]);

  const langContext = useContext(LangContext);

  useEffect(() => {
    (async () => {
      const names = await getEntityNames(langContext.lang);
      const descriptions = await Promise.all(names.map(name => getEntityDescription(name.uri, langContext.lang)));
      setState(descriptions);
    })()
  }, [langContext])

  return state;
}
