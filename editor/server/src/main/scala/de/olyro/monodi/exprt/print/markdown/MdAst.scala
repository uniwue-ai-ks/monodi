package de.olyro.monodi.exprt.print.markdown

import org.commonmark.node.*
import de.olyro.monodi.exprt.print.markdown.MdBlock.*

final case class MdAst(blocks: List[MdBlock]) derives CanEqual

object MdAst:
  private class MkAstVisitor extends AbstractVisitor:
    private var blockAccu: List[MdBlock]             = Nil
    private var openBlock: Option[MdBlock]           = None
    private var textCreators: List[String => MdText] = List(MdText.MdNormal(_))
    def blocks: List[MdBlock]                        = blockAccu.reverse

    private def pushText(text: MdText): Unit =
      openBlock = openBlock match
        case None                          => Some(MdParagraph(List(text)))
        case Some(MdParagraph(texts))      => Some(MdParagraph(texts :+ text))
        case Some(MdHeading(level, texts)) => Some(MdHeading(level, texts :+ text))
        case Some(MdList(ordered, items))  =>
          Some(MdList(ordered, if items.isEmpty then List(List(text)) else items.init :+ (items.last :+ text)))

    override def visit(node: Text): Unit =
      val text = textCreators.head(node.getLiteral)
      pushText(text)

    override def visit(node: Heading): Unit =
      openBlock match
        case None =>
          openBlock = Some(MdHeading(node.getLevel, Nil))
          visitChildren(node)
          blockAccu = openBlock.get :: blockAccu
          openBlock = None
        case _    => visitChildren(node)

    override def visit(node: Paragraph): Unit =
      openBlock match
        case None =>
          openBlock = Some(MdParagraph(Nil))
          visitChildren(node)
          blockAccu = openBlock.get :: blockAccu
          openBlock = None
        case _    => visitChildren(node)

    override def visit(node: Emphasis): Unit =
      textCreators = ((text: String) => MdText.MdEmph(text)) :: textCreators
      visitChildren(node)
      textCreators = textCreators.tail

    override def visit(node: StrongEmphasis): Unit =
      textCreators = ((text: String) => MdText.MdStrongEmph(text)) :: textCreators
      visitChildren(node)
      textCreators = textCreators.tail

    override def visit(node: Image): Unit =
      val altText = Option(node.getFirstChild())
        .collect:
          case t: Text => t.getLiteral()
        .filter(_.nonEmpty)
      val text    = MdText.MdImage(altText, node.getDestination)
      pushText(text)

    override def visit(node: OrderedList): Unit =
      openBlock match
        case None =>
          openBlock = Some(MdList(ordered = true, Nil))
          visitChildren(node)
          blockAccu = openBlock.get :: blockAccu
          openBlock = None
        case _    => visitChildren(node)

    override def visit(node: BulletList): Unit =
      openBlock match
        case None =>
          openBlock = Some(MdList(ordered = false, Nil))
          visitChildren(node)
          blockAccu = openBlock.get :: blockAccu
          openBlock = None
        case _    => visitChildren(node)

    override def visit(node: ListItem): Unit =
      openBlock = openBlock match
        case Some(MdList(ordered, items)) => Some(MdList(ordered, items :+ Nil))
        case _                            => openBlock
      visitChildren(node)

    override def visit(node: CustomNode): Unit =
      openBlock match
        case None =>
          openBlock = Some(MdParagraph(Nil))
          visitChildren(node)
          blockAccu = openBlock.get :: blockAccu
          openBlock = None
        case _    => visitChildren(node)

  def fromDocument(document: Node): MdAst =
    val visitor = new MkAstVisitor
    document.accept(visitor)
    MdAst(visitor.blocks)
