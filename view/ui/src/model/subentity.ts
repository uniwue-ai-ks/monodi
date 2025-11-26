import { EntityDescription } from "./entity-description";

export interface SubEntity {
  uri: string;
  label: string;
  referenceAttribute: string;
  description: EntityDescription;
}
