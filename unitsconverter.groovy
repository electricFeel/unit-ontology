import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.apibinding.OWLManager
import uk.ac.ebi.owlapi.extension.*

def infile = new File(args[0])
def patouri = "http://purl.obolibrary.org/obo/"

def l = []

def start = false

def term = false
def exp = null

infile.eachLine {
  if (it.startsWith("[Term]")) {
    term = true
    start = true
  }
  if (!start) {
  } else {
    if (it.startsWith("[Term]")) {
      start = true
      term = true
      if (exp!=null) { l << exp }
      exp = new Expando()
      exp.isa = []
      exp.rel = []
      exp.unit = false
    } else if (it.trim().size()==0) {
      term = false
    } else if (it.trim().startsWith("id:")) {
      exp.id = it.substring(3).trim()
    } else if (it.trim().startsWith("def:")) {
      exp.definition = it.substring(4).trim()
    } else if (it.trim().startsWith("subset: unit_group_slim")) {
      exp.unit = false
    } else if (it.trim().startsWith("subset: unit_slim")) {
      exp.unit = true
    } else if (it.trim().startsWith("relationship: unit_of")) {
      def rel = it.substring(22).trim()
      if (rel.indexOf('!')>-1) {
	rel = rel.substring(0,rel.indexOf('!')).trim()
      }
      exp.rel << rel
    } else if (it.trim().startsWith("name:")) {
      exp.name = it.trim().substring(6)
    } else if (it.trim().startsWith("is_a:")) {
      exp.isa << it.substring(6,it.indexOf('!')).trim()
    }
  }
}

def onturi = "http://bioonto.de/unit.owl#"
OWLOntologyManager man = OWLManager.createOWLOntologyManager();
OWLDataFactory fac = man.getOWLDataFactory()
OWLOntology ont = man.createOntology(IRI.create(onturi))
def unitof = fac.getOWLObjectProperty(IRI.create(onturi+"unit_of"))

l.each {
  def cls = onturi+it.id
  def cl = fac.getOWLClass(IRI.create(cls))
  man.addAxiom(ont, fac.getOWLDeclarationAxiom(cl))
  if (it.unit) {
    //    def cls2 = onturi+"i"+it.id
    def cls2 = onturi+it.id
    def ind = fac.getOWLNamedIndividual(IRI.create(cls2))
    def oneof = fac.getOWLObjectOneOf(ind)
    def equiv = fac.getOWLEquivalentClassesAxiom(cl,oneof)
    man.addAxiom(ont, equiv)
  }
  it.isa.each { sup ->
    def cl2 = fac.getOWLClass(IRI.create(onturi+sup))
    def subc = fac.getOWLSubClassOfAxiom(cl,cl2)
    man.addAxiom(ont, subc)
  }
  def label = fac.getRDFSLabel()
  def definition = fac.getRDFSComment()
  def anno = fac.getOWLAnnotation(label, fac.getOWLTypedLiteral(it.name))
  def annoassert = fac.getOWLAnnotationAssertionAxiom(IRI.create(cls),anno)
  man.addAxiom(ont,annoassert)
  if (it.definition!=null) {
    anno = fac.getOWLAnnotation(definition, fac.getOWLTypedLiteral(it.definition))
    annoassert = fac.getOWLAnnotationAssertionAxiom(IRI.create(cls),anno)
    man.addAxiom(ont,annoassert)
  }
  if (it.rel.size()>0) {
    it.rel = it.rel.collect { fac.getOWLClass(IRI.create(patouri+it.replaceAll(":","_"))) }
    def se = new TreeSet()
    it.rel.each { se << it }
    def cl2 = fac.getOWLObjectUnionOf(se)
    cl2 = fac.getOWLObjectAllValuesFrom(unitof, cl2)
    def subc = fac.getOWLSubClassOfAxiom(cl,cl2)
    man.addAxiom(ont, subc)

  }
}

man.saveOntology(ont, IRI.create("file:/tmp/units.owl"))