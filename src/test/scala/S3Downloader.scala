import java.util

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.model.{GetObjectRequest, ListObjectsRequest, ObjectListing, S3Object, S3ObjectInputStream, S3ObjectSummary}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client, AmazonS3ClientBuilder}
import com.amazonaws.services.s3.transfer.TransferManager
import com.lowagie.text.pdf.codec.Base64.InputStream
import org.apache.pdfbox.pdmodel.PDDocument

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class S3Downloader(bucketName: String, pathOfDir: String) {
    //https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/transfer/TransferManager.html#downloadDirectory-java.lang.String-java.lang.String-java.io.File-
    //https://stackoverflow.com/questions/49116960/download-all-the-files-from-a-s3-bucket-using-scala
    //https://docs.aws.amazon.com/AmazonS3/latest/dev/RetrievingObjectUsingJava.html


    val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard()
        //.withRegion(Region.getRegion())
        .withCredentials(new ProfileCredentialsProvider())
        .build()

    def getAllFiles: Array[PDFCases] = {
        val listObjectsRequest = new ListObjectsRequest().
            withBucketName(bucketName).
            withPrefix(pathOfDir).
            withDelimiter("/")

        val objectSummaries: util.List[S3ObjectSummary] = s3Client.listObjects(listObjectsRequest).getObjectSummaries

        var mutableArray = new ArrayBuffer[PDFCases]

        objectSummaries.forEach{ obj: S3ObjectSummary  =>
            val key = obj.getKey
            println("Downloading " + key)

            val stream = s3Client.getObject(new GetObjectRequest(bucketName, key)).getObjectContent.asInstanceOf[InputStream]
            mutableArray += PDFStream(stream)
        }

        mutableArray.toArray
    }

    //TODO get le nom des objets?
    //TODO on peut ouvrir un PDDocument par inputstream, donc simplement lui passer le stream de l'objet qu'on download!
}
