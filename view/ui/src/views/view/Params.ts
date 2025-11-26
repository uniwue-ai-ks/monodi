import { QueryParameter } from '../../hooks/useQuery';
import { Attribute } from "../../model/attribute";

export interface Params {
  "document": string | null;
  "search": string | null;
}

export interface SearchNavParams {
  results: Params[];
  link: string;
  querys: QueryParameter[] | null;
  attributes: Attribute[];
}
