package de.olyro.monodi
package exprt
package iiif

import zio.test.*
import zio.test.Assertion.*

object ExprSpec extends ZIOSpecDefault:
  def spec = suite("IIIF-Expr")(
    test("a literal should give back a function that only returns the literal"):
      val num    = scala.util.Random.nextInt()
      val actual = Scope.runExpr(num.toString).toOption.get
      assert(actual)(equalTo(num))
    ,
    test("it should be possible to write a literal with a plus or minus"):
      val actual = Scope.runExpr("-1").toOption.get
      assert(actual)(equalTo(-1))
    ,
    test("simple addition, subtraction, multiplication and division should work"):
      val operators = List[(String, (Int, Int) => Int)](
        ("+", _ + _),
        ("-", _ - _),
        ("*", _ * _),
        ("/", _ / _),
        ("%", _ % _)
      )

      val numbers  = LazyList.continually(scala.util.Random.nextInt()).filter(_ != 0)
      val operands = numbers.zip(numbers)

      val expected = operators.map(_._2).zip(operands).map((f, ops) => f.tupled(ops))
      val actual   = operators
        .map(_._1)
        .zip(operands)
        .map((s, ops) => Scope.runExpr(s"${ops._1} $s ${ops._2}").toOption.get)

      assert(actual)(equalTo(expected))
    ,
    test("just the identifier i should return whichever number is passed in"):
      val num    = scala.util.Random.nextInt()
      val actual = Scope.runExpr("i", "i" -> num).toOption.get
      assert(actual)(equalTo(num))
    ,
    test("a expression should automatically use normal precedence rules"):
      val actual = Scope.runExpr("i + 3 * 4", "i" -> 1).toOption.get
      assert(actual)(equalTo(13))
    ,
    test("as nested exrpression should work"):
      val actual = Scope.runExpr("(2 * ((i + 3) * 4))/2", "i" -> 1).toOption.get
      assert(actual)(equalTo(16))
    ,
    test("piecewise should work"):
      val values   = List(
        10,
        100,
        1000,
        10000
      )
      val expected = List(
        11,
        102,
        1003,
        10004
      )

      val actual = values.map(i =>
        Scope
          .runExpr("piecewise(i / 2, (0, 9, i + 1), (10, 99, i + 2), (100, 999, i + 3), i + 4)", "i" -> i)
          .toOption
          .get
      )

      assert(actual)(equalTo(expected))
  )
