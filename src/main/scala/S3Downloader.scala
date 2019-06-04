import java.io.InputStream

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}


class S3Downloader(bucketName: String, region: String = "us-east-1") {
  //https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/transfer/TransferManager.html#downloadDirectory-java.lang.String-java.lang.String-java.io.File-
  //https://stackoverflow.com/questions/49116960/download-all-the-files-from-a-s3-bucket-using-scala
  //https://docs.aws.amazon.com/AmazonS3/latest/dev/RetrievingObjectUsingJava.html

  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard()
    .withRegion(region)
    .withCredentials(new ProfileCredentialsProvider())
    .build()

  def download(key: String): InputStream = {
    val obj: S3Object = s3Client.getObject(new GetObjectRequest(bucketName, key))

    println("S3Object content type: " + obj.getObjectMetadata.getContentType)

    obj.getObjectContent
  }

  /*
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
*/
  //TODO get le nom des objets?
  //TODO on peut ouvrir un PDDocument par inputstream, donc simplement lui passer le stream de l'objet qu'on download!
}
