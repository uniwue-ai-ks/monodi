import { useHistory, useLocation } from 'react-router-dom';
import { useCallback, useRef } from 'react';

// This hook guarantees to return the same object (reference) when the query
// string data doesn't change. It can therefore be used directly in dependency arrays.
// Note that the disabling of the warnings is due to the fact that we need to wrap the result in
// a function without arguments, to get a two-level-type-inference process.
type Keys<T> = keyof T & string;
export const useUrlData = <Params>() => <Name extends Keys<Params>>(parameterName: Name): [Params[Name] | null, (data: Params[Name] | null) => void, (update: ((data: Params[Name] | null) => (Params[Name] | null))) => void] => {
  type Data = Params[Name];
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const location = useLocation();
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const history = useHistory();
  const params = new URLSearchParams(location.search);
  const param = params.get(parameterName);

  // eslint-disable-next-line react-hooks/rules-of-hooks
  const cache = useRef<Cache<Data>>({ stringData: null, data: null });

  if (param !== cache.current.stringData) {
    cache.current.stringData = param;

    if (param === null) {
      cache.current.data = null;
    } else {
      try {
        cache.current.data = JSON.parse(param);
      } catch (error) {
        console.log(error);
        cache.current.data = null;
      }
    }
  }

  // eslint-disable-next-line react-hooks/rules-of-hooks
  const redirect = useCallback((d: Data | null): void => {
    const newParams = new URLSearchParams(location.search);
    if (d === null) {
      newParams.delete(parameterName);
    } else {
      newParams.set(parameterName, JSON.stringify(d));
    }
    const newSearch = `?${newParams.toString()}`;
    if (document.location.search === newSearch) return;
    history.push({ ...document.location, search: newSearch });
  }, [history, parameterName, location]);

  // eslint-disable-next-line react-hooks/rules-of-hooks
  const update = useCallback((update: ((data: Params[Name] | null) => (Params[Name] | null))): void => {
    const nextState = update(cache.current.data);
    redirect(nextState);
  }, [redirect]);

  return [cache.current.data, redirect, update];
}

// the same as the function useUrlData, but returning a default instead of null
export const useUrlDataWithDefault = <Params>() => <Name extends Keys<Params>>(parameterName: Name, def: Params[Name]): [Params[Name], (data: Params[Name] | null) => void] => {
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const [ret, set] = useUrlData<Params>()(parameterName);
  return [ret === null ? def : ret, set];
}

interface Cache<Data> {
  stringData: string | null;
  data: Data | null;
}

export type ParamInput<Params> = {
  [name in keyof Params]: Params[name] | null
}

export function useRedirect<Params extends object>(component: string): (data: ParamInput<Params>) => void {
  const hist = useHistory();
  const location = useLocation();

  return useCallback((data: ParamInput<Params>): void => {
    const newParams = new URLSearchParams(location.search);

    for (const paraName in data!) {
      if (data[paraName] === null) {
        newParams.delete(paraName);
      } else {
        newParams.set(paraName, JSON.stringify(data[paraName]));
      }
    }
    hist.push({ ...location, search: `?${newParams.toString()}`, pathname: `/${component}` });
  }, [hist, component, location]);
}
