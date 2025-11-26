import { synopseBase } from "./api";
import { PrintSettings } from "./views/booklet/Settings";

const backendBase = synopseBase;

export const getSynopsis = async (
  documentIds: string[], search?: string,
  singleLine: boolean = false, textInLastDocOnly: boolean = false
): Promise<string[][]> => {
  const response = await fetch(backendBase + "/synopsis", {
    body: JSON.stringify({ documents: documentIds, search, singleLine, textInLastDocOnly }),
    method: "POST"
  });

  return await response.json();
}

export const queueDocsToMerge = async (
  documentIds: string[],
  printSettings: PrintSettings,
  customCover: CustomCover | null
): Promise<string | null> => {
  const backendIds = documentIds.map(u => u.substring("http://monodicum/".length));
  const response = await fetch(backendBase + "/makePdfQueue", {
    body: JSON.stringify({
      documents: backendIds,
      printSettings: printSettings,
      customCover: customCover
    }),
    method: "POST"
  });

  return await response.json();
}

export const getMerginStatus = async (id: string): Promise<MergedDocStatus> => {
  const response = await fetch(backendBase + "/makePdfStatus", {
    body: JSON.stringify(id),
    method: "POST"
  });

  return await response.json();
}

export const abortMerging = async (id: string): Promise<any> => {
  const response = await fetch(backendBase + "/makePdfAbort", {
    body: JSON.stringify(id),
    method: "POST"
  });

  return await response.json();
}


export type MergedDocStatus = {
  kind: "Queued",
  atIndex: number
} | {
  kind: "Active",
  progress: Progress
} | {
  kind: "Done",
  data: string
} | {
  kind: "NotFound"
}

export type Progress = {
  kind: "StartingUp"
} | {
  kind: "LoadingNotes",
  document: number
} | {
  kind: "Layouting",
  page: number,
  stage: number
} | {
  kind: "AddingLineNumbers"
} | {
  kind: "AddingToc"
} | {
  kind: "BoxToSvg",
  page: number
} | {
  kind: "ConvertingSvgToPdf"
}

export type Size = [number, "pw" | "ph" | "sfs"];

export type CustomCover = {
  kind: "LineItemCover",
  items: CoverLineItem[]
}

export type CoverLineItemText = {
  kind: "Text",
  text: string,
  size: Size,
  color?: string
}

export type CoverLineItemVSkip = {
  kind: "VSkip",
  size: Size
}

export type CoverLineItem =
  CoverLineItemText |
  CoverLineItemVSkip
