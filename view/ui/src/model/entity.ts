import { EntityDescription } from "./entity-description";
import { Attribute, AttributeWithData, DataOf, DocumentPosition } from "./attribute";
import { align, chainComparator } from "../util";

export class Entity {
  constructor(public uri: string, public description: EntityDescription, public attributes: AttributeWithData[]) {
    const alignedAttributes = align(description.attributes, attributes, a => a.uri, a => a.attribute.uri, (a, b) => a.localeCompare(b));
    const missingDescription = alignedAttributes.filter(a => a[0] === undefined);

    if (missingDescription.length > 0) {
      throw new Error(`The following descriptions are missing from ${description.uri}: ${missingDescription.map(a => JSON.stringify(a)).join(" ;;; ")}`);
    }

    const posComparator = (key: keyof DocumentPosition) => (a: DocumentPosition, b: DocumentPosition): number => {
      const ak = a[key];
      const bk = b[key];

      return ak === undefined && bk === undefined ? 0 :
        ak === undefined ? 1 :
          bk === undefined ? -1 :
            ak < bk ? -1 :
              ak > bk ? 1 : 0;
    }

    const comp = [posComparator('main'), posComparator('right')]
      .reduce((c1, c2) => chainComparator(c1, c2), posComparator('header'));
    attributes = attributes.sort((a, b) => comp(a.attribute.documentPosition, b.attribute.documentPosition));
  }

  lookupFirst<A extends Attribute>(query: A): DataOf<A["kind"]> | undefined {
    for (const attribute of this.attributes) {
      if (attribute.attribute.uri === query.uri && attribute.kind === query.kind) {
        return attribute.data as any;
      }
    }
  }
}
