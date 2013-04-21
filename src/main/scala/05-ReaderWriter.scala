/*
 *
 */

object ReaderWriter {

  import scala.xml.pull._
  import scala.io.Source
  import scala.collection.mutable
  import scalaz._
  import std.list._
  import syntax.traverse._
  import syntax.comonad._
  import syntax.monad._
  import syntax.writer._

  type PpWriter[+A] = Writer[List[String],A]
  type PpReaderWriter[+A] = ReaderT[PpWriter,String,A]

  def getStream(filename: String) = {
    new XMLEventReader(Source.fromFile(filename)).toStream
  }

  def indented( level: Int, text: String ):PpReaderWriter[String] = Kleisli{
    (indentSeq:String) => ((indentSeq * level) + text).point[PpWriter]
  }

  def verifyNewElement( event: XMLEvent ): PpReaderWriter[Unit] = Kleisli{ _ =>
     (foundElems.headOption,event) match {
       case (Some("msg"),EvElemStart( _ , l , _ , _ )) => List(
         s"WARN: <$l> shouldn't be within <msg>. Msg should only contain text."
       ).tell
       case _ => Nil.tell
     }
  }

  //val indentSeq = "  "
  //val errors:mutable.ListBuffer[String] = mutable.ListBuffer()
  var foundElems = mutable.Stack.empty[String]

  def indentEvent( event: XMLEvent ):PpReaderWriter[String] = event match {
    case EvComment(t) => indented( foundElems.size , s"<!--$t-->" )
    case EvElemStart( _ , l , _ , _ ) => {
      val out = indented( foundElems.size , s"<$l>" )
      foundElems.push( l )
      out
    }
    case EvElemEnd( _ , l ) => {
      foundElems.pop()
      indented( foundElems.size , s"</$l>" )
    }
    case EvText(t) => indented( foundElems.size , t )
    case e => throw new RuntimeException( s"Can't match event: $e" )
  }

  def main(filename: String) = {
    val readerWriters = for ( event <- getStream( filename ).toList ) yield {
      for{
        line <- indentEvent( event )
        _    <- verifyNewElement( event )
      } yield line
    }

    val (errors,lines) = readerWriters.sequenceU.run( "  " ).run
    //Haven't fixed our mutability bug yet.
    errors.foreach( System.err.println _ )
    lines.foreach( println _ )

  }

}