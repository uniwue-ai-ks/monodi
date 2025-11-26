package de.olyro.monodi
package exprt

import zio.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets

import de.olyro.monodi.data.notes.{
  BaseNote,
  Comment,
  Grouped,
  NonSpaced,
  Note,
  NoteType,
  Spaced,
  Syllable,
  SyllableType,
}

object SvgTest extends zio.ZIOAppDefault:
  val svg                = Svg.defaultSvg
  val useSystemFontNames = false

  override def run: ZIO[Any, Nothing, Int] =
    for
      out <- ZIO.attemptBlocking(
               Files.newOutputStream(
                 Paths.get("/tmp/out.html"),
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING,
               ),
             ).orDie
      _   <- ZIO.attemptBlocking(out.write(htmlString(examples.mkString("<br/><br/><br/>")).getBytes(StandardCharsets.UTF_8))).orDie
      _   <- ZIO.attemptBlocking(out.close).orDie
    yield 0

  def htmlString(content: String) =
    s"""
       |<html>
       |<head>
       |<style></style>
       |</head>
       |<body>
       |  $content
       |""".stripMargin('|')

  val examples: List[Example] = List(
    Example(
      "Simple Text",
      TextBox.normal("aAáäåÁÄÅȩ", useSystemFontNames),
    ),
    Example(
      "Simple Shrink",
      TextBox.normal("Ag", useSystemFontNames).shrinkToFullY,
    ),
    Example(
      "Real Shrink",
      BoundingBox(
        100,
        100,
        List(Sub(Point(50, 50), Left(TextBox.normal("Ag", useSystemFontNames)))),
      ).shrinkToFullY,
    ),
    Example(
      "Unicode Small Caps",
      TextBox.normal("Cᴏʀᴘᴜs Mᴏɴᴏᴅɪᴄᴜᴍ", useSystemFontNames),
    ),
    Example(
      "Simple concatYFull",
      BoundingBox.concatYFull(
        List(
          TextBox.normal("A", useSystemFontNames),
          TextBox.normal("B", useSystemFontNames),
        ),
        10,
      ),
    ),
    Example(
      "Sub- and Superscript concatX",
      BoundingBox.concatX(
        List(
          TextBox.normal("8", useSystemFontNames),
          TextBox.raw("th", None, TextBox.Formatting.normal.superscript.withUseSystemFontNames(useSystemFontNames), false),
          TextBox.normal(" ", useSystemFontNames),
          TextBox.normal("H", useSystemFontNames),
          TextBox.raw("2", None, TextBox.Formatting.normal.subscript.withUseSystemFontNames(useSystemFontNames), false),
          TextBox.normal("O x", useSystemFontNames),
          TextBox.raw("0", None, TextBox.Formatting.normal.subscript.withUseSystemFontNames(useSystemFontNames), false),
          TextBox.raw("2n", None, TextBox.Formatting.normal.superscript.withUseSystemFontNames(useSystemFontNames), false),
          TextBox.normal("  T", useSystemFontNames),
          TextBox.raw("lp0", None, TextBox.Formatting.normal.subscript.withUseSystemFontNames(useSystemFontNames), false),
          TextBox.normal("   T", useSystemFontNames),
          TextBox.raw("lp0", None, TextBox.Formatting.normal.superscript.withUseSystemFontNames(useSystemFontNames), false),
        ),
        0,
      ),
    ),
    Example(
      "Sub- and Superscript parse",
      TextBox.normal("8^th H_2 O x_0^2n  T_lp0   T^lp0", useSystemFontNames),
    ),
    Example(
      "Sub- and Superscript small caps",
      TextBox.smallCaps("Harald^CnG lug_und Trug aAáäåÁÄÅȩ", useSystemFontNames),
    ),
    Example(
      "Sub- and Superscript italic",
      TextBox.italic("Harald^CnG lug_und Trug aAáäåÁÄÅȩ", useSystemFontNames),
    ),
    Example(
      "Sharp",
      sharp,
    ),
    Example(
      "Line with Tie",
      smallNotes,
    ),
    Example(
      "Line with Tie fit full",
      smallNotes.shrinkToFullY,
    ),
    Example(
      "complex concatYFull",
      BoundingBox.concatYFull(
        List(
          TextBox.normal("Ag", useSystemFontNames),
          smallNotes,
        ),
        0,
      ),
    ),
    Example(
      "syllable spacing default",
      multipleSyllablesWithMoreNotesThanText,
    ),
  )

  def sharp      =
    svg.drawLineParts(
      List(
        Syllable(
          "",
          "blar",
          Spaced(
            List(
              NonSpaced(
                List(
                  Grouped(
                    List(
                      Note("", NoteType.Sharp, BaseNote.A, false, 5, false),
                      Note("", NoteType.Normal, BaseNote.A, false, 5, false),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SyllableType.Normal,
        ),
      ),
      FontKind.Normal,
      Nil,
    )
  def smallNotes =
    svg.drawLineParts(
      List(
        Syllable(
          "",
          "blar",
          Spaced(
            List(
              NonSpaced(
                List(
                  Grouped(
                    List(
                      Note("", NoteType.Normal, BaseNote.A, false, 5, false),
                      Note("", NoteType.Normal, BaseNote.A, false, 5, false),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SyllableType.Normal,
        ),
      ),
      FontKind.Normal,
      Nil,
    )

  def multipleSyllablesWithMoreNotesThanText =
    svg.drawLineParts(
      List(
        Syllable(
          "s1",
          "A",
          Spaced(
            List(
              NonSpaced(
                List(
                  Grouped(
                    List(
                      Note("s1n1", NoteType.Normal, BaseNote.A, false, 4, false),
                    ),
                  ),
                  Grouped(
                    List(
                      Note("s1n2", NoteType.Normal, BaseNote.A, false, 4, false),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SyllableType.Normal,
        ),
        Syllable(
          "s2",
          "A",
          Spaced(
            List(
              NonSpaced(
                List(
                  Grouped(
                    List(
                      Note("s2n1", NoteType.Normal, BaseNote.A, false, 4, false),
                    ),
                  ),
                  Grouped(
                    List(
                      Note("s2n2", NoteType.Normal, BaseNote.A, false, 4, false),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SyllableType.Normal,
        ),
        Syllable(
          "s3",
          "A",
          Spaced(
            List(
              NonSpaced(
                List(
                  Grouped(
                    List(
                      Note("s3n1", NoteType.Normal, BaseNote.A, false, 4, false),
                    ),
                  ),
                  Grouped(
                    List(
                      Note("s3n2", NoteType.Normal, BaseNote.A, false, 4, false),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SyllableType.Normal,
        ),
      ),
      FontKind.Normal,
    List(Comment("s2n2", "s3n1", "", None, None, None)))

  final case class Example(
      description: String,
      bb: BoundingBox,
  ):
    override def toString: String =
      s"""
         |<div class="example">
         |    <div class="header">${description}</div>
         |    <div class="image">${svg.makeSVG(bb, "")}</div>
         |</div>
         |""".stripMargin('|')
