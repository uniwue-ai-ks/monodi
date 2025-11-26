package de.olyro.monodi
package exprt

import zio.test.*
import zio.test.Assertion.*
import java.net.URI

object TurtleSpec extends ZIOSpecDefault {
  import Turtle.*

  def spec = suite("Turtle")(
    test("a complex document should be stringified correctly") {
      val statement = Statement(
        Ref(new URI("a")),
        (
          Ref(new URI("b")),
          Blank(
            (Ref(new URI("c")), LString("hallo")),
            (Ref(new URI("d")), LInt(3)),
            (Ref(new URI("e")), LBoolean(false)),
            (
              Ref(new URI("f")),
              Blank(
                (Ref(new URI("g")), LString("hallo")),
                (Ref(new URI("h")), LInt(3)),
                (Ref(new URI("i")), LBoolean(true))
              )
            )
          )
        )
      )

      assert(stringify(statement))(equalTo(expected))
    },
    test("a complex document should be stringified correctly 2") {
      val statement = Statement(
        Ref(new URI("a")),
        (
          Ref(new URI("b")),
          Blank(
            (Ref(new URI("c")), LString("hallo")),
            (Ref(new URI("d")), LInt(3)),
            (Ref(new URI("e")), LBoolean(false)),
            (
              Ref(new URI("f")),
              Blank(
                (Ref(new URI("g")), LString("hallo")),
                (Ref(new URI("h")), LInt(3)),
                (Ref(new URI("i")), LBoolean(true))
              )
            )
          )
        ),
        (
          Ref(new URI("b")),
          Blank(
            (Ref(new URI("c")), LString("hallo")),
            (Ref(new URI("d")), LInt(3)),
            (Ref(new URI("e")), LBoolean(false)),
            (
              Ref(new URI("f")),
              Blank(
                (Ref(new URI("g")), LString("hallo")),
                (Ref(new URI("h")), LInt(3)),
                (Ref(new URI("i")), LBoolean(true))
              )
            )
          )
        )
      )

      assert(stringify(statement).replace(' ', '_'))(equalTo(expected2.replace(' ', '_')))
    }
  )

  val expected = s"""<a> <b> [
  |    <c> ${"\"\"\""}hallo${"\"\"\""};
  |    <d> 3;
  |    <e> false;
  |    <f> [
  |        <g> ${"\"\"\""}hallo${"\"\"\""};
  |        <h> 3;
  |        <i> true;
  |    ];
  |].""".trim().stripMargin('|')

  val expected2 = s"""<a> <b> [
  |        <c> ${"\"\"\""}hallo${"\"\"\""};
  |        <d> 3;
  |        <e> false;
  |        <f> [
  |            <g> ${"\"\"\""}hallo${"\"\"\""};
  |            <h> 3;
  |            <i> true;
  |        ];
  |    ];
  |    <b> [
  |        <c> ${"\"\"\""}hallo${"\"\"\""};
  |        <d> 3;
  |        <e> false;
  |        <f> [
  |            <g> ${"\"\"\""}hallo${"\"\"\""};
  |            <h> 3;
  |            <i> true;
  |        ];
  |    ].""".trim().stripMargin('|')
}
