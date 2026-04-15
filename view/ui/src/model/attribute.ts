export type Kind =
  "http://olyro.de/mondiview/number" |
  "http://olyro.de/mondiview/imageCollection" |
  "http://olyro.de/mondiview/pdf" |
  "http://olyro.de/mondiview/string" |
  "http://olyro.de/mondiview/category" |
  "http://olyro.de/mondiview/htmlContent" |
  "http://olyro.de/mondiview/htmlImageCollection" |
  "http://olyro.de/mondiview/substringSearchText" |
  "http://olyro.de/mondiview/entity" |
  "http://olyro.de/mondiview/copyText" |
  "http://olyro.de/mondiview/reference" |
  "http://olyro.de/mondiview/boolean";

interface AttributeBase<T extends Kind> {
  kind: T;
  uri: string;
  label: string;
  headerOrder?: number;
  searchOrder?: number;
  searchSpan?: boolean;
  searchPreview?: SearchPreview;
  documentPosition: DocumentPosition;
  alternativeSortAttribute?: string;
  referenceSearch?: string;
  pdfTextAttribute?: string;
  shortenPosition?: ShortenPosition;
  downloadIcon?: string;
  initialSearch: boolean;
  // If set, this value is automatically added to search queries unless the
  // user has provided their own value for this attribute
  implicitQueryValue?: string;
  // If set, prefills the input field with this value when the user adds
  // this attribute as a filter to the search bar
  uiQueryPrefillValue?: string;
  // If true, hides the input field for this attribute in the search bar
  // (the attribute can still be added as a filter)
  hideInputFromSearch?: boolean;
  // If false, the search bar shows only a single input for this attribute
  // (no "add another value" button, no intersection/union toggle).
  // Defaults to false for boolean, true for all other types.
  allowMultipleSearch?: boolean;
}

export interface SearchPreview {
  label: string;
  config: SearchPreviewConfig;
}

export interface SearchPreviewConfig {
  contextKind: "words" | "lines";
  contextCount: number;
  maxDistanceRatio: number;
}

export interface DocumentPosition {
  header?: number;
  right?: number;
  main?: number;
  priority?: number;
  sticky?: number;
  popup?: number;
  synopsisId?: number;
  synopsis?: number;
  download?: number;
  navigation?: number;
}

export interface ShortenPosition {
  header: boolean;
  right: boolean;
  main: boolean;
  priority: boolean;
  sticky: boolean;
  popup: boolean;
  synopsisId: boolean;
  synopsis: boolean;
  download: boolean;
  navigation: boolean;
  results: boolean;
}

export const SHORTEN_POSITION_NONE = {
  header: false,
  right: false,
  main: false,
  priority: false,
  sticky: false,
  popup: false,
  synopsisId: false,
  synopsis: false,
  download: false,
  navigation: false,
  results: false
}

export interface ImageCollection {
  uri: string;
  completeDocument: DocumentPart[];
}

export interface MetadataEntry {
  index: number;
  label: string;
  value: string;
}

export interface DocumentPart {
  page: number;
  imageURL: string;
  label: string;
  pdfUrl: string;
  resolutions: Resolution[];
  metadata?: MetadataEntry[];
};

export interface Resolution {
  fileName: string;
  width: number;
}

export interface HtmlImageCollection {
  uri: string;
  parts: CollectionImageHtml[];
}

export type CollectionImageHtml = {
  orderNumber: number;
  html: string;
} | {
  orderNumber: number;
  fileName: string;
} | {
  orderNumber: number;
  blockStartClass: string;
}

export interface CollectionImage {
  orderNumber: number;
  fileName: string;
  hierarchy: number[];
  searchableTexts: SearchableText[];
}

export interface SearchableText {
  text: string;
  allowPartialMatch: boolean;
}

export interface CategoryEntry {
  value: string;
  label: string;
  guards: CategoryEntryGuard[]
}

export interface CategoryEntryGuard {
  attribute: string;
  value: string;
}

export interface Normalization {
  attribute: string;
  normalizer: string;
}

export type BooleanAttribute = AttributeBase<"http://olyro.de/mondiview/boolean">
export type NumberAttribute = AttributeBase<"http://olyro.de/mondiview/number">
export interface StringAttribute extends AttributeBase<"http://olyro.de/mondiview/string"> {
  normalization?: Normalization;
}
export type HtmlContentAttribute = AttributeBase<"http://olyro.de/mondiview/htmlContent">
export type ImageCollectionAttribute = AttributeBase<"http://olyro.de/mondiview/imageCollection">
export type PDFAttribute = AttributeBase<"http://olyro.de/mondiview/pdf">
export interface CategoryAttribute extends AttributeBase<"http://olyro.de/mondiview/category"> {
  values: CategoryEntry[];
  mapping?: { [key: string]: string };
}
export type HtmlImageCollectionAttribute = AttributeBase<"http://olyro.de/mondiview/htmlImageCollection">
export type SubstringSearchTextAttribute = AttributeBase<"http://olyro.de/mondiview/substringSearchText">
export type EntityAttribute = AttributeBase<"http://olyro.de/mondiview/entity">
export type CopyTextAttribute = AttributeBase<"http://olyro.de/mondiview/copyText">
export interface ReferenceAttribute extends AttributeBase<"http://olyro.de/mondiview/reference"> {
  referenceField: string;
}

export type Attribute = NumberAttribute | StringAttribute | ImageCollectionAttribute | PDFAttribute |
  CategoryAttribute | HtmlContentAttribute | HtmlImageCollectionAttribute | SubstringSearchTextAttribute |
  EntityAttribute | CopyTextAttribute | ReferenceAttribute | BooleanAttribute;

export type DataOf<T extends Kind> =
  T extends "http://olyro.de/mondiview/number" ? number :
  T extends "http://olyro.de/mondiview/imageCollection" ? ImageCollection :
  T extends "http://olyro.de/mondiview/pdf" ? string :
  T extends "http://olyro.de/mondiview/string" ? string :
  T extends "http://olyro.de/mondiview/category" ? string :
  T extends "http://olyro.de/mondiview/htmlContent" ? string :
  T extends "http://olyro.de/mondiview/htmlImageCollection" ? HtmlImageCollection :
  T extends "http://olyro.de/mondiview/substringSearchText" ? string :
  T extends "http://olyro.de/mondiview/entity" ? string :
  T extends "http://olyro.de/mondiview/copyText" ? string :
  T extends "http://olyro.de/mondiview/reference" ? AttributeWithData :
  T extends "http://olyro.de/mondiview/boolean" ? boolean :
  never;

type Tmp<X extends Attribute> = X extends any ? { kind: X["kind"]; attribute: X; data: DataOf<X["kind"]> } : never;
export type DataType = DataOf<Attribute["kind"]>;
export type AttributeWithData = Tmp<Attribute>;
