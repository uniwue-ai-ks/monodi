package de.olyro.monodi.exprt
package iiif

import scala.meta.*
import cats.syntax.all.*

object Expr:
  def parseString(s: String): Either[String, Scope ?=> Int] =
    val ast = s.parse[Stat].toEither.leftMap(_.toString)
    ast.flatMap(parseInt)

  private type INT     = Scope ?=> Int
  private type BOOLEAN = Scope ?=> Boolean

  private def parseInt(ast: Tree): Either[String, INT] = ast match
    case Lit.Int(n)      => Right(n)
    case Term.Name(name) => Right(Scope.get(name))

    case Term.ApplyInfix.After_4_6_0(lhs, op, Type.ArgClause(Nil), Term.ArgClause(List(rhs), None)) =>
      for
        o <- parseOp(op)
        l <- parseInt(lhs)
        r <- parseInt(rhs)
      yield o(l, r)

    case Term.Apply.After_4_6_0(func, Term.ArgClause(params, None)) =>
      parseFunction(func, params)

    case _ => Left(s"unknown expression: $ast")

  private def parseOp(t: Tree): Either[String, (Int, Int) => Int] =
    t match
      case Term.Name("+") => Right(_ + _)
      case Term.Name("-") => Right(_ - _)
      case Term.Name("*") => Right(_ * _)
      case Term.Name("/") => Right(_ / _)
      case Term.Name("%") => Right(_ % _)
      case _              => Left(s"unknown operator: $t")

  private def parseFunction(t: Tree, params: List[Tree]): Either[String, INT] =
    t match
      case Term.Name("piecewise") => parsePiecewise(params)
      case _                      => Left(s"unknown function: $t")

  private def parsePiecewise(params: List[Tree]): Either[String, INT] =
    if params.size < 2 then Left("piecewise needs at least two arguments")
    else
      val scrutineeTerm = params.head
      val branchTerms   = params.drop(1).dropRight(1)
      val defaultTerm   = params.last

      for
        scrutinee <- parseInt(scrutineeTerm)
        branches  <- branchTerms.traverse(parseBranch)
        default   <- parseInt(defaultTerm)
      yield branches.find(_.matches(scrutinee)) match
        case None         => default
        case Some(branch) => branch.value

  private def parseBranch(branchTerm: Tree): Either[String, Branch] =
    branchTerm match
      case Term.Tuple(List( fromIncTerm, toExTerm, valueTerm)) => 
        for
          fromInc <- parseInt(fromIncTerm)
          toEx    <- parseInt(toExTerm)
          value   <- parseInt(valueTerm)
        yield Branch(fromInc, toEx, value)

      case _ => Left(s"unknown branch: $branchTerm")

  final private class Branch(val fromInc: INT, val toEx: INT, val value: INT):
    def matches(i: INT): BOOLEAN = i >= fromInc && i < toEx
