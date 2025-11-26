import { useEffect, useState } from "react";

export interface QueueDoc {
  uri: string;
  data: { [key: string]: any };
}

const storageKey = "corpus-monodicum-pdfs"

export interface PrintQueue {
  readonly docs: QueueDoc[];
  readonly add: (toAddDocs: QueueDoc[]) => void;
  readonly remove: (toAddDocs: QueueDoc[]) => void;
  readonly addOrRemove: (toAddDocs: QueueDoc[]) => void;
  readonly move: (uri: string, newIndex: number) => void;
  readonly clear: () => void;
}
export const usePrintQueue: () => PrintQueue = () => {

  const [docs, setDocs] = useState<QueueDoc[]>([]);

  const addOrRemove = (toAddDocs: QueueDoc[]) => {
    console.log("AddOrRemove")
    const newDocs: QueueDoc[] = [...docs];
    toAddDocs.forEach(d => {
      const index = newDocs.findIndex(od => od.uri === d.uri);
      if (index === -1) {
        newDocs.push(d);
      } else {
        newDocs[index] = { ...newDocs[index], uri: "", data: {} };
      }
    })
    const newFilterd = newDocs.filter(d => d.uri !== "");
    writeDocsToStorage(newFilterd);
    setDocs(newFilterd);
  }

  const add = (toAddDocs: QueueDoc[]) => {
    console.log("ADD")
    const newDocs: QueueDoc[] = [...docs];
    toAddDocs.forEach(d => {
      const index = newDocs.findIndex(od => od.uri === d.uri);
      if (index === -1) {
        newDocs.push(d);
      }
    })
    writeDocsToStorage(newDocs);
    setDocs(newDocs);
  }

  const remove = (toAddDocs: QueueDoc[]) => {
    console.log("Remove")
    const newDocs: QueueDoc[] = [...docs];
    toAddDocs.forEach(d => {
      const index = newDocs.findIndex(od => od.uri === d.uri);
      if (index >= 0) {
        newDocs[index] = { ...newDocs[index], uri: "", data: {} };
      }
    })
    const newFilterd = newDocs.filter(d => d.uri !== "");
    writeDocsToStorage(newFilterd);
    setDocs(newFilterd);
  }

  const move = (uri: string, newIndex: number) => {
    const oldIndex = docs.findIndex(d => d.uri === uri);
    if (oldIndex >= 0) {
      const newDocs = [...docs];
      const toMove = docs[oldIndex];
      newDocs.splice(oldIndex, 1);
      newDocs.splice(newIndex, 0, toMove);
      writeDocsToStorage(newDocs);
      setDocs(newDocs);
    }
  }

  const clear = () => {
    setDocs([]);
    writeDocsToStorage([]);
  }

  useEffect(() => {
    setDocs(readDocsFromStorage());
  }, [])

  return { docs, add, remove, addOrRemove, move, clear } as const

}

export const readDocsFromStorage = (): QueueDoc[] => {
  const rawVal = localStorage.getItem(storageKey);
  if (rawVal !== null) {
    let res: QueueDoc[] = [];
    try {
      res = JSON.parse(rawVal);
    } catch (_error) {
      console.error("Unable to parse list of documents from stroage. Reseting storage");
      writeDocsToStorage([]);
    }
    return res;
  }

  return [];
}

export const writeDocsToStorage = (docs: QueueDoc[]) => {
  localStorage.setItem(storageKey, JSON.stringify(docs));
}

export const addDocToStorage = (doc: QueueDoc) => {
  const docs = readDocsFromStorage();
  docs.push(doc);
  writeDocsToStorage(docs);
}
