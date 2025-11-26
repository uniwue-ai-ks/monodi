import { Attribute } from "./attribute";
import { Switch } from "../api";
import { SubEntity } from "./subentity";

export interface EntityDescription {
  uri: string;
  name: string;
  attributes: Attribute[];
  conditionalOpenText?: Switch;
  openPopupText?: string;
  popupTitle?: string;
  openIIIFText?: string;
  comparableInSynopsis: boolean;
  referenceAttribute?: string;
  referenceClass?: string;
  alternativeLink?: string;
  disablePrintColumn: boolean;
  subEntities?: SubEntity[];
  shortUrlTag?: string;
}
