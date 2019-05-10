import java.io.{File, InputStream}

abstract sealed class PDFCases(f: Any) {
    def getPDF: Any = f
}
case class PDFFile(f: File) extends PDFCases
case class PDFStream(f: InputStream) extends PDFCases
