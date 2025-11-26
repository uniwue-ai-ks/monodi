import { ReactElement } from "react";
import * as D from "../../DedicatedBackend"
import { assertNever } from "../../util";
import './custom-cover.scss'

export type Props = {
  customCover: D.CustomCover | null;
  setCustomCover: (cover: D.CustomCover | null) => void;
  onClose: () => void;
}

export const CustomCover = ({ onClose, customCover, setCustomCover }: Props) => {
  const items = customCover?.items || [];

  const addItem = () => {
    if (customCover) {
      setCustomCover({
        ...customCover,
        items: [...customCover.items, newEmptyTextItem]
      })
    } else {
      setCustomCover({
        kind: "LineItemCover",
        items: [newEmptyTextItem]
      })
    }
  }

  const doClose = () => {
    if (items.length === 0) {
      setCustomCover(null);
    }
    onClose();
  }

  return <div className="custom-cover-component">
    <div className="items">
      {
        !customCover ? null : items.map((item, index) => {
          const onUpdate = (item: D.CoverLineItem) => {
            setCustomCover({
              ...customCover,
              items: customCover.items.map((i, iIndex) => iIndex === index ? item : i)
            })
          }

          switch (item.kind) {
            case "Text":
              return <TextItem item={item} onUpdate={onUpdate} key={index} />

            case "VSkip":
              return <VSkipItem item={item} onUpdate={onUpdate} key={index} />

            default: return assertNever(item);
          }
        })
      }
      <button onClick={addItem}>Neues Item</button>
    </div>
    <div className="errors">
      {getErrors(items).map((error, index) => <div className="error" key={index}>{error}</div>)}
    </div>
    <div className="actions">
      <button onClick={() => { setCustomCover(null); onClose(); }}>Cover Löschen</button>
      <button onClick={() => { doClose(); }}>Ok</button>
    </div>
  </div>;
}

const TextItem = ({ item, onUpdate }: { item: D.CoverLineItemText, onUpdate: (item: D.CoverLineItem) => void }): ReactElement => {
  return <div className="text-item">
    {kindSwitch(item.kind, onUpdate)}
    <input type="text" value={item.text} onChange={e => onUpdate({ ...item, text: e.target.value })} />
    <ColorPicker color={item.color || "#000000"} onUpdate={color => onUpdate({ ...item, color })} />
    <Size size={item.size} onUpdate={size => onUpdate({ ...item, size })} />
  </div>
}

const VSkipItem = ({ item, onUpdate }: { item: D.CoverLineItemVSkip, onUpdate: (item: D.CoverLineItem) => void }): ReactElement => {
  return <div className="v-skip-item">
    {kindSwitch(item.kind, onUpdate)}
    <Size size={item.size} onUpdate={size => onUpdate({ ...item, size })} />
  </div>
}

const newEmptyTextItem: D.CoverLineItemText = {
  kind: "Text",
  text: "",
  size: [0.05, "pw"],
  color: "black"
}

const newEmptyVSkipItem: D.CoverLineItemVSkip = {
  kind: "VSkip",
  size: [0.05, "ph"]
}

const kindSwitch = (kind: D.CoverLineItem["kind"], onUpdate: (item: D.CoverLineItem) => void): ReactElement => {
  const doEmit = (kind: D.CoverLineItem["kind"]) => {
    switch (kind) {
      case "Text": onUpdate(newEmptyTextItem); break;
      case "VSkip": onUpdate(newEmptyVSkipItem); break;
      default: assertNever(kind);
    }
  }

  return <select value={kind} onChange={e => doEmit(e.target.value as any)}>
    <option value="Text">Text</option>
    <option value="VSkip">Vertikaler Abstand</option>
  </select>
}

const Size = ({ size, onUpdate }: { size: D.Size, onUpdate: (size: D.Size) => void }) => {
  return <div className="size">
    <input
      type="number"
      value={size[0]}
      onChange={e => onUpdate([e.target.valueAsNumber, size[1]])}
    />
    <select value={size[1]} onChange={e => onUpdate([size[0], e.target.value as any])}>
      <option value="pw">Anteil Seitenbreite</option>
      <option value="ph">Anteil Seitenhöhe</option>
      <option value="sfs">Relativ zur Silbenfontgröße</option>
    </select>
  </div>
}

const ColorPicker = ({ color, onUpdate }: { color: string, onUpdate: (color: string) => void }) => {
  return <input type="color" value={color} onChange={e => onUpdate(e.target.value)} />
}

const getErrors = (items: D.CoverLineItem[]): string[] => {
  return items.flatMap(item => {
    const [min, max] = getMinMaxFromUnit(item.size[1]);
    if (item.size[0] < min) return [`Der Wert ${item.size[0]} ist zu klein für die Einheit ${item.size[1]}`];
    if (item.size[0] > max) return [`Der Wert ${item.size[0]} ist zu groß für die Einheit ${item.size[1]}`];
    return [];
  });
}

const getMinMaxFromUnit = (unit: D.Size[1]): [number, number] => {
  switch (unit) {
    case "pw": return [0.01, 1];
    case "ph": return [0.01, 1];
    case "sfs": return [0.01, 1000];
    default: return assertNever(unit);
  }
}
