package de.olyro.monodi.exprt.print.hierarchy

enum TocEntityId derives CanEqual:
  case Document(docId: String)
  case SourceDescription(sourceId: String)
  case CriticalApparatus(docId: String)
