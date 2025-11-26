package de.olyro.monodi.data.notes

import zio.test.*
import zio.test.Assertion.*

object CutterSpec extends ZIOSpecDefault {
  def spec = suite("Cutter")(
    test("If the UUIDs are not find, the result should be empty") {
      assert(Cutter.cut(testRoot(), "x", "x"))(equalTo(Right(None)))
    },
    test("If the start and end are both on root, the document should not be modified") {
      assert(Cutter.cut(testRoot(), "1", "1"))(equalTo(Right(Some(testRoot()))))
    },
    test("If the start and end are both the same element, only this element should be returned") {
      assert(Cutter.cut(testRoot(), "122", "122"))(equalTo(Right(Some(testRoot(Set("11", "13", "121", "123"))))))
    },
    test("It should work on the same level 1") {
      assert(Cutter.cut(testRoot(), "12", "13"))(equalTo(Right(Some(testRoot(Set("11"))))))
    },
    test("It should work on the same level 2") {
      assert(Cutter.cut(testRoot(), "11", "13"))(equalTo(Right(Some(testRoot()))))
    },
    test("It should work on different levels in the intuitive direction") {
      assert(Cutter.cut(testRoot(), "1", "12"))(equalTo(Right(Some(testRoot(Set("13"))))))
    },
    test("It should work on different levels in the reverse direction") {
      assert(Cutter.cut(testRoot(), "12", "1"))(equalTo(Right(Some(testRoot(Set("11"))))))
    },
    test("Syllables should always be taken whole or not at all") {
      assert(Cutter.cut(testRoot(), "12222", "12222"))(
        equalTo(Right(Some(testRoot(Set("11", "13", "121", "123", "1221", "1223")))))
      )
    }
  )

  def testRoot(exclude: Set[String] = Set.empty[String]): RootContainer = RootContainer(
    uuid = "1",
    children = List("11", "12", "13")
      .filter(id => !exclude.exists(ex => id.startsWith(ex)))
      .map(prefix => testFormteilContainer(exclude)(prefix)),
    comments = Nil,
    documentType = DocumentType.Level1,
    version = None,
    globalComment = None
  )

  def testFormteilContainer(exclude: Set[String])(id: String): FormteilContainer = FormteilContainer(
    uuid = id,
    children = List("1", "2", "3")
      .map(id + _)
      .filter(id => !exclude.exists(ex => id.startsWith(ex)))
      .map(prefix => testZeileContainer(exclude)(prefix)),
    data = Nil
  )

  def testZeileContainer(exclude: Set[String])(id: String): ZeileContainer = ZeileContainer(
    uuid = id,
    children = List("1", "2", "3")
      .map(id + _)
      .filter(id => !exclude.exists(ex => id.startsWith(ex)))
      .map(prefix => testSyllable(prefix))
  )

  def testSyllable(id: String): LinePart = Syllable(
    uuid = id,
    text = "",
    notes = Spaced(
      List(
        NonSpaced(List(Grouped(List(Note(id + "1", NoteType.Normal, BaseNote.A, false, 5, false))))),
        NonSpaced(List(Grouped(List(Note(id + "2", NoteType.Normal, BaseNote.A, false, 5, false))))),
        NonSpaced(List(Grouped(List(Note(id + "3", NoteType.Normal, BaseNote.A, false, 5, false)))))
      )
    ),
    syllableType = SyllableType.Normal
  )

}
