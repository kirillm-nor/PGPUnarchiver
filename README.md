# PGP Unarchiver tool

Wrapping pgp encrypted file to akka stream and iteratively process it.
It will find `.zip`,`.gz`,`tar.gz` archived files and try to decrypt and extract content.

###Build 

Supports sbt and maven build. To run application, build a fat jar first.

`mvn clean package` or `sbt clean assembly`

###Run

You should pass a few mandatory parameters, which described in scopt help:

```
pgp unarchiver 0.1
Usage: pgpunarchiver [options]

  -s, --secret <secret>  AWS Secret Key
  -a, --access <access>  AWS Access Key
  -r, --region <value>   AWS Region
  -p, --path <file>      GPG KeyRing file path
  -b, --bucket <bucket>  AWS Bucket Name
  --phrase <phrase>      Pass phrase of pgp
  --prefix <value>       S3 prefix

```
For example:
`java -jar target/pgpunarchiver.jar --secret <aws_secret> --access <aws_access> --path <path to keys.gpg> --bucket <bucket_name> --phrase <pgp_phrase>`
