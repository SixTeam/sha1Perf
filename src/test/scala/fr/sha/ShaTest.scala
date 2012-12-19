package fr.sha

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import java.io.{BufferedInputStream, RandomAccessFile, FileInputStream, File}
import java.security.{MessageDigest, DigestInputStream}
import fr.sha.SHA._
import com.vidal.glow.GlowTools
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode.READ_ONLY
import java.nio.ByteBuffer
import io.Source

class ShaTest extends FunSuite with ShouldMatchers {

    val _6Mo = new File("/home/ubourdon/Images/Images.7z") // 6 Mo
    val _6Mo_sha1 = "7457a824ec408ea417314a80dd8247135e31540a"

    val _900Mo = new File("/home/ubourdon/SERVER_HTTP/vxp_2013_13-12.7z")

    System.setProperty("jna.library.path", "/home/ubourdon/VALTECH/VIDAL/v2/sha1Perf/lib")

    test("naive jvm implementation with 6 Mo file") {
        val start = System.currentTimeMillis()

        val sha = naiveSum(_6Mo)

        val end = System.currentTimeMillis()
        println("[naive] calcul SHA-1 in %s ms # sha = %s".format((end - start), sha))

        sha should be (_6Mo_sha1)
    }

    test("with buffer jvm implementation with 6 Mo file") {
        val start = System.currentTimeMillis()

        val sha = withBufferSum(_6Mo.getAbsolutePath)

        val end = System.currentTimeMillis()
        println("[withBuffer][6Mo] calcul SHA-1 in %s ms # sha = %s".format((end - start), sha))

        sha should be (_6Mo_sha1)
    }

    test("with buffer jvm implementation with 900 Mo file") {
        val start = System.currentTimeMillis()

        val sha = withBufferSum(_900Mo.getAbsolutePath)

        val end = System.currentTimeMillis()
        println("[withBuffer algo][900Mo] calcul SHA-1 in %s ms # sha = %s".format((end - start), sha))

        sha should be ("10ce3e0512722897b5a9ff13954e10341ad6eb8b")
    }

    test("with nio jvm implementation with 900 Mo file") {
        val start = System.currentTimeMillis()

        val sha = nioSum(_900Mo.getAbsolutePath)

        val end = System.currentTimeMillis()
        println("[nio algo][900Mo] calcul SHA-1 in %s ms # sha = %s".format((end - start), sha))

        sha should be ("10ce3e0512722897b5a9ff13954e10341ad6eb8b")
    }

    test("with scala jvm implementation with 900 Mo file") {
        val start = System.currentTimeMillis()

        val sha = scalaSum(_900Mo)

        val end = System.currentTimeMillis()
        println("[scala algo][900Mo] calcul SHA-1 in %s ms # sha = %s".format((end - start), sha))

        sha should be ("10ce3e0512722897b5a9ff13954e10341ad6eb8b")
    }

    ignore("glow algo jvm implementation with 900 Mo file") {
        val start = System.currentTimeMillis()

        val sha = try{ new GlowTools().verifySHA1(_900Mo, "10ce3e0512722897b5a9ff13954e10341ad6eb8b")} catch { case _ => "erreur"}

        val end = System.currentTimeMillis()
        println("[glow algo] calcul SHA-1 in %s ms # sha = %s".format((end - start), sha))
    }
}

object SHA {
    def naiveSum(file: File): String = {
        var stream: FileInputStream = null
        var dis: DigestInputStream = null
        val md: MessageDigest = MessageDigest.getInstance("SHA-1")
        stream = new FileInputStream(file)
        dis = new DigestInputStream(stream, md)
        dis.on(true)
        // we need to read all the file for md to get the proper digest computed
        while (dis.read != -1) {}
        val b: Array[Byte] = md.digest
        val localSha1Sum = hexEncode(b)

        localSha1Sum
    }

    def withBufferSum(filePath: String): String = {
        val sha1 = MessageDigest.getInstance("SHA1")
        val fileStream = new BufferedInputStream( new FileInputStream(filePath) )

        val chunk = new Array[Byte](8*1024)

        var read: Int = 0

        while (read != -1) {
            read = fileStream.read(chunk)
            if (read != -1) sha1.update(chunk, 0, read)
        }

        val hashBytes = sha1.digest()

        val sb = new StringBuilder()
        for (i <- 0 until hashBytes.length) {
            sb.append( Integer.toString( (hashBytes(i) & 0xff) + 0x100, 16).substring(1) )
        }

        sb.toString()
    }

    def nioSum(filePath: String): String = {
        /*val sha1 = MessageDigest.getInstance("SHA1")
        val aFile = new RandomAccessFile(filePath, "r")
        //val aFile = new FileInputStream( filePath )
        val inChannel: FileChannel = aFile.getChannel

        //readFileWithDirectByteBuffer(inChannel, (bytes: Byte) => sha1.update( bytes ))   // marche meme pas
        readFileClassic(inChannel, (bytes: ByteBuffer) => sha1.update( bytes ) )  // 3848ms
        //readFileWithMappedByteBuffer(inChannel, (bytes: Byte) => sha1.update( bytes ))   // 19s


        aFile.close()*/

        val sha1 = MessageDigest.getInstance("SHA1")

        val memoryfileStream = new RandomAccessFile(filePath, "r")
        val channel = memoryfileStream.getChannel
        val buffer = channel.map( READ_ONLY, 0L, channel.size )
        while ( buffer.hasRemaining ) sha1.update(buffer)

        val hashBytes = sha1.digest()

        val sb = new StringBuilder()
        hashBytes.foreach( byte => sb.append( Integer.toString( (byte & 0xff) + 0x100, 16).substring(1) ) )

        sb.toString()
    }

    def scalaSum(file: File): String = {
        val sha1 = MessageDigest.getInstance("SHA1")
        //val fileStream = Source.fromFile(file, "iso8859-1").bufferedReader()
        val fileStream = new BufferedInputStream( new FileInputStream(file) )

        val chunk = new Array[Byte](8*1024)
        var read: Int = 0
        while (read != -1) {
            read = fileStream.read(chunk)
            if (read != -1) sha1.update(chunk, 0, read)
        }

        val hashBytes = sha1.digest

        val sb = new StringBuilder()
        hashBytes.foreach( byte => sb.append( Integer.toString( (byte & 0xff) + 0x100, 16).substring(1) ) )

        sb.toString
    }

    private def readFileWithDirectByteBuffer(ch: FileChannel, f: (Byte) => Unit) {
        val SIZE = 1024
        val BIGSIZE = 8*1024

        var bb = ByteBuffer.allocateDirect( BIGSIZE )
        var barray = new Array[Byte](SIZE)
        var nRead: Int = 0
        var nGet: Int = 0
        while ( nRead != -1 ) {
            nRead = ch.read( bb )
            //if ( nRead == 0 ) continue
            bb.position( 0 )
            bb.limit( nRead )
            while( bb.hasRemaining() ) {
                nGet = Math.min( bb.remaining( ), SIZE )
                bb.get( barray, 0, nGet )
                for ( i <- 0 until nGet ) f(barray(i))
            }
            bb.clear( )
        }
    }

    private def readFileClassic(inChannel: FileChannel, f: (ByteBuffer) => Unit) {
        val chunk = ByteBuffer.allocate(8*1024)
        var bytesRead = inChannel.read(chunk) //read into buffer.
        while (bytesRead != -1) {

            chunk.flip()  //make buffer ready for read

            f(chunk)//, 0, bytesRead

            chunk.clear() //make buffer ready for writing
            bytesRead = inChannel.read(chunk)
        }
    }

    //18 s ca pue !!!
    private def readFileWithMappedByteBuffer( inChannel: FileChannel, f: (Byte) => Unit ) {
        val mappedByteBuffer = inChannel.map( FileChannel.MapMode.READ_ONLY, 0L, inChannel.size() )
        val chunk = new Array[Byte](8*1024)      //ByteBuffer.allocate
        var nGet: Int = 0
        while( mappedByteBuffer.hasRemaining ) {
            nGet = Math.min( mappedByteBuffer.remaining( ), 8*1024 )
            mappedByteBuffer.get(chunk, 0, nGet)
            for (i <- 0 until nGet) f( chunk(i) )//, 0, bytesRead
        }
    }


    private def hexEncode(in: Array[Byte]): String = {
        val sb = new StringBuilder
        val len = in.length
        def addDigit(in: Array[Byte], pos: Int, len: Int, sb: StringBuilder) {
            if (pos < len) {
                val b: Int = in(pos)
                val msb = (b & 0xf0) >> 4
                val lsb = (b & 0x0f)
                sb.append((if (msb < 10) ('0' + msb).asInstanceOf[Char] else ('a' + (msb - 10)).asInstanceOf[Char]))
                sb.append((if (lsb < 10) ('0' + lsb).asInstanceOf[Char] else ('a' + (lsb - 10)).asInstanceOf[Char]))

                addDigit(in, pos + 1, len, sb)
            }
        }
        addDigit(in, 0, len, sb)
        sb.toString()
    }
}