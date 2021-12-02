package run.cosy.rdfjs.model

import java.math.BigInteger
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

/**
 * Implementation of rdf.js.org [https://rdf.js.org/data-model-spec/ Data Model Specification]
 * with a few (documented) extra tweaks for compatibility with rdflib.js
 */
trait Term[Type <: Term.TT](@JSExport val termType: Type):
	@JSExport("equals")
	def termEquals(other: js.UndefOr[Term[?]]): Boolean =
		other.map(o => this.equals(o)).getOrElse(false)
end Term

trait ValueTerm[Type <: Term.TT] extends Term[Type] :
	val value: String

@JSExportAll
object Term:
	type TT = VTT | Quad.type
	type VTT = NamedNode.type | Literal.type | Variable.type | BlankNode.type | DefaultGraph.type

	val NamedNode: "NamedNode" = "NamedNode"
	val Literal: "Literal" = "Literal"
	val Variable: "Variable" = "Variable"
	val BlankNode: "BlankNode" = "BlankNode"
	val DefaultGraph: "DefaultGraph" = "DefaultGraph"
	val Quad: "Quad" = "Quad"
end Term

@JSExportTopLevel("NamedNode")
@JSExportAll
case class NamedNode(override val value: String) extends
	ValueTerm[Term.NamedNode.type], Term(Term.NamedNode)

@JSExportAll
case class Literal private(
	override val value: String, val language: String, val datatype: NamedNode
) extends ValueTerm[Term.Literal.type], Term(Term.Literal) :
end Literal

object Literal:
	lazy val rdfLangStr = NamedNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")
	lazy val xsdStr = NamedNode("http://www.w3.org/2001/XMLSchema#string")

	def apply(value: String): Literal = new Literal(value, "", xsdStr)

	def apply(value: String, lang: String) = new Literal(value, lang, rdfLangStr)

	def apply(value: String, dataType: NamedNode) = new Literal(value, "", dataType)
end Literal

//blank nodes should be created via the factory
@JSExportAll
case class BlankNode private[rdfjs](val value: String)
	extends ValueTerm[Term.BlankNode.type], Term(Term.BlankNode)

@JSExportTopLevel("Variable")
@JSExportAll
case class Variable(val value: String) extends ValueTerm[Term.Variable.type], Term(Term.Variable)

//rdflib.js states in default-graph-uri.ts
//"Prevents circular dependencies between data-factory-internal and statement"
//Note: 1) I cannot find it being used though 2) it is modelled as a NamedNode
@JSExportTopLevel("DefaultGraph")
@JSExportAll
case class DefaultGraph(val value: String = "chrome:theSession")
	extends ValueTerm[Term.DefaultGraph.type], Term(Term.DefaultGraph)

case class Quad private[rdfjs](
	@JSExport("subject") val subj: Quad.Subject,
	@JSExport("predicate") val rel: Quad.Predicate,
	@JSExport("object") val obj: Quad.Object,
	@JSExport("graph") val graph: Quad.Graph
) extends Term(Term.Quad)

object Quad:
	type Subject = BlankNode | NamedNode
	type Predicate = NamedNode
	type Object = BlankNode | NamedNode | Literal
	type Graph = BlankNode | NamedNode | DefaultGraph

/**
 * sjrd said on Discord:
 * > If you're implementing a JS interface, it's better to declare the interface as a trait Foo extends js.Object and then extend that trait.
Instead of using exports.
 **/
trait DataFactoryI extends js.Object:
	val namedNode: js.Function1[String,NamedNode]
	val literal: js.Function2[String, js.UndefOr[String|NamedNode],Literal]
	val variable: js.Function1[String,Variable]
	val blankNode: js.Function1[js.UndefOr[String],BlankNode]

	import Quad.{Subject,Predicate,Object,Graph}
	val quad: js.Function4[Subject, Predicate, Object, js.UndefOr[Graph],Quad]
	val defaultGraph: js.Function0[DefaultGraph]
	type Nodes = BlankNode | NamedNode | Literal | DefaultGraph | Variable

	/**
	 * Required by rdflib.js which states:
	 * Generates a uniquely identifiably *idempotent* string for the given {term}.
	 * Equivalent to "Term.hashString"x§
	 *
	 * @example Use this to associate data with a term in an object { obj[id(term)] = "myData" }
	 */
	val id: js.Function1[Term[?],String]
end DataFactoryI

class DataFactory(bnodeStart: BigInt = DataFactory.one) extends DataFactoryI:

	private val bnodeCounter: Iterator[BigInt] = new Iterator[BigInt] {
		var nextNode = bnodeStart

		override def hasNext: Boolean = true

		override def next(): BigInt =
			nextNode = nextNode + DataFactory.one
			nextNode
	}
	private val bnode: Iterator[String] = bnodeCounter.map(_.toString(Character.MAX_RADIX))

	override val namedNode: js.Function1[String,NamedNode] =
		value => NamedNode(value)

	override val literal: js.Function2[String, js.UndefOr[String|NamedNode],Literal] =
		(value, tp) => tp.map{
				case lang: String => Literal(value, lang)
				case uri: NamedNode => Literal(value, uri)
			}.getOrElse(Literal(value))

	override val variable: js.Function1[String,Variable] =
		(name) => Variable(name)

	override val blankNode: js.Function1[js.UndefOr[String],BlankNode] =
		label => label.map(lstr =>
			if !lstr.contains('#') then BlankNode(lstr)
			else BlankNode(bnode.next())
		).getOrElse(BlankNode(bnode.next()))

	import Quad.{Subject,Predicate,Object,Graph}
	override val quad: js.Function4[Subject, Predicate, Object, js.UndefOr[Graph],Quad] =
		(subj, pred, obj, graph) =>
			Quad(subj, pred, obj, graph.getOrElse(defaultGraph()))

	override val defaultGraph: js.Function0[DefaultGraph] = () => new DefaultGraph()

	override val id: js.Function1[Term[?],String] = term => term.toString

end DataFactory

//todo: how do frameworks make sure blank nodes don't end up clashing in their store?
@JSExportTopLevel("DataFactory")
object DataFactory:
	val one: BigInt = BigInt(1)

	@JSExport("create")
	def apply(): DataFactory = new DataFactory(one)

object Test:
	def main(args: Array[String]): Unit =
		val bbl = new NamedNode("https://bblfish.net/#me")
		val bbl2 = new NamedNode("https://bblfish.net/#me")
		println(s"$bbl == bbl2 is " + (bbl == bbl2))



