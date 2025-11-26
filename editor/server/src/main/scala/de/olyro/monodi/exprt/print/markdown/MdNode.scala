package de.olyro.monodi.exprt.print.markdown

import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableBlock

enum MdNode:
  case MdDocument(children: List[MdNode])
  case MdText(text: String)
  case MdEmphasis(children: List[MdNode])
  case MdStrongEmphasis(children: List[MdNode])
  case MdParagraph(children: List[MdNode])
  case MdHeading(level: Int, children: List[MdNode])
  case MdList(ordered: Boolean, items: List[MdNode])
  case MdListItem(items: List[MdNode])
  case MdImage(altText: Option[String], src: String)
  case MdTable(rows: List[MdNode])
  case MdRow(children: List[MdNode])
  case MdCell(header: Boolean, children: List[MdNode])

  def addChild(child: MdNode): MdNode = this match
    case MdDocument(children)       => MdDocument(children :+ child)
    case MdText(_)                  => sys.error("MdText nodes cannot have children")
    case MdEmphasis(children)       => MdEmphasis(children :+ child)
    case MdStrongEmphasis(children) => MdStrongEmphasis(children :+ child)
    case MdParagraph(children)      => MdParagraph(children :+ child)
    case MdHeading(level, children) => MdHeading(level, children :+ child)
    case MdList(ordered, items)     => MdList(ordered, items :+ child)
    case MdListItem(items)          => MdListItem(items :+ child)
    case MdImage(_, _)              => sys.error("MdImage nodes cannot have children")
    case MdTable(rows)              => MdTable(rows :+ child)
    case MdRow(children)            => MdRow(children :+ child)
    case MdCell(header, children)   => MdCell(header, children :+ child)

object MdNode:
  private class MdNodeMaker extends AbstractVisitor:
    var nodeStack: List[MdNode] = List(MdNode.MdDocument(Nil))

    def addChild(child: MdNode): Unit =
      nodeStack = nodeStack.head.addChild(child) :: nodeStack.tail

    def closeNode(): Unit = nodeStack match
      case n1 :: n2 :: rest =>
        nodeStack = n2.addChild(n1) :: rest
      case _                => sys.error("No node to close")

    def openNode(node: MdNode): Unit =
      nodeStack = node :: nodeStack

    def scoped(mdNode: MdNode, node: Node): Unit =
      openNode(mdNode)
      visitChildren(node)
      closeNode()

    override def visit(node: Text): Unit           = addChild(MdNode.MdText(node.getLiteral))
    override def visit(node: Heading): Unit        = scoped(MdNode.MdHeading(node.getLevel, Nil), node)
    override def visit(node: Paragraph): Unit      = scoped(MdNode.MdParagraph(Nil), node)
    override def visit(node: Emphasis): Unit       = scoped(MdNode.MdEmphasis(Nil), node)
    override def visit(node: StrongEmphasis): Unit = scoped(MdNode.MdStrongEmphasis(Nil), node)
    override def visit(node: OrderedList): Unit    = scoped(MdNode.MdList(ordered = true, Nil), node)
    override def visit(node: BulletList): Unit     = scoped(MdNode.MdList(ordered = false, Nil), node)
    override def visit(node: ListItem): Unit       = scoped(MdNode.MdListItem(Nil), node)
    override def visit(node: Image): Unit          =
      val altText = Option(node.getFirstChild())
        .collect:
          case t: Text => t.getLiteral()
        .filter(_.nonEmpty)
      addChild(MdNode.MdImage(altText, node.getDestination))

    override def visit(node: CustomBlock): Unit =
      node match
        case _: TableBlock => scoped(MdNode.MdTable(Nil), node)
        case _             => visitChildren(node)

    override def visit(node: CustomNode): Unit =
      node match
        case _: TableRow   => scoped(MdNode.MdRow(Nil), node)
        case tc: TableCell => scoped(MdNode.MdCell(tc.isHeader, Nil), node)
        case _             => visitChildren(node)

  def fromDocument(document: Node): MdNode =
    val maker = new MdNodeMaker
    document.accept(maker)
    maker.nodeStack.head
