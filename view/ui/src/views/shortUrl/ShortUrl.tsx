import './ShortUrl.scss';
import { ReactElement } from 'react';
import { Redirect, useParams } from 'react-router-dom';
import { ShortUrlEntitys } from "../../api";

interface ParamTypes {
  id: string
}

export interface shortState {
  shortEntity: ShortUrlEntitys;
}

export const ShortUrl = (props: shortState): ReactElement => {
  const { id } = useParams<ParamTypes>();

  const buildURL = (): string => {
    if (props.shortEntity) {
      const component = props.shortEntity.uri;
      const url = new URL(component);
      const redirectTo = "/view?document=" + encodeURIComponent('"http://' + url.hostname + '/' + id + '"') + '&search=' + encodeURIComponent('"' + component + '"');
      return redirectTo;
    }
    return "/404"
  }

  return (
    <div>
      <div>
        <a href={buildURL()}>Document</a>
        <Redirect to={buildURL()} />
      </div>
    </div>
  )

}
