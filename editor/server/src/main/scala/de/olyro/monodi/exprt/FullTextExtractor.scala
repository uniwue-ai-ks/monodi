package de.olyro.monodi
package exprt

import de.olyro.monodi.data.notes.*

import java.text.Normalizer
import scala.util.chaining.*

/**
 * Extracts the running full text of one or more documents.
 *
 * The input is always a list of root containers; [[Sources]] picks which kinds
 * of text are pulled out of them, and [[Form]] picks how the collected pieces
 * are rendered. Every form is NFD-normalized.
 */
object FullTextExtractor:

  /** Which kinds of text to pull out of the containers. */
  final case class Sources(
      syllables: Boolean = true,
      folioChanges: Boolean = false,
      paratexts: Boolean = true
  )

  object Sources:
    /** Syllables + paratexts, no folio changes — the historical default. */
    val default: Sources = Sources()

  /** How the collected text pieces are rendered. All forms are NFD. */
  enum Form derives CanEqual:
    /** Pieces joined by single spaces, nothing merged: `"do- mi- nus est"`. */
    case Raw
    /** Syllable hyphens dropped so words are whole: `"dominus est"`. */
    case Words
    /** Lower-cased, spaces and hyphens gone, ASCII only: `"dominusest"`. */
    case Normalized

  def extract(
      roots: List[RootContainer],
      form: Form,
      sources: Sources = Sources.default
  ): String =
    render(pieces(roots, sources), form)

  /** Collects the raw text pieces in document order. */
  private def pieces(roots: List[RootContainer], sources: Sources): List[String] =
    roots.flatMap: root =>
      Container
        .fold[List[String]] {
          case _: RootContainer     => Nil
          case _: FormteilContainer => Nil
          case _: MiscContainer     => Nil
          case p: ParatextContainer => if sources.paratexts then List(p.text) else Nil
          case z: ZeileContainer    =>
            z.children.collect {
              case s: Syllable if sources.syllables       => s.text
              case f: FolioChange if sources.folioChanges => f.text
            }
        }(root)
        .flatten

  private def nfd(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)

  private def render(pieces: List[String], form: Form): String =
    form match
      case Form.Raw        =>
        pieces.mkString(" ").replaceAll("\\s+", " ").pipe(nfd)
      case Form.Words      =>
        pieces.mkString(" ").replace("- ", "").replaceAll("\\s+", " ").pipe(nfd)
      case Form.Normalized =>
        pieces
          .mkString("")
          .toLowerCase
          .replace("-", "")
          .replaceAll("\\s+", "")
          .pipe(nfd)
          .replaceAll("[^\\p{ASCII}]", "")
